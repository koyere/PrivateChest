package me.tuplugin.privatechest.storage;

import java.util.Map;
import java.util.Set;

/**
 * A persistence backend for protection and trust data.
 *
 * <p>Rewritten in 3.0. The previous contract only offered "load everything" and "save everything",
 * which meant a single player locking a chest rewrote the entire data file (or, on SQLite, deleted
 * and re-inserted every row) on the main thread. It also silently dropped container names and trust
 * relations on the SQLite backend, because those parameters were accepted and ignored.</p>
 *
 * <p>The contract is now incremental: the persistence worker reports exactly which records changed
 * and the backend applies just those. Implementations only ever run on the persistence worker
 * thread, so they do not need to be thread-safe internally, but they must never touch the Bukkit
 * API.</p>
 *
 * @since 3.0
 */
public interface DataStorage {

    /**
     * Opens the backend, creating and upgrading the schema or file layout as needed.
     *
     * @return true if the backend is usable
     */
    boolean initialize();

    /**
     * Reads the full dataset. Called once at startup and again after a storage migration.
     *
     * @return the loaded data, or null if reading failed
     */
    LoadResult loadAll();

    /**
     * Applies a set of changes.
     *
     * <p>Keys present in {@code protectionUpserts} must be inserted or updated. Keys present in
     * {@code protectionDeletes} must be removed. The same applies to the trust maps, where the key
     * is an owner UUID string and the value is that owner's complete trusted list.</p>
     *
     * @return true if every change was applied
     */
    boolean writeBatch(Map<ProtectionKey, Protection> protectionUpserts,
                       Set<ProtectionKey> protectionDeletes,
                       Map<String, Set<String>> trustUpserts,
                       Set<String> trustDeletes);

    /**
     * Replaces the entire contents of the backend with the given dataset.
     * Used for storage migrations and for the initial write of an empty backend.
     *
     * @return true if the write succeeded
     */
    boolean writeAll(Map<ProtectionKey, Protection> protections, Map<String, Set<String>> trust);

    /**
     * Flushes any buffered state to disk. Called before shutdown and after migrations.
     *
     * @return true if the flush succeeded
     */
    boolean flush();

    /**
     * Releases file handles and connections.
     */
    void close();

    /**
     * Human readable backend name, used in log output and admin messages.
     */
    String getStorageType();

    /**
     * Whether the backend is open and usable.
     */
    boolean isReady();

    /**
     * Result of a full read.
     */
    final class LoadResult {

        private final Map<ProtectionKey, Protection> protections;
        private final Map<String, Set<String>> trust;
        private final int skipped;

        public LoadResult(Map<ProtectionKey, Protection> protections,
                          Map<String, Set<String>> trust,
                          int skipped) {
            this.protections = protections;
            this.trust = trust;
            this.skipped = skipped;
        }

        public Map<ProtectionKey, Protection> getProtections() {
            return protections;
        }

        public Map<String, Set<String>> getTrust() {
            return trust;
        }

        /**
         * Number of records that could not be read, for example because they were malformed.
         * Reported in the startup log so admins can spot a damaged data file.
         */
        public int getSkipped() {
            return skipped;
        }
    }
}
