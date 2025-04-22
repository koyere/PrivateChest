package me.tuplugin.privatechest;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;

public class FileUtil {

    public static void updateConfig(JavaPlugin plugin, String fileName) {
        try {
            File file = new File(plugin.getDataFolder(), fileName);
            if (!file.exists()) {
                plugin.saveResource(fileName, false);
                return;
            }

            FileConfiguration existingConfig = YamlConfiguration.loadConfiguration(file);
            Reader defConfigStream = new InputStreamReader(plugin.getResource(fileName));
            FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration(defConfigStream);

            boolean changed = false;

            for (String key : defaultConfig.getKeys(true)) {
                if (!existingConfig.contains(key)) {
                    existingConfig.set(key, defaultConfig.get(key));
                    changed = true;
                }
            }

            if (changed) {
                existingConfig.save(file);
                plugin.getLogger().info("Updated " + fileName + " with new defaults.");
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Could not update " + fileName + ": " + e.getMessage());
        }
    }
}
