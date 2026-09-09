package me.tuplugin.privatechest.storage;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Immutable identity of a protected container position.
 *
 * <p>Replaces {@link org.bukkit.Location} as the map key of the protection registry. This matters
 * for three reasons:</p>
 * <ul>
 *     <li>{@code Location} holds a hard reference to a {@link World}. When a world was unloaded and
 *     loaded again, the old keys stopped matching and every protection inside that world was
 *     silently lost. This key stores the world <em>name</em> instead, so records survive world
 *     unloads, and can even be loaded for worlds that are not mounted at all.</li>
 *     <li>{@code Location#hashCode} mixes in yaw and pitch and is recomputed on every lookup. This
 *     key caches its hash at construction time, so protection lookups on hot paths (hopper
 *     transfers, block breaks, interactions) are a single int comparison plus an equals.</li>
 *     <li>It is much smaller in memory, which adds up on servers with tens of thousands of
 *     protected containers.</li>
 * </ul>
 *
 * @since 3.0
 */
public final class ProtectionKey {

    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private final int hash;

    private ProtectionKey(String world, int x, int y, int z) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;

        int h = world.hashCode();
        h = 31 * h + x;
        h = 31 * h + y;
        h = 31 * h + z;
        this.hash = h;
    }

    /**
     * Creates a key from a world name and block coordinates.
     *
     * @return the key, or null if the world name is null
     */
    public static ProtectionKey of(String world, int x, int y, int z) {
        if (world == null) {
            return null;
        }
        return new ProtectionKey(world, x, y, z);
    }

    /**
     * Creates a key from a block.
     *
     * @return the key, or null if the block is null
     */
    public static ProtectionKey of(Block block) {
        if (block == null) {
            return null;
        }
        return new ProtectionKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    /**
     * Creates a key from a location.
     *
     * @return the key, or null if the location or its world is null
     */
    public static ProtectionKey of(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return new ProtectionKey(location.getWorld().getName(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    /**
     * Parses the legacy {@code world,x,y,z} string used by data.yml.
     *
     * <p>The coordinates are parsed from the right so that world names containing commas are
     * handled correctly. The old implementation split on every comma and discarded such records.</p>
     *
     * @param serialized the serialized form
     * @return the key, or null if the string is malformed
     */
    public static ProtectionKey deserialize(String serialized) {
        if (serialized == null) {
            return null;
        }

        int zSep = serialized.lastIndexOf(',');
        if (zSep <= 0) {
            return null;
        }
        int ySep = serialized.lastIndexOf(',', zSep - 1);
        if (ySep <= 0) {
            return null;
        }
        int xSep = serialized.lastIndexOf(',', ySep - 1);
        if (xSep <= 0) {
            return null;
        }

        String world = serialized.substring(0, xSep);
        if (world.isEmpty()) {
            return null;
        }

        try {
            int x = Integer.parseInt(serialized.substring(xSep + 1, ySep).trim());
            int y = Integer.parseInt(serialized.substring(ySep + 1, zSep).trim());
            int z = Integer.parseInt(serialized.substring(zSep + 1).trim());
            return new ProtectionKey(world, x, y, z);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Serializes to the {@code world,x,y,z} form used by data.yml.
     */
    public String serialize() {
        return world + "," + x + "," + y + "," + z;
    }

    public String getWorld() {
        return world;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    /**
     * Resolves this key to a live location.
     *
     * @return the location, or null if the world is not currently loaded
     */
    public Location toLocation() {
        World w = Bukkit.getWorld(world);
        if (w == null) {
            return null;
        }
        return new Location(w, x, y, z);
    }

    /**
     * Checks whether the chunk containing this key is currently loaded.
     *
     * <p>Must be called from the thread that owns this position. On Folia that is the region thread,
     * which means anything scheduled through
     * {@code SchedulerUtils.runAtChunk} or an event handler for this location.</p>
     *
     * @return false when the world is not loaded at all
     */
    public boolean isChunkLoaded() {
        World w = Bukkit.getWorld(world);
        return w != null && w.isChunkLoaded(x >> 4, z >> 4);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProtectionKey)) {
            return false;
        }
        ProtectionKey that = (ProtectionKey) other;
        return this.hash == that.hash
                && this.x == that.x
                && this.y == that.y
                && this.z == that.z
                && this.world.equals(that.world);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return serialize();
    }
}
