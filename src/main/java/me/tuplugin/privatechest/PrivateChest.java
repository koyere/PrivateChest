package me.tuplugin.privatechest;

import me.tuplugin.privatechest.commands.TrustCommand;
import me.tuplugin.privatechest.commands.UntrustCommand;
import me.tuplugin.privatechest.container.ContainerRegistry;
import me.tuplugin.privatechest.storage.ProtectionStore;
import me.tuplugin.privatechest.util.AttemptTracker;
import me.tuplugin.privatechest.util.SchedulerUtils;
import org.bstats.bukkit.Metrics;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

public class PrivateChest extends JavaPlugin {

    private static PrivateChest instance;

    private MessageManager messageManager;
    private ProtectionStore protectionStore;
    private DataManager dataManager;
    private ChestLocker chestLocker;
    private TrustManager trustManager;
    private LimitManager limitManager;
    private AutoCleanupManager autoCleanupManager;
    private ContainerNameManager containerNameManager;
    private BedrockUtils bedrockUtils;
    private AccessGuard accessGuard;
    private AttemptTracker passwordAttempts;
    private ContainerRegistry containerRegistry;
    private SchedulerUtils.Task maintenanceTask;

    @Override
    public void onEnable() {
        instance = this;

        getLogger().info("Running on " + SchedulerUtils.getServerSoftware()
                + " (Minecraft " + getServer().getBukkitVersion().split("-")[0] + ")");

        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().severe("Could not create the plugin folder. Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Create the bundled files if missing, then add any options introduced by this update.
        saveDefaultConfig();
        FileUtil.updateConfig(this, "config.yml");
        FileUtil.updateConfig(this, "messages.yml");
        reloadConfig();

        messageManager = new MessageManager(this);

        applySecuritySettings();

        // Which blocks can be protected is resolved against this server's materials, so containers
        // added by newer Minecraft versions work without a plugin update.
        containerRegistry = new ContainerRegistry(this);
        containerRegistry.load();

        // Protection state lives in one registry that every other component reads and writes.
        protectionStore = new ProtectionStore();
        chestLocker = new ChestLocker(this, protectionStore);
        trustManager = new TrustManager(this);
        containerNameManager = new ContainerNameManager(this, protectionStore);
        limitManager = new LimitManager(this, protectionStore);
        bedrockUtils = new BedrockUtils(this);
        accessGuard = new AccessGuard(this, protectionStore, trustManager);

        // Storage has to come up before anything can touch the data.
        dataManager = new DataManager(this, protectionStore, trustManager);
        if (!dataManager.initialize()) {
            getLogger().severe("Storage could not be initialised. Disabling PrivateChest so that "
                    + "protections are not silently lost.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        chestLocker.migrateAllPasswords();

        autoCleanupManager = new AutoCleanupManager(this, protectionStore, trustManager);
        autoCleanupManager.initialize();

        new Metrics(this, 25606);

        registerCommands();
        registerListeners();
        startMaintenanceTasks();

        getLogger().info("PrivateChest v" + getDescription().getVersion() + " enabled ("
                + dataManager.getStorageType() + " storage, " + protectionStore.size()
                + " protected container(s)).");
    }

    @Override
    public void onDisable() {
        if (maintenanceTask != null) {
            maintenanceTask.cancel();
            maintenanceTask = null;
        }

        if (autoCleanupManager != null) {
            autoCleanupManager.shutdown();
        }

        // Closing the data manager writes everything still queued before releasing the backend.
        if (dataManager != null) {
            dataManager.close();
        }

        getLogger().info("PrivateChest disabled.");
    }

    /**
     * Registers every command. Each executor also provides its own permission-aware tab completion,
     * so nothing is ever suggested to a player who cannot run it.
     */
    private void registerCommands() {
        register("privatechest", new PrivateChestCommand(this));
        register("lockchest", new LockCommand(this));
        register("unlockchest", new UnlockCommand(this));
        register("clearchests", new ClearChestsCommand(this));
        register("trust", new TrustCommand(this));
        register("untrust", new UntrustCommand(this));
        register("renamecontainer", new RenameCommand(this));
    }

    /**
     * Wires a command, warning instead of throwing if plugin.yml and the code disagree.
     */
    private <T extends CommandExecutor & TabCompleter> void register(String name, T handler) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("Command '" + name + "' is missing from plugin.yml and was not registered.");
            return;
        }
        command.setExecutor(handler);
        command.setTabCompleter(handler);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new ChestListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new HopperProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new SignProtectionListener(this), this);
    }

    /**
     * Re-applies settings that are read once rather than per use.
     * Called by {@code /privatechest reload}.
     */
    public void reloadSettings() {
        applySecuritySettings();
        containerRegistry.load();
    }

    /**
     * Reads the password security settings and builds the attempt tracker.
     */
    private void applySecuritySettings() {
        int applied = PasswordManager.configure(
                getConfig().getInt("security.password-hash-iterations", PasswordManager.DEFAULT_ITERATIONS));

        passwordAttempts = new AttemptTracker(
                getConfig().getInt("security.max-password-attempts", 5),
                getConfig().getLong("security.attempt-window-seconds", 60L),
                getConfig().getLong("security.lockout-seconds", 300L));

        getLogger().fine("[Security] Password hashing uses " + applied + " iterations.");
    }

    /**
     * Starts the periodic housekeeping that keeps in-memory tracking from growing without bound.
     */
    private void startMaintenanceTasks() {
        long fiveMinutesInTicks = 5L * 60L * 20L;
        maintenanceTask = SchedulerUtils.runTimerAsync(this,
                () -> passwordAttempts.purgeExpired(),
                fiveMinutesInTicks, fiveMinutesInTicks);
    }

    // --- Getters ---

    public static PrivateChest getInstance() {
        return instance;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    /**
     * The in-memory registry of every protected container.
     */
    public ProtectionStore getProtectionStore() {
        return protectionStore;
    }

    public DataManager getDataManager() {
        return dataManager;
    }

    public ChestLocker getChestLocker() {
        return chestLocker;
    }

    public TrustManager getTrustManager() {
        return trustManager;
    }

    public LimitManager getLimitManager() {
        return limitManager;
    }

    public AutoCleanupManager getAutoCleanupManager() {
        return autoCleanupManager;
    }

    public ContainerNameManager getContainerNameManager() {
        return containerNameManager;
    }

    public BedrockUtils getBedrockUtils() {
        return bedrockUtils;
    }

    /**
     * The shared access decision maker used by every protection listener.
     */
    public AccessGuard getAccessGuard() {
        return accessGuard;
    }

    /**
     * Rate limiting for password entry.
     */
    public AttemptTracker getPasswordAttempts() {
        return passwordAttempts;
    }

    /**
     * Which blocks can be protected on this server.
     */
    public ContainerRegistry getContainerRegistry() {
        return containerRegistry;
    }
}
