package me.tuplugin.privatechest;

import me.tuplugin.privatechest.storage.Protection;
import me.tuplugin.privatechest.storage.ProtectionKey;
import me.tuplugin.privatechest.storage.ProtectionStore;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

/**
 * Locking, unlocking and ownership checks for protected containers.
 *
 * <p>State lives in {@link ProtectionStore} as of 3.0. Every mutation here is persisted
 * automatically by the store, so no caller needs to trigger a save.</p>
 */
public class ChestLocker {

    private static ChestLocker instance;

    private final PrivateChest plugin;
    private final ProtectionStore store;

    public ChestLocker(PrivateChest plugin, ProtectionStore store) {
        this.plugin = plugin;
        this.store = store;
        instance = this;
    }

    public static ChestLocker getInstance() {
        return instance;
    }

    public ProtectionStore getStore() {
        return store;
    }

    /**
     * Locks a container with a hashed password.
     *
     * @return false if the block is invalid, already locked, or the password could not be hashed
     */
    public boolean lockChest(Block block, Player player, String password) {
        if (block == null || player == null || password == null || password.isEmpty()) {
            return false;
        }

        ProtectionKey key = ProtectionKey.of(block);
        if (key == null || store.isProtected(key)) {
            return false;
        }

        String hashedPassword = PasswordManager.hashPassword(password);
        if (hashedPassword == null) {
            plugin.getLogger().warning("Could not hash the password for the container at " + key.serialize()
                    + ". The container was not locked.");
            return false;
        }

        Protection protection = new Protection(player.getUniqueId(), hashedPassword,
                block.getType().name(), null);
        return store.add(key, protection);
    }

    /**
     * Verifies a password against a locked container, migrating legacy plain text entries on a
     * successful match.
     *
     * @return true if the password is correct
     */
    public boolean unlockChest(Block block, Player player, String password) {
        ProtectionKey key = ProtectionKey.of(block);
        if (key == null || password == null) {
            return false;
        }

        Protection protection = store.get(key);
        if (protection == null) {
            return false;
        }

        String stored = protection.getPasswordHash();
        if (!PasswordManager.verifyPassword(password, stored)) {
            return false;
        }

        // Correct password: take the opportunity to rewrite it in the current format. This is how a
        // server upgrades its stored passwords, without a bulk rehashing pause at startup.
        if (PasswordManager.needsRehash(stored)) {
            String upgraded = PasswordManager.hashPassword(password);
            if (upgraded != null) {
                store.replace(key, protection.withPasswordHash(upgraded));
                plugin.getLogger().fine("Rehashed the password for the container at " + key.serialize() + ".");
            }
        }

        return true;
    }

    public boolean isChestLocked(Block block) {
        return store.isProtected(ProtectionKey.of(block));
    }

    public boolean isOwner(Block block, Player player) {
        if (player == null) {
            return false;
        }
        Protection protection = store.get(ProtectionKey.of(block));
        return protection != null && protection.isOwnedBy(player.getUniqueId());
    }

    /**
     * The owner's UUID as a string, or null if the container is not locked.
     */
    public String getOwnerUUID(Block block) {
        Protection protection = store.get(ProtectionKey.of(block));
        return protection == null ? null : protection.getOwner().toString();
    }

    /**
     * The owner's UUID, or null if the container is not locked.
     */
    public UUID getOwner(Block block) {
        Protection protection = store.get(ProtectionKey.of(block));
        return protection == null ? null : protection.getOwner();
    }

    /**
     * The full protection record, or null if the container is not locked.
     */
    public Protection getProtection(Block block) {
        return store.get(ProtectionKey.of(block));
    }

    /**
     * Removes protection from a container.
     *
     * @return true if the container was protected
     */
    public boolean removeProtection(Block block) {
        return store.remove(ProtectionKey.of(block)) != null;
    }

    /**
     * Hashes any password still stored as plain text by a pre-2.0 version.
     *
     * <p>Runs once at startup, because plain text on disk is worth removing immediately. Passwords in
     * the older hashed format are left for the lazy upgrade in {@link #unlockChest}, so a large server
     * does not pause at boot to rehash data that is already hashed.</p>
     */
    public void migrateAllPasswords() {
        int plainText = 0;
        for (Protection protection : store.view().values()) {
            if (PasswordManager.isLegacyPlainText(protection.getPasswordHash())) {
                plainText++;
            }
        }

        if (plainText == 0) {
            return;
        }
        if (plainText > 250) {
            plugin.getLogger().info("Hashing " + plainText + " plain text password(s) left by an old version. "
                    + "This may take a few seconds.");
        }

        int migrated = 0;
        int failed = 0;

        for (Map.Entry<ProtectionKey, Protection> entry : store.view().entrySet()) {
            Protection protection = entry.getValue();
            if (!PasswordManager.isLegacyPlainText(protection.getPasswordHash())) {
                continue;
            }

            String hashed = PasswordManager.hashPassword(protection.getPasswordHash());
            if (hashed != null) {
                store.replace(entry.getKey(), protection.withPasswordHash(hashed));
                migrated++;
            } else {
                plugin.getLogger().warning("Could not hash the stored password for the container at "
                        + entry.getKey().serialize() + ".");
                failed++;
            }
        }

        if (migrated > 0) {
            plugin.getLogger().info("Hashed " + migrated + " password(s) that were stored as plain text.");
        }
        if (failed > 0) {
            plugin.getLogger().warning(failed + " password(s) could not be hashed and remain plain text.");
        }
    }

    /**
     * Total number of protected containers.
     */
    public int getProtectionCount() {
        return store.size();
    }

    // --- Legacy helpers kept for compatibility ---

    /**
     * @deprecated use {@link ProtectionKey#serialize()}.
     */
    @Deprecated
    public static String serializeLocation(Location loc) {
        ProtectionKey key = ProtectionKey.of(loc);
        return key == null ? "unknown" : key.serialize();
    }

    /**
     * @deprecated use {@link ProtectionKey#deserialize(String)}.
     */
    @Deprecated
    public static Location deserializeLocation(String serialized) {
        ProtectionKey key = ProtectionKey.deserialize(serialized);
        return key == null ? null : key.toLocation();
    }
}
