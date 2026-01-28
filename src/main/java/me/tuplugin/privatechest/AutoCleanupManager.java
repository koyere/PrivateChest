package me.tuplugin.privatechest;

import me.tuplugin.privatechest.util.SchedulerUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitTask;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages automatic cleanup of orphaned container data.
 * This includes containers that no longer exist in the world or have been
 * replaced with non-lockable blocks. Provides both startup cleanup and
 * periodic maintenance to ensure data integrity.
 * 
 * Compatible with Folia through SchedulerUtils.
 * 
 * @since 2.1
 * @author PrivateChest Team
 */
public class AutoCleanupManager {

    private final PrivateChest plugin;
    private final ChestLocker chestLocker;
    private final DataManager dataManager;
    private final TrustManager trustManager;
    private BukkitTask periodicCleanupTask;

    // Configuration constants
    private static final long STARTUP_CLEANUP_DELAY_TICKS = 200L; // 10 seconds after startup
    private static final long PERIODIC_CLEANUP_INTERVAL_MINUTES = 30L; // Every 30 minutes
    private static final int MAX_CLEANUP_PER_CYCLE = 50; // Limit per cleanup cycle to prevent lag

    /**
     * Constructs a new AutoCleanupManager.
     * 
     * @param plugin The main plugin instance
     */
    public AutoCleanupManager(PrivateChest plugin) {
        this.plugin = plugin;
        this.chestLocker = plugin.getChestLocker();
        this.dataManager = plugin.getDataManager();
        this.trustManager = plugin.getTrustManager();
    }

    /**
     * Initializes the auto-cleanup system.
     * Schedules both startup cleanup and periodic maintenance.
     * Uses SchedulerUtils for Folia compatibility.
     */
    public void initialize() {
        // Schedule startup cleanup after server is fully loaded
        SchedulerUtils.runTaskLater(plugin, () -> {
            performCleanup(CleanupType.STARTUP);
        }, STARTUP_CLEANUP_DELAY_TICKS);

        // Schedule periodic cleanup if enabled in config
        if (isPeriodicCleanupEnabled()) {
            long intervalTicks = PERIODIC_CLEANUP_INTERVAL_MINUTES * 60L * 20L; // Convert to ticks
            
            periodicCleanupTask = SchedulerUtils.runTaskTimerAsync(plugin, () -> {
                performCleanup(CleanupType.PERIODIC);
            }, intervalTicks, intervalTicks);

            plugin.getLogger().info("[AutoCleanup] Periodic cleanup scheduled every " + 
                    PERIODIC_CLEANUP_INTERVAL_MINUTES + " minutes");
        }
    }

    /**
     * Shuts down the auto-cleanup system.
     * Cancels any running cleanup tasks.
     */
    public void shutdown() {
        if (periodicCleanupTask != null && !periodicCleanupTask.isCancelled()) {
            periodicCleanupTask.cancel();
            periodicCleanupTask = null;
        }
    }

    /**
     * Performs a comprehensive cleanup of orphaned data.
     * This method is thread-safe and can be called from async contexts.
     * 
     * @param cleanupType The type of cleanup being performed
     */
    public void performCleanup(CleanupType cleanupType) {
        long startTime = System.currentTimeMillis();
        AtomicInteger cleanedContainers = new AtomicInteger(0);
        AtomicInteger cleanedTrustRelations = new AtomicInteger(0);

        plugin.getLogger().info("[AutoCleanup] Starting " + cleanupType.name().toLowerCase() + 
                " cleanup of orphaned data...");

        // Cleanup orphaned containers
        cleanupOrphanedContainers(cleanedContainers, cleanupType);

        // Cleanup orphaned trust relations
        cleanupOrphanedTrustRelations(cleanedTrustRelations);

        // Save data if anything was cleaned
        if (cleanedContainers.get() > 0 || cleanedTrustRelations.get() > 0) {
            // Run save operation on main thread (Folia compatible)
            SchedulerUtils.runTask(plugin, () -> {
                dataManager.saveData();
            });
        }

        long duration = System.currentTimeMillis() - startTime;
        
        // Log cleanup results
        if (cleanedContainers.get() > 0 || cleanedTrustRelations.get() > 0) {
            plugin.getLogger().info(String.format(
                    "[AutoCleanup] %s cleanup completed in %dms: %d containers, %d trust relations cleaned",
                    cleanupType.name().toLowerCase(),
                    duration,
                    cleanedContainers.get(),
                    cleanedTrustRelations.get()
            ));
        } else {
            plugin.getLogger().fine(String.format(
                    "[AutoCleanup] %s cleanup completed in %dms: No orphaned data found",
                    cleanupType.name().toLowerCase(),
                    duration
            ));
        }
    }

    /**
     * Cleans up containers that no longer exist in the world.
     * This method respects the cleanup limits to prevent server lag.
     * 
     * @param cleanedCounter Counter to track cleaned containers
     * @param cleanupType The type of cleanup being performed
     */
    private void cleanupOrphanedContainers(AtomicInteger cleanedCounter, CleanupType cleanupType) {
        Map<Location, String> owners = chestLocker.getChestOwners();
        Map<Location, String> passwords = chestLocker.getChestPasswords();

        int processed = 0;
        int maxToProcess = cleanupType == CleanupType.STARTUP ? Integer.MAX_VALUE : MAX_CLEANUP_PER_CYCLE;

        Iterator<Map.Entry<Location, String>> it = owners.entrySet().iterator();
        while (it.hasNext() && processed < maxToProcess) {
            Map.Entry<Location, String> entry = it.next();
            Location loc = entry.getKey();
            processed++;

            if (isOrphanedContainer(loc)) {
                // Remove from both maps
                passwords.remove(loc);
                it.remove();
                cleanedCounter.incrementAndGet();

                plugin.getLogger().fine(String.format(
                        "[AutoCleanup] Removed orphaned container at %s:%d,%d,%d",
                        loc.getWorld() != null ? loc.getWorld().getName() : "null",
                        loc.getBlockX(),
                        loc.getBlockY(),
                        loc.getBlockZ()
                ));
            }
        }
    }

    /**
     * Cleans up trust relationships for players who no longer own any containers.
     * This helps prevent the trust map from growing indefinitely.
     * 
     * @param cleanedCounter Counter to track cleaned relationships
     */
    private void cleanupOrphanedTrustRelations(AtomicInteger cleanedCounter) {
        Map<String, String> owners = chestLocker.getChestOwners().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        entry -> entry.getValue(), // UUID as key
                        entry -> entry.getValue(), // UUID as value
                        (existing, replacement) -> existing // Keep existing on conflict
                ));

        // Get all trust relations
        Map<String, java.util.Set<String>> allTrustRelations = trustManager.getAllTrustRelations();
        Iterator<Map.Entry<String, java.util.Set<String>>> it = allTrustRelations.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<String, java.util.Set<String>> entry = it.next();
            String ownerUUID = entry.getKey();

            // If this owner no longer has any containers, remove their trust relations
            if (!owners.containsKey(ownerUUID)) {
                it.remove();
                cleanedCounter.addAndGet(entry.getValue().size());

                plugin.getLogger().fine(String.format(
                        "[AutoCleanup] Removed trust relations for player UUID %s (no longer owns containers)",
                        ownerUUID
                ));
            }
        }
    }

    /**
     * Checks if a container location represents orphaned data.
     * A container is considered orphaned if:
     * - The world no longer exists
     * - The block is no longer a lockable container type
     * 
     * @param loc The location to check
     * @return true if the container is orphaned
     */
    private boolean isOrphanedContainer(Location loc) {
        // Check if world exists
        if (loc.getWorld() == null) {
            return true;
        }

        // Check if chunk is loaded (avoid loading chunks during cleanup)
        if (!loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
            // Don't consider unloaded chunks as orphaned - they might be valid
            return false;
        }

        // Check if block is still a lockable container
        Block block = loc.getBlock();
        return !isLockableContainer(block);
    }

    /**
     * Checks if a block is a lockable container type.
     * This mirrors the logic from HopperProtectionListener for consistency.
     * 
     * @param block The block to check
     * @return true if the block is a lockable container
     */
    private boolean isLockableContainer(Block block) {
        if (block == null) {
            return false;
        }

        Material type = block.getType();
        
        // Standard containers
        if (type == Material.CHEST || type == Material.TRAPPED_CHEST || type == Material.BARREL) {
            return true;
        }
        
        // All shulker box variants
        String typeName = type.name();
        return typeName.contains("SHULKER_BOX");
    }

    /**
     * Checks if periodic cleanup is enabled in the configuration.
     * 
     * @return true if periodic cleanup should run
     */
    private boolean isPeriodicCleanupEnabled() {
        return plugin.getConfig().getBoolean("auto-cleanup.periodic-enabled", true);
    }

    /**
     * Forces an immediate cleanup operation.
     * This method can be called from commands or other components.
     * 
     * @return A cleanup report with statistics
     */
    public CleanupReport performImmediateCleanup() {
        long startTime = System.currentTimeMillis();
        AtomicInteger cleanedContainers = new AtomicInteger(0);
        AtomicInteger cleanedTrustRelations = new AtomicInteger(0);

        cleanupOrphanedContainers(cleanedContainers, CleanupType.MANUAL);
        cleanupOrphanedTrustRelations(cleanedTrustRelations);

        if (cleanedContainers.get() > 0 || cleanedTrustRelations.get() > 0) {
            dataManager.saveData();
        }

        long duration = System.currentTimeMillis() - startTime;
        
        return new CleanupReport(
                cleanedContainers.get(),
                cleanedTrustRelations.get(),
                duration
        );
    }

    /**
     * Enum representing different types of cleanup operations.
     */
    public enum CleanupType {
        STARTUP,  // Cleanup performed after plugin startup
        PERIODIC, // Scheduled periodic cleanup
        MANUAL    // Manual cleanup triggered by command or API
    }

    /**
     * Data class containing cleanup operation results.
     */
    public static class CleanupReport {
        private final int cleanedContainers;
        private final int cleanedTrustRelations;
        private final long durationMs;

        public CleanupReport(int cleanedContainers, int cleanedTrustRelations, long durationMs) {
            this.cleanedContainers = cleanedContainers;
            this.cleanedTrustRelations = cleanedTrustRelations;
            this.durationMs = durationMs;
        }

        public int getCleanedContainers() { return cleanedContainers; }
        public int getCleanedTrustRelations() { return cleanedTrustRelations; }
        public long getDurationMs() { return durationMs; }
        public int getTotalCleaned() { return cleanedContainers + cleanedTrustRelations; }
        
        @Override
        public String toString() {
            return String.format(
                    "CleanupReport{containers=%d, trustRelations=%d, duration=%dms}",
                    cleanedContainers, cleanedTrustRelations, durationMs
            );
        }
    }
}