package me.tuplugin.privatechest;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class UnlockCommand implements CommandExecutor {

    private final PrivateChest plugin;
    private final ChestLocker chestLocker;
    private final MessageManager messages;

    public UnlockCommand(PrivateChest plugin) {
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
            player.sendMessage(messages.raw("usage_unlock")); // Add this to messages.yml
            return true;
        }

        String password = args[0];
        Block targetBlock = player.getTargetBlockExact(5); // Must be close to the chest

        if (targetBlock == null || !isLockableContainer(targetBlock)) {
            player.sendMessage(messages.get("not_a_chest"));
            return true;
        }

        if (!chestLocker.isChestLocked(targetBlock)) {
            player.sendMessage(messages.raw("not_locked")); // Optional message if chest isn't locked
            return true;
        }

        if (!chestLocker.isOwner(targetBlock, player)) {
            player.sendMessage(messages.get("not_your_chest"));
            return true;
        }

        boolean success = chestLocker.unlockChest(targetBlock, player, password);

        if (success) {
            chestLocker.removeProtection(targetBlock);
            plugin.getDataManager().saveData();
            player.sendMessage(messages.get("unlocked"));
        } else {
            player.sendMessage(messages.get("wrong_password"));
        }

        return true;
    }

    private boolean isLockableContainer(Block block) {
        Material type = block.getType();
        return type == Material.CHEST || type == Material.TRAPPED_CHEST || type == Material.BARREL;
    }
}
