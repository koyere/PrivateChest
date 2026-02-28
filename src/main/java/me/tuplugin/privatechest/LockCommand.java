package me.tuplugin.privatechest;

import java.util.Set;

import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LockCommand implements CommandExecutor {

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
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("privatechest.lock")) {
            player.sendMessage(messages.raw("no_permission"));
            return true;
        }

        if (args.length != 1) {
            player.sendMessage(messages.raw("usage_lock"));
            return true;
        }

        String password = args[0];
        Block targetBlock = player.getTargetBlock(null, 5);

        if (targetBlock == null || !ContainerUtils.isLockableContainer(targetBlock.getType())) {
            player.sendMessage(messages.get("not_a_chest"));
            return true;
        }

        Set<Block> blocksToLock = ContainerUtils.getContainerBlocks(targetBlock);

        if (blocksToLock.isEmpty()) {
            player.sendMessage(messages.get("not_a_chest"));
            return true;
        }

        // Check if any part is already locked
        for (Block block : blocksToLock) {
            if (chestLocker.isChestLocked(block)) {
                player.sendMessage(messages.get("already_locked"));
                return true;
            }
        }

        // Check chest limits (only if not admin)
        if (!player.hasPermission("privatechest.admin") && limitManager.areLimitsEnabled()) {
            if (!limitManager.canPlayerLockAdditional(player, blocksToLock.size())) {
                int limit = limitManager.getPlayerLimit(player);
                int current = limitManager.getPlayerChestCount(player);

                if (limit == -1) {
                    // This shouldn't happen but just in case
                    player.sendMessage(messages.get("limit_error"));
                } else {
                    player.sendMessage(messages.get("limit_exceeded")
                            .replace("{current}", String.valueOf(current))
                            .replace("{limit}", String.valueOf(limit))
                            .replace("{trying}", String.valueOf(blocksToLock.size())));
                }
                return true;
            }
        }

        // Lock all parts
        boolean allLockedSuccessfully = true;
        for (Block block : blocksToLock) {
            if (!chestLocker.lockChest(block, player, password)) {
                allLockedSuccessfully = false;
                break;
            }
        }

        // Send feedback
        if (allLockedSuccessfully) {
            plugin.getDataManager().saveData();
            player.sendMessage(messages.get("locked"));

            // Send limit warning if applicable
            String warningMessage = limitManager.getLimitWarningMessage(player);
            if (warningMessage != null) {
                player.sendMessage(warningMessage);
            }

            // Show current limit status if limits are enabled
            if (limitManager.areLimitsEnabled() && !player.hasPermission("privatechest.admin")) {
                String status = limitManager.getLimitStatus(player);
                player.sendMessage(messages.get("limit_status").replace("{status}", status));
            }
        } else {
            player.sendMessage(messages.get("error_generic"));
        }

        return true;
    }
<<<<<<< HEAD

    /**
     * Determines the direction of the other half of a double chest.
     * @param type The type (LEFT or RIGHT).
     * @param facing The direction the chest is facing.
     * @return The BlockFace direction to the other half, or null if single/error.
     */
    private BlockFace getOtherChestHalfDirection(Chest.Type type, BlockFace facing) {
        if (type == Chest.Type.LEFT) {
            switch (facing) {
                case NORTH: return BlockFace.EAST;
                case EAST:  return BlockFace.SOUTH;
                case SOUTH: return BlockFace.WEST;
                case WEST:  return BlockFace.NORTH;
                default:    return null; // Should not happen for chests
            }
        } else if (type == Chest.Type.RIGHT) {
            switch (facing) {
                case NORTH: return BlockFace.WEST;
                case EAST:  return BlockFace.NORTH;
                case SOUTH: return BlockFace.EAST;
                case WEST:  return BlockFace.SOUTH;
                default:    return null; // Should not happen for chests
            }
        }
        return null; // Not a double chest part
    }

    private boolean isLockableContainer(Material type) {
        if (type == Material.CHEST || type == Material.TRAPPED_CHEST || type == Material.BARREL) {
            return true;
        }
        // Support all shulker box variants
        return type.name().contains("SHULKER_BOX");
    }
=======
>>>>>>> 22e8436 (Version 2.3.1)
}