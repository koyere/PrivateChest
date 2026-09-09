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

public class LockCommand implements CommandExecutor, TabCompleter {

    private final PrivateChest plugin;
    private final ChestLocker chestLocker;
    private final LimitManager limitManager;
    private final MessageManager messages;

    public LockCommand(PrivateChest plugin) {
        this.plugin = plugin;
        this.chestLocker = ChestLocker.getInstance();
        this.limitManager = LimitManager.getInstance();
        this.messages = plugin.getMessageManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            messages.send(sender, messages.raw("console_only_player"));
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("privatechest.lock")) {
            messages.send(player, messages.raw("no_permission"));
            return true;
        }

        if (args.length != 1) {
            messages.send(player, messages.raw("usage_lock"));
            return true;
        }

        String password = args[0];
        Block targetBlock = ContainerUtils.getTargetContainer(player);

        if (targetBlock == null) {
            messages.send(player, messages.get("not_a_chest"));
            return true;
        }

        Set<Block> blocksToLock = ContainerUtils.getContainerBlocks(targetBlock);

        if (blocksToLock.isEmpty()) {
            messages.send(player, messages.get("not_a_chest"));
            return true;
        }

        // Check if any part is already locked
        for (Block block : blocksToLock) {
            if (chestLocker.isChestLocked(block)) {
                messages.send(player, messages.get("already_locked"));
                return true;
            }
        }

        ContainerFamily family = plugin.getContainerRegistry().getFamily(targetBlock.getType());

        // One check for both limit systems, shared with sign-based locking.
        LimitManager.LimitCheck limitCheck =
                limitManager.checkCanLock(player, targetBlock.getType(), blocksToLock.size());
        if (!limitCheck.isAllowed()) {
            messages.send(player, limitManager.getDeniedMessage(limitCheck));
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
        }

        // Send feedback
        if (allLockedSuccessfully) {
            messages.send(player, messages.get("locked")
                    .replace("{container}", limitManager.familyName(family)));

            String warningMessage = limitManager.getLimitWarningMessage(player, family);
            if (warningMessage != null) {
                messages.send(player, warningMessage);
            }

            if (limitManager.areLimitsEnabled() && !player.hasPermission("privatechest.admin")) {
                messages.send(player, messages.get("limit_status")
                        .replace("{status}", limitManager.getLimitStatus(player, family))
                        .replace("{container}", limitManager.familyName(family)));
            }
        } else {
            messages.send(player, messages.get("error_generic"));
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