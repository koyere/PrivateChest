package me.tuplugin.privatechest;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace; // Importado
import org.bukkit.block.BlockState; // Importado
import org.bukkit.block.data.BlockData; // Importado
import org.bukkit.block.data.type.Chest; // Importado
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashSet; // Importado
import java.util.Set; // Importado

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
            player.sendMessage(messages.raw("usage_unlock"));
            return true;
        }

        String password = args[0];
        Block targetBlock = player.getTargetBlock(null, 5); // Usamos getTargetBlock como en LockCommand

        if (targetBlock == null || !isLockableContainer(targetBlock.getType())) {
            player.sendMessage(messages.get("not_a_chest"));
            return true;
        }

        // --- INICIO: Lógica de Cofre Doble ---
        Set<Block> containerBlocks = getContainerBlocks(targetBlock);
        Block lockedBlock = null;

        // Find if any part is locked
        for (Block part : containerBlocks) {
            if (chestLocker.isChestLocked(part)) {
                lockedBlock = part;
                break;
            }
        }
        // --- FIN: Lógica de Cofre Doble ---

        // If no part is locked, send message
        if (lockedBlock == null) {
            player.sendMessage(messages.raw("not_locked"));
            return true;
        }

        // Check ownership using the found locked block
        if (!chestLocker.isOwner(lockedBlock, player)) {
            player.sendMessage(messages.get("not_your_chest"));
            return true;
        }

        // Try to unlock using the found locked block's password
        boolean success = chestLocker.unlockChest(lockedBlock, player, password);

        if (success) {
            // On success, remove protection from ALL parts
            for (Block part : containerBlocks) {
                if (chestLocker.isChestLocked(part)) { // Check again in case of weird states
                    chestLocker.removeProtection(part);
                }
            }
            plugin.getDataManager().saveData();
            player.sendMessage(messages.get("unlocked"));
        } else {
            player.sendMessage(messages.get("wrong_password"));
        }

        return true;
    }

    // --- Helper Methods (Copied or similar to other classes) ---
    private Set<Block> getContainerBlocks(Block block) {
        Set<Block> blocks = new HashSet<>();
        blocks.add(block);

        BlockState state = block.getState();
        BlockData blockData = block.getBlockData();

        if (state instanceof org.bukkit.block.Chest && blockData instanceof Chest) {
            Chest chestData = (Chest) blockData;
            Chest.Type chestType = chestData.getType();

            if (chestType != Chest.Type.SINGLE) {
                BlockFace facing = chestData.getFacing();
                BlockFace otherHalfDirection = getOtherChestHalfDirection(chestType, facing);

                if (otherHalfDirection != null) {
                    Block otherBlock = block.getRelative(otherHalfDirection);
                    if (otherBlock.getState() instanceof org.bukkit.block.Chest) {
                        blocks.add(otherBlock);
                    }
                }
            }
        }
        return blocks;
    }

    private BlockFace getOtherChestHalfDirection(Chest.Type type, BlockFace facing) {
        if (type == Chest.Type.LEFT) {
            switch (facing) {
                case NORTH: return BlockFace.EAST;
                case EAST:  return BlockFace.SOUTH;
                case SOUTH: return BlockFace.WEST;
                case WEST:  return BlockFace.NORTH;
                default:    return null;
            }
        } else if (type == Chest.Type.RIGHT) {
            switch (facing) {
                case NORTH: return BlockFace.WEST;
                case EAST:  return BlockFace.NORTH;
                case SOUTH: return BlockFace.EAST;
                case WEST:  return BlockFace.SOUTH;
                default:    return null;
            }
        }
        return null;
    }

    private boolean isLockableContainer(Material type) {
        if (type == Material.CHEST || type == Material.TRAPPED_CHEST || type == Material.BARREL) {
            return true;
        }
        // Support all shulker box variants
        return type.name().contains("SHULKER_BOX");
    }
}