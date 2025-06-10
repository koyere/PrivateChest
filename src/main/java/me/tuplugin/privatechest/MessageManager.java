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

    /**
     * Loads messages.yml, updates it with new defaults, and loads prefix settings.
     */
    private void loadMessages() {
        File file = new File(plugin.getDataFolder(), "messages.yml");

        // --- MEJORA 1: Integrar FileUtil ---
        // Asegura que messages.yml existe Y lo actualiza con nuevas claves
        // antes de cargarlo en memoria.
        FileUtil.updateConfig(plugin, "messages.yml");
        // --- FIN MEJORA 1 ---

        messages = YamlConfiguration.loadConfiguration(file);
        loadPrefixSettings(); // Carga la configuración del prefijo
    }

    /**
     * Loads or re-loads prefix settings from the currently loaded config.yml.
     * It no longer triggers a full config reload itself.
     */
    private void loadPrefixSettings() {
        // --- MEJORA 2: Eliminar reloadConfig() ---
        // Ahora solo leemos la configuración que el plugin tiene cargada en este momento.
        // Es responsabilidad del comando /reload (o del arranque) cargar/recargar config.yml.
        FileConfiguration config = plugin.getConfig();
        this.usePrefix = config.getBoolean("use-prefix", true);
        this.prefix = translate(config.getString("prefix", "&7[&6PrivateChest&7] "));
        // --- FIN MEJORA 2 ---
    }

    /**
     * Reloads messages.yml (updating it) and prefix settings.
     * Should be called AFTER plugin.reloadConfig() if config.yml changed.
     */
    public void reload() {
        // Recargamos messages.yml (y lo actualizamos)
        File file = new File(plugin.getDataFolder(), "messages.yml");
        FileUtil.updateConfig(plugin, "messages.yml");
        messages = YamlConfiguration.loadConfiguration(file);

        // Recargamos la configuración del prefijo desde la config.yml
        // que (se supone) ya fue recargada por el comando principal.
        loadPrefixSettings();
    }


    /**
     * Get message with prefix.
     */
    public String get(String key) {
        String msg = messages.getString(key, "Message not found: " + key); // Changed default
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
        return translate(messages.getString(key, "Message not found: " + key)); // Changed default
    }

    /**
     * Translate color codes (&a, &c, etc.).
     */
    private String translate(String text) {
        // Added null check for safety
        return text == null ? "" : ChatColor.translateAlternateColorCodes('&', text);
    }
}