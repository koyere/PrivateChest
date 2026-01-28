package me.tuplugin.privatechest;

import org.bukkit.Location;
import org.bukkit.Material;
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

        sender.sendMessage(messages.raw("cleaning_start")); // Optional: Inform about start

        int cleared = 0;
        Map<Location, String> owners = chestLocker.getChestOwners();
        Map<Location, String> passwords = chestLocker.getChestPasswords();

        // Use an iterator to safely remove elements while looping
        Iterator<Map.Entry<Location, String>> it = owners.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Location, String> entry = it.next();
            Location loc = entry.getKey();

            // Check if the world is loaded. If not, it's orphaned.
            if (loc.getWorld() == null) {
                passwords.remove(loc); // Remove from passwords first
                it.remove(); // Remove from owners
                cleared++;
                continue; // Move to the next entry
            }

            Block block = loc.getBlock();

            // Check if the block is no longer a lockable container. If so, it's orphaned.
            if (!isLockableContainer(block)) {
                passwords.remove(loc); // Remove from passwords
                it.remove(); // Remove from owners
                cleared++;
            }
        }

        // Only save if something was cleared to avoid unnecessary disk writes
        if (cleared > 0) {
            dataManager.saveData();
            sender.sendMessage(messages.raw("cleaned_chests").replace("{amount}", String.valueOf(cleared))); // Use 'cleaned_chests' or update 'cleared_chests'
        } else {
            sender.sendMessage(messages.raw("no_chests_to_clean")); // Optional: Message if nothing was found
        }

        return true;
    }

    private boolean isLockableContainer(Block block) {
        if (block == null) return false;
        Material type = block.getType();
        if (type == Material.CHEST || type == Material.TRAPPED_CHEST || type == Material.BARREL) {
            return true;
        }
        // Support all shulker box variants
        return type.name().contains("SHULKER_BOX");
    }
}