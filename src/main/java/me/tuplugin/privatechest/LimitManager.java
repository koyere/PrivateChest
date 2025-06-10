package me.tuplugin.privatechest;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Manages chest locking limits based on player permissions.
 * Allows server administrators to control how many chests players can lock.
 */
public class LimitManager {

    private final PrivateChest plugin;
    private final ChestLocker chestLocker;

    private static LimitManager instance;

    public LimitManager(PrivateChest plugin) {
        this.plugin = plugin;
        this.chestLocker = ChestLocker.getInstance();
        instance = this;
    }

    public static LimitManager getInstance() {
        return instance;
    }

    /**
     * Gets the maximum number of chests a player can lock based on their permissions.
     * @param player The player to check
     * @return The maximum number of chests, or -1 for unlimited
     */
    public int getPlayerLimit(Player player) {
        // Check if limits are enabled
        if (!plugin.getConfig().getBoolean("enable-chest-limits", false)) {
            return -1; // Unlimited when disabled
        }

        // Check for unlimited permission first
        if (player.hasPermission("privatechest.limit.unlimited")) {
            return -1; // Unlimited
        }

        // Check for specific numeric limits (highest number wins)
        int maxLimit = plugin.getConfig().getInt("default-chest-limit", 5);

        // Check permissions in descending order to find the highest limit
        int[] checkLimits = {1000, 500, 100, 50, 25, 20, 15, 10, 5, 3, 1};

        for (int limit : checkLimits) {
            if (player.hasPermission("privatechest.limit." + limit)) {
                maxLimit = Math.max(maxLimit, limit);
                break; // Take the first (highest) match
            }
        }

        return maxLimit;
    }

    /**
     * Gets the current number of chests locked by a player.
     * @param player The player to check
     * @return The number of locked chests
     */
    public int getPlayerChestCount(Player player) {
        String playerUUID = player.getUniqueId().toString();
        Map<Location, String> chestOwners = chestLocker.getChestOwners();

        int count = 0;
        for (String ownerUUID : chestOwners.values()) {
            if (playerUUID.equals(ownerUUID)) {
                count++;
            }
        }

        return count;
    }

    /**
     * Gets the current number of chests locked by a player using UUID.
     * @param playerUUID The player's UUID as string
     * @return The number of locked chests
     */
    public int getPlayerChestCount(String playerUUID) {
        Map<Location, String> chestOwners = chestLocker.getChestOwners();

        int count = 0;
        for (String ownerUUID : chestOwners.values()) {
            if (playerUUID.equals(ownerUUID)) {
                count++;
            }
        }

        return count;
    }

    /**
     * Checks if a player can lock more chests.
     * @param player The player to check
     * @return true if the player can lock more chests, false otherwise
     */
    public boolean canPlayerLockMore(Player player) {
        int limit = getPlayerLimit(player);
        if (limit == -1) {
            return true; // Unlimited
        }

        int currentCount = getPlayerChestCount(player);
        return currentCount < limit;
    }

    /**
     * Checks if a player can lock a specific number of additional chests.
     * @param player The player to check
     * @param additionalChests The number of additional chests to lock
     * @return true if the player can lock that many more chests
     */
    public boolean canPlayerLockAdditional(Player player, int additionalChests) {
        int limit = getPlayerLimit(player);
        if (limit == -1) {
            return true; // Unlimited
        }

        int currentCount = getPlayerChestCount(player);
        return (currentCount + additionalChests) <= limit;
    }

    /**
     * Gets the remaining number of chests a player can lock.
     * @param player The player to check
     * @return The number of remaining locks, or -1 for unlimited
     */
    public int getRemainingLocks(Player player) {
        int limit = getPlayerLimit(player);
        if (limit == -1) {
            return -1; // Unlimited
        }

        int currentCount = getPlayerChestCount(player);
        return Math.max(0, limit - currentCount);
    }

    /**
     * Gets a formatted string describing the player's limit status.
     * @param player The player to check
     * @return A formatted string like "5/10" or "5/Unlimited"
     */
    public String getLimitStatus(Player player) {
        int currentCount = getPlayerChestCount(player);
        int limit = getPlayerLimit(player);

        if (limit == -1) {
            return currentCount + "/Unlimited";
        } else {
            return currentCount + "/" + limit;
        }
    }

    /**
     * Checks if chest limits are enabled in the configuration.
     * @return true if limits are enabled, false otherwise
     */
    public boolean areLimitsEnabled() {
        return plugin.getConfig().getBoolean("enable-chest-limits", false);
    }

    /**
     * Gets the default limit for players without specific permissions.
     * @return The default limit
     */
    public int getDefaultLimit() {
        return plugin.getConfig().getInt("default-chest-limit", 5);
    }

    /**
     * Gets a warning message when a player is approaching their limit.
     * @param player The player to check
     * @return A warning message if near limit, null otherwise
     */
    public String getLimitWarningMessage(Player player) {
        if (!areLimitsEnabled()) {
            return null;
        }

        int limit = getPlayerLimit(player);
        if (limit == -1) {
            return null; // No warning for unlimited
        }

        int currentCount = getPlayerChestCount(player);
        int remaining = limit - currentCount;

        // Warn when 2 or fewer locks remain
        if (remaining <= 2 && remaining > 0) {
            return plugin.getMessageManager().get("limit_warning")
                    .replace("{remaining}", String.valueOf(remaining))
                    .replace("{limit}", String.valueOf(limit));
        } else if (remaining <= 0) {
            return plugin.getMessageManager().get("limit_reached")
                    .replace("{limit}", String.valueOf(limit));
        }

        return null;
    }
}