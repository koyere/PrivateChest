package me.tuplugin.privatechest;

import me.tuplugin.privatechest.container.ContainerFamily;
import me.tuplugin.privatechest.storage.Protection;
import me.tuplugin.privatechest.storage.ProtectionKey;
import me.tuplugin.privatechest.storage.ProtectionStore;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.regex.Pattern;

/**
 * Custom names for protected containers.
 *
 * <p>Names are part of the protection record as of 3.0 instead of living in a separate map. Two
 * problems go away with that: names were lost entirely on the SQLite backend, and saving a name used
 * the Bukkit async scheduler directly, which fails on Folia and could race with other writes to
 * data.yml. Names are now persisted by the same worker as everything else.</p>
 *
 * @since 2.1, reworked in 3.0
 */
public class ContainerNameManager {

    private static final int MAX_NAME_LENGTH = 16;
    private static final int MIN_NAME_LENGTH = 1;
    private static final Pattern VALID_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\-\\s]+$");

    /** Names that would be confusing or misleading on a container label. */
    private static final String[] FORBIDDEN_NAMES = {
            "null", "undefined", "admin", "console", "system", "server",
            "privatechest", "chest", "container", "locked", "protected"
    };

    private final PrivateChest plugin;
    private final ProtectionStore store;

    public ContainerNameManager(PrivateChest plugin, ProtectionStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    /**
     * Assigns a custom name to a protected container.
     *
     * @return the outcome, including the message key to show the player
     */
    public NameResult setContainerName(Location location, Player player, String name) {
        ProtectionKey key = ProtectionKey.of(location);
        if (key == null) {
            return new NameResult(false, "container_not_protected");
        }

        Protection protection = store.get(key);
        if (protection == null) {
            return new NameResult(false, "container_not_protected");
        }

        if (!protection.isOwnedBy(player.getUniqueId()) && !player.hasPermission("privatechest.admin")) {
            return new NameResult(false, "container_not_owned");
        }

        NameValidationResult validation = validateName(name);
        if (!validation.isValid()) {
            return new NameResult(false, validation.getErrorKey(), validation.getErrorDetails());
        }

        String trimmed = name.trim();
        store.replace(key, protection.withCustomName(trimmed));

        if (plugin.getLogger().isLoggable(java.util.logging.Level.FINE)) {
            plugin.getLogger().fine("[Names] " + player.getName() + " named the container at "
                    + key.serialize() + " '" + trimmed + "'.");
        }

        return new NameResult(true, "container_name_set");
    }

    /**
     * Clears the custom name of a protected container.
     */
    public NameResult removeContainerName(Location location, Player player) {
        ProtectionKey key = ProtectionKey.of(location);
        if (key == null) {
            return new NameResult(false, "container_not_protected");
        }

        Protection protection = store.get(key);
        if (protection == null) {
            return new NameResult(false, "container_not_protected");
        }

        if (!protection.isOwnedBy(player.getUniqueId()) && !player.hasPermission("privatechest.admin")) {
            return new NameResult(false, "container_not_owned");
        }

        if (protection.getCustomName() == null) {
            return new NameResult(false, "container_no_custom_name");
        }

        store.replace(key, protection.withCustomName(null));

        if (plugin.getLogger().isLoggable(java.util.logging.Level.FINE)) {
            plugin.getLogger().fine("[Names] " + player.getName() + " cleared the name of the container at "
                    + key.serialize() + ".");
        }

        return new NameResult(true, "container_name_removed");
    }

    /**
     * The custom name of a container, or null if it has none or is not protected.
     */
    public String getContainerName(Location location) {
        Protection protection = store.get(ProtectionKey.of(location));
        return protection == null ? null : protection.getCustomName();
    }

    /**
     * Whether a container has a custom name.
     */
    public boolean hasCustomName(Location location) {
        return getContainerName(location) != null;
    }

    /**
     * The custom name if set, otherwise the container's type name, otherwise null.
     *
     * <p>Answered from the protection record only, never by reading the block, so it is safe to call
     * from any thread and from any region. Callers provide their own translated fallback.</p>
     */
    public String getDisplayName(Location location) {
        Protection protection = store.get(ProtectionKey.of(location));
        if (protection == null) {
            return null;
        }

        if (protection.getCustomName() != null) {
            return protection.getCustomName();
        }

        // No custom name: fall back to the translated name of the container's family.
        ContainerFamily family = plugin.getContainerRegistry().getFamilyFromName(protection.getContainerType());
        if (family == null) {
            return null;
        }
        return plugin.getMessageManager().rawOrNull("container_type_" + family.getConfigName());
    }

    /**
     * Number of protected containers that carry a custom name.
     */
    public int getNamedContainerCount() {
        int count = 0;
        for (Protection protection : store.view().values()) {
            if (protection.getCustomName() != null) {
                count++;
            }
        }
        return count;
    }

    // --- Validation ---

    private NameValidationResult validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return new NameValidationResult(false, "name_empty");
        }

        String trimmedName = name.trim();

        if (trimmedName.length() < MIN_NAME_LENGTH) {
            return new NameValidationResult(false, "name_too_short", String.valueOf(MIN_NAME_LENGTH));
        }
        if (trimmedName.length() > MAX_NAME_LENGTH) {
            return new NameValidationResult(false, "name_too_long", String.valueOf(MAX_NAME_LENGTH));
        }
        if (!VALID_NAME_PATTERN.matcher(trimmedName).matches()) {
            return new NameValidationResult(false, "name_invalid_characters");
        }
        for (String forbidden : FORBIDDEN_NAMES) {
            if (trimmedName.equalsIgnoreCase(forbidden)) {
                return new NameValidationResult(false, "name_forbidden");
            }
        }

        return new NameValidationResult(true, null);
    }

    /**
     * Outcome of a naming operation.
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

        public boolean isSuccess() {
            return success;
        }

        public String getMessageKey() {
            return messageKey;
        }

        public String getDetails() {
            return details;
        }
    }

    private static class NameValidationResult {
        private final boolean valid;
        private final String errorKey;
        private final String errorDetails;

        NameValidationResult(boolean valid, String errorKey) {
            this(valid, errorKey, null);
        }

        NameValidationResult(boolean valid, String errorKey, String errorDetails) {
            this.valid = valid;
            this.errorKey = errorKey;
            this.errorDetails = errorDetails;
        }

        boolean isValid() {
            return valid;
        }

        String getErrorKey() {
            return errorKey;
        }

        String getErrorDetails() {
            return errorDetails;
        }
    }
}
