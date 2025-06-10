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

    // Stores passwords: location -> hashed password
    private final Map<Location, String> chestPasswords = new HashMap<>();

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
        Location loc = block.getLocation();

        if (chestOwners.containsKey(loc)) {
            return false; // Already locked
        }

        // Hash the password before storing
        String hashedPassword = PasswordManager.hashPassword(password);
        if (hashedPassword == null) {
            // Hashing failed, log error and fallback to plain text for backward compatibility
            plugin.getLogger().warning("Failed to hash password for chest at " + serializeLocation(loc) + ". Using plain text as fallback.");
            hashedPassword = password;
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
            // Legacy plain text comparison
            boolean isCorrect = storedPassword.equals(password);

            if (isCorrect) {
                // Migrate to hashed format
                String hashedPassword = PasswordManager.migratePlainPassword(password);
                if (hashedPassword != null) {
                    chestPasswords.put(loc, hashedPassword);
                    plugin.getLogger().info("Migrated plain text password to hashed format for chest at " + serializeLocation(loc));
                    // Save the migration immediately
                    plugin.getDataManager().saveData();
                } else {
                    plugin.getLogger().warning("Failed to migrate password for chest at " + serializeLocation(loc));
                }
            }

            return isCorrect;
        } else {
            // Use secure hash verification
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
     * @param block The chest block.
     * @return The owner's UUID as string, or null if not locked.
     */
    public String getOwnerUUID(Block block) {
        return chestOwners.get(block.getLocation());
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
     * Migrates all plain text passwords to hashed format.
     * Called during plugin startup or reload.
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