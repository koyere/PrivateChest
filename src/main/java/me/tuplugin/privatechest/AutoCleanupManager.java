package me.tuplugin.privatechest;

import me.tuplugin.privatechest.storage.Protection;
import me.tuplugin.privatechest.storage.ProtectionKey;
import me.tuplugin.privatechest.storage.ProtectionStore;
import me.tuplugin.privatechest.util.SchedulerUtils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Removes protection records whose container no longer exists.
 *
 * <p>Rewritten in 3.0 to respect how the server actually threads world access. The old version read
 * blocks from an asynchronous timer, which is unsafe on Paper and throws outright on Folia, and it
 * could delete valid protections based on an inconsistent read.</p>
 *
 * <p>The pass now runs in two stages. Selecting candidates only reads the in-memory registry, so it
 * happens off the server threads. Verifying a candidate touches the world, so it is grouped by chunk
 * and dispatched to whichever thread owns that chunk: the region thread on Folia, the main thread in
 * bounded batches everywhere else. Records in chunks that are not loaded are always left alone, so an
 * unloaded or lazily mounted world can never be mistaken for deleted containers.</p>
 *
 * @since 2.1, reworked in 3.0
 */
public class AutoCleanupManager {

    private static final long DEFAULT_STARTUP_DELAY_SECONDS = 10L;
    private static final long DEFAULT_INTERVAL_MINUTES = 30L;
    private static final int DEFAULT_MAX_CHUNKS_PER_PASS = 200;
    private static final int DEFAULT_CHUNKS_PER_TICK = 20;

    private final PrivateChest plugin;
    private final ProtectionStore store;
    private final TrustManager trustManager;

    private final AtomicBoolean cleanupRunning = new AtomicBoolean(false);

    private SchedulerUtils.Task periodicCleanupTask;

    public AutoCleanupManager(PrivateChest plugin, ProtectionStore store, TrustManager trustManager) {
        this.plugin = plugin;
        this.store = store;
        this.trustManager = trustManager;
    }

    /**
     * Schedules the startup pass and, if enabled, the periodic pass.
     */
    public void initialize() {
        if (isStartupCleanupEnabled()) {
            long delayTicks = Math.max(1L, getStartupDelaySeconds()) * 20L;
            SchedulerUtils.runGlobalLater(plugin, () -> runCleanup(CleanupType.STARTUP, null), delayTicks);
        }

        long intervalMinutes = getIntervalMinutes();
        if (isPeriodicCleanupEnabled() && intervalMinutes > 0L) {
            long intervalTicks = intervalMinutes * 60L * 20L;

            // Selecting candidates only reads memory, so the timer itself can run off-thread.
            periodicCleanupTask = SchedulerUtils.runTimerAsync(plugin,
                    () -> runCleanup(CleanupType.PERIODIC, null),
                    intervalTicks, intervalTicks);

            plugin.getLogger().info("[Cleanup] Orphaned data cleanup will run every "
                    + intervalMinutes + " minutes.");
        }
    }

    public void shutdown() {
        if (periodicCleanupTask != null) {
            periodicCleanupTask.cancel();
            periodicCleanupTask = null;
        }
    }

    /**
     * Runs a cleanup pass.
     *
     * <p>Returns immediately. Verification happens on the threads that own the chunks involved, and
     * the callback is invoked once every chunk has been examined.</p>
     *
     * @param type       what triggered this pass
     * @param onComplete notified with the results, may be null
     */
    public void runCleanup(CleanupType type, Consumer<CleanupReport> onComplete) {
        if (!cleanupRunning.compareAndSet(false, true)) {
            plugin.getLogger().fine("[Cleanup] A cleanup pass is already running; skipping this one.");
            if (onComplete != null) {
                onComplete.accept(new CleanupReport(0, 0, 0, 0, true));
            }
            return;
        }

        long startTime = System.currentTimeMillis();

        try {
            // Stage one: memory only.
            int removedTrust = removeOrphanedTrustRelations();
            List<ChunkBatch> batches = groupCandidatesByChunk(type);

            if (batches.isEmpty()) {
                finish(type, startTime, new AtomicInteger(0), new AtomicInteger(0),
                        removedTrust, 0, onComplete);
                return;
            }

            // Stage two: world access, on the owning thread.
            AtomicInteger removedContainers = new AtomicInteger(0);
            AtomicInteger skippedUnloaded = new AtomicInteger(0);
            Runnable onDone = () -> finish(type, startTime, removedContainers, skippedUnloaded,
                    removedTrust, batches.size(), onComplete);

            if (SchedulerUtils.isFolia()) {
                dispatchPerRegion(batches, removedContainers, skippedUnloaded, onDone);
            } else {
                dispatchInTickBatches(batches, removedContainers, skippedUnloaded, onDone);
            }
        } catch (Throwable t) {
            // Release the guard, or no cleanup would ever run again this session.
            cleanupRunning.set(false);
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "[Cleanup] Cleanup pass failed to start.", t);
            if (onComplete != null) {
                onComplete.accept(new CleanupReport(0, 0, 0,
                        System.currentTimeMillis() - startTime, false));
            }
        }
    }

    /**
     * Groups every recorded position by chunk, reading nothing but the registry.
     */
    private List<ChunkBatch> groupCandidatesByChunk(CleanupType type) {
        Map<ChunkId, List<ProtectionKey>> grouped = new HashMap<>();

        for (ProtectionKey key : store.keys()) {
            ChunkId id = new ChunkId(key.getWorld(), key.getX() >> 4, key.getZ() >> 4);
            List<ProtectionKey> keys = grouped.get(id);
            if (keys == null) {
                keys = new ArrayList<>(2);
                grouped.put(id, keys);
            }
            keys.add(key);
        }

        int limit = type == CleanupType.PERIODIC ? getMaxChunksPerPass() : Integer.MAX_VALUE;

        List<ChunkBatch> batches = new ArrayList<>(Math.min(grouped.size(), limit));
        for (Map.Entry<ChunkId, List<ProtectionKey>> entry : grouped.entrySet()) {
            if (batches.size() >= limit) {
                break;
            }
            // Worlds that are not mounted have nothing to verify against; leave their records alone.
            World world = Bukkit.getWorld(entry.getKey().world);
            if (world == null) {
                continue;
            }
            batches.add(new ChunkBatch(world, entry.getKey().chunkX, entry.getKey().chunkZ, entry.getValue()));
        }
        return batches;
    }

    /**
     * Folia: hand each chunk to the region that owns it.
     */
    private void dispatchPerRegion(List<ChunkBatch> batches, AtomicInteger removed,
                                   AtomicInteger skipped, Runnable onDone) {
        AtomicInteger pending = new AtomicInteger(batches.size());

        for (ChunkBatch batch : batches) {
            SchedulerUtils.runAtChunk(plugin, batch.world, batch.chunkX, batch.chunkZ, () -> {
                try {
                    verifyChunk(batch, removed, skipped);
                } finally {
                    if (pending.decrementAndGet() == 0) {
                        onDone.run();
                    }
                }
            });
        }
    }

    /**
     * Single-threaded servers: process a bounded number of chunks per tick on the main thread.
     */
    private void dispatchInTickBatches(List<ChunkBatch> batches, AtomicInteger removed,
                                       AtomicInteger skipped, Runnable onDone) {
        processNextBatchWindow(batches, 0, removed, skipped, onDone);
    }

    private void processNextBatchWindow(List<ChunkBatch> batches, int fromIndex, AtomicInteger removed,
                                        AtomicInteger skipped, Runnable onDone) {
        SchedulerUtils.runGlobal(plugin, () -> {
            int end = Math.min(fromIndex + getChunksPerTick(), batches.size());
            try {
                for (int i = fromIndex; i < end; i++) {
                    verifyChunk(batches.get(i), removed, skipped);
                }
            } catch (Throwable t) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        "[Cleanup] Error while verifying containers; stopping this pass.", t);
                onDone.run();
                return;
            }

            if (end < batches.size()) {
                // Give the tick back before continuing.
                SchedulerUtils.runGlobalLater(plugin,
                        () -> processNextBatchWindow(batches, end, removed, skipped, onDone), 1L);
            } else {
                onDone.run();
            }
        });
    }

    /**
     * Verifies one chunk's records. Always runs on the thread that owns the chunk.
     */
    private void verifyChunk(ChunkBatch batch, AtomicInteger removed, AtomicInteger skipped) {
        // Scheduling for a region does not load its chunks, so this has to be checked here.
        if (!batch.world.isChunkLoaded(batch.chunkX, batch.chunkZ)) {
            skipped.addAndGet(batch.keys.size());
            return;
        }

        for (ProtectionKey key : batch.keys) {
            Protection protection = store.get(key);
            if (protection == null) {
                // Removed by a player while this pass was in flight.
                continue;
            }

            Block block = batch.world.getBlockAt(key.getX(), key.getY(), key.getZ());

            // Checks whether the block is still a container at all, not whether it is currently
            // allowed to be locked. Disabling a container type in the configuration must not make this
            // pass delete every existing protection on that type.
            if (!plugin.getContainerRegistry().isKnownContainer(block.getType())) {
                if (store.remove(key) != null) {
                    removed.incrementAndGet();
                    plugin.getLogger().fine("[Cleanup] Removed orphaned record at " + key.serialize() + ".");
                }
                continue;
            }

            // Records imported from older versions have no container type. Filling it in here means
            // limit checks and statistics never have to read the world again.
            if (protection.getContainerType() == null) {
                store.replace(key, protection.withContainerType(block.getType().name()));
            }
        }
    }

    private void finish(CleanupType type, long startTime, AtomicInteger removedContainers,
                        AtomicInteger skipped, int removedTrust, int chunksExamined,
                        Consumer<CleanupReport> onComplete) {
        long duration = System.currentTimeMillis() - startTime;
        CleanupReport report = new CleanupReport(removedContainers.get(), removedTrust,
                skipped.get(), duration, false);

        cleanupRunning.set(false);

        if (report.getTotalCleaned() > 0) {
            plugin.getLogger().info("[Cleanup] Removed " + report.getCleanedContainers()
                    + " orphaned container record(s) and " + report.getCleanedTrustRelations()
                    + " unused trust relation(s) across " + chunksExamined + " chunk(s) in "
                    + duration + "ms.");
        } else {
            plugin.getLogger().fine("[Cleanup] Nothing to clean (" + chunksExamined + " chunk(s), "
                    + duration + "ms).");
        }

        if (onComplete != null) {
            onComplete.accept(report);
        }
    }

    /**
     * Drops trust lists belonging to players who own no protected container.
     *
     * <p>Off by default, and this is a deliberate reversal. 2.x did this on every pass, which quietly
     * destroyed real data: a player who ran {@code /trust} before locking their first container had the
     * whole list wiped within half an hour, and anyone who temporarily broke all of their containers
     * lost their trusted players too. Trust relations are a few bytes each, so keeping them costs
     * nothing worth measuring.</p>
     */
    private int removeOrphanedTrustRelations() {
        if (!plugin.getConfig().getBoolean("auto-cleanup.remove-unused-trust", false)) {
            return 0;
        }

        Set<String> ownersWithContainers = new HashSet<>();
        for (Protection protection : store.view().values()) {
            ownersWithContainers.add(protection.getOwner().toString());
        }

        int removed = 0;
        for (String owner : trustManager.owners()) {
            if (!ownersWithContainers.contains(owner)) {
                removed += trustManager.removeOwner(owner);
            }
        }
        return removed;
    }

    // --- Configuration ---

    private boolean isPeriodicCleanupEnabled() {
        return plugin.getConfig().getBoolean("auto-cleanup.periodic-enabled", true);
    }

    private boolean isStartupCleanupEnabled() {
        return plugin.getConfig().getBoolean("auto-cleanup.startup-enabled", true);
    }

    private long getStartupDelaySeconds() {
        return plugin.getConfig().getLong("auto-cleanup.startup-delay-seconds", DEFAULT_STARTUP_DELAY_SECONDS);
    }

    private long getIntervalMinutes() {
        return plugin.getConfig().getLong("auto-cleanup.interval-minutes", DEFAULT_INTERVAL_MINUTES);
    }

    private int getMaxChunksPerPass() {
        int configured = plugin.getConfig().getInt("auto-cleanup.max-chunks-per-pass",
                DEFAULT_MAX_CHUNKS_PER_PASS);
        return configured <= 0 ? Integer.MAX_VALUE : configured;
    }

    private int getChunksPerTick() {
        return Math.max(1, plugin.getConfig().getInt("auto-cleanup.chunks-per-tick",
                DEFAULT_CHUNKS_PER_TICK));
    }

    public enum CleanupType {
        STARTUP,
        PERIODIC,
        MANUAL
    }

    /**
     * Identifies a chunk for grouping purposes.
     */
    private static final class ChunkId {
        private final String world;
        private final int chunkX;
        private final int chunkZ;
        private final int hash;

        ChunkId(String world, int chunkX, int chunkZ) {
            this.world = world;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            int h = world.hashCode();
            h = 31 * h + chunkX;
            this.hash = 31 * h + chunkZ;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ChunkId)) {
                return false;
            }
            ChunkId that = (ChunkId) other;
            return chunkX == that.chunkX && chunkZ == that.chunkZ && world.equals(that.world);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    /**
     * The records to verify inside one chunk, with the world already resolved.
     */
    private static final class ChunkBatch {
        private final World world;
        private final int chunkX;
        private final int chunkZ;
        private final List<ProtectionKey> keys;

        ChunkBatch(World world, int chunkX, int chunkZ, List<ProtectionKey> keys) {
            this.world = world;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.keys = keys;
        }
    }

    /**
     * What a cleanup pass did.
     */
    public static class CleanupReport {
        private final int cleanedContainers;
        private final int cleanedTrustRelations;
        private final int skippedUnloaded;
        private final long durationMs;
        private final boolean alreadyRunning;

        public CleanupReport(int cleanedContainers, int cleanedTrustRelations, int skippedUnloaded,
                             long durationMs, boolean alreadyRunning) {
            this.cleanedContainers = cleanedContainers;
            this.cleanedTrustRelations = cleanedTrustRelations;
            this.skippedUnloaded = skippedUnloaded;
            this.durationMs = durationMs;
            this.alreadyRunning = alreadyRunning;
        }

        public int getCleanedContainers() {
            return cleanedContainers;
        }

        public int getCleanedTrustRelations() {
            return cleanedTrustRelations;
        }

        /**
         * Records that were left alone because their chunk was not loaded.
         */
        public int getSkippedUnloaded() {
            return skippedUnloaded;
        }

        public long getDurationMs() {
            return durationMs;
        }

        /**
         * True when this pass did nothing because another one was already in progress.
         */
        public boolean wasAlreadyRunning() {
            return alreadyRunning;
        }

        public int getTotalCleaned() {
            return cleanedContainers + cleanedTrustRelations;
        }

        @Override
        public String toString() {
            return "CleanupReport{containers=" + cleanedContainers
                    + ", trustRelations=" + cleanedTrustRelations
                    + ", skipped=" + skippedUnloaded
                    + ", duration=" + durationMs + "ms}";
        }
    }
}
