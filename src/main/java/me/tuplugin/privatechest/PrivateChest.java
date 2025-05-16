package me.tuplugin.privatechest;

import org.bukkit.plugin.java.JavaPlugin;
import org.bstats.bukkit.Metrics;
import me.tuplugin.privatechest.ReloadCommand;
import me.tuplugin.privatechest.ClearChestsCommand;

public class PrivateChest extends JavaPlugin {

    private static PrivateChest instance;
    private MessageManager messageManager;
    private DataManager dataManager;

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
        //saveResource("messages.yml", false); // messages.yml (only if not present)

        // bStats Metrics
        new Metrics(this, 25606);

        // Load managers
        messageManager = new MessageManager(this);

        new ChestLocker(this);

        dataManager = new DataManager(this);


        // Register commands and events
        getCommand("privatechest").setExecutor(new ReloadCommand(this));
        getCommand("lockchest").setExecutor(new LockCommand(this));
        getCommand("unlockchest").setExecutor(new UnlockCommand(this));
        getCommand("clearchests").setExecutor(new ClearChestsCommand(this));
        getServer().getPluginManager().registerEvents(new ChestListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new HopperProtectionListener(), this);


        getLogger().info("✅ PrivateChest has been enabled successfully.");
    }

    @Override
    public void onDisable() {
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
}
