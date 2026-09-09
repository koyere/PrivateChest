package me.tuplugin.privatechest;

import me.tuplugin.privatechest.storage.DataStorage;
import me.tuplugin.privatechest.storage.PersistenceQueue;
import me.tuplugin.privatechest.storage.Protection;
import me.tuplugin.privatechest.storage.ProtectionKey;
import me.tuplugin.privatechest.storage.ProtectionStore;
import me.tuplugin.privatechest.storage.SqliteStorage;
import me.tuplugin.privatechest.storage.YamlStorage;
import org.bukkit.Bukkit;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Owns the storage backend and the persistence worker.
 *
 * <p>The public surface changed in 3.0: there is no longer a {@code saveData()} method for callers to
 * invoke. Every change to a protection or a trust relation is registered automatically by the
 * registry and written by the background worker. What remains here is lifecycle (open, load, close),
 * an explicit {@link #flushNow()} for shutdown and admin commands, and storage migration.</p>
 *
 * @since 2.0, rewritten in 3.0
 */
public class DataManager {

    private static final long DEFAULT_SAVE_INTERVAL_SECONDS = 5L;

    private final PrivateChest plugin;
    private final ProtectionStore store;
    private final TrustManager trustManager;

    private DataStorage storage;
    private PersistenceQueue queue;

    public DataManager(PrivateChest plugin, ProtectionStore store, TrustManager trustManager) {
        this.plugin = plugin;
        this.store = store;
        this.trustManager = trustManager;
    }

    /**
     * Opens the configured backend, loads all data into memory and starts the persistence worker.
     *
     * @return true if the plugin has usable storage
     */
    public boolean initialize() {
        storage = openConfiguredStorage();
        if (storage == null) {
            return false;
        }

        loadData();

        long interval = resolveSaveInterval();
        queue = new PersistenceQueue(plugin.getLogger(), storage, store, trustManager, interval);
        store.setPersistence(queue);
        trustManager.setPersistence(queue);
        queue.start();

        return true;
    }

    /**
     * Opens the backend named in config.yml, falling back to YAML if it cannot be opened.
     */
    private DataStorage openConfiguredStorage() {
        String configured = plugin.getConfig().getString("storage-type", "yaml");
        String type = configured == null ? "yaml" : configured.trim().toLowerCase();

        if ("sqlite".equals(type)) {
            DataStorage sqlite = new SqliteStorage(plugin);
            if (sqlite.initialize()) {
                return sqlite;
            }
            plugin.getLogger().severe("[Storage] SQLite could not be opened. Falling back to YAML so the "
                    + "server keeps its protections. Fix the error above and restart to use SQLite.");
        } else if (!"yaml".equals(type)) {
            plugin.getLogger().warning("[Storage] Unknown storage-type '" + configured + "'. Using YAML.");
        }

        DataStorage yaml = new YamlStorage(plugin);
        if (yaml.initialize()) {
            return yaml;
        }

        plugin.getLogger().severe("[Storage] No storage backend could be opened. Container protection "
                + "will not persist. Check file permissions on the plugin folder.");
        return null;
    }

    private long resolveSaveInterval() {
        long configured = plugin.getConfig().getLong("save-interval-seconds", DEFAULT_SAVE_INTERVAL_SECONDS);
        if (configured < 1L) {
            plugin.getLogger().warning("[Storage] save-interval-seconds must be at least 1. Using "
                    + DEFAULT_SAVE_INTERVAL_SECONDS + ".");
            return DEFAULT_SAVE_INTERVAL_SECONDS;
        }
        if (configured > 300L) {
            plugin.getLogger().warning("[Storage] save-interval-seconds is very high (" + configured
                    + "s). Recent changes could be lost on a crash.");
        }
        return configured;
    }

    /**
     * Reads everything from the backend into memory.
     */
    private void loadData() {
        DataStorage.LoadResult result = storage.loadAll();
        if (result == null) {
            plugin.getLogger().severe("[Storage] Could not read existing data. Starting with no protections "
                    + "loaded. Nothing has been deleted; fix the error above and restart.");
            return;
        }

        store.loadAll(result.getProtections());
        trustManager.loadAll(result.getTrust());

        int total = store.size();
        int unmounted = countUnmountedWorlds();

        plugin.getLogger().info("[Storage] Loaded " + total + " protected container(s) and "
                + trustManager.getTotalTrustCount() + " trust relation(s) from " + storage.getStorageType() + ".");

        if (unmounted > 0) {
            plugin.getLogger().info("[Storage] " + unmounted + " protection(s) belong to worlds that are not "
                    + "loaded right now. They are kept and will apply if those worlds are loaded later.");
        }
        if (result.getSkipped() > 0) {
            plugin.getLogger().warning("[Storage] " + result.getSkipped() + " record(s) could not be read and "
                    + "were skipped. See the warnings above.");
        }
    }

    private int countUnmountedWorlds() {
        Set<String> missing = new HashSet<>();
        int count = 0;
        for (ProtectionKey key : store.view().keySet()) {
            if (Bukkit.getWorld(key.getWorld()) == null) {
                missing.add(key.getWorld());
                count++;
            }
        }
        if (!missing.isEmpty()) {
            plugin.getLogger().info("[Storage] Worlds not currently loaded: " + String.join(", ", missing));
        }
        return count;
    }

    /**
     * Writes every pending change and waits for it to hit disk.
     * Used on shutdown, before migrations, and by admin commands.
     *
     * @return true if everything was written
     */
    public boolean flushNow() {
        return queue == null || queue.flushNow();
    }

    /**
     * Moves all data to a different backend and switches to it.
     *
     * <p>The current dataset already lives in memory, so the migration writes it to the target
     * backend rather than reading the source again. The previous backend is left untouched on disk as
     * a fallback.</p>
     *
     * @param targetType "yaml" or "sqlite"
     * @return the outcome, for the command to report
     */
    public MigrationResult migrate(String targetType) {
        if (targetType == null) {
            return MigrationResult.UNKNOWN_TYPE;
        }

        String target = targetType.trim().toLowerCase();
        if (!"yaml".equals(target) && !"sqlite".equals(target)) {
            return MigrationResult.UNKNOWN_TYPE;
        }
        if (storage != null && storage.getStorageType().equalsIgnoreCase(target)) {
            return MigrationResult.ALREADY_ACTIVE;
        }

        // Make sure nothing is left in the queue for the backend we are about to abandon.
        flushNow();

        DataStorage destination = "sqlite".equals(target) ? new SqliteStorage(plugin) : new YamlStorage(plugin);
        if (!destination.initialize()) {
            plugin.getLogger().severe("[Storage] Migration aborted: could not open the " + target + " backend.");
            return MigrationResult.FAILED;
        }

        Map<ProtectionKey, Protection> protections = store.snapshot();
        Map<String, Set<String>> trust = trustManager.snapshotAllTrust();

        if (!destination.writeAll(protections, trust)) {
            plugin.getLogger().severe("[Storage] Migration aborted: could not write to the " + target + " backend. "
                    + "The current backend is untouched.");
            destination.close();
            return MigrationResult.FAILED;
        }
        destination.flush();

        DataStorage previous = storage;
        String previousType = previous == null ? "unknown" : previous.getStorageType();

        // Point the worker at the new backend before releasing the old one.
        if (queue != null) {
            queue.shutdown();
        }
        storage = destination;
        queue = new PersistenceQueue(plugin.getLogger(), storage, store, trustManager, resolveSaveInterval());
        store.setPersistence(queue);
        trustManager.setPersistence(queue);
        queue.start();

        if (previous != null) {
            previous.close();
        }

        plugin.getConfig().set("storage-type", target);
        FileUtil.setTopLevelValue(plugin, "config.yml", "storage-type", target);

        plugin.getLogger().info("[Storage] Migrated " + protections.size() + " container(s) and "
                + trust.size() + " trust list(s) from " + previousType + " to " + storage.getStorageType()
                + ". The old data files were left in place as a backup.");

        return MigrationResult.SUCCESS;
    }

    /**
     * The active backend name, for logs and admin messages.
     */
    public String getStorageType() {
        return storage != null ? storage.getStorageType() : "none";
    }

    /**
     * Whether protections are currently being persisted.
     */
    public boolean isStorageReady() {
        return storage != null && storage.isReady();
    }

    /**
     * Number of changes still waiting to be written.
     */
    public boolean hasPendingChanges() {
        return queue != null && queue.hasPendingChanges();
    }

    /**
     * Flushes and closes everything. Called on plugin disable.
     */
    public void close() {
        if (queue != null) {
            queue.shutdown();
            queue = null;
        }
        if (store != null) {
            store.setPersistence(null);
        }
        if (trustManager != null) {
            trustManager.setPersistence(null);
        }
        if (storage != null) {
            storage.close();
            storage = null;
        }
    }

    /**
     * Outcome of a storage migration.
     */
    public enum MigrationResult {
        SUCCESS,
        ALREADY_ACTIVE,
        UNKNOWN_TYPE,
        FAILED
    }
}
