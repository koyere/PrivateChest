package me.tuplugin.privatechest;

import me.tuplugin.privatechest.storage.PersistenceQueue;
import me.tuplugin.privatechest.storage.TrustSource;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Trust relations between container owners and the players they grant access to.
 *
 * <p>As of 3.0 every mutation reports itself to the persistence worker, so commands no longer trigger
 * a full data rewrite, and trust is stored correctly on both backends.</p>
 */
public class TrustManager implements TrustSource {

    /** Owner UUID string to the set of UUID strings they trust. */
    private final Map<String, Set<String>> trustRelations = new ConcurrentHashMap<>();

    private static TrustManager instance;
    private final PrivateChest plugin;

    private volatile PersistenceQueue persistence;

    public TrustManager(PrivateChest plugin) {
        this.plugin = plugin;
        instance = this;
    }

    public static TrustManager getInstance() {
        return instance;
    }

    public void setPersistence(PersistenceQueue persistence) {
        this.persistence = persistence;
    }

    // --- Mutations ---

    /**
     * Grants a player access to the owner's containers.
     *
     * @return false if the relation already existed
     */
    public boolean trustPlayer(Player owner, Player trusted) {
        if (owner == null || trusted == null) {
            return false;
        }
        return trustPlayer(owner.getUniqueId().toString(), trusted.getUniqueId().toString());
    }

    /**
     * Grants a player access using UUID strings.
     *
     * @return false if the relation already existed, or the owner and target are the same player
     */
    public boolean trustPlayer(String ownerUUID, String trustedUUID) {
        if (ownerUUID == null || trustedUUID == null || ownerUUID.equals(trustedUUID)) {
            return false;
        }

        boolean added = trustRelations
                .computeIfAbsent(ownerUUID, k -> ConcurrentHashMap.newKeySet())
                .add(trustedUUID);

        if (added) {
            markDirty(ownerUUID);
        }
        return added;
    }

    /**
     * Revokes a player's access.
     *
     * @return false if the relation did not exist
     */
    public boolean untrustPlayer(Player owner, Player trusted) {
        if (owner == null || trusted == null) {
            return false;
        }
        return untrustPlayer(owner.getUniqueId().toString(), trusted.getUniqueId().toString());
    }

    /**
     * Revokes a player's access using UUID strings.
     *
     * @return false if the relation did not exist
     */
    public boolean untrustPlayer(String ownerUUID, String trustedUUID) {
        if (ownerUUID == null || trustedUUID == null) {
            return false;
        }

        Set<String> trusted = trustRelations.get(ownerUUID);
        if (trusted == null) {
            return false;
        }

        boolean removed = trusted.remove(trustedUUID);
        if (trusted.isEmpty()) {
            trustRelations.remove(ownerUUID, trusted);
        }
        if (removed) {
            markDirty(ownerUUID);
        }
        return removed;
    }

    /**
     * Drops an owner's entire trusted list.
     *
     * @return the number of relations removed
     */
    public int removeOwner(String ownerUUID) {
        if (ownerUUID == null) {
            return 0;
        }
        Set<String> removed = trustRelations.remove(ownerUUID);
        if (removed == null || removed.isEmpty()) {
            return 0;
        }
        markDirty(ownerUUID);
        return removed.size();
    }

    /**
     * Removes a player from the system entirely, both as an owner and as a trusted player.
     */
    public void removePlayerCompletely(String playerUUID) {
        if (playerUUID == null) {
            return;
        }

        removeOwner(playerUUID);

        for (Map.Entry<String, Set<String>> entry : trustRelations.entrySet()) {
            Set<String> trusted = entry.getValue();
            if (trusted.remove(playerUUID)) {
                if (trusted.isEmpty()) {
                    trustRelations.remove(entry.getKey(), trusted);
                }
                markDirty(entry.getKey());
            }
        }
    }

    // --- Queries ---

    public boolean isTrusted(Player owner, Player accessor) {
        if (owner == null || accessor == null) {
            return false;
        }
        return isTrusted(owner.getUniqueId().toString(), accessor.getUniqueId().toString());
    }

    public boolean isTrusted(String ownerUUID, String accessorUUID) {
        if (ownerUUID == null || accessorUUID == null) {
            return false;
        }
        if (ownerUUID.equals(accessorUUID)) {
            return true;
        }
        Set<String> trusted = trustRelations.get(ownerUUID);
        return trusted != null && trusted.contains(accessorUUID);
    }

    public Set<String> getTrustedPlayers(Player owner) {
        return owner == null ? new HashSet<>() : getTrustedPlayers(owner.getUniqueId().toString());
    }

    public Set<String> getTrustedPlayers(String ownerUUID) {
        if (ownerUUID == null) {
            return new HashSet<>();
        }
        Set<String> trusted = trustRelations.get(ownerUUID);
        return trusted == null ? new HashSet<>() : new HashSet<>(trusted);
    }

    /**
     * Owners who trust the given player.
     */
    public Set<String> getOwnersTrusting(String trustedUUID) {
        if (trustedUUID == null) {
            return new HashSet<>();
        }
        Set<String> owners = new HashSet<>();
        for (Map.Entry<String, Set<String>> entry : trustRelations.entrySet()) {
            if (entry.getValue().contains(trustedUUID)) {
                owners.add(entry.getKey());
            }
        }
        return owners;
    }

    public Set<String> getOwnersTrusting(Player trustedPlayer) {
        return trustedPlayer == null ? new HashSet<>() : getOwnersTrusting(trustedPlayer.getUniqueId().toString());
    }

    /**
     * Snapshot of every owner that has a trusted list. Safe to iterate while mutating.
     */
    public Set<String> owners() {
        return new HashSet<>(trustRelations.keySet());
    }

    /**
     * Total number of individual trust relations.
     */
    public int getTotalTrustCount() {
        int total = 0;
        for (Set<String> trusted : trustRelations.values()) {
            total += trusted.size();
        }
        return total;
    }

    /**
     * Read-only view of all relations.
     */
    public Map<String, Set<String>> view() {
        return Collections.unmodifiableMap(trustRelations);
    }

    // --- Persistence hooks ---

    /**
     * Replaces all relations with freshly loaded data, without marking anything dirty.
     */
    public void loadAll(Map<String, Set<String>> loaded) {
        trustRelations.clear();
        if (loaded == null) {
            return;
        }
        for (Map.Entry<String, Set<String>> entry : loaded.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }
            Set<String> trusted = ConcurrentHashMap.newKeySet();
            trusted.addAll(entry.getValue());
            trustRelations.put(entry.getKey(), trusted);
        }
    }

    @Override
    public Set<String> getTrustedSnapshot(String ownerUuid) {
        return getTrustedPlayers(ownerUuid);
    }

    @Override
    public Map<String, Set<String>> snapshotAllTrust() {
        Map<String, Set<String>> copy = new HashMap<>(trustRelations.size() * 2);
        for (Map.Entry<String, Set<String>> entry : trustRelations.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                copy.put(entry.getKey(), new HashSet<>(entry.getValue()));
            }
        }
        return copy;
    }

    private void markDirty(String ownerUUID) {
        PersistenceQueue queue = this.persistence;
        if (queue != null) {
            queue.markTrustDirty(ownerUUID);
        }
    }

    // --- Helpers ---

    /**
     * Best-effort player name for a UUID, for display in trust lists.
     */
    public String getPlayerName(String uuid) {
        if (uuid == null) {
            return null;
        }

        try {
            UUID parsed = UUID.fromString(uuid);
            Player online = Bukkit.getPlayer(parsed);
            if (online != null) {
                return online.getName();
            }
            return Bukkit.getOfflinePlayer(parsed).getName();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
