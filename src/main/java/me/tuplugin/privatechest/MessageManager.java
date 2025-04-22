package me.tuplugin.privatechest;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class MessageManager {

    private final PrivateChest plugin;
    private FileConfiguration messages;
    private String prefix;

    public MessageManager(PrivateChest plugin) {
        this.plugin = plugin;
        loadMessages();
    }

    private void loadMessages() {
        File file = new File(plugin.getDataFolder(), "messages.yml");

        if (!file.exists()) {
            plugin.saveResource("messages.yml", false); // Create if not exist
        }

        messages = YamlConfiguration.loadConfiguration(file);
        prefix = translate(messages.getString("prefix", "&7[&6PrivateChest&7] "));
    }

    /**
     * Get message with prefix.
     */
    public String get(String key) {
        String msg = messages.getString(key, key);
        return prefix + translate(msg);
    }

    /**
     * Get message without prefix.
     */
    public String raw(String key) {
        return translate(messages.getString(key, key));
    }

    /**
     * Translate color codes (&a, &c, etc.).
     */
    private String translate(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
