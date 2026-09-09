package me.tuplugin.privatechest;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;

/**
 * Loads and formats every message the plugin sends.
 *
 * <p>Sending goes through {@link #send} as of 3.0, which is what finally connects the Bedrock
 * adaptation layer. Before that, callers sent strings straight to the player and the adaptation code
 * was never reached.</p>
 */
public class MessageManager {

    private final PrivateChest plugin;

    private FileConfiguration messages;
    private String prefix;
    private boolean usePrefix;

    public MessageManager(PrivateChest plugin) {
        this.plugin = plugin;
        load();
    }

    /**
     * Loads messages.yml, adding any keys introduced by this update, then reads the prefix settings.
     */
    private void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        FileUtil.updateConfig(plugin, "messages.yml");

        messages = YamlConfiguration.loadConfiguration(file);
        loadPrefixSettings();
    }

    /**
     * Reads the prefix settings from the configuration the plugin currently has loaded.
     * Reloading config.yml is the caller's job.
     */
    private void loadPrefixSettings() {
        FileConfiguration config = plugin.getConfig();
        this.usePrefix = config.getBoolean("use-prefix", true);
        this.prefix = translate(config.getString("prefix", "&7[&6PrivateChest&7] "));
    }

    /**
     * Reloads messages.yml and the prefix settings.
     * Call after {@code plugin.reloadConfig()} when config.yml has changed.
     */
    public void reload() {
        load();
    }

    // --- Reading ---

    /**
     * A message with the configured prefix.
     */
    public String get(String key) {
        String message = messages.getString(key);
        if (message == null) {
            return missing(key);
        }
        return usePrefix ? prefix + translate(message) : translate(message);
    }

    /**
     * A message without the prefix.
     */
    public String raw(String key) {
        String message = messages.getString(key);
        return message == null ? missing(key) : translate(message);
    }

    /**
     * A message without the prefix, or null if the key is not configured.
     *
     * <p>For optional keys, where the caller has a better fallback than a placeholder shown to a
     * player.</p>
     */
    public String rawOrNull(String key) {
        String value = messages.getString(key);
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return translate(value);
    }

    /**
     * Whether a key is configured.
     */
    public boolean has(String key) {
        return messages.contains(key);
    }

    private String missing(String key) {
        // Visible enough for an admin to notice and fix, without looking like a crash to a player.
        plugin.getLogger().warning("Missing message key '" + key + "' in messages.yml.");
        return ChatColor.RED + "[" + key + "]";
    }

    // --- Sending ---

    /**
     * Sends an already-formatted message, adapting it for the recipient's platform.
     *
     * <p>Every message the plugin sends to a player goes through here. On a Java-only server this is a
     * single boolean check and a plain send.</p>
     */
    public void send(CommandSender recipient, String message) {
        if (recipient == null || message == null) {
            return;
        }
        recipient.sendMessage(adaptFor(recipient, message));
    }

    /**
     * Sends a message by key, with the prefix.
     */
    public void sendKey(CommandSender recipient, String key) {
        send(recipient, get(key));
    }

    /**
     * Sends a message by key, without the prefix.
     */
    public void sendRawKey(CommandSender recipient, String key) {
        send(recipient, raw(key));
    }

    /**
     * Adapts a message for a recipient's platform.
     *
     * @return the message unchanged for console and for Java players
     */
    public String adaptFor(CommandSender recipient, String message) {
        if (!(recipient instanceof Player) || message == null) {
            return message;
        }

        BedrockUtils bedrock = plugin.getBedrockUtils();
        if (bedrock == null || !bedrock.isBridgeAvailable()) {
            return message;
        }
        if (!bedrock.isBedrockPlayer((Player) recipient)) {
            return message;
        }
        return bedrock.adaptMessage(message);
    }

    /**
     * Translates {@code &} colour codes.
     */
    private String translate(String text) {
        return text == null ? "" : ChatColor.translateAlternateColorCodes('&', text);
    }
}
