package me.tuplugin.privatechest.storage;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Coalescing, off-thread writer for protection and trust data.
 *
 * <p>This replaces the old behaviour where every lock, unlock, break, trust and rename call rewrote
 * the complete dataset synchronously on the main thread. Mutations now only flag which records
 * changed; a dedicated worker thread applies the accumulated changes on a fixed interval. Ten
 * players locking chests in the same second produce one write instead of ten full rewrites, and none
 * of it happens on a server thread.</p>
 *
 * <p>The worker is a plain daemon thread rather than a Bukkit task on purpose: it works identically
 * on Bukkit, Paper and Folia, and it keeps running during shutdown long enough to flush.</p>
 *
 * <p>Dirty records are resolved against the live registry at flush time rather than captured when
 * marked. A record that is still present becomes an insert or update, a record that is gone becomes
 * a delete. That makes rapid lock/unlock sequences on the same position collapse into whatever the
 * final state actually is, and removes any chance of writing stale values.</p>
 *
 * @since 3.0
 */
public final class PersistenceQueue {

    private static final long SHUTDOWN_TIMEOUT_SECONDS = 15L;
    private static final long FLUSH_WAIT_TIMEOUT_SECONDS = 30L;

    private final Logger logger;
    private final DataStorage storage;
    private final ProtectionStore store;
    private final TrustSource trustSource;
    private final long intervalSeconds;

    private final Set<ProtectionKey> dirtyProtections = ConcurrentHashMap.newKeySet();
    private final Set<String> dirtyTrust = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean fullRewritePending = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(false);

    private ScheduledExecutorService worker;

    public PersistenceQueue(Logger logger, DataStorage storage, ProtectionStore store,
                            TrustSource trustSource, long intervalSeconds) {
        this.logger = logger;
        this.storage = storage;
        this.store = store;
        this.trustSource = trustSource;
        this.intervalSeconds = Math.max(1L, intervalSeconds);
    }

    /**
     * Starts the background writer.
     */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        ThreadFactory factory = task -> {
            Thread thread = new Thread(task, "PrivateChest-Persistence");
            thread.setDaemon(true);
            return thread;
        };
        worker = Executors.newSingleThreadScheduledExecutor(factory);
        worker.scheduleWithFixedDelay(this::safeFlush, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);

        logger.info("[Storage] Persistence worker started (writing pending changes every "
                + intervalSeconds + "s).");
    }

    // --- Marking ---

    /**
     * Flags a protected position as changed. Called automatically by {@link ProtectionStore}.
     */
    public void markProtectionDirty(ProtectionKey key) {
        if (key != null) {
            dirtyProtections.add(key);
        }
    }

    /**
     * Flags an owner's trust list as changed.
     */
    public void markTrustDirty(String ownerUuid) {
        if (ownerUuid != null) {
            dirtyTrust.add(ownerUuid);
        }
    }

    /**
     * Requests a full rewrite of the backend on the next flush. Used after bulk operations such as
     * a mass cleanup or a storage migration.
     */
    public void markAllDirty() {
        fullRewritePending.set(true);
    }

    /**
     * Whether there is anything waiting to be written.
     */
    public boolean hasPendingChanges() {
        return fullRewritePending.get() || !dirtyProtections.isEmpty() || !dirtyTrust.isEmpty();
    }

    // --- Flushing ---

    /**
     * Writes all pending changes and waits for completion.
     *
     * <p>Used on shutdown, before a storage migration, and by admin commands. Safe to call from a
     * server thread: it blocks, but only for as long as the queued writes take.</p>
     *
     * @return true if everything was written
     */
    public boolean flushNow() {
        if (worker == null || worker.isShutdown()) {
            // Worker is gone (or never started); write inline so nothing is lost.
            return flush();
        }

        try {
            Future<Boolean> result = worker.submit(this::flush);
            return result.get(FLUSH_WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[Storage] Timed out or failed while flushing pending changes.", e);
            return false;
        }
    }

    /**
     * Stops the worker after writing everything that is still pending.
     */
    public void shutdown() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        if (worker == null) {
            flush();
            return;
        }

        try {
            worker.submit(this::flush).get(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[Storage] Failed to write pending changes during shutdown. "
                    + "Some recent changes may be missing.", e);
        } finally {
            worker.shutdown();
            try {
                if (!worker.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    worker.shutdownNow();
                }
            } catch (InterruptedException e) {
                worker.shutdownNow();
                Thread.currentThread().interrupt();
            }
            worker = null;
        }
    }

    private void safeFlush() {
        try {
            flush();
        } catch (Throwable t) {
            // Never let an exception kill the scheduled worker.
            logger.log(Level.SEVERE, "[Storage] Unexpected error while writing data.", t);
        }
    }

    /**
     * Applies pending changes. Only ever runs on the worker thread, except for the inline fallback
     * paths in {@link #flushNow()} and {@link #shutdown()}.
     */
    private synchronized boolean flush() {
        if (!storage.isReady()) {
            return false;
        }

        boolean fullRewrite = fullRewritePending.getAndSet(false);
        Set<ProtectionKey> keys = drain(dirtyProtections);
        Set<String> owners = drain(dirtyTrust);

        if (!fullRewrite && keys.isEmpty() && owners.isEmpty()) {
            return true;
        }

        if (fullRewrite) {
            boolean ok = storage.writeAll(store.snapshot(), trustSource.snapshotAllTrust());
            if (!ok) {
                // Retry on the next cycle rather than losing the request.
                fullRewritePending.set(true);
                dirtyProtections.addAll(keys);
                dirtyTrust.addAll(owners);
                logger.severe("[Storage] Full data rewrite failed. Will retry.");
                return false;
            }
            storage.flush();
            return true;
        }

        Map<ProtectionKey, Protection> upserts = new HashMap<>();
        Set<ProtectionKey> deletes = new HashSet<>();
        for (ProtectionKey key : keys) {
            Protection current = store.get(key);
            if (current != null) {
                upserts.put(key, current);
            } else {
                deletes.add(key);
            }
        }

        Map<String, Set<String>> trustUpserts = new HashMap<>();
        Set<String> trustDeletes = new HashSet<>();
        for (String owner : owners) {
            Set<String> trusted = trustSource.getTrustedSnapshot(owner);
            if (trusted != null && !trusted.isEmpty()) {
                trustUpserts.put(owner, trusted);
            } else {
                trustDeletes.add(owner);
            }
        }

        boolean ok = storage.writeBatch(upserts, deletes, trustUpserts, trustDeletes);
        if (!ok) {
            // Put the work back so the next cycle retries it.
            dirtyProtections.addAll(keys);
            dirtyTrust.addAll(owners);
            logger.severe("[Storage] Failed to write " + keys.size() + " container change(s) and "
                    + owners.size() + " trust change(s). Will retry.");
            return false;
        }

        storage.flush();

        if (logger.isLoggable(Level.FINE)) {
            logger.fine("[Storage] Wrote " + upserts.size() + " update(s), " + deletes.size()
                    + " removal(s), " + trustUpserts.size() + " trust update(s), "
                    + trustDeletes.size() + " trust removal(s).");
        }
        return true;
    }

    private static <T> Set<T> drain(Set<T> source) {
        if (source.isEmpty()) {
            return new HashSet<>(0);
        }
        Set<T> drained = new HashSet<>(source.size() * 2);
        Iterator<T> it = source.iterator();
        while (it.hasNext()) {
            drained.add(it.next());
            it.remove();
        }
        return drained;
    }
}
