package me.tuplugin.privatechest;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;

public class LockCommand implements CommandExecutor {

    private final PrivateChest plugin;
    private final ChestLocker chestLocker;
    private final MessageManager messages;

    public LockCommand(PrivateChest plugin) {
        this.plugin = plugin;
        this.chestLocker = ChestLocker.getInstance();
        this.messages = plugin.getMessageManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("privatechest.use")) {
            player.sendMessage(messages.raw("no_permission"));
            return true;
        }

        if (args.length != 1) {
            player.sendMessage(messages.raw("usage_lock")); // Add this line in messages.yml
            return true;
        }

        String password = args[0];

        // Use getTargetBlock with null to allow air passthrough, more accurate
        Block targetBlock = player.getTargetBlock(null, 5);

        if (targetBlock == null || !isLockableContainer(targetBlock)) {
            player.sendMessage(messages.get("not_a_chest"));
            return true;
        }

        if (chestLocker.isChestLocked(targetBlock)) {
            player.sendMessage(messages.get("already_locked"));
            return true;
        }

        boolean success = chestLocker.lockChest(targetBlock, player, password);

        if (success) {
            plugin.getDataManager().saveData(); // Save immediately
            player.sendMessage(messages.get("locked"));
        } else {
            player.sendMessage(messages.get("already_locked"));
        }

        return true;
    }

    private boolean isLockableContainer(Block block) {
        Material type = block.getType();
        return type == Material.CHEST || type == Material.TRAPPED_CHEST;
    }
}
