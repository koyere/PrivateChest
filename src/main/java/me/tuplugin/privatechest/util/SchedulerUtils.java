package me.tuplugin.privatechest.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Utility class for scheduling tasks with Folia compatibility.
 * Automatically detects if running on Folia and uses appropriate scheduler.
 * 
 * On standard Bukkit/Spigot/Paper: Uses BukkitScheduler
 * On Folia: Uses GlobalRegionScheduler for global tasks
 * 
 * @since 2.3
 */
public final class SchedulerUtils {

    private static Boolean isFolia = null;

    private SchedulerUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Checks if the server is running Folia.
     * Result is cached after first check.
     * 
     * @return true if running on Folia
     */
    public static boolean isFolia() {
        if (isFolia == null) {
            try {
                Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
                isFolia = true;
            } catch (ClassNotFoundException e) {
                isFolia = false;
            }
        }
        return isFolia;
    }

    /**
     * Runs a task on the main thread (or global region for Folia).
     * 
     * @param plugin The plugin instance
     * @param task The task to run
     */
    public static void runTask(Plugin plugin, Runnable task) {
        if (isFolia()) {
            try {
                // Use Folia's global region scheduler
                Object globalScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
                globalScheduler.getClass().getMethod("run", Plugin.class, java.util.function.Consumer.class)
                    .invoke(globalScheduler, plugin, (java.util.function.Consumer<Object>) (t) -> task.run());
            } catch (Exception e) {
                // Fallback to standard scheduler if reflection fails
                Bukkit.getScheduler().runTask(plugin, task);
            }
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * Runs a task later on the main thread (or global region for Folia).
     * 
     * @param plugin The plugin instance
     * @param task The task to run
     * @param delayTicks Delay in ticks before running
     */
    public static void runTaskLater(Plugin plugin, Runnable task, long delayTicks) {
        if (isFolia()) {
            try {
                Object globalScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
                globalScheduler.getClass().getMethod("runDelayed", Plugin.class, java.util.function.Consumer.class, long.class)
                    .invoke(globalScheduler, plugin, (java.util.function.Consumer<Object>) (t) -> task.run(), delayTicks);
            } catch (Exception e) {
                Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
            }
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    /**
     * Runs a task asynchronously.
     * Works the same on both Folia and standard servers.
     * 
     * @param plugin The plugin instance
     * @param task The task to run
     */
    public static void runTaskAsync(Plugin plugin, Runnable task) {
        if (isFolia()) {
            try {
                Object asyncScheduler = Bukkit.class.getMethod("getAsyncScheduler").invoke(null);
                asyncScheduler.getClass().getMethod("runNow", Plugin.class, java.util.function.Consumer.class)
                    .invoke(asyncScheduler, plugin, (java.util.function.Consumer<Object>) (t) -> task.run());
            } catch (Exception e) {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
            }
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    /**
     * Runs a repeating task asynchronously.
     * 
     * @param plugin The plugin instance
     * @param task The task to run
     * @param delayTicks Initial delay in ticks
     * @param periodTicks Period between executions in ticks
     * @return BukkitTask on standard servers, null on Folia (use cancelTask instead)
     */
    public static BukkitTask runTaskTimerAsync(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (isFolia()) {
            try {
                Object asyncScheduler = Bukkit.class.getMethod("getAsyncScheduler").invoke(null);
                // Convert ticks to TimeUnit for Folia
                long delayMs = delayTicks * 50; // 1 tick = 50ms
                long periodMs = periodTicks * 50;
                asyncScheduler.getClass().getMethod("runAtFixedRate", Plugin.class, java.util.function.Consumer.class, 
                    long.class, long.class, java.util.concurrent.TimeUnit.class)
                    .invoke(asyncScheduler, plugin, (java.util.function.Consumer<Object>) (t) -> task.run(), 
                        delayMs, periodMs, java.util.concurrent.TimeUnit.MILLISECONDS);
                return null; // Folia doesn't return BukkitTask
            } catch (Exception e) {
                return Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks);
            }
        } else {
            return Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks);
        }
    }

    /**
     * Runs a task at a specific location (region-aware for Folia).
     * 
     * @param plugin The plugin instance
     * @param location The location context
     * @param task The task to run
     */
    public static void runAtLocation(Plugin plugin, Location location, Runnable task) {
        if (isFolia()) {
            try {
                Object regionScheduler = Bukkit.class.getMethod("getRegionScheduler").invoke(null);
                regionScheduler.getClass().getMethod("run", Plugin.class, Location.class, java.util.function.Consumer.class)
                    .invoke(regionScheduler, plugin, location, (java.util.function.Consumer<Object>) (t) -> task.run());
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, task);
            }
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * Gets the server software name for logging purposes.
     * 
     * @return Server software name
     */
    public static String getServerSoftware() {
        String version = Bukkit.getVersion().toLowerCase();
        String name = Bukkit.getName().toLowerCase();
        
        // Check for Folia-based servers first (regionized)
        if (isFolia()) {
            // Check for specific Folia forks
            if (name.contains("luminol") || version.contains("luminol")) {
                return "Luminol";
            } else if (name.contains("lightingluminol") || version.contains("lightingluminol")) {
                return "LightingLuminol";
            } else if (name.contains("leaf") || version.contains("leaf")) {
                return "LeafMC";
            } else if (name.contains("kaiiju") || version.contains("kaiiju")) {
                return "Kaiiju";
            }
            return "Folia";
        }
        
        // Check for Paper-based servers
        if (version.contains("purpur") || name.contains("purpur")) {
            return "Purpur";
        } else if (version.contains("pufferfish") || name.contains("pufferfish")) {
            return "Pufferfish";
        } else if (version.contains("paper") || name.contains("paper")) {
            return "Paper";
        } else if (version.contains("spigot") || name.contains("spigot")) {
            return "Spigot";
        } else if (name.contains("craftbukkit")) {
            return "CraftBukkit";
        }
        
        return "Bukkit-Compatible";
    }
    
    /**
     * Checks if the server is running a Folia-based fork.
     * This includes Folia, Luminol, LightingLuminol, LeafMC, Kaiiju, etc.
     * 
     * @return true if running on any Folia-based server
     */
    public static boolean isFoliaBasedServer() {
        return isFolia();
    }
}
