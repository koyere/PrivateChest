package me.tuplugin.privatechest;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace; // Importado
import org.bukkit.block.BlockState; // Importado
import org.bukkit.block.data.BlockData; // Importado
import org.bukkit.block.data.type.Chest; // Importado
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.HashSet; // Importado
import java.util.Iterator;
import java.util.Set; // Importado

public class BlockProtectionListener implements Listener {

    private final PrivateChest plugin;
    private final ChestLocker chestLocker;
    private final MessageManager messages;

    public BlockProtectionListener(PrivateChest plugin) {
        this.plugin = plugin;
        this.chestLocker = ChestLocker.getInstance();
        this.messages = plugin.getMessageManager();
    }

    @EventHandler
    public void onChestBreak(BlockBreakEvent event) {
        Block blockBeingBroken = event.getBlock();
        Player player = event.getPlayer();

        if (!isLockableContainer(blockBeingBroken.getType())) return;

        // --- INICIO: Lógica de Cofre Doble ---
        Set<Block> containerBlocks = getContainerBlocks(blockBeingBroken);
        Block lockedBlock = null;

        // Find if any part of the container is locked
        for (Block part : containerBlocks) {
            if (chestLocker.isChestLocked(part)) {
                lockedBlock = part;
                break;
            }
        }
        // --- FIN: Lógica de Cofre Doble ---

        // If no part is locked, do nothing (allow break)
        if (lockedBlock == null) {
            return;
        }

        // A part is locked, check ownership
        if (!chestLocker.isOwner(lockedBlock, player) && !player.hasPermission("privatechest.admin")) {
            // Not the owner and not admin, deny break
            event.setCancelled(true);
            player.sendMessage(messages.get("not_your_chest"));
        } else {
            // It's the owner or an admin, allow break BUT remove protection from ALL parts.
            // Send message only to owner if they are the one breaking it
            if (chestLocker.isOwner(lockedBlock, player)) {
                player.sendMessage(messages.get("chest_break_warning"));
            } else {
                player.sendMessage(messages.get("admin_chest_break_notice")); // Consider adding this message
            }

            // Remove protection from ALL blocks in the set
            for (Block part : containerBlocks) {
                if (chestLocker.isChestLocked(part)) {
                    chestLocker.removeProtection(part);
                }
            }
            plugin.getDataManager().saveData(); // Save changes
        }
    }

    // --- Explosions Handlers (Unchanged, but be aware they might not be perfect for double chests) ---
    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        Iterator<Block> it = event.blockList().iterator();
        while (it.hasNext()) {
            Block block = it.next();
            if (isLockableContainer(block.getType()) && chestLocker.isChestLocked(block)) {
                it.remove(); // Protects this specific block
            }
        }
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        Iterator<Block> it = event.blockList().iterator();
        while (it.hasNext()) {
            Block block = it.next();
            if (isLockableContainer(block.getType()) && chestLocker.isChestLocked(block)) {
                it.remove(); // Protects this specific block
            }
        }
    }
    // --- End Explosions Handlers ---

    // --- Helper Methods (Copied from ChestListener) ---
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
        return type == Material.CHEST || type == Material.TRAPPED_CHEST || type == Material.BARREL;
    }
}