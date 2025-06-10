package me.tuplugin.privatechest;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Manages trust relationships between players.
 * Allows chest owners to give access to other players without sharing passwords.
 */
public class TrustManager {

    // Map: Owner UUID -> Set of trusted player UUIDs
    private final Map<String, Set<String>> trustRelations = new HashMap<>();

    private static TrustManager instance;
    private final PrivateChest plugin;

    public TrustManager(PrivateChest plugin) {
        this.plugin = plugin;
        instance = this;
    }

    public static TrustManager getInstance() {
        return instance;
    }

    /**
     * Adds a trust relationship between two players.
     * @param owner The player who owns chests
     * @param trusted The player to trust
     * @return true if trust was added, false if already existed
     */
    public boolean trustPlayer(Player owner, Player trusted) {
        if (owner == null || trusted == null) return false;
        if (owner.getUniqueId().equals(trusted.getUniqueId())) return false; // Can't trust yourself

        String ownerUUID = owner.getUniqueId().toString();
        String trustedUUID = trusted.getUniqueId().toString();

        trustRelations.computeIfAbsent(ownerUUID, k -> new HashSet<>());
        return trustRelations.get(ownerUUID).add(trustedUUID);
    }

    /**
     * Adds a trust relationship using UUIDs.
     */
    public boolean trustPlayer(String ownerUUID, String trustedUUID) {
        if (ownerUUID == null || trustedUUID == null) return false;
        if (ownerUUID.equals(trustedUUID)) return false;

        trustRelations.computeIfAbsent(ownerUUID, k -> new HashSet<>());
        return trustRelations.get(ownerUUID).add(trustedUUID);
    }

    /**
     * Removes a trust relationship between two players.
     * @param owner The player who owns chests
     * @param trusted The player to untrust
     * @return true if trust was removed, false if didn't exist
     */
    public boolean untrustPlayer(Player owner, Player trusted) {
        if (owner == null || trusted == null) return false;

        String ownerUUID = owner.getUniqueId().toString();
        String trustedUUID = trusted.getUniqueId().toString();

        Set<String> trusted_players = trustRelations.get(ownerUUID);
        if (trusted_players == null) return false;

        boolean removed = trusted_players.remove(trustedUUID);

        // Clean up empty sets
        if (trusted_players.isEmpty()) {
            trustRelations.remove(ownerUUID);
        }

        return removed;
    }

    /**
     * Removes a trust relationship using UUIDs.
     */
    public boolean untrustPlayer(String ownerUUID, String trustedUUID) {
        if (ownerUUID == null || trustedUUID == null) return false;

        Set<String> trusted_players = trustRelations.get(ownerUUID);
        if (trusted_players == null) return false;

        boolean removed = trusted_players.remove(trustedUUID);

        // Clean up empty sets
        if (trusted_players.isEmpty()) {
            trustRelations.remove(ownerUUID);
        }

        return removed;
    }

    /**
     * Checks if a player is trusted by another player.
     * @param owner The owner of chests
     * @param accessor The player trying to access
     * @return true if accessor is trusted by owner
     */
    public boolean isTrusted(Player owner, Player accessor) {
        if (owner == null || accessor == null) return false;
        if (owner.getUniqueId().equals(accessor.getUniqueId())) return true; // Owner always trusts themselves

        String ownerUUID = owner.getUniqueId().toString();
        String accessorUUID = accessor.getUniqueId().toString();

        return isTrusted(ownerUUID, accessorUUID);
    }

    /**
     * Checks if a player is trusted using UUIDs.
     */
    public boolean isTrusted(String ownerUUID, String accessorUUID) {
        if (ownerUUID == null || accessorUUID == null) return false;
        if (ownerUUID.equals(accessorUUID)) return true;

        Set<String> trusted_players = trustRelations.get(ownerUUID);
        return trusted_players != null && trusted_players.contains(accessorUUID);
    }

    /**
     * Gets all players trusted by a specific owner.
     * @param owner The owner player
     * @return Set of trusted player UUIDs (empty if none)
     */
    public Set<String> getTrustedPlayers(Player owner) {
        if (owner == null) return new HashSet<>();
        return getTrustedPlayers(owner.getUniqueId().toString());
    }

    /**
     * Gets all players trusted by a specific owner using UUID.
     */
    public Set<String> getTrustedPlayers(String ownerUUID) {
        if (ownerUUID == null) return new HashSet<>();

        Set<String> trusted = trustRelations.get(ownerUUID);
        return trusted != null ? new HashSet<>(trusted) : new HashSet<>();
    }

    /**
     * Gets all players who trust a specific player.
     * @param trustedPlayer The player who might be trusted
     * @return Set of owner UUIDs who trust this player
     */
    public Set<String> getOwnersTrusting(Player trustedPlayer) {
        if (trustedPlayer == null) return new HashSet<>();
        return getOwnersTrusting(trustedPlayer.getUniqueId().toString());
    }

    /**
     * Gets all players who trust a specific player using UUID.
     */
    public Set<String> getOwnersTrusting(String trustedUUID) {
        if (trustedUUID == null) return new HashSet<>();

        Set<String> owners = new HashSet<>();
        for (Map.Entry<String, Set<String>> entry : trustRelations.entrySet()) {
            if (entry.getValue().contains(trustedUUID)) {
                owners.add(entry.getKey());
            }
        }
        return owners;
    }

    /**
     * Removes all trust relationships for a player (when they leave permanently).
     * @param playerUUID The UUID of the player to remove
     */
    public void removePlayerCompletely(String playerUUID) {
        if (playerUUID == null) return;

        // Remove as owner
        trustRelations.remove(playerUUID);

        // Remove as trusted player from all relationships
        for (Set<String> trustedSet : trustRelations.values()) {
            trustedSet.remove(playerUUID);
        }

        // Clean up empty sets
        trustRelations.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    /**
     * Gets all trust relationships (for storage purposes).
     */
    public Map<String, Set<String>> getAllTrustRelations() {
        return trustRelations;
    }

    /**
     * Clears all trust relationships.
     */
    public void clearAllTrust() {
        trustRelations.clear();
    }

    /**
     * Gets the total number of trust relationships.
     */
    public int getTotalTrustCount() {
        return trustRelations.values().stream().mapToInt(Set::size).sum();
    }

    /**
     * Gets player name safely, handling offline players.
     */
    public String getPlayerName(String uuid) {
        if (uuid == null) return "Unknown";

        try {
            Player player = Bukkit.getPlayer(UUID.fromString(uuid));
            if (player != null) {
                return player.getName();
            }

            // Try to get offline player name
            return Bukkit.getOfflinePlayer(UUID.fromString(uuid)).getName();
        } catch (IllegalArgumentException e) {
            return "Unknown";
        }
    }
}