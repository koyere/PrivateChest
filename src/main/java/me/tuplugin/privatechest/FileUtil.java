package me.tuplugin.privatechest;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Helpers for keeping the bundled config.yml and messages.yml in sync with the files on disk.
 */
public final class FileUtil {

    private FileUtil() {
        // Utility class.
    }

    /**
     * Copies the bundled file if it does not exist yet, otherwise adds any keys that were introduced
     * by a plugin update while leaving existing values alone.
     */
    public static void updateConfig(JavaPlugin plugin, String fileName) {
        try {
            File file = new File(plugin.getDataFolder(), fileName);
            if (!file.exists()) {
                plugin.saveResource(fileName, false);
                return;
            }

            InputStream bundled = plugin.getResource(fileName);
            if (bundled == null) {
                plugin.getLogger().warning("Bundled " + fileName + " is missing from the plugin jar; "
                        + "cannot check it for new options.");
                return;
            }

            FileConfiguration existingConfig = YamlConfiguration.loadConfiguration(file);

            FileConfiguration defaultConfig;
            try (Reader reader = new InputStreamReader(bundled, StandardCharsets.UTF_8)) {
                defaultConfig = YamlConfiguration.loadConfiguration(reader);
            }

            int added = 0;
            for (String key : defaultConfig.getKeys(true)) {
                if (!existingConfig.contains(key)) {
                    existingConfig.set(key, defaultConfig.get(key));
                    added++;
                }
            }

            if (added > 0) {
                existingConfig.save(file);
                plugin.getLogger().info("Added " + added + " new option(s) to " + fileName + ".");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not check " + fileName + " for new options: " + e.getMessage());
        }
    }

    /**
     * Rewrites a single top-level value in a YAML file, in place, line by line.
     *
     * <p>Used when the plugin itself has to change a setting (currently only {@code storage-type},
     * after a storage migration). Saving the whole configuration object would work, but on some
     * server versions it strips every comment out of config.yml, which is hostile to admins. Editing
     * the one line keeps the file exactly as it was written.</p>
     *
     * @return true if the key was found and updated
     */
    public static boolean setTopLevelValue(JavaPlugin plugin, String fileName, String key, String value) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            return false;
        }

        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            Pattern pattern = Pattern.compile("^" + Pattern.quote(key) + "\\s*:.*$");

            boolean replaced = false;
            for (int i = 0; i < lines.size(); i++) {
                if (pattern.matcher(lines.get(i)).matches()) {
                    lines.set(i, key + ": " + value);
                    replaced = true;
                    break;
                }
            }

            if (!replaced) {
                plugin.getLogger().warning("Could not find '" + key + "' in " + fileName
                        + ". Please set it to '" + value + "' manually.");
                return false;
            }

            writeAtomically(file, lines);
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("Could not update '" + key + "' in " + fileName + ": " + e.getMessage()
                    + ". Please set it to '" + value + "' manually.");
            return false;
        }
    }

    private static void writeAtomically(File file, List<String> lines) throws IOException {
        File temp = new File(file.getParentFile(), file.getName() + ".tmp");
        Files.write(temp.toPath(), lines, StandardCharsets.UTF_8);
        try {
            Files.move(temp.toPath(), file.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(temp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
