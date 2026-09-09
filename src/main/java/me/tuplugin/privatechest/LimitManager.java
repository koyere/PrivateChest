package me.tuplugin.privatechest;

import me.tuplugin.privatechest.container.ContainerFamily;
import me.tuplugin.privatechest.storage.Protection;
import me.tuplugin.privatechest.storage.ProtectionKey;
import me.tuplugin.privatechest.storage.ProtectionStore;
import org.bukkit.entity.Player;

import org.bukkit.Material;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player container limits driven by permissions.
 *
 * <p>Counting was rewritten in 3.0. Totals now come from the registry's owner index instead of
 * scanning every protection on the server, and per-type counts read the container type recorded in
 * the protection itself. The old implementation called {@code Location#getBlock()} for every record
 * a player owned, which forced chunk loads on the main thread on every single lock attempt.</p>
 */
public class LimitManager {

    private static LimitManager instance;

    private final PrivateChest plugin;
    private final ProtectionStore store;

    public LimitManager(PrivateChest plugin, ProtectionStore store) {
        this.plugin = plugin;
        this.store = store;
        instance = this;
    }

    public static LimitManager getInstance() {
        return instance;
    }

    // --- Configuration ---

    /**
     * Whether the basic limit system is switched on.
     */
    public boolean areLimitsEnabled() {
        return plugin.getConfig().getBoolean("enable-chest-limits", false);
    }

    /**
     * Whether per-container-type limits are switched on.
     */
    public boolean areGranularLimitsEnabled() {
        return plugin.getConfig().getBoolean("container-limits.enabled", false);
    }

    /**
     * The limit applied to players without a specific limit permission.
     */
    public int getDefaultLimit() {
        return plugin.getConfig().getInt("default-chest-limit", 5);
    }

    /**
     * The total number of containers a player may lock, or -1 for unlimited.
     *
     * <p>A limit permission always wins over {@code default-chest-limit}. 2.x seeded the result with
     * the default and then took the larger of the two, so a player granted
     * {@code privatechest.limit.1} on a server with a default of 5 was given 5. Permissions below the
     * default could not restrict anyone, which is the opposite of what they are for.</p>
     */
    public int getPlayerLimit(Player player) {
        if (!areLimitsEnabled()) {
            return -1;
        }

        Integer fromPermission = highestLimitFromPermissions(player, "privatechest.limit.");
        return fromPermission != null ? fromPermission : getDefaultLimit();
    }

    /**
     * The number of containers of one family a player may lock, or -1 for unlimited.
     *
     * <p>Resolution order: a family permission, then the family's configured limit, then
     * {@code container-limits.default-limit}. A global {@code privatechest.limit.unlimited} still
     * overrides everything.</p>
     */
    public int getPlayerLimitForType(Player player, ContainerFamily family) {
        if (family == null || !areGranularLimitsEnabled()) {
            return getPlayerLimit(player);
        }

        if (player.hasPermission("privatechest.limit.unlimited")) {
            return -1;
        }

        Integer fromPermission = highestLimitFromPermissions(player,
                "privatechest.limit." + family.getConfigName() + ".");
        if (fromPermission != null) {
            return fromPermission;
        }

        return plugin.getConfig().getInt("container-limits.types." + family.getConfigName(),
                plugin.getConfig().getInt("container-limits.default-limit", 5));
    }

    /**
     * Finds the most generous limit granted directly under a permission prefix.
     *
     * <p>Reads the player's effective permissions rather than testing a fixed list of numbers. 2.x only
     * ever checked eleven specific values, so {@code privatechest.limit.7} silently did nothing. Any
     * number works now.</p>
     *
     * @param prefix the node prefix, ending in a dot
     * @return the highest limit found, -1 for unlimited, or null if the player has none
     */
    private Integer highestLimitFromPermissions(Player player, String prefix) {
        Integer best = null;

        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (!info.getValue()) {
                continue;
            }

            String permission = info.getPermission().toLowerCase(Locale.ROOT);
            if (!permission.startsWith(prefix)) {
                continue;
            }

            String suffix = permission.substring(prefix.length());
            // Skip deeper nodes, so the global prefix does not pick up family limits.
            if (suffix.isEmpty() || suffix.indexOf('.') >= 0) {
                continue;
            }

            if ("unlimited".equals(suffix)) {
                return -1;
            }

            try {
                int value = Integer.parseInt(suffix);
                if (value >= 0 && (best == null || value > best)) {
                    best = value;
                }
            } catch (NumberFormatException e) {
                // Not a limit node.
            }
        }

        return best;
    }

    // --- Counting ---

    /**
     * How many containers a player currently owns. Answered from the owner index in constant time.
     */
    public int getPlayerChestCount(Player player) {
        return player == null ? 0 : store.countByOwner(player.getUniqueId());
    }

    /**
     * How many containers a player currently owns, by UUID string.
     */
    public int getPlayerChestCount(String playerUUID) {
        if (playerUUID == null) {
            return 0;
        }
        try {
            return store.countByOwner(UUID.fromString(playerUUID));
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }

    /**
     * How many containers of one type a player currently owns.
     */
    public int getPlayerContainerCountByType(Player player, ContainerFamily family) {
        if (player == null || family == null) {
            return 0;
        }

        int count = 0;
        for (ProtectionKey key : store.keysByOwner(player.getUniqueId())) {
            if (resolveFamily(key) == family) {
                count++;
            }
        }
        return count;
    }

    /**
     * A breakdown of a player's containers by type. Types that cannot be determined are omitted.
     */
    public Map<ContainerFamily, Integer> getPlayerContainerCountsByType(Player player) {
        Map<ContainerFamily, Integer> counts = new HashMap<>();
        for (ContainerFamily family : ContainerFamily.values()) {
            counts.put(family, 0);
        }

        if (player == null) {
            return counts;
        }

        for (ProtectionKey key : store.keysByOwner(player.getUniqueId())) {
            ContainerFamily family = resolveFamily(key);
            if (family != null) {
                counts.put(family, counts.get(family) + 1);
            }
        }
        return counts;
    }

    /**
     * Determines a protected container's family from its record alone.
     *
     * <p>Deliberately never reads the world. A player's containers can be anywhere, and on Folia
     * reading a block owned by another region from this thread is not allowed. Records created before
     * the type was tracked return null until the cleanup pass fills them in, which happens shortly
     * after startup.</p>
     *
     * <p>Resolving by family rather than by exact material is what makes this stable: a copper chest
     * changes material as it oxidizes, and shulker boxes come in seventeen colours.</p>
     */
    private ContainerFamily resolveFamily(ProtectionKey key) {
        Protection protection = store.get(key);
        if (protection == null) {
            return null;
        }
        return plugin.getContainerRegistry().getFamilyFromName(protection.getContainerType());
    }

    // --- Checks ---

    public boolean canPlayerLockMore(Player player) {
        int limit = getPlayerLimit(player);
        return limit == -1 || getPlayerChestCount(player) < limit;
    }

    public boolean canPlayerLockAdditional(Player player, int additionalChests) {
        int limit = getPlayerLimit(player);
        return limit == -1 || (getPlayerChestCount(player) + additionalChests) <= limit;
    }

    public boolean canPlayerLockMoreOfType(Player player, ContainerFamily family) {
        int limit = getPlayerLimitForType(player, family);
        return limit == -1 || getPlayerContainerCountByType(player, family) < limit;
    }

    public boolean canPlayerLockAdditionalOfType(Player player, ContainerFamily family, int additional) {
        int limit = getPlayerLimitForType(player, family);
        return limit == -1
                || (getPlayerContainerCountByType(player, family) + additional) <= limit;
    }

    /**
     * How many more containers a player may lock, or -1 for unlimited.
     */
    public int getRemainingLocks(Player player) {
        int limit = getPlayerLimit(player);
        if (limit == -1) {
            return -1;
        }
        return Math.max(0, limit - getPlayerChestCount(player));
    }

    // --- Single entry point used by every locking path ---

    /**
     * Decides whether a player may lock a number of containers of a given type.
     *
     * <p>Every locking path goes through here, so the command and the {@code [Private]} sign can no
     * longer disagree. It also picks the right system: per-family limits when
     * {@code container-limits.enabled} is on, the overall limit otherwise. The per-family system was
     * fully implemented in 2.x but nothing ever called it, so the whole {@code container-limits}
     * section of config.yml and every per-type permission node had no effect.</p>
     *
     * @param player   the player attempting to lock
     * @param material the container being locked
     * @param count    how many container blocks this would protect, two for a double chest
     */
    public LimitCheck checkCanLock(Player player, Material material, int count) {
        if (player == null) {
            return LimitCheck.allowed();
        }
        // Staff bypass, and the whole system being switched off, are the same answer.
        if (player.hasPermission("privatechest.admin") || !areLimitsEnabled()) {
            return LimitCheck.allowed();
        }

        ContainerFamily family = plugin.getContainerRegistry().getFamily(material);
        boolean perFamily = areGranularLimitsEnabled() && family != null;

        int limit = perFamily ? getPlayerLimitForType(player, family) : getPlayerLimit(player);
        if (limit == -1) {
            return LimitCheck.allowed();
        }

        int current = perFamily
                ? getPlayerContainerCountByType(player, family)
                : getPlayerChestCount(player);

        boolean allowed = (current + count) <= limit;
        return new LimitCheck(allowed, current, limit, count, perFamily ? family : null);
    }

    /**
     * The message to show when {@link #checkCanLock} refuses.
     */
    public String getDeniedMessage(LimitCheck check) {
        MessageManager messages = plugin.getMessageManager();

        String key = check.getFamily() != null ? "limit_exceeded_type" : "limit_exceeded";
        return applyPlaceholders(messages.get(key), check);
    }

    /**
     * The message to show when a {@code [Private]} sign is refused for exceeding a limit.
     */
    public String getSignDeniedMessage(LimitCheck check) {
        MessageManager messages = plugin.getMessageManager();

        String key = check.getFamily() != null ? "sign_limit_exceeded_type" : "sign_limit_exceeded";
        return applyPlaceholders(messages.get(key), check);
    }

    private String applyPlaceholders(String message, LimitCheck check) {
        return message
                .replace("{current}", String.valueOf(check.getCurrent()))
                .replace("{limit}", String.valueOf(check.getLimit()))
                .replace("{trying}", String.valueOf(check.getRequested()))
                .replace("{container}", familyName(check.getFamily()));
    }

    /**
     * The translated name of a container family, for use in messages.
     */
    public String familyName(ContainerFamily family) {
        MessageManager messages = plugin.getMessageManager();
        if (family == null) {
            return messages.raw("container_generic_name");
        }

        String translated = messages.rawOrNull("container_type_" + family.getConfigName());
        return translated != null ? translated : messages.raw("container_generic_name");
    }

    // --- Messaging ---

    /**
     * A "used/allowed" string for display, using the translated word for unlimited.
     */
    public String getLimitStatus(Player player) {
        return getLimitStatus(player, null);
    }

    /**
     * A "used/allowed" string, scoped to one family when per-family limits are in use.
     */
    public String getLimitStatus(Player player, ContainerFamily family) {
        boolean perFamily = areGranularLimitsEnabled() && family != null;

        int limit = perFamily ? getPlayerLimitForType(player, family) : getPlayerLimit(player);
        int current = perFamily ? getPlayerContainerCountByType(player, family) : getPlayerChestCount(player);

        if (limit == -1) {
            return current + "/" + plugin.getMessageManager().raw("limit_unlimited");
        }
        return current + "/" + limit;
    }

    /**
     * A warning when a player is close to or at their limit, or null when there is nothing to say.
     */
    public String getLimitWarningMessage(Player player) {
        return getLimitWarningMessage(player, null);
    }

    /**
     * A warning scoped to one family when per-family limits are in use.
     */
    public String getLimitWarningMessage(Player player, ContainerFamily family) {
        if (!areLimitsEnabled() || player.hasPermission("privatechest.admin")) {
            return null;
        }

        boolean perFamily = areGranularLimitsEnabled() && family != null;
        int limit = perFamily ? getPlayerLimitForType(player, family) : getPlayerLimit(player);
        if (limit == -1) {
            return null;
        }

        int current = perFamily ? getPlayerContainerCountByType(player, family) : getPlayerChestCount(player);
        int remaining = limit - current;

        MessageManager messages = plugin.getMessageManager();

        if (remaining <= 0) {
            return messages.get("limit_reached")
                    .replace("{limit}", String.valueOf(limit))
                    .replace("{container}", familyName(perFamily ? family : null));
        }
        if (remaining <= 2) {
            return messages.get("limit_warning")
                    .replace("{remaining}", String.valueOf(remaining))
                    .replace("{limit}", String.valueOf(limit))
                    .replace("{container}", familyName(perFamily ? family : null));
        }

        return null;
    }

    /**
     * The outcome of a limit check.
     */
    public static final class LimitCheck {

        private final boolean allowed;
        private final int current;
        private final int limit;
        private final int requested;
        private final ContainerFamily family;

        private LimitCheck(boolean allowed, int current, int limit, int requested, ContainerFamily family) {
            this.allowed = allowed;
            this.current = current;
            this.limit = limit;
            this.requested = requested;
            this.family = family;
        }

        static LimitCheck allowed() {
            return new LimitCheck(true, 0, -1, 0, null);
        }

        public boolean isAllowed() {
            return allowed;
        }

        public int getCurrent() {
            return current;
        }

        public int getLimit() {
            return limit;
        }

        public int getRequested() {
            return requested;
        }

        /**
         * The family the limit applied to, or null when the overall limit was used.
         */
        public ContainerFamily getFamily() {
            return family;
        }
    }
}
