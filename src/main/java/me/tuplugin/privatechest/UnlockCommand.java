package me.tuplugin.privatechest;

import java.util.List;
import java.util.Set;

import me.tuplugin.privatechest.util.AttemptTracker;
import me.tuplugin.privatechest.util.TabUtils;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * Removes a player's own protection from a container after they confirm the password.
 *
 * <p>Wrong attempts are rate limited as of 3.0, and failures can be logged for staff. Guessing was
 * previously unlimited, free, and invisible.</p>
 */
public class UnlockCommand implements CommandExecutor, TabCompleter {

    private final PrivateChest plugin;
    private final ChestLocker chestLocker;
    private final MessageManager messages;

    public UnlockCommand(PrivateChest plugin) {
        this.plugin = plugin;
        this.chestLocker = plugin.getChestLocker();
        this.messages = plugin.getMessageManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            messages.send(sender, messages.raw("console_only_player"));
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("privatechest.unlock")) {
            messages.send(player, messages.raw("no_permission"));
            return true;
        }

        if (args.length != 1) {
            messages.send(player, messages.raw("usage_unlock"));
            return true;
        }

        AttemptTracker attempts = plugin.getPasswordAttempts();
        long lockout = attempts.getRemainingLockoutSeconds(player.getUniqueId());
        if (lockout > 0L) {
            messages.send(player, messages.get("too_many_attempts")
                    .replace("{seconds}", String.valueOf(lockout)));
            return true;
        }

        String password = args[0];
        Block targetBlock = ContainerUtils.getTargetContainer(player);

        if (targetBlock == null) {
            messages.send(player, messages.get("not_a_chest"));
            return true;
        }

        // Find whichever part of the container carries the record.
        Set<Block> containerBlocks = ContainerUtils.getContainerBlocks(targetBlock);
        Block lockedBlock = null;
        for (Block part : containerBlocks) {
            if (chestLocker.isChestLocked(part)) {
                lockedBlock = part;
                break;
            }
        }

        if (lockedBlock == null) {
            messages.send(player, messages.raw("not_locked"));
            return true;
        }

        if (!chestLocker.isOwner(lockedBlock, player)) {
            messages.send(player, messages.get("not_your_chest"));
            return true;
        }

        if (!chestLocker.unlockChest(lockedBlock, player, password)) {
            handleWrongPassword(player, lockedBlock, attempts);
            return true;
        }

        attempts.clear(player.getUniqueId());
        for (Block part : containerBlocks) {
            chestLocker.removeProtection(part);
        }
        messages.send(player, messages.get("unlocked"));
        return true;
    }

    private void handleWrongPassword(Player player, Block lockedBlock, AttemptTracker attempts) {
        boolean lockedOut = attempts.recordFailure(player.getUniqueId());

        if (plugin.getConfig().getBoolean("security.log-failed-attempts", true)) {
            plugin.getLogger().warning("[Security] " + player.getName() + " entered a wrong password for the "
                    + "container at " + lockedBlock.getWorld().getName() + "," + lockedBlock.getX() + ","
                    + lockedBlock.getY() + "," + lockedBlock.getZ()
                    + (lockedOut ? " (now rate limited)." : "."));
        }

        if (lockedOut) {
            long remaining = attempts.getRemainingLockoutSeconds(player.getUniqueId());
            messages.send(player, messages.get("too_many_attempts")
                    .replace("{seconds}", String.valueOf(remaining)));
        } else {
            messages.send(player, messages.get("wrong_password"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // The only argument is a password; never suggest anything for it.
        return TabUtils.none();
    }
}
