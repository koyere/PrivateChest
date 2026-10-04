package me.tuplugin.privatechest.storage;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry of every protected container.
 *
 * <p>This is the single source of truth at runtime. Every read and write of protection data goes
 * through here, and every mutation is reported to the {@link PersistenceQueue} automatically, so
 * callers can no longer forget to persist a change (or trigger a full-file rewrite by hand, which is
 * what the old {@code saveData()} calls did on every single event).</p>
 *
 * <p>A secondary index by owner is maintained so that per-player counts, which the limit system asks
 * for on every lock attempt, are O(1) instead of a full scan of every protection on the server.</p>
 *
 * @since 3.0
 */
public final class ProtectionStore {

    private final Map<ProtectionKey, Protection> protections = new ConcurrentHashMap<>();
    private final Map<UUID, Set<ProtectionKey>> byOwner = new ConcurrentHashMap<>();

    /**
     * How many protections each chunk contains.
     *
     * <p>This is the index that makes protection checks cheap on hot event paths. Hopper transfers,
     * piston moves and explosions can rule themselves out with one lookup here, without inspecting
     * any block state.</p>
     */
    private final Map<ChunkKey, Set<ProtectionKey>> byChunk = new ConcurrentHashMap<>();

    /** Set once persistence is available. Null while data is being loaded. */
    private volatile PersistenceQueue persistence;

    public void setPersistence(PersistenceQueue persistence) {
        this.persistence = persistence;
    }

    // --- Queries ---

    public Protection get(ProtectionKey key) {
        return key == null ? null : protections.get(key);
    }

    public boolean isProtected(ProtectionKey key) {
        return key != null && protections.containsKey(key);
    }

    public int size() {
        return protections.size();
    }

    /**
     * Number of containers owned by a player. Answered from the owner index, no world access.
     */
    public int countByOwner(UUID owner) {
        if (owner == null) {
            return 0;
        }
        Set<ProtectionKey> keys = byOwner.get(owner);
        return keys == null ? 0 : keys.size();
    }

    /**
     * Snapshot of the keys owned by a player. Safe to iterate and mutate the store while iterating.
     */
    public Set<ProtectionKey> keysByOwner(UUID owner) {
        if (owner == null) {
            return Collections.emptySet();
        }
        Set<ProtectionKey> keys = byOwner.get(owner);
        return keys == null ? Collections.<ProtectionKey>emptySet() : new HashSet<>(keys);
    }

    /**
     * Snapshot of every player who owns at least one protection.
     *
     * <p>Lets a lookup by player name stay proportional to the number of owners rather than to the
     * number of protections.</p>
     */
    public Set<UUID> owners() {
        return new HashSet<>(byOwner.keySet());
    }

    /**
     * Snapshot of every key. Safe to iterate while the store is being mutated.
     */
    public Set<ProtectionKey> keys() {
        return new HashSet<>(protections.keySet());
    }

    /**
     * Live, read-only view of all entries. Intended for the persistence worker, which must not
     * copy the whole dataset on every flush.
     */
    public Map<ProtectionKey, Protection> view() {
        return Collections.unmodifiableMap(protections);
    }

    /**
     * Whether a chunk contains any protected container.
     *
     * <p>One map lookup, no world access. Event handlers on hot paths use this to rule themselves out
     * before doing any real work.</p>
     */
    public boolean hasProtectionsInChunk(String world, int chunkX, int chunkZ) {
        if (world == null || byChunk.isEmpty()) {
            return false;
        }
        return byChunk.containsKey(ChunkKey.of(world, chunkX, chunkZ));
    }

    /**
     * Whether a block position's chunk contains any protected container.
     */
    public boolean hasProtectionsNearBlock(String world, int blockX, int blockZ) {
        if (world == null || byChunk.isEmpty()) {
            return false;
        }
        return byChunk.containsKey(ChunkKey.ofBlock(world, blockX, blockZ));
    }

    /**
     * Whether anything at all is protected. Lets handlers exit immediately on servers with no
     * protections yet.
     */
    public boolean isEmpty() {
        return protections.isEmpty();
    }

    /**
     * Snapshot of every chunk that contains at least one protection.
     */
    public Set<ChunkKey> chunksWithProtections() {
        return new HashSet<>(byChunk.keySet());
    }

    /**
     * Snapshot of the protected positions inside one chunk. Safe to iterate while mutating.
     */
    public Set<ProtectionKey> keysInChunk(ChunkKey chunk) {
        if (chunk == null) {
            return Collections.emptySet();
        }
        Set<ProtectionKey> keys = byChunk.get(chunk);
        return keys == null ? Collections.<ProtectionKey>emptySet() : new HashSet<>(keys);
    }

    // --- Mutations (all of these mark the record dirty) ---

    /**
     * Registers a new protection.
     *
     * @return false if the position was already protected, leaving the existing record untouched
     */
    public boolean add(ProtectionKey key, Protection protection) {
        if (key == null || protection == null) {
            return false;
        }
        if (protections.putIfAbsent(key, protection) != null) {
            return false;
        }
        indexOwner(key, protection.getOwner());
        indexChunk(key);
        markDirty(key);
        return true;
    }

    /**
     * Replaces an existing record, for example after a password migration or a rename.
     *
     * @return the previous record, or null if the position was not protected
     */
    public Protection replace(ProtectionKey key, Protection protection) {
        if (key == null || protection == null) {
            return null;
        }
        Protection previous = protections.replace(key, protection);
        if (previous == null) {
            return null;
        }
        if (!previous.getOwner().equals(protection.getOwner())) {
            unindexOwner(key, previous.getOwner());
            indexOwner(key, protection.getOwner());
        }
        markDirty(key);
        return previous;
    }

    /**
     * Removes a protection.
     *
     * @return the removed record, or null if the position was not protected
     */
    public Protection remove(ProtectionKey key) {
        if (key == null) {
            return null;
        }
        Protection removed = protections.remove(key);
        if (removed == null) {
            return null;
        }
        unindexOwner(key, removed.getOwner());
        unindexChunk(key);
        markDirty(key);
        return removed;
    }

    // --- Bulk loading ---

    /**
     * Replaces the whole dataset with freshly loaded records, without marking anything dirty.
     * Used by the storage backends at startup and after a migration.
     */
    public void loadAll(Map<ProtectionKey, Protection> loaded) {
        protections.clear();
        byOwner.clear();
        byChunk.clear();

        if (loaded == null) {
            return;
        }
        for (Map.Entry<ProtectionKey, Protection> entry : loaded.entrySet()) {
            ProtectionKey key = entry.getKey();
            Protection protection = entry.getValue();
            if (key == null || protection == null) {
                continue;
            }
            protections.put(key, protection);
            indexOwner(key, protection.getOwner());
            indexChunk(key);
        }
    }

    /**
     * Copy of the dataset, used when handing data to a migration target.
     */
    public Map<ProtectionKey, Protection> snapshot() {
        return new HashMap<>(protections);
    }

    // --- Internals ---

    private void indexOwner(ProtectionKey key, UUID owner) {
        byOwner.computeIfAbsent(owner, k -> ConcurrentHashMap.newKeySet()).add(key);
    }

    private void unindexOwner(ProtectionKey key, UUID owner) {
        Set<ProtectionKey> keys = byOwner.get(owner);
        if (keys == null) {
            return;
        }
        keys.remove(key);
        if (keys.isEmpty()) {
            byOwner.remove(owner, keys);
        }
    }

    private void indexChunk(ProtectionKey key) {
        byChunk.computeIfAbsent(ChunkKey.of(key), k -> ConcurrentHashMap.newKeySet()).add(key);
    }

    private void unindexChunk(ProtectionKey key) {
        ChunkKey chunk = ChunkKey.of(key);
        Set<ProtectionKey> keys = byChunk.get(chunk);
        if (keys == null) {
            return;
        }
        keys.remove(key);
        if (keys.isEmpty()) {
            byChunk.remove(chunk, keys);
        }
    }

    private void markDirty(ProtectionKey key) {
        PersistenceQueue queue = this.persistence;
        if (queue != null) {
            queue.markProtectionDirty(key);
        }
    }
}
