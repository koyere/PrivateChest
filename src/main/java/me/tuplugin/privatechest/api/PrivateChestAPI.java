package me.tuplugin.privatechest.api;

import me.tuplugin.privatechest.ChestLocker;
import me.tuplugin.privatechest.PrivateChest;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Public API for PrivateChest plugin.
 * Provides safe methods for other plugins to interact with protected chests.
 *
 * @since 1.4
 */
public class PrivateChestAPI {

    /**
     * Checks if a block is locked by PrivateChest.
     *
     * @param block The block to check
     * @return true if the block is locked, false otherwise
     * @throws IllegalArgumentException if block is null
     */
    public static boolean isLocked(Block block) {
        if (block == null) {
            throw new IllegalArgumentException("Block cannot be null");
        }

        ChestLocker locker = getChestLocker();
        if (locker == null) return false;

        return locker.isChestLocked(block);
    }

    /**
     * Checks if a player owns a locked block.
     *
     * @param block The block to check
     * @param player The player to check ownership for
     * @return true if the player owns the block, false otherwise
     * @throws IllegalArgumentException if block or player is null
     */
    public static boolean isOwner(Block block, Player player) {
        if (block == null) {
            throw new IllegalArgumentException("Block cannot be null");
        }
        if (player == null) {
            throw new IllegalArgumentException("Player cannot be null");
        }

        ChestLocker locker = getChestLocker();
        if (locker == null) return false;

        return locker.isOwner(block, player);
    }

    /**
     * Checks if a player owns a locked block by UUID.
     *
     * @param block The block to check
     * @param playerUUID The UUID of the player to check ownership for
     * @return true if the player owns the block, false otherwise
     * @throws IllegalArgumentException if block or playerUUID is null
     */
    public static boolean isOwner(Block block, UUID playerUUID) {
        if (block == null) {
            throw new IllegalArgumentException("Block cannot be null");
        }
        if (playerUUID == null) {
            throw new IllegalArgumentException("Player UUID cannot be null");
        }

        ChestLocker locker = getChestLocker();
        if (locker == null) return false;

        String ownerUUID = locker.getOwnerUUID(block);
        return ownerUUID != null && ownerUUID.equals(playerUUID.toString());
    }

    /**
     * Locks a block with a password for a specific player.
     *
     * @param block The block to lock
     * @param player The player who will own the lock
     * @param password The password to set for the lock
     * @return true if the block was successfully locked, false otherwise
     * @throws IllegalArgumentException if any parameter is null or if password is empty
     * @throws UnsupportedOperationException if the block type is not lockable
     */
    public static boolean lockBlock(Block block, Player player, String password) {
        if (block == null) {
            throw new IllegalArgumentException("Block cannot be null");
        }
        if (player == null) {
            throw new IllegalArgumentException("Player cannot be null");
        }
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        if (!isLockableContainer(block.getType())) {
            throw new UnsupportedOperationException("Block type " + block.getType() + " is not lockable");
        }

        ChestLocker locker = getChestLocker();
        if (locker == null) return false;

        boolean success = locker.lockChest(block, player, password);
        if (success) {
            // Save data after successful lock
            PrivateChest plugin = PrivateChest.getInstance();
            if (plugin != null && plugin.getDataManager() != null) {
                plugin.getDataManager().saveData();
            }
        }

        return success;
    }

    /**
     * Unlocks a block by removing its protection.
     * This method bypasses password verification - use with caution!
     *
     * @param block The block to unlock
     * @return true if the block was successfully unlocked, false otherwise
     * @throws IllegalArgumentException if block is null
     */
    public static boolean unlockBlock(Block block) {
        if (block == null) {
            throw new IllegalArgumentException("Block cannot be null");
        }

        ChestLocker locker = getChestLocker();
        if (locker == null) return false;

        if (!locker.isChestLocked(block)) {
            return false; // Block wasn't locked
        }

        locker.removeProtection(block);

        // Save data after successful unlock
        PrivateChest plugin = PrivateChest.getInstance();
        if (plugin != null && plugin.getDataManager() != null) {
            plugin.getDataManager().saveData();
        }

        return true;
    }

    /**
     * Gets the owner UUID of a locked block.
     *
     * @param block The block to check
     * @return The UUID of the owner, or null if the block is not locked
     * @throws IllegalArgumentException if block is null
     */
    public static UUID getOwner(Block block) {
        if (block == null) {
            throw new IllegalArgumentException("Block cannot be null");
        }

        ChestLocker locker = getChestLocker();
        if (locker == null) return null;

        String ownerUUID = locker.getOwnerUUID(block);
        if (ownerUUID == null) return null;

        try {
            return UUID.fromString(ownerUUID);
        } catch (IllegalArgumentException e) {
            return null; // Invalid UUID format
        }
    }

    /**
     * Checks if a material type can be locked by PrivateChest.
     *
     * @param material The material to check
     * @return true if the material can be locked, false otherwise
     * @throws IllegalArgumentException if material is null
     */
    public static boolean isLockableContainer(Material material) {
        if (material == null) {
            throw new IllegalArgumentException("Material cannot be null");
        }

        if (material == Material.CHEST || material == Material.TRAPPED_CHEST || material == Material.BARREL) {
            return true;
        }
        
        // Support all shulker box variants
        return material.name().contains("SHULKER_BOX");
    }

    /**
     * Gets the total number of locked chests on the server.
     *
     * @return The number of locked chests
     */
    public static int getLockedChestCount() {
        ChestLocker locker = getChestLocker();
        if (locker == null) return 0;

        return locker.getChestOwners().size();
    }

    /**
     * Checks if the PrivateChest plugin is loaded and available.
     *
     * @return true if the plugin is available, false otherwise
     */
    public static boolean isAvailable() {
        return PrivateChest.getInstance() != null && getChestLocker() != null;
    }

    /**
     * Gets the version of the PrivateChest plugin.
     *
     * @return The plugin version, or null if the plugin is not available
     */
    public static String getVersion() {
        PrivateChest plugin = PrivateChest.getInstance();
        if (plugin == null) return null;

        return plugin.getDescription().getVersion();
    }

    // --- Private Helper Methods ---

    /**
     * Gets the ChestLocker instance safely.
     */
    private static ChestLocker getChestLocker() {
        PrivateChest plugin = PrivateChest.getInstance();
        if (plugin == null) {
            return null;
        }

        return ChestLocker.getInstance();
    }
}