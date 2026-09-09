package me.tuplugin.privatechest.storage;

/**
 * Identifies a chunk by world name and chunk coordinates.
 *
 * <p>Used as the key of the registry's per-chunk index. That index is what lets the hot event paths
 * answer "is there anything protected near here?" with a single map lookup, instead of inspecting
 * block state on every hopper transfer.</p>
 *
 * @since 3.0
 */
public final class ChunkKey {

    private final String world;
    private final int chunkX;
    private final int chunkZ;
    private final int hash;

    private ChunkKey(String world, int chunkX, int chunkZ) {
        this.world = world;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;

        int h = world.hashCode();
        h = 31 * h + chunkX;
        this.hash = 31 * h + chunkZ;
    }

    public static ChunkKey of(String world, int chunkX, int chunkZ) {
        if (world == null) {
            return null;
        }
        return new ChunkKey(world, chunkX, chunkZ);
    }

    /**
     * The chunk containing a protected position.
     */
    public static ChunkKey of(ProtectionKey key) {
        if (key == null) {
            return null;
        }
        return new ChunkKey(key.getWorld(), key.getX() >> 4, key.getZ() >> 4);
    }

    /**
     * The chunk containing a block position.
     */
    public static ChunkKey ofBlock(String world, int blockX, int blockZ) {
        if (world == null) {
            return null;
        }
        return new ChunkKey(world, blockX >> 4, blockZ >> 4);
    }

    public String getWorld() {
        return world;
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ChunkKey)) {
            return false;
        }
        ChunkKey that = (ChunkKey) other;
        return this.hash == that.hash
                && this.chunkX == that.chunkX
                && this.chunkZ == that.chunkZ
                && this.world.equals(that.world);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return world + " [" + chunkX + ", " + chunkZ + "]";
    }
}
