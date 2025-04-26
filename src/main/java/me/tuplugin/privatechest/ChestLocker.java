package me.tuplugin.privatechest;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class ChestLocker {

    // Stores protected chests: location -> owner UUID
    private final Map<Location, String> chestOwners = new HashMap<>();

    // Stores passwords: location -> password
    private final Map<Location, String> chestPasswords = new HashMap<>();

    private static ChestLocker instance;

    public ChestLocker(PrivateChest plugin) {
        instance = this;
    }

    public static ChestLocker getInstance() {
        return instance;
    }

    /**
     * Locks the given chest block with a password and owner.
     */
    public boolean lockChest(Block block, Player player, String password) {
        Location loc = block.getLocation();

        if (chestOwners.containsKey(loc)) {
            return false; // Already locked
        }

        chestOwners.put(loc, player.getUniqueId().toString());
        chestPasswords.put(loc, password);
        return true;
    }

    /**
     * Tries to unlock a chest. Returns true if the password is correct.
     */
    public boolean unlockChest(Block block, Player player, String password) {
        Location loc = block.getLocation();

        if (!chestOwners.containsKey(loc)) {
            return false;
        }

        String correctPassword = chestPasswords.get(loc);
        return correctPassword != null && correctPassword.equals(password);
    }

    /**
     * Checks if a chest is locked.
     */
    public boolean isChestLocked(Block block) {
        return chestOwners.containsKey(block.getLocation());
    }

    /**
     * Checks if the player is the owner of the chest.
     */
    public boolean isOwner(Block block, Player player) {
        String ownerUUID = chestOwners.get(block.getLocation());
        return ownerUUID != null && ownerUUID.equals(player.getUniqueId().toString());
    }

    /**
     * Removes protection from a chest (used after successful unlock or removal).
     */
    public void removeProtection(Block block) {
        Location loc = block.getLocation();
        chestOwners.remove(loc);
        chestPasswords.remove(loc);
    }

    /**
     * Gets all protected chests.
     */
    public Map<Location, String> getChestOwners() {
        return chestOwners;
    }

    public Map<Location, String> getChestPasswords() {
        return chestPasswords;
    }

    /**
     * Serializes a Location to a String (world:x:y:z)
     */
    public static String serializeLocation(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    /**
     * Deserializes a String to a Location (world:x:y:z)
     */
    public static Location deserializeLocation(String serialized) {
        String[] parts = serialized.split(":");
        if (parts.length != 4) {
            return null;
        }

        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            return null;
        }

        try {
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            return new Location(world, x, y, z);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
