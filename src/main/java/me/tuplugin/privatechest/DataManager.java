package me.tuplugin.privatechest;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World; // Importado explícitamente
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
        dataFile = new File(plugin.getDataFolder(), "data.yml"); // Mantenemos data.yml

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

        // Limpiar siempre antes de cargar para evitar duplicados en recargas
        chestLocker.getChestOwners().clear();
        chestLocker.getChestPasswords().clear();

        // Usar isConfigurationSection es más seguro
        if (dataConfig.isConfigurationSection("chests")) {
            for (String key : dataConfig.getConfigurationSection("chests").getKeys(false)) {
                Location loc = deserializeLocation(key); // Usamos la versión mejorada
                String owner = dataConfig.getString("chests." + key + ".owner");
                String password = dataConfig.getString("chests." + key + ".password");

                if (loc != null && owner != null && password != null) {
                    chestLocker.getChestOwners().put(loc, owner);
                    chestLocker.getChestPasswords().put(loc, password);
                }
                // El logging ya está dentro de deserializeLocation si loc es null
            }
        }

        Bukkit.getLogger().info("[PrivateChest] Loaded " + chestLocker.getChestOwners().size() + " locked chests.");
    }

    public void saveData() {
        ChestLocker chestLocker = ChestLocker.getInstance();
        Map<Location, String> owners = chestLocker.getChestOwners();
        Map<Location, String> passwords = chestLocker.getChestPasswords();

        dataConfig.set("chests", null); // Limpiar para evitar entradas viejas

        for (Location loc : owners.keySet()) {
            String path = serializeLocation(loc);
            if (path != null) { // Solo guardar si la serialización fue exitosa
                dataConfig.set("chests." + path + ".owner", owners.get(loc));
                dataConfig.set("chests." + path + ".password", passwords.get(loc));
            }
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            Bukkit.getLogger().severe("[PrivateChest] Could not save data.yml!");
            e.printStackTrace();
        }
    }

    /**
     * Serializes a Location to a String (world,x,y,z)
     * Added safety check.
     */
    private String serializeLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            Bukkit.getLogger().warning("[PrivateChest] Attempted to save a null location or location with null world. Skipping.");
            return null;
        }
        return loc.getWorld().getName() + "," +
                loc.getBlockX() + "," +
                loc.getBlockY() + "," +
                loc.getBlockZ();
    }

    /**
     * Deserializes a String to a Location (world,x,y,z)
     * Added improved checks and logging.
     */
    private Location deserializeLocation(String str) {
        String[] parts = str.split(",");
        if (parts.length != 4) {
            Bukkit.getLogger().warning("[PrivateChest] Malformed location string in data.yml: " + str);
            return null;
        }

        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            Bukkit.getLogger().warning("[PrivateChest] World '" + parts[0] + "' not found for location in data.yml: " + str);
            return null;
        }

        try {
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            return new Location(world, x, y, z);
        } catch (NumberFormatException e) {
            Bukkit.getLogger().warning("[PrivateChest] Invalid number format in location from data.yml: " + str);
            return null;
        }
    }
}