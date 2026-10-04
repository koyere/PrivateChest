package me.tuplugin.privatechest;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Decides which worlds new protections may be created in.
 *
 * <p>Only the creation of new locks is restricted. Existing protections are enforced in every world
 * regardless of this policy, and {@link AccessGuard} deliberately never consults it: if it did, adding
 * a world to a blacklist would silently open every locked container in that world, hoppers included.</p>
 *
 * <p>The settings are read once and rebuilt by {@code /privatechest reload}, because the check runs on
 * every lock attempt and the world list would otherwise be re-parsed each time.</p>
 *
 * @since 3.1
 */
public final class WorldPolicy {

    /** Lets a player lock containers in worlds the policy excludes. */
    public static final String BYPASS_PERMISSION = "privatechest.bypass.worlds";

    public enum Mode {
        /** No restriction at all. */
        DISABLED,
        /** Locking only in the listed worlds. */
        WHITELIST,
        /** Locking everywhere except the listed worlds. */
        BLACKLIST
    }

    private final PrivateChest plugin;

    private volatile Mode mode = Mode.DISABLED;
    private volatile Set<String> worlds = Collections.emptySet();

    public WorldPolicy(PrivateChest plugin) {
        this.plugin = plugin;
    }

    /**
     * Re-reads the {@code worlds} section of config.yml.
     */
    public void reload() {
        String configured = plugin.getConfig().getString("worlds.mode", "DISABLED");
        Mode parsed;
        try {
            parsed = Mode.valueOf(configured.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            // Falling back to no restriction is the safe direction: a typo must not stop every player
            // on the server from locking anything.
            plugin.getLogger().warning("[Worlds] Unknown worlds.mode '" + configured
                    + "'. Expected WHITELIST, BLACKLIST or DISABLED. World restrictions are off.");
            parsed = Mode.DISABLED;
        }

        // World folder names are case-sensitive on most file systems, but two worlds differing only in
        // case is far less likely than an admin typing "World" for "world".
        Set<String> names = new HashSet<>();
        for (String name : plugin.getConfig().getStringList("worlds.list")) {
            if (name != null && !name.trim().isEmpty()) {
                names.add(name.trim().toLowerCase(Locale.ROOT));
            }
        }

        this.worlds = Collections.unmodifiableSet(names);
        this.mode = parsed;

        if (parsed == Mode.WHITELIST && names.isEmpty()) {
            plugin.getLogger().warning("[Worlds] worlds.mode is WHITELIST but worlds.list is empty, "
                    + "so no container can be locked in any world.");
        }
    }

    /**
     * Whether new protections may be created in a world, ignoring permissions.
     */
    public boolean allowsLocking(World world) {
        return world != null && allowsLocking(world.getName());
    }

    /**
     * Whether new protections may be created in a world, by name, ignoring permissions.
     */
    public boolean allowsLocking(String worldName) {
        Mode current = this.mode;
        if (current == Mode.DISABLED) {
            return true;
        }
        if (worldName == null) {
            return false;
        }

        boolean listed = worlds.contains(worldName.toLowerCase(Locale.ROOT));
        return current == Mode.WHITELIST ? listed : !listed;
    }

    /**
     * Whether a player may create new protections in a world, taking the bypass permission into
     * account.
     */
    public boolean allowsLocking(Player player, World world) {
        if (allowsLocking(world)) {
            return true;
        }
        return player != null && player.hasPermission(BYPASS_PERMISSION);
    }

    public Mode getMode() {
        return mode;
    }

    /**
     * Number of worlds in the configured list.
     */
    public int getWorldCount() {
        return worlds.size();
    }
}
