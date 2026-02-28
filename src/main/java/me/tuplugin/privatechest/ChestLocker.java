package me.tuplugin.privatechest;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public class ChestLocker {

    // Thread-safe maps for concurrent access from listeners, commands, and async cleanup
    private final Map<Location, String> chestOwners = new ConcurrentHashMap<>();
    private final Map<Location, String> chestPasswords = new ConcurrentHashMap<>();

    private static ChestLocker instance;
    private final PrivateChest plugin;

    public ChestLocker(PrivateChest plugin) {
        this.plugin = plugin;
        instance = this;
    }

    public static ChestLocker getInstance() {
        return instance;
    }

    /**
     * Locks the given chest block with a password and owner.
     */
    public boolean lockChest(Block block, Player player, String password) {
        if (block == null || player == null || password == null || password.isEmpty()) {
            return false;
        }
        
        Location loc = block.getLocation();

        if (chestOwners.containsKey(loc)) {
            return false; // Already locked
        }

        // Hash the password before storing
        String hashedPassword = PasswordManager.hashPassword(password);
        if (hashedPassword == null) {
<<<<<<< HEAD
            // Hashing failed, log error and return false for security
            plugin.getLogger().warning("Failed to hash password for chest at " + serializeLocation(loc) + ". Lock operation aborted.");
            return false;
=======
            plugin.getLogger().warning("Failed to hash password for chest at " + serializeLocation(loc) + ". Using plain text as fallback.");
            hashedPassword = password;
>>>>>>> 22e8436 (Version 2.3.1)
        }

        chestOwners.put(loc, player.getUniqueId().toString());
        chestPasswords.put(loc, hashedPassword);
        return true;
    }

    /**
     * Tries to unlock a chest. Returns true if the password is correct.
     * Automatically migrates plain text passwords to hashed format.
     */
    public boolean unlockChest(Block block, Player player, String password) {
        Location loc = block.getLocation();

        if (!chestOwners.containsKey(loc)) {
            return false;
        }

        String storedPassword = chestPasswords.get(loc);
        if (storedPassword == null) {
            return false;
        }

        // Check if stored password is in plain text (legacy format)
        if (PasswordManager.isPlainText(storedPassword)) {
            boolean isCorrect = storedPassword.equals(password);

            if (isCorrect) {
                String hashedPassword = PasswordManager.migratePlainPassword(password);
                if (hashedPassword != null) {
                    chestPasswords.put(loc, hashedPassword);
                    plugin.getLogger().info("Migrated plain text password to hashed format for chest at " + serializeLocation(loc));
                    plugin.getDataManager().saveData();
                } else {
                    plugin.getLogger().warning("Failed to migrate password for chest at " + serializeLocation(loc));
                }
            }

            return isCorrect;
        } else {
            return PasswordManager.verifyPassword(password, storedPassword);
        }
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
     * Gets the owner UUID of a chest.
     */
    public String getOwnerUUID(Block block) {
        return chestOwners.get(block.getLocation());
    }

    /**
     * Removes protection from a chest.
     */
    public void removeProtection(Block block) {
        Location loc = block.getLocation();
        chestOwners.remove(loc);
        chestPasswords.remove(loc);
    }

    public Map<Location, String> getChestOwners() {
        return chestOwners;
    }

    public Map<Location, String> getChestPasswords() {
        return chestPasswords;
    }

    /**
     * Migrates all plain text passwords to hashed format.
     * Called during plugin startup.
     */
    public void migrateAllPasswords() {
        int migrated = 0;
        boolean dataChanged = false;

        for (Map.Entry<Location, String> entry : chestPasswords.entrySet()) {
            String currentPassword = entry.getValue();

            if (PasswordManager.isPlainText(currentPassword)) {
                String hashedPassword = PasswordManager.migratePlainPassword(currentPassword);
                if (hashedPassword != null) {
                    entry.setValue(hashedPassword);
                    migrated++;
                    dataChanged = true;
                } else {
                    plugin.getLogger().warning("Failed to migrate password for chest at " + serializeLocation(entry.getKey()));
                }
            }
        }

        if (dataChanged) {
            plugin.getDataManager().saveData();
            plugin.getLogger().info("Successfully migrated " + migrated + " plain text passwords to hashed format.");
        }
    }

    /**
     * Serializes a Location to a String (world:x:y:z).
     * Null-safe: returns "unknown" if location or world is null.
     */
    public static String serializeLocation(Location loc) {
        String result = ContainerUtils.serializeLocation(loc);
        return result != null ? result : "unknown";
    }

    /**
     * Deserializes a String to a Location (world:x:y:z).
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
