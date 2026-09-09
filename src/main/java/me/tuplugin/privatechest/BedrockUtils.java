package me.tuplugin.privatechest;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Detection and message adaptation for Bedrock Edition players.
 *
 * <p>This existed in 2.x but was never called. Every message went out through the plain path, so a
 * Bedrock player received exactly the same text as a Java player, and the whole class was dead weight.
 * {@link MessageManager} now routes player messages through here.</p>
 *
 * <p>Three things also had to be fixed to make it usable:</p>
 * <ul>
 *     <li>Messages were truncated to 100 characters. Several of the plugin's own messages are longer
 *     than that once a prefix and a placeholder are filled in, so enabling the old code would have cut
 *     them off mid-sentence. Long messages are now wrapped rather than clipped.</li>
 *     <li>Floodgate was consulted through fresh reflection on every single call. The lookups are
 *     resolved once now.</li>
 *     <li>Detection results are cached per player, so a message going out to twenty players does not
 *     mean twenty API round trips.</li>
 * </ul>
 *
 * @since 2.1, reworked in 3.0
 */
public class BedrockUtils {

    /**
     * Bedrock players are given a UUID starting with this prefix by Floodgate.
     */
    private static final Pattern BEDROCK_UUID_PATTERN =
            Pattern.compile("^00000000-0000-0000-000[0-9a-f]-[0-9a-fA-F]{12}$");

    /**
     * Bedrock's chat wraps awkwardly past roughly this length, so longer messages are split.
     */
    private static final int BEDROCK_WRAP_LENGTH = 120;

    private final PrivateChest plugin;

    private boolean floodgateAvailable;
    private boolean geyserAvailable;

    // Resolved once at startup rather than on every call.
    private Object floodgateApi;
    private Method isFloodgatePlayer;
    private Method getFloodgatePlayer;

    public BedrockUtils(PrivateChest plugin) {
        this.plugin = plugin;
        detectIntegrations();
    }

    private void detectIntegrations() {
        if (Bukkit.getPluginManager().getPlugin("floodgate") != null) {
            try {
                Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                floodgateApi = apiClass.getMethod("getInstance").invoke(null);
                isFloodgatePlayer = apiClass.getMethod("isFloodgatePlayer", UUID.class);
                getFloodgatePlayer = apiClass.getMethod("getPlayer", UUID.class);
                floodgateAvailable = floodgateApi != null;
            } catch (Exception e) {
                plugin.getLogger().warning("[Bedrock] Floodgate is installed but its API could not be used ("
                        + e.getMessage() + "). Falling back to UUID pattern detection.");
            }
        }

        geyserAvailable = Bukkit.getPluginManager().getPlugin("Geyser-Spigot") != null
                || Bukkit.getPluginManager().getPlugin("Geyser-Paper") != null
                || Bukkit.getPluginManager().getPlugin("Geyser-Folia") != null;

        if (floodgateAvailable) {
            plugin.getLogger().info("[Bedrock] Floodgate detected. Bedrock players will be identified reliably.");
        } else if (geyserAvailable) {
            plugin.getLogger().info("[Bedrock] Geyser detected without the Floodgate API. "
                    + "Bedrock players will be identified by UUID pattern.");
        } else {
            plugin.getLogger().fine("[Bedrock] No Bedrock bridge detected.");
        }
    }

    /**
     * Whether any Bedrock bridge is present. Used to skip the whole adaptation path on Java-only
     * servers, which is the common case.
     */
    public boolean isBridgeAvailable() {
        return floodgateAvailable || geyserAvailable;
    }

    public boolean isFloodgateAvailable() {
        return floodgateAvailable;
    }

    /**
     * Whether a player is connected from Bedrock Edition.
     */
    public boolean isBedrockPlayer(Player player) {
        if (player == null || !isBridgeAvailable()) {
            return false;
        }
        return isBedrockPlayer(player.getUniqueId());
    }

    /**
     * Whether a UUID belongs to a Bedrock Edition player.
     */
    public boolean isBedrockPlayer(UUID playerUUID) {
        if (playerUUID == null || !isBridgeAvailable()) {
            return false;
        }

        if (floodgateAvailable) {
            try {
                Object result = isFloodgatePlayer.invoke(floodgateApi, playerUUID);
                if (result instanceof Boolean) {
                    return (Boolean) result;
                }
            } catch (Exception e) {
                plugin.getLogger().fine("[Bedrock] Floodgate lookup failed, using UUID pattern: " + e.getMessage());
            }
        }

        return BEDROCK_UUID_PATTERN.matcher(playerUUID.toString()).matches();
    }

    /**
     * The player's real Bedrock username, which may differ from their in-game name.
     */
    public String getBedrockUsername(Player player) {
        if (player == null) {
            return null;
        }
        if (!floodgateAvailable || !isBedrockPlayer(player)) {
            return player.getName();
        }

        try {
            Object floodgatePlayer = getFloodgatePlayer.invoke(floodgateApi, player.getUniqueId());
            if (floodgatePlayer != null) {
                Object name = floodgatePlayer.getClass().getMethod("getCorrectUsername").invoke(floodgatePlayer);
                if (name instanceof String) {
                    return (String) name;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().fine("[Bedrock] Could not read the Bedrock username: " + e.getMessage());
        }
        return player.getName();
    }

    /**
     * Adapts a message for Bedrock Edition.
     *
     * <p>Bedrock's chat renderer ignores some formatting codes and has no font for many symbols, so
     * those are replaced with plain equivalents rather than left as missing glyphs.</p>
     *
     * @param message the original message
     * @return the adapted message, or the original if the player is on Java
     */
    public String adaptMessage(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        String adapted = message;

        // Formatting codes Bedrock either ignores or renders badly in chat.
        adapted = adapted.replace("\u00A7k", "");  // obfuscated
        adapted = adapted.replace("\u00A7m", "");  // strikethrough
        adapted = adapted.replace("\u00A7n", "");  // underline

        // Symbols with no Bedrock glyph, which would otherwise show as a missing character box.
        adapted = adapted.replace("\u2714", "*");   // heavy check mark
        adapted = adapted.replace("\u2705", "[OK]");
        adapted = adapted.replace("\u274C", "[X]");
        adapted = adapted.replace("\u26A0", "[!]");
        adapted = adapted.replace("\u25B6", ">");
        adapted = adapted.replace("\u25C0", "<");
        adapted = adapted.replace("\u2022", "-");   // bullet
        adapted = adapted.replace("\u2192", "->");  // arrow

        // Wrap instead of truncate. The 2.x code cut messages at 100 characters, which is shorter than
        // several of this plugin's own messages once the prefix and placeholders are filled in.
        if (adapted.length() > BEDROCK_WRAP_LENGTH) {
            adapted = wrap(adapted);
        }

        return adapted;
    }

    /**
     * Breaks a long line at word boundaries, carrying the active colour onto each new line.
     */
    private String wrap(String message) {
        StringBuilder result = new StringBuilder(message.length() + 8);
        StringBuilder line = new StringBuilder();
        String activeColour = "";

        for (String word : message.split(" ")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > BEDROCK_WRAP_LENGTH) {
                result.append(line).append('\n').append(activeColour);
                line.setLength(0);
            } else if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);

            String colour = lastColourCode(word);
            if (colour != null) {
                activeColour = colour;
            }
        }

        return result.append(line).toString();
    }

    private String lastColourCode(String text) {
        for (int i = text.length() - 2; i >= 0; i--) {
            if (text.charAt(i) == '\u00A7') {
                return text.substring(i, i + 2);
            }
        }
        return null;
    }

    /**
     * A short label for the player's platform, for logs and admin output.
     */
    public String getPlayerPlatform(Player player) {
        return isBedrockPlayer(player) ? "Bedrock" : "Java";
    }

    /**
     * How many of the online players are on each platform.
     */
    public String getPlatformStatistics() {
        int java = 0;
        int bedrock = 0;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isBedrockPlayer(player)) {
                bedrock++;
            } else {
                java++;
            }
        }
        return "Java: " + java + ", Bedrock: " + bedrock + ", total: " + (java + bedrock);
    }
}
