package me.tuplugin.privatechest;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Loads, translates and sends every message the plugin produces.
 *
 * <p>Messages are resolved by key at the moment they are sent, not when the calling code builds them.
 * That is what allows the recipient to influence the result: their language when per-player languages
 * are enabled, and their platform for Bedrock adaptation. 2.x built the string first and sent it
 * blindly, which is why the Bedrock layer could never be reached.</p>
 *
 * <p>Resolution order for a key:</p>
 * <ol>
 *     <li>the recipient's language file under {@code lang/}, when {@code use-player-locale} is on</li>
 *     <li>{@code messages.yml}, the file the server owner edits</li>
 * </ol>
 *
 * <p>{@code messages.yml} keeps working exactly as before for servers that only need one language, so
 * upgrading changes nothing for them.</p>
 */
public class MessageManager {

    /** Languages shipped inside the jar. Extracted to lang/ so they can be copied and edited. */
    private static final String[] BUNDLED_LANGUAGES = {"en", "es"};

    private static final String FALLBACK_LANGUAGE = "en";

    private final PrivateChest plugin;

    /** The server owner's active message file. */
    private FileConfiguration base;

    /** Language code to its message file, loaded from the lang/ folder. */
    private Map<String, FileConfiguration> languages = new HashMap<>();

    private String defaultLanguage = FALLBACK_LANGUAGE;
    private boolean usePlayerLocale;
    private boolean usePrefix;
    private String prefix;

    public MessageManager(PrivateChest plugin) {
        this.plugin = plugin;
        load();
    }

    // --- Loading ---

    private void load() {
        defaultLanguage = normalizeLanguage(plugin.getConfig().getString("language", FALLBACK_LANGUAGE));
        usePlayerLocale = plugin.getConfig().getBoolean("use-player-locale", false);

        extractBundledLanguages();
        ensureBaseFileExists();

        // New keys added by an update arrive in the server's chosen language, not always in English.
        FileUtil.updateConfigFrom(plugin, "messages.yml", languageResource(defaultLanguage));

        base = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "messages.yml"));

        languages = usePlayerLocale ? loadLanguageFolder() : Collections.<String, FileConfiguration>emptyMap();

        if (usePlayerLocale) {
            plugin.getLogger().info("[Messages] Per-player languages enabled: "
                    + (languages.isEmpty() ? "none found" : String.join(", ", languages.keySet()))
                    + ". messages.yml fills any gaps.");
        }

        loadPrefixSettings();
    }

    /**
     * Writes the bundled language files into the lang/ folder without overwriting edits.
     */
    private void extractBundledLanguages() {
        File langFolder = new File(plugin.getDataFolder(), "lang");
        if (!langFolder.exists() && !langFolder.mkdirs()) {
            plugin.getLogger().warning("[Messages] Could not create the lang folder.");
            return;
        }

        for (String language : BUNDLED_LANGUAGES) {
            File target = new File(langFolder, language + ".yml");
            if (target.exists()) {
                continue;
            }

            try (InputStream in = plugin.getResource(languageResource(language))) {
                if (in == null) {
                    continue;
                }
                Files.copy(in, target.toPath());
            } catch (IOException e) {
                plugin.getLogger().warning("[Messages] Could not write lang/" + language + ".yml: "
                        + e.getMessage());
            }
        }
    }

    /**
     * Creates messages.yml from the chosen language the first time the plugin runs.
     */
    private void ensureBaseFileExists() {
        File target = new File(plugin.getDataFolder(), "messages.yml");
        if (target.exists()) {
            return;
        }

        String resource = languageResource(defaultLanguage);
        try (InputStream in = plugin.getResource(resource)) {
            if (in == null) {
                plugin.getLogger().warning("[Messages] No bundled language '" + defaultLanguage
                        + "'. Falling back to " + FALLBACK_LANGUAGE + ".");
                try (InputStream fallback = plugin.getResource(languageResource(FALLBACK_LANGUAGE))) {
                    if (fallback != null) {
                        Files.copy(fallback, target.toPath());
                    }
                }
                return;
            }
            Files.copy(in, target.toPath());
            plugin.getLogger().info("[Messages] Created messages.yml from the '" + defaultLanguage
                    + "' translation.");
        } catch (IOException e) {
            plugin.getLogger().severe("[Messages] Could not create messages.yml: " + e.getMessage());
        }
    }

    /**
     * Loads every language file present in the lang/ folder.
     */
    private Map<String, FileConfiguration> loadLanguageFolder() {
        Map<String, FileConfiguration> loaded = new HashMap<>();

        File langFolder = new File(plugin.getDataFolder(), "lang");
        File[] files = langFolder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            return loaded;
        }

        for (File file : files) {
            String code = normalizeLanguage(file.getName().substring(0, file.getName().length() - 4));
            loaded.put(code, YamlConfiguration.loadConfiguration(file));
        }
        return loaded;
    }

    private void loadPrefixSettings() {
        FileConfiguration config = plugin.getConfig();
        this.usePrefix = config.getBoolean("use-prefix", true);
        this.prefix = translate(config.getString("prefix", "&7[&6PrivateChest&7] "));
    }

    /**
     * Reloads the message files and the prefix settings.
     * Call after {@code plugin.reloadConfig()} when config.yml has changed.
     */
    public void reload() {
        load();
    }

    /**
     * The language codes available for per-player selection.
     */
    public Set<String> getAvailableLanguages() {
        return new LinkedHashSet<>(languages.keySet());
    }

    // --- Sending ---

    /**
     * Sends a message by key, with the configured prefix.
     *
     * <p>Placeholders are given as alternating name and value, without braces:</p>
     * <pre>send(player, "locked", "container", "barrel")</pre>
     */
    public void send(CommandSender recipient, String key, Object... placeholders) {
        if (recipient == null) {
            return;
        }
        deliver(recipient, resolve(key, recipient, true), placeholders);
    }

    /**
     * Sends a message by key, without the prefix.
     */
    public void sendRaw(CommandSender recipient, String key, Object... placeholders) {
        if (recipient == null) {
            return;
        }
        deliver(recipient, resolve(key, recipient, false), placeholders);
    }

    /**
     * Sends text that has already been built, for the few cases where a caller assembles a message
     * from more than one key.
     */
    public void sendText(CommandSender recipient, String text) {
        if (recipient == null || text == null) {
            return;
        }
        recipient.sendMessage(adaptFor(recipient, text));
    }

    private void deliver(CommandSender recipient, String message, Object... placeholders) {
        recipient.sendMessage(adaptFor(recipient, applyPlaceholders(message, placeholders)));
    }

    // --- Reading ---

    /**
     * A message with the prefix, in the server's own language.
     */
    public String get(String key, Object... placeholders) {
        return applyPlaceholders(resolve(key, null, true), placeholders);
    }

    /**
     * A message without the prefix, in the server's own language.
     */
    public String raw(String key, Object... placeholders) {
        return applyPlaceholders(resolve(key, null, false), placeholders);
    }

    /**
     * A message with the prefix, in the recipient's language.
     */
    public String getFor(CommandSender recipient, String key, Object... placeholders) {
        return applyPlaceholders(resolve(key, recipient, true), placeholders);
    }

    /**
     * A message without the prefix, in the recipient's language.
     */
    public String rawFor(CommandSender recipient, String key, Object... placeholders) {
        return applyPlaceholders(resolve(key, recipient, false), placeholders);
    }

    /**
     * A message without the prefix, or null if the key is not configured anywhere.
     *
     * <p>For optional keys, where the caller has a better fallback than a placeholder shown to a
     * player.</p>
     */
    public String rawOrNull(String key) {
        String value = lookup(key, null);
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return translate(value);
    }

    /**
     * Whether a key is configured.
     */
    public boolean has(String key) {
        return lookup(key, null) != null;
    }

    /**
     * A list-valued entry, for keys such as the accepted sign tags.
     *
     * @return the entries with colour codes translated, empty if the key is absent
     */
    public List<String> rawList(String key) {
        List<String> values = base == null ? null : base.getStringList(key);
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> translated = new ArrayList<>(values.size());
        for (String value : values) {
            translated.add(translate(value));
        }
        return translated;
    }

    // --- Resolution ---

    private String resolve(String key, CommandSender recipient, boolean withPrefix) {
        String value = lookup(key, recipient);
        if (value == null) {
            return missing(key);
        }

        String translated = translate(value);
        return withPrefix && usePrefix ? prefix + translated : translated;
    }

    /**
     * Finds a key's text, preferring the recipient's language when that is enabled.
     */
    private String lookup(String key, CommandSender recipient) {
        if (usePlayerLocale && recipient instanceof Player) {
            FileConfiguration localeFile = languages.get(languageOf((Player) recipient));
            if (localeFile != null) {
                String value = localeFile.getString(key);
                if (value != null) {
                    return value;
                }
            }
        }
        return base == null ? null : base.getString(key);
    }

    /**
     * The language code to use for a player: their client language if a file exists for it, otherwise
     * the server default.
     */
    private String languageOf(Player player) {
        String locale = player.getLocale();
        if (locale == null || locale.isEmpty()) {
            return defaultLanguage;
        }

        String normalized = normalizeLanguage(locale);
        if (languages.containsKey(normalized)) {
            return normalized;
        }

        // Client locales look like "es_es" or "pt_br". Fall back to the language part alone.
        int separator = normalized.indexOf('_');
        if (separator > 0) {
            String languageOnly = normalized.substring(0, separator);
            if (languages.containsKey(languageOnly)) {
                return languageOnly;
            }
        }
        return defaultLanguage;
    }

    private static String normalizeLanguage(String value) {
        return value == null ? FALLBACK_LANGUAGE : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String languageResource(String language) {
        return "lang/" + language + ".yml";
    }

    private String missing(String key) {
        // Visible enough for an admin to notice and fix, without looking like a crash to a player.
        plugin.getLogger().warning("Missing message key '" + key + "'. Check messages.yml.");
        return ChatColor.RED + "[" + key + "]";
    }

    /**
     * Replaces {@code {name}} placeholders from alternating name and value arguments.
     */
    private String applyPlaceholders(String message, Object... placeholders) {
        if (message == null || placeholders == null || placeholders.length == 0) {
            return message;
        }

        if (placeholders.length % 2 != 0) {
            plugin.getLogger().warning("Placeholder list is not in name/value pairs: "
                    + placeholders.length + " argument(s) given.");
        }

        String result = message;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            Object name = placeholders[i];
            Object value = placeholders[i + 1];
            if (name == null) {
                continue;
            }
            result = result.replace("{" + name + "}", value == null ? "" : String.valueOf(value));
        }
        return result;
    }

    // --- Bedrock ---

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

    /**
     * Reads a bundled resource as a configuration, for defaults comparison.
     */
    static FileConfiguration readBundled(PrivateChest plugin, String resource) {
        try (InputStream in = plugin.getResource(resource)) {
            if (in == null) {
                return null;
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return YamlConfiguration.loadConfiguration(reader);
            }
        } catch (IOException e) {
            return null;
        }
    }
}
