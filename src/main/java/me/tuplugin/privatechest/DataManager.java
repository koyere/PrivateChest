package me.tuplugin.privatechest;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class DataManager {

    private final PrivateChest plugin;
    private File dataFile;
    private FileConfiguration dataConfig;

    public DataManager(PrivateChest plugin) {
        this.plugin = plugin;
        createDataFile();
        loadData();
    }

    private void createDataFile() {
        dataFile = new File(plugin.getDataFolder(), "data.yml");

        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
                Bukkit.getLogger().info("[PrivateChest] data.yml created.");
            } catch (IOException e) {
                Bukkit.getLogger().severe("[PrivateChest] Failed to create data.yml!");
                e.printStackTrace();
            }
        }

        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void loadData() {
        ChestLocker chestLocker = ChestLocker.getInstance();

        if (dataConfig.contains("chests")) {
            for (String key : dataConfig.getConfigurationSection("chests").getKeys(false)) {
                Location loc = deserializeLocation(key);
                String owner = dataConfig.getString("chests." + key + ".owner");
                String password = dataConfig.getString("chests." + key + ".password");

                if (loc != null && owner != null && password != null) {
                    chestLocker.getChestOwners().put(loc, owner);
                    chestLocker.getChestPasswords().put(loc, password);
                }
            }
        }

        Bukkit.getLogger().info("[PrivateChest] Loaded locked chests.");
    }

    public void saveData() {
        ChestLocker chestLocker = ChestLocker.getInstance();
        Map<Location, String> owners = chestLocker.getChestOwners();
        Map<Location, String> passwords = chestLocker.getChestPasswords();

        dataConfig.set("chests", null); // Clear previous data

        for (Location loc : owners.keySet()) {
            String path = serializeLocation(loc);
            dataConfig.set("chests." + path + ".owner", owners.get(loc));
            dataConfig.set("chests." + path + ".password", passwords.get(loc));
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            Bukkit.getLogger().severe("[PrivateChest] Could not save data.yml!");
            e.printStackTrace();
        }
    }

    private String serializeLocation(Location loc) {
        return loc.getWorld().getName() + "," +
                loc.getBlockX() + "," +
                loc.getBlockY() + "," +
                loc.getBlockZ();
    }

    private Location deserializeLocation(String str) {
        try {
            String[] parts = str.split(",");
            return new Location(
                    Bukkit.getWorld(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3])
            );
        } catch (Exception e) {
            return null;
        }
    }
}
