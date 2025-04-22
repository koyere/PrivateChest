package me.tuplugin.privatechest;

import org.bukkit.plugin.java.JavaPlugin;

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

        // Load managers
        messageManager = new MessageManager(this);

        new ChestLocker(this);

        dataManager = new DataManager(this);


        // Register commands and events
        getCommand("lockchest").setExecutor(new LockCommand(this));
        getCommand("unlockchest").setExecutor(new UnlockCommand(this));
        getServer().getPluginManager().registerEvents(new ChestListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockProtectionListener(this), this);


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
