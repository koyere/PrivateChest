package me.tuplugin.privatechest;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class MessageManager {

    private final PrivateChest plugin;
    private FileConfiguration messages;
    private String prefix;
    private boolean usePrefix;

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
        reloadPrefix();
    }

    /**
     * Reload prefix settings from config.yml
     */
    private void reloadPrefix() {
        plugin.reloadConfig(); // Ensure config is loaded
        this.usePrefix = plugin.getConfig().getBoolean("use-prefix", true);
        this.prefix = translate(plugin.getConfig().getString("prefix", "&7[&6PrivateChest&7] "));
    }

    /**
     * Reload messages.yml and update prefix
     */
    public void reloadMessages() {
        loadMessages();
    }

    /**
     * Get message with prefix.
     */
    public String get(String key) {
        String msg = messages.getString(key, key);
        if (usePrefix) {
            return prefix + translate(msg);
        } else {
            return translate(msg);
        }
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
