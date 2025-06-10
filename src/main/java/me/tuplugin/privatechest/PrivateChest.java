package me.tuplugin.privatechest;

import org.bukkit.plugin.java.JavaPlugin;
import org.bstats.bukkit.Metrics;
import me.tuplugin.privatechest.ReloadCommand;
import me.tuplugin.privatechest.ClearChestsCommand;
import me.tuplugin.privatechest.commands.TrustCommand;
import me.tuplugin.privatechest.commands.UntrustCommand;

public class PrivateChest extends JavaPlugin {

    private static PrivateChest instance;
    private MessageManager messageManager;
    private DataManager dataManager;
    private ChestLocker chestLocker;
    private TrustManager trustManager;
    private LimitManager limitManager;

    @Override
    public void onEnable() {
        instance = this;

        // Create plugin folder if it doesn't exist
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        // Update config and messages preserving custom edits
        FileUtil.updateConfig(this, "config.yml");
        FileUtil.updateConfig(this, "messages.yml");

        // Save default configuration files if they don't exist
        saveDefaultConfig(); // config.yml

        // bStats Metrics
        new Metrics(this, 25606);

        // Load managers
        messageManager = new MessageManager(this);

        chestLocker = new ChestLocker(this);

        trustManager = new TrustManager(this);

        limitManager = new LimitManager(this);

        dataManager = new DataManager(this);

        // Migrate plain text passwords to hashed format after data is loaded
        getLogger().info("Checking for plain text passwords to migrate...");
        chestLocker.migrateAllPasswords();

        // Register commands and events
        getCommand("privatechest").setExecutor(new ReloadCommand(this));
        getCommand("lockchest").setExecutor(new LockCommand(this));
        getCommand("unlockchest").setExecutor(new UnlockCommand(this));
        getCommand("clearchests").setExecutor(new ClearChestsCommand(this));
        getCommand("trust").setExecutor(new TrustCommand(this));
        getCommand("untrust").setExecutor(new UntrustCommand(this));
        getServer().getPluginManager().registerEvents(new ChestListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new HopperProtectionListener(), this);
        getServer().getPluginManager().registerEvents(new SignProtectionListener(this), this);

        getLogger().info("✅ PrivateChest has been enabled successfully.");
    }

    @Override
    public void onDisable() {
        // Close storage connection properly
        if (dataManager != null) {
            dataManager.close();
        }
        getLogger().info("⛔ PrivateChest has been disabled.");
    }

    // Getters
    public static PrivateChest getInstance() {
        return instance;
    }

    public MessageManager getMessageManager() {
        return messageManager;
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
}