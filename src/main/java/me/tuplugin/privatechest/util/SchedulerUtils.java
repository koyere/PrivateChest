package me.tuplugin.privatechest.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Single entry point for scheduling work, on both standard servers and Folia-based ones.
 *
 * <p>Folia replaces the single main thread with independently ticking regions. A plugin may only
 * touch a block from the thread that owns the region containing it, which means "run this on the main
 * thread" is not a meaningful instruction there. Every scheduling decision in the plugin goes through
 * this class so the right scheduler is always used:</p>
 * <ul>
 *     <li>{@link #runGlobal} for work that belongs to no particular location</li>
 *     <li>{@link #runAtChunk} and {@link #runAtLocation} for anything that reads or writes blocks</li>
 *     <li>{@link #runAsync} and {@link #runTimerAsync} for work that never touches the world</li>
 * </ul>
 *
 * <p>Reflection is resolved once at class load instead of on every call, and every scheduling method
 * returns a {@link Task} that can be cancelled on either platform. Folia tasks were previously
 * impossible to cancel, so repeating work kept running after the plugin was disabled.</p>
 *
 * @since 2.3, reworked in 3.0
 */
public final class SchedulerUtils {

    private static final boolean FOLIA;

    // Folia reflection handles, resolved once. Null on standard servers.
    private static Method getGlobalRegionScheduler;
    private static Method globalRun;
    private static Method globalRunDelayed;
    private static Method getAsyncScheduler;
    private static Method asyncRunNow;
    private static Method asyncRunAtFixedRate;
    private static Method getRegionScheduler;
    private static Method regionExecuteChunk;
    private static Method regionExecuteLocation;
    private static Method scheduledTaskCancel;

    // Resolved on first use rather than at class load, since it needs a running server.
    private static Method minecraftVersionMethod;
    private static boolean resolvedMinecraftVersion;

    static {
        boolean folia;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException e) {
            folia = false;
        }
        FOLIA = folia;

        if (FOLIA) {
            try {
                resolveFoliaMethods();
            } catch (Exception e) {
                // Leave the handles null; every method falls back to the Bukkit scheduler and logs.
                Bukkit.getLogger().warning("[PrivateChest] Folia was detected but its schedulers could not be "
                        + "resolved (" + e.getMessage() + "). Falling back to the standard scheduler.");
            }
        }
    }

    private SchedulerUtils() {
        // Utility class.
    }

    private static void resolveFoliaMethods() throws Exception {
        Class<?> scheduledTask = Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask");
        scheduledTaskCancel = scheduledTask.getMethod("cancel");

        Class<?> globalScheduler = Class.forName(
                "io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
        getGlobalRegionScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler");
        globalRun = globalScheduler.getMethod("run", Plugin.class, Consumer.class);
        globalRunDelayed = globalScheduler.getMethod("runDelayed", Plugin.class, Consumer.class, long.class);

        Class<?> asyncScheduler = Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
        getAsyncScheduler = Bukkit.class.getMethod("getAsyncScheduler");
        asyncRunNow = asyncScheduler.getMethod("runNow", Plugin.class, Consumer.class);
        asyncRunAtFixedRate = asyncScheduler.getMethod("runAtFixedRate", Plugin.class, Consumer.class,
                long.class, long.class, TimeUnit.class);

        Class<?> regionScheduler = Class.forName("io.papermc.paper.threadedregions.scheduler.RegionScheduler");
        getRegionScheduler = Bukkit.class.getMethod("getRegionScheduler");
        regionExecuteChunk = regionScheduler.getMethod("execute", Plugin.class, World.class,
                int.class, int.class, Runnable.class);
        regionExecuteLocation = regionScheduler.getMethod("execute", Plugin.class, Location.class, Runnable.class);
    }

    /**
     * Whether this server splits the world into independently ticking regions.
     */
    public static boolean isFolia() {
        return FOLIA;
    }

    // --- Global / main thread ---

    /**
     * Runs a task where global server state is safe to touch: the main thread on standard servers,
     * the global region on Folia. Not valid for reading or writing blocks on Folia.
     */
    public static Task runGlobal(Plugin plugin, Runnable task) {
        if (FOLIA && globalRun != null) {
            try {
                Object scheduler = getGlobalRegionScheduler.invoke(null);
                Object handle = globalRun.invoke(scheduler, plugin, asConsumer(task));
                return new FoliaTask(handle);
            } catch (Exception e) {
                warn(plugin, "global task", e);
            }
        }
        return new BukkitTaskHandle(Bukkit.getScheduler().runTask(plugin, task));
    }

    /**
     * Runs a task after a delay, on the main thread or the global region.
     */
    public static Task runGlobalLater(Plugin plugin, Runnable task, long delayTicks) {
        long delay = Math.max(1L, delayTicks);
        if (FOLIA && globalRunDelayed != null) {
            try {
                Object scheduler = getGlobalRegionScheduler.invoke(null);
                Object handle = globalRunDelayed.invoke(scheduler, plugin, asConsumer(task), delay);
                return new FoliaTask(handle);
            } catch (Exception e) {
                warn(plugin, "delayed global task", e);
            }
        }
        return new BukkitTaskHandle(Bukkit.getScheduler().runTaskLater(plugin, task, delay));
    }

    // --- Region aware ---

    /**
     * Runs a task on the thread that owns the given chunk, which is the only place its blocks may be
     * touched on Folia.
     *
     * <p>On Folia, if that region is not ticking yet the task waits until it is. It does not force the
     * chunk to load, so callers still have to check that the chunk is loaded once the task runs.</p>
     */
    public static void runAtChunk(Plugin plugin, World world, int chunkX, int chunkZ, Runnable task) {
        if (FOLIA && regionExecuteChunk != null) {
            try {
                Object scheduler = getRegionScheduler.invoke(null);
                regionExecuteChunk.invoke(scheduler, plugin, world, chunkX, chunkZ, task);
                return;
            } catch (Exception e) {
                warn(plugin, "region task", e);
            }
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    /**
     * Runs a task on the thread that owns the given location.
     */
    public static void runAtLocation(Plugin plugin, Location location, Runnable task) {
        if (FOLIA && regionExecuteLocation != null) {
            try {
                Object scheduler = getRegionScheduler.invoke(null);
                regionExecuteLocation.invoke(scheduler, plugin, location, task);
                return;
            } catch (Exception e) {
                warn(plugin, "region task", e);
            }
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    // --- Asynchronous ---

    /**
     * Runs a task off any server thread. Must never touch the world.
     */
    public static void runAsync(Plugin plugin, Runnable task) {
        if (FOLIA && asyncRunNow != null) {
            try {
                Object scheduler = getAsyncScheduler.invoke(null);
                asyncRunNow.invoke(scheduler, plugin, asConsumer(task));
                return;
            } catch (Exception e) {
                warn(plugin, "async task", e);
            }
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    /**
     * Runs a repeating task off any server thread. Must never touch the world.
     *
     * @return a cancellable handle on both platforms
     */
    public static Task runTimerAsync(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        long delay = Math.max(1L, delayTicks);
        long period = Math.max(1L, periodTicks);

        if (FOLIA && asyncRunAtFixedRate != null) {
            try {
                Object scheduler = getAsyncScheduler.invoke(null);
                Object handle = asyncRunAtFixedRate.invoke(scheduler, plugin, asConsumer(task),
                        delay * 50L, period * 50L, TimeUnit.MILLISECONDS);
                return new FoliaTask(handle);
            } catch (Exception e) {
                warn(plugin, "repeating async task", e);
            }
        }
        return new BukkitTaskHandle(
                Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delay, period));
    }

    // --- Server identification ---

    /**
     * The Minecraft version, for the startup log.
     *
     * <p>Prefers the server's own accessor where it exists. Falling back to the Bukkit version string
     * is not enough on its own: some forks report build metadata in it, so Purpur 26.1.2 comes through
     * as {@code 26.1.2.build.2587-R0.1-SNAPSHOT}, which reads like a build number rather than a
     * version.</p>
     */
    public static String getMinecraftVersion() {
        if (!resolvedMinecraftVersion) {
            resolvedMinecraftVersion = true;
            try {
                minecraftVersionMethod = Bukkit.getServer().getClass().getMethod("getMinecraftVersion");
            } catch (Exception e) {
                minecraftVersionMethod = null;
            }
        }

        if (minecraftVersionMethod != null) {
            try {
                Object version = minecraftVersionMethod.invoke(Bukkit.getServer());
                if (version instanceof String && !((String) version).isEmpty()) {
                    return (String) version;
                }
            } catch (Exception e) {
                minecraftVersionMethod = null;
            }
        }

        String bukkitVersion = Bukkit.getBukkitVersion();
        int dash = bukkitVersion.indexOf('-');
        String base = dash > 0 ? bukkitVersion.substring(0, dash) : bukkitVersion;

        int build = base.indexOf(".build");
        return build > 0 ? base.substring(0, build) : base;
    }

    /**
     * The server software name, for the startup log.
     */
    public static String getServerSoftware() {
        String version = Bukkit.getVersion().toLowerCase();
        String name = Bukkit.getName().toLowerCase();

        if (FOLIA) {
            if (contains(name, version, "lightingluminol")) {
                return "LightingLuminol";
            }
            if (contains(name, version, "luminol")) {
                return "Luminol";
            }
            if (contains(name, version, "leaf")) {
                return "LeafMC";
            }
            if (contains(name, version, "kaiiju")) {
                return "Kaiiju";
            }
            return "Folia";
        }

        if (contains(name, version, "purpur")) {
            return "Purpur";
        }
        if (contains(name, version, "pufferfish")) {
            return "Pufferfish";
        }
        if (contains(name, version, "paper")) {
            return "Paper";
        }
        if (contains(name, version, "spigot")) {
            return "Spigot";
        }
        if (name.contains("craftbukkit")) {
            return "CraftBukkit";
        }
        return "Bukkit-compatible";
    }

    private static boolean contains(String name, String version, String needle) {
        return name.contains(needle) || version.contains(needle);
    }

    // --- Internals ---

    private static Consumer<Object> asConsumer(Runnable task) {
        return ignored -> task.run();
    }

    private static void warn(Plugin plugin, String what, Exception e) {
        plugin.getLogger().warning("Could not schedule a " + what + " through Folia (" + e.getMessage()
                + "). Falling back to the standard scheduler.");
    }

    /**
     * A cancellable scheduled task, independent of the platform.
     */
    public interface Task {
        void cancel();
    }

    private static final class BukkitTaskHandle implements Task {
        private final BukkitTask task;

        BukkitTaskHandle(BukkitTask task) {
            this.task = task;
        }

        @Override
        public void cancel() {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
    }

    private static final class FoliaTask implements Task {
        private final Object handle;

        FoliaTask(Object handle) {
            this.handle = handle;
        }

        @Override
        public void cancel() {
            if (handle == null || scheduledTaskCancel == null) {
                return;
            }
            try {
                scheduledTaskCancel.invoke(handle);
            } catch (Exception e) {
                Bukkit.getLogger().warning("[PrivateChest] Could not cancel a Folia task: " + e.getMessage());
            }
        }
    }
}
