package me.tuplugin.privatechest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import me.tuplugin.privatechest.container.ContainerFamily;
import me.tuplugin.privatechest.util.TabUtils;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * Locks the container a player is looking at with a password.
 */
public class LockCommand implements CommandExecutor, TabCompleter {

    private final PrivateChest plugin;
    private final ChestLocker chestLocker;
    private final LimitManager limitManager;
    private final MessageManager messages;

    public LockCommand(PrivateChest plugin) {
        this.plugin = plugin;
        this.chestLocker = plugin.getChestLocker();
        this.limitManager = plugin.getLimitManager();
        this.messages = plugin.getMessageManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            messages.sendRaw(sender, "console_only_player");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("privatechest.lock")) {
            messages.sendRaw(player, "no_permission");
            return true;
        }

        if (args.length != 1) {
            messages.sendRaw(player, "usage_lock");
            return true;
        }

        // The target is always in the player's own world, so this is decided before any block lookup.
        if (!plugin.getWorldPolicy().allowsLocking(player, player.getWorld())) {
            messages.send(player, "world_locking_disabled");
            return true;
        }

        String password = args[0];
        Block targetBlock = ContainerUtils.getTargetContainer(player);

        if (targetBlock == null) {
            messages.send(player, "not_a_chest");
            return true;
        }

        Set<Block> blocksToLock = ContainerUtils.getContainerBlocks(targetBlock);
        if (blocksToLock.isEmpty()) {
            messages.send(player, "not_a_chest");
            return true;
        }

        for (Block block : blocksToLock) {
            if (chestLocker.isChestLocked(block)) {
                messages.send(player, "already_locked");
                return true;
            }
        }

        ContainerFamily family = plugin.getContainerRegistry().getFamily(targetBlock.getType());

        // One check for both limit systems, shared with sign-based locking.
        LimitManager.LimitCheck limitCheck =
                limitManager.checkCanLock(player, targetBlock.getType(), blocksToLock.size());
        if (!limitCheck.isAllowed()) {
            messages.sendText(player, limitManager.getDeniedMessage(limitCheck));
            return true;
        }

        // Lock every part of the container, rolling back if any part fails so a double chest can
        // never end up with one protected half and one open half.
        boolean allLockedSuccessfully = true;
        List<Block> lockedParts = new ArrayList<>(blocksToLock.size());
        for (Block block : blocksToLock) {
            if (chestLocker.lockChest(block, player, password)) {
                lockedParts.add(block);
            } else {
                allLockedSuccessfully = false;
                break;
            }
        }

        if (!allLockedSuccessfully) {
            for (Block locked : lockedParts) {
                chestLocker.removeProtection(locked);
            }
            messages.send(player, "error_generic");
            return true;
        }

        messages.send(player, "locked", "container", limitManager.familyName(family));

        String warningMessage = limitManager.getLimitWarningMessage(player, family);
        if (warningMessage != null) {
            messages.sendText(player, warningMessage);
        }

        if (limitManager.areLimitsEnabled() && !player.hasPermission("privatechest.admin")) {
            messages.send(player, "limit_status",
                    "status", limitManager.getLimitStatus(player, family),
                    "container", limitManager.familyName(family));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // The only argument is a password. Nothing is ever suggested for it, and returning an empty
        // list stops the server from offering online player names as password candidates.
        return TabUtils.none();
    }
}
