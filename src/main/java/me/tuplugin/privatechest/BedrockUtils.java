package me.tuplugin.privatechest;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Utility class for handling Bedrock Edition players and cross-platform compatibility.
 * Provides detection and adaptation features for Java and Bedrock players using Floodgate.
 * 
 * @since 2.1
 * @author PrivateChest Team
 */
public class BedrockUtils {

    private final PrivateChest plugin;
    private static boolean floodgateEnabled = false;
    private static boolean floodgateChecked = false;

    // Floodgate Bedrock UUID prefix pattern
    private static final String FLOODGATE_PREFIX = "00000000-0000-0000-0009-";
    private static final Pattern BEDROCK_UUID_PATTERN = Pattern.compile("^00000000-0000-0000-0009-[0-9a-fA-F]{12}$");

    // Character limitations for Bedrock Edition
    private static final Pattern BEDROCK_SAFE_CHARS = Pattern.compile("^[a-zA-Z0-9\\s\\-_\\.]+$");
    private static final int BEDROCK_MAX_MESSAGE_LENGTH = 100;

    /**
     * Constructs a new BedrockUtils instance.
     * 
     * @param plugin The main plugin instance
     */
    public BedrockUtils(PrivateChest plugin) {
        this.plugin = plugin;
        checkFloodgateAvailability();
    }

    /**
     * Checks if Floodgate is available and properly configured.
     * This method is called once during initialization and caches the result.
     */
    private void checkFloodgateAvailability() {
        if (floodgateChecked) {
            return;
        }

        try {
            // Check if Floodgate plugin is present
            if (Bukkit.getPluginManager().getPlugin("floodgate") != null) {
                // Try to access Floodgate API to confirm it's working
                Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                floodgateEnabled = true;
                plugin.getLogger().info("[BedrockUtils] Floodgate detected - Bedrock Edition support enabled");
            } else {
                floodgateEnabled = false;
                plugin.getLogger().info("[BedrockUtils] Floodgate not detected - Java Edition only mode");
            }
        } catch (ClassNotFoundException e) {
            floodgateEnabled = false;
            plugin.getLogger().warning("[BedrockUtils] Floodgate plugin found but API not available");
        } catch (Exception e) {
            floodgateEnabled = false;
            plugin.getLogger().warning("[BedrockUtils] Error checking Floodgate availability: " + e.getMessage());
        }

        floodgateChecked = true;
    }

    /**
     * Checks if a player is connecting from Bedrock Edition.
     * This method uses Floodgate's UUID pattern recognition when available,
     * or falls back to UUID pattern matching.
     * 
     * @param player The player to check
     * @return true if the player is from Bedrock Edition
     */
    public boolean isBedrockPlayer(Player player) {
        if (player == null) {
            return false;
        }

        return isBedrockPlayer(player.getUniqueId());
    }

    /**
     * Checks if a UUID belongs to a Bedrock Edition player.
     * 
     * @param playerUUID The player's UUID
     * @return true if the UUID indicates a Bedrock Edition player
     */
    public boolean isBedrockPlayer(UUID playerUUID) {
        if (playerUUID == null) {
            return false;
        }

        if (floodgateEnabled) {
            try {
                // Use Floodgate API if available for more accurate detection
                Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                Object api = apiClass.getMethod("getInstance").invoke(null);
                return (Boolean) apiClass.getMethod("isFloodgatePlayer", UUID.class).invoke(api, playerUUID);
            } catch (Exception e) {
                plugin.getLogger().fine("[BedrockUtils] Floodgate API error, falling back to pattern matching: " + e.getMessage());
                // Fall back to pattern matching if API fails
            }
        }

        // Fallback: Check UUID pattern
        return isBedrockUUID(playerUUID.toString());
    }

    /**
     * Checks if a UUID string matches the Bedrock/Floodgate pattern.
     * 
     * @param uuid The UUID string to check
     * @return true if it matches Bedrock UUID pattern
     */
    public boolean isBedrockUUID(String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return false;
        }

        return BEDROCK_UUID_PATTERN.matcher(uuid).matches();
    }

    /**
     * Gets the Bedrock username from a Bedrock player.
     * This attempts to get the original Bedrock username when possible.
     * 
     * @param player The Bedrock player
     * @return The Bedrock username, or regular username if not available
     */
    public String getBedrockUsername(Player player) {
        if (!isBedrockPlayer(player)) {
            return player.getName();
        }

        if (floodgateEnabled) {
            try {
                Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                Object api = apiClass.getMethod("getInstance").invoke(null);
                Object floodgatePlayer = apiClass.getMethod("getPlayer", UUID.class).invoke(api, player.getUniqueId());
                if (floodgatePlayer != null) {
                    return (String) floodgatePlayer.getClass().getMethod("getCorrectUsername").invoke(floodgatePlayer);
                }
            } catch (Exception e) {
                plugin.getLogger().fine("[BedrockUtils] Error getting Bedrock username: " + e.getMessage());
            }
        }

        return player.getName();
    }

    /**
     * Adapts a message for Bedrock Edition compatibility.
     * Removes or replaces characters that may not display properly on Bedrock.
     * 
     * @param message The original message
     * @param isBedrockPlayer Whether the receiving player is from Bedrock
     * @return The adapted message
     */
    public String adaptMessageForBedrock(String message, boolean isBedrockPlayer) {
        if (!isBedrockPlayer || message == null) {
            return message;
        }

        String adapted = message;

        // Replace complex color codes that might not work on Bedrock
        adapted = adapted.replace("§k", ""); // Remove magic/obfuscated text
        adapted = adapted.replace("§l", ""); // Remove bold (can cause issues)
        adapted = adapted.replace("§m", ""); // Remove strikethrough
        adapted = adapted.replace("§n", ""); // Remove underline
        adapted = adapted.replace("§o", ""); // Remove italic

        // Limit message length for Bedrock
        if (adapted.length() > BEDROCK_MAX_MESSAGE_LENGTH) {
            adapted = adapted.substring(0, BEDROCK_MAX_MESSAGE_LENGTH - 3) + "...";
        }

        // Replace problematic characters
        adapted = adapted.replace("✅", "[OK]");
        adapted = adapted.replace("❌", "[X]");
        adapted = adapted.replace("⚠", "[!]");
        adapted = adapted.replace("▶", ">");
        adapted = adapted.replace("◀", "<");
        
        return adapted;
    }

    /**
     * Validates if a container name is safe for Bedrock Edition players.
     * Bedrock Edition has more restrictive character support.
     * 
     * @param name The container name to validate
     * @return true if the name is safe for Bedrock players
     */
    public boolean isBedrockSafeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }

        String trimmed = name.trim();
        
        // Check for safe characters only
        if (!BEDROCK_SAFE_CHARS.matcher(trimmed).matches()) {
            return false;
        }

        // Check length (Bedrock has stricter limits)
        return trimmed.length() <= 12;
    }

    /**
     * Gets platform information for a player.
     * 
     * @param player The player
     * @return A string indicating the player's platform
     */
    public String getPlayerPlatform(Player player) {
        if (isBedrockPlayer(player)) {
            return "Bedrock";
        }
        return "Java";
    }

    /**
     * Checks if Floodgate is available and working.
     * 
     * @return true if Floodgate is available
     */
    public static boolean isFloodgateAvailable() {
        return floodgateEnabled;
    }

    /**
     * Gets statistics about connected players by platform.
     * 
     * @return A formatted string with platform statistics
     */
    public String getPlatformStatistics() {
        int javaPlayers = 0;
        int bedrockPlayers = 0;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isBedrockPlayer(player)) {
                bedrockPlayers++;
            } else {
                javaPlayers++;
            }
        }

        return String.format("Java: %d, Bedrock: %d, Total: %d", 
                javaPlayers, bedrockPlayers, javaPlayers + bedrockPlayers);
    }

    /**
     * Logs platform information for debugging purposes.
     * 
     * @param player The player
     * @param action The action being performed
     */
    public void logPlatformInfo(Player player, String action) {
        if (plugin.getConfig().getBoolean("debug.log-platform-info", false)) {
            String platform = getPlayerPlatform(player);
            plugin.getLogger().info(String.format("[Platform] %s (%s) performed: %s", 
                    player.getName(), platform, action));
        }
    }
}