package me.tuplugin.privatechest;

import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Iterator;
import java.util.Map;

public class ClearChestsCommand implements CommandExecutor {

    private final PrivateChest plugin;
    private final DataManager dataManager;
    private final ChestLocker chestLocker;
    private final MessageManager messages;

    public ClearChestsCommand(PrivateChest plugin) {
        this.plugin = plugin;
        this.dataManager = plugin.getDataManager();
        this.chestLocker = ChestLocker.getInstance();
        this.messages = plugin.getMessageManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("privatechest.admin")) {
            sender.sendMessage(messages.raw("no_permission"));
            return true;
        }

        int cleared = 0;

        Iterator<Map.Entry<org.bukkit.Location, String>> it = chestLocker.getChestOwners().entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<org.bukkit.Location, String> entry = it.next();
            Block block = entry.getKey().getBlock();

            if (block == null || !isLockableContainer(block)) {
                it.remove();
                cleared++;
            }
        }

        dataManager.saveData();
        sender.sendMessage(messages.raw("cleared_chests").replace("{amount}", String.valueOf(cleared)));
        return true;
    }

    private boolean isLockableContainer(Block block) {
        if (block == null) return false;
        switch (block.getType()) {
            case CHEST:
            case TRAPPED_CHEST:
            case BARREL:
                return true;
            default:
                return false;
        }
    }
}
