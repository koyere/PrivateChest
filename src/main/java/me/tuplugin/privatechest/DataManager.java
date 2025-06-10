package me.tuplugin.privatechest;

import me.tuplugin.privatechest.storage.DataStorage;
import me.tuplugin.privatechest.storage.SqliteStorage;
import me.tuplugin.privatechest.storage.YamlStorage;
import org.bukkit.Location;

import java.util.Map;

public class DataManager {

    private final PrivateChest plugin;
    private DataStorage storage;

    public DataManager(PrivateChest plugin) {
        this.plugin = plugin;
        initializeStorage();
        loadData();
    }

    /**
     * Initializes the appropriate storage system based on configuration.
     */
    private void initializeStorage() {
        String storageType = plugin.getConfig().getString("storage-type", "yaml").toLowerCase();

        switch (storageType) {
            case "sqlite":
                plugin.getLogger().info("[PrivateChest] Attempting to initialize SQLite storage...");
                storage = new SqliteStorage(plugin);
                if (!storage.initialize()) {
                    plugin.getLogger().warning("[PrivateChest] SQLite initialization failed! Falling back to YAML storage.");
                    storage = new YamlStorage(plugin);
                    if (!storage.initialize()) {
                        plugin.getLogger().severe("[PrivateChest] YAML storage initialization also failed! Plugin may not work correctly.");
                    }
                }
                break;

            case "yaml":
            default:
                plugin.getLogger().info("[PrivateChest] Using YAML storage.");
                storage = new YamlStorage(plugin);
                if (!storage.initialize()) {
                    plugin.getLogger().severe("[PrivateChest] YAML storage initialization failed! Plugin may not work correctly.");
                }
                break;
        }

        plugin.getLogger().info("[PrivateChest] Storage system initialized: " + storage.getStorageType());
    }

    /**
     * Loads chest data from the configured storage system.
     */
    public void loadData() {
        if (storage == null || !storage.isReady()) {
            plugin.getLogger().severe("[PrivateChest] Storage system is not ready! Cannot load data.");
            return;
        }

        ChestLocker chestLocker = ChestLocker.getInstance();
        Map<Location, String> owners = chestLocker.getChestOwners();
        Map<Location, String> passwords = chestLocker.getChestPasswords();

        if (!storage.loadData(owners, passwords)) {
            plugin.getLogger().severe("[PrivateChest] Failed to load chest data from storage!");
        }
    }

    /**
     * Saves chest data to the configured storage system.
     */
    public void saveData() {
        if (storage == null || !storage.isReady()) {
            plugin.getLogger().warning("[PrivateChest] Storage system is not ready! Cannot save data.");
            return;
        }

        ChestLocker chestLocker = ChestLocker.getInstance();
        Map<Location, String> owners = chestLocker.getChestOwners();
        Map<Location, String> passwords = chestLocker.getChestPasswords();

        if (!storage.saveData(owners, passwords)) {
            plugin.getLogger().severe("[PrivateChest] Failed to save chest data to storage!");
        }
    }

    /**
     * Migrates data from one storage type to another.
     * Useful when switching between YAML and SQLite.
     */
    public boolean migrateStorage(String fromType, String toType) {
        if (fromType.equals(toType)) {
            plugin.getLogger().info("[PrivateChest] Source and target storage types are the same. No migration needed.");
            return true;
        }

        plugin.getLogger().info("[PrivateChest] Starting migration from " + fromType + " to " + toType + "...");

        // Initialize source storage
        DataStorage sourceStorage;
        switch (fromType.toLowerCase()) {
            case "yaml":
                sourceStorage = new YamlStorage(plugin);
                break;
            case "sqlite":
                sourceStorage = new SqliteStorage(plugin);
                break;
            default:
                plugin.getLogger().severe("[PrivateChest] Unknown source storage type: " + fromType);
                return false;
        }

        if (!sourceStorage.initialize()) {
            plugin.getLogger().severe("[PrivateChest] Failed to initialize source storage for migration!");
            return false;
        }

        // Initialize target storage
        DataStorage targetStorage;
        switch (toType.toLowerCase()) {
            case "yaml":
                targetStorage = new YamlStorage(plugin);
                break;
            case "sqlite":
                targetStorage = new SqliteStorage(plugin);
                break;
            default:
                plugin.getLogger().severe("[PrivateChest] Unknown target storage type: " + toType);
                sourceStorage.close();
                return false;
        }

        if (!targetStorage.initialize()) {
            plugin.getLogger().severe("[PrivateChest] Failed to initialize target storage for migration!");
            sourceStorage.close();
            return false;
        }

        try {
            // Load data from source
            ChestLocker chestLocker = ChestLocker.getInstance();
            Map<Location, String> owners = chestLocker.getChestOwners();
            Map<Location, String> passwords = chestLocker.getChestPasswords();

            if (!sourceStorage.loadData(owners, passwords)) {
                plugin.getLogger().severe("[PrivateChest] Failed to load data from source storage during migration!");
                return false;
            }

            int dataCount = owners.size();
            plugin.getLogger().info("[PrivateChest] Loaded " + dataCount + " entries from " + fromType + " storage.");

            // Save data to target
            if (!targetStorage.saveData(owners, passwords)) {
                plugin.getLogger().severe("[PrivateChest] Failed to save data to target storage during migration!");
                return false;
            }

            plugin.getLogger().info("[PrivateChest] Successfully migrated " + dataCount + " entries to " + toType + " storage.");

            // Switch to new storage
            if (storage != null) {
                storage.close();
            }
            storage = targetStorage;

            plugin.getLogger().info("[PrivateChest] Migration from " + fromType + " to " + toType + " completed successfully!");
            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("[PrivateChest] Error during storage migration!");
            e.printStackTrace();
            return false;
        } finally {
            sourceStorage.close();
            if (targetStorage != storage) {
                targetStorage.close();
            }
        }
    }

    /**
     * Gets the current storage type.
     */
    public String getStorageType() {
        return storage != null ? storage.getStorageType() : "Unknown";
    }

    /**
     * Checks if the storage system is ready.
     */
    public boolean isStorageReady() {
        return storage != null && storage.isReady();
    }

    /**
     * Closes the storage connection.
     * Should be called when the plugin is disabled.
     */
    public void close() {
        if (storage != null) {
            storage.close();
            storage = null;
        }
    }

    /**
     * Gets the current storage instance.
     * Useful for advanced operations with specific storage types.
     */
    public DataStorage getStorage() {
        return storage;
    }
}