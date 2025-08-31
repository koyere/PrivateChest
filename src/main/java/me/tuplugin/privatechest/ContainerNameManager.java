package me.tuplugin.privatechest;

import me.tuplugin.privatechest.enums.ContainerType;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Manages custom names for protected containers.
 * Provides functionality to assign, retrieve, and validate container names
 * while maintaining data consistency and security.
 * 
 * This is a basic implementation suitable for the free version of PrivateChest.
 * Names are limited to alphanumeric characters and basic length restrictions.
 * 
 * @since 2.1
 * @author PrivateChest Team
 */
public class ContainerNameManager {

    private final PrivateChest plugin;
    private final ChestLocker chestLocker;
    private final Map<Location, String> containerNames;

    // Configuration constants
    private static final int MAX_NAME_LENGTH = 16;
    private static final int MIN_NAME_LENGTH = 1;
    private static final Pattern VALID_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\-\\s]+$");

    // Forbidden names to prevent confusion
    private static final String[] FORBIDDEN_NAMES = {
        "null", "undefined", "admin", "console", "system", "server", 
        "privatechest", "chest", "container", "locked", "protected"
    };

    /**
     * Constructs a new ContainerNameManager.
     * 
     * @param plugin The main plugin instance
     */
    public ContainerNameManager(PrivateChest plugin) {
        this.plugin = plugin;
        this.chestLocker = plugin.getChestLocker();
        this.containerNames = new ConcurrentHashMap<>();
    }

    /**
     * Sets a custom name for a container.
     * The container must be protected and owned by the player to be renamed.
     * 
     * @param location The location of the container
     * @param player The player attempting to set the name
     * @param name The desired name for the container
     * @return A result indicating success or failure with details
     */
    public NameResult setContainerName(Location location, Player player, String name) {
        // Validate container exists and is protected
        Block block = location.getBlock();
        if (!chestLocker.isChestLocked(block)) {
            return new NameResult(false, "container_not_protected");
        }

        // Validate ownership (allow admins to override)
        if (!chestLocker.isOwner(block, player) && !player.hasPermission("privatechest.admin")) {
            return new NameResult(false, "container_not_owned");
        }

        // Validate the proposed name
        NameValidationResult validation = validateName(name);
        if (!validation.isValid()) {
            return new NameResult(false, validation.getErrorKey(), validation.getErrorDetails());
        }

        // Set the name
        containerNames.put(location.clone(), name.trim());

        // Save data asynchronously to prevent lag
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getDataManager().saveData();
        });

        plugin.getLogger().fine(String.format(
            "[ContainerNames] Player %s named container at %s:%d,%d,%d as '%s'",
            player.getName(),
            location.getWorld() != null ? location.getWorld().getName() : "null",
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ(),
            name
        ));

        return new NameResult(true, "container_name_set");
    }

    /**
     * Removes a custom name from a container.
     * 
     * @param location The location of the container
     * @param player The player attempting to remove the name
     * @return A result indicating success or failure with details
     */
    public NameResult removeContainerName(Location location, Player player) {
        // Validate container exists and is protected
        Block block = location.getBlock();
        if (!chestLocker.isChestLocked(block)) {
            return new NameResult(false, "container_not_protected");
        }

        // Validate ownership (allow admins to override)
        if (!chestLocker.isOwner(block, player) && !player.hasPermission("privatechest.admin")) {
            return new NameResult(false, "container_not_owned");
        }

        // Check if container has a name to remove
        if (!hasCustomName(location)) {
            return new NameResult(false, "container_no_custom_name");
        }

        String oldName = containerNames.remove(location);

        // Save data asynchronously
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getDataManager().saveData();
        });

        plugin.getLogger().fine(String.format(
            "[ContainerNames] Player %s removed name '%s' from container at %s:%d,%d,%d",
            player.getName(),
            oldName,
            location.getWorld() != null ? location.getWorld().getName() : "null",
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ()
        ));

        return new NameResult(true, "container_name_removed");
    }

    /**
     * Gets the custom name of a container.
     * 
     * @param location The location of the container
     * @return The custom name, or null if no custom name is set
     */
    public String getContainerName(Location location) {
        return containerNames.get(location);
    }

    /**
     * Gets the display name of a container.
     * This returns the custom name if set, otherwise a default name based on the container type.
     * 
     * @param location The location of the container
     * @return The display name (never null)
     */
    public String getDisplayName(Location location) {
        String customName = getContainerName(location);
        if (customName != null) {
            return customName;
        }

        // Fall back to type-based name
        if (location.getWorld() != null) {
            Block block = location.getBlock();
            ContainerType type = ContainerType.fromMaterial(block.getType());
            if (type != null) {
                return type.getDisplayName();
            }
        }

        return "Container";
    }

    /**
     * Checks if a container has a custom name.
     * 
     * @param location The location of the container
     * @return true if the container has a custom name
     */
    public boolean hasCustomName(Location location) {
        return containerNames.containsKey(location);
    }

    /**
     * Validates a proposed container name.
     * 
     * @param name The name to validate
     * @return A validation result with details
     */
    private NameValidationResult validateName(String name) {
        // Check for null or empty
        if (name == null || name.trim().isEmpty()) {
            return new NameValidationResult(false, "name_empty");
        }

        String trimmedName = name.trim();

        // Check length constraints
        if (trimmedName.length() < MIN_NAME_LENGTH) {
            return new NameValidationResult(false, "name_too_short", 
                String.valueOf(MIN_NAME_LENGTH));
        }

        if (trimmedName.length() > MAX_NAME_LENGTH) {
            return new NameValidationResult(false, "name_too_long", 
                String.valueOf(MAX_NAME_LENGTH));
        }

        // Check character restrictions (alphanumeric, spaces, hyphens, underscores only)
        if (!VALID_NAME_PATTERN.matcher(trimmedName).matches()) {
            return new NameValidationResult(false, "name_invalid_characters");
        }

        // Check forbidden names
        for (String forbidden : FORBIDDEN_NAMES) {
            if (trimmedName.equalsIgnoreCase(forbidden)) {
                return new NameValidationResult(false, "name_forbidden");
            }
        }

        return new NameValidationResult(true, null);
    }

    /**
     * Gets all container names (for storage and debugging purposes).
     * Returns an unmodifiable view to prevent external modification.
     * 
     * @return An unmodifiable map of all container names
     */
    public Map<Location, String> getAllContainerNames() {
        return Collections.unmodifiableMap(containerNames);
    }

    /**
     * Loads container names from the provided map.
     * This is typically called during plugin initialization.
     * 
     * @param names The map of container names to load
     */
    public void loadContainerNames(Map<Location, String> names) {
        containerNames.clear();
        if (names != null) {
            containerNames.putAll(names);
        }
        
        plugin.getLogger().info("[ContainerNames] Loaded " + containerNames.size() + " custom container names");
    }

    /**
     * Removes a container name entry (used during cleanup).
     * This method is package-private and intended for use by cleanup operations.
     * 
     * @param location The location to remove
     * @return true if a name was removed
     */
    boolean removeContainerNameEntry(Location location) {
        return containerNames.remove(location) != null;
    }

    /**
     * Gets the total number of named containers.
     * 
     * @return The count of containers with custom names
     */
    public int getNamedContainerCount() {
        return containerNames.size();
    }

    /**
     * Result class for name operations.
     */
    public static class NameResult {
        private final boolean success;
        private final String messageKey;
        private final String details;

        public NameResult(boolean success, String messageKey) {
            this(success, messageKey, null);
        }

        public NameResult(boolean success, String messageKey, String details) {
            this.success = success;
            this.messageKey = messageKey;
            this.details = details;
        }

        public boolean isSuccess() { return success; }
        public String getMessageKey() { return messageKey; }
        public String getDetails() { return details; }
    }

    /**
     * Result class for name validation.
     */
    private static class NameValidationResult {
        private final boolean valid;
        private final String errorKey;
        private final String errorDetails;

        public NameValidationResult(boolean valid, String errorKey) {
            this(valid, errorKey, null);
        }

        public NameValidationResult(boolean valid, String errorKey, String errorDetails) {
            this.valid = valid;
            this.errorKey = errorKey;
            this.errorDetails = errorDetails;
        }

        public boolean isValid() { return valid; }
        public String getErrorKey() { return errorKey; }
        public String getErrorDetails() { return errorDetails; }
    }
}