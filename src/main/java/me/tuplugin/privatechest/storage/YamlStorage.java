package me.tuplugin.privatechest.storage;

import me.tuplugin.privatechest.PrivateChest;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * YAML-based storage implementation for chest data.
 * Uses the traditional data.yml file format.
 */
public class YamlStorage implements DataStorage {

    private final PrivateChest plugin;
    private File dataFile;
    private FileConfiguration dataConfig;

    public YamlStorage(PrivateChest plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean initialize() {
        try {
            dataFile = new File(plugin.getDataFolder(), "data.yml");

            if (!dataFile.exists()) {
                dataFile.createNewFile();
                plugin.getLogger().info("[PrivateChest] data.yml created.");
            }

            dataConfig = YamlConfiguration.loadConfiguration(dataFile);
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("[PrivateChest] Failed to initialize YAML storage!");
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean loadData(Map<Location, String> owners, Map<Location, String> passwords) {
        try {
            // Clear maps before loading to avoid duplicates
            owners.clear();
            passwords.clear();

            // Load data from YAML
            if (dataConfig.isConfigurationSection("chests")) {
                for (String key : dataConfig.getConfigurationSection("chests").getKeys(false)) {
                    Location loc = deserializeLocation(key);
                    String owner = dataConfig.getString("chests." + key + ".owner");
                    String password = dataConfig.getString("chests." + key + ".password");

                    if (loc != null && owner != null && password != null) {
                        owners.put(loc, owner);
                        passwords.put(loc, password);
                    }
                }
            }

            plugin.getLogger().info("[PrivateChest] Loaded " + owners.size() + " locked chests from YAML storage.");
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("[PrivateChest] Failed to load data from YAML storage!");
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean saveData(Map<Location, String> owners, Map<Location, String> passwords) {
        try {
            // Clear existing data to avoid old entries
            dataConfig.set("chests", null);

            // Save current data
            for (Location loc : owners.keySet()) {
                String path = serializeLocation(loc);
                if (path != null) {
                    dataConfig.set("chests." + path + ".owner", owners.get(loc));
                    dataConfig.set("chests." + path + ".password", passwords.get(loc));
                }
            }

            dataConfig.save(dataFile);
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("[PrivateChest] Could not save data to YAML storage!");
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void close() {
        // YAML doesn't require explicit closing
        dataConfig = null;
        dataFile = null;
    }

    @Override
    public String getStorageType() {
        return "YAML";
    }

    @Override
    public boolean isReady() {
        return dataFile != null && dataConfig != null;
    }

    // --- Helper Methods ---

    /**
     * Serializes a Location to a String (world,x,y,z)
     */
    private String serializeLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            plugin.getLogger().warning("[PrivateChest] Attempted to save a null location or location with null world. Skipping.");
            return null;
        }
        return loc.getWorld().getName() + "," +
                loc.getBlockX() + "," +
                loc.getBlockY() + "," +
                loc.getBlockZ();
    }

    /**
     * Deserializes a String to a Location (world,x,y,z)
     */
    private Location deserializeLocation(String str) {
        String[] parts = str.split(",");
        if (parts.length != 4) {
            plugin.getLogger().warning("[PrivateChest] Malformed location string in data.yml: " + str);
            return null;
        }

        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            plugin.getLogger().warning("[PrivateChest] World '" + parts[0] + "' not found for location in data.yml: " + str);
            return null;
        }

        try {
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            return new Location(world, x, y, z);
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("[PrivateChest] Invalid number format in location from data.yml: " + str);
            return null;
        }
    }
}