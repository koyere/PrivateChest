package me.tuplugin.privatechest;

import org.bukkit.Location; // Importado
import org.bukkit.Material; // Importado
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace; // Importado
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData; // Importado
import org.bukkit.block.data.type.Chest; // Importado
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashSet; // Importado
import java.util.Set; // Importado

public class HopperProtectionListener implements Listener {

    private final ChestLocker chestLocker;

    public HopperProtectionListener() {
        this.chestLocker = ChestLocker.getInstance();
    }

    @EventHandler
    public void onInventoryMove(InventoryMoveItemEvent event) {
        // Check both the source and the destination inventory
        boolean sourceProtected = isInventoryProtected(event.getSource());
        boolean destinationProtected = isInventoryProtected(event.getDestination());

        // If either inventory is part of a locked container, cancel the event
        if (sourceProtected || destinationProtected) {
            event.setCancelled(true);
        }
    }

    /**
     * Checks if an inventory belongs to a protected container.
     * Handles single chests, barrels, and double chests.
     * @param inventory The inventory to check.
     * @return true if the inventory is protected, false otherwise.
     */
    private boolean isInventoryProtected(Inventory inventory) {
        if (inventory == null) return false;

        InventoryHolder holder = inventory.getHolder();
        if (holder == null) return false;

        Set<Block> containerBlocks = new HashSet<>();

        // Try getting block from BlockState first (single chests, barrels, etc.)
        if (holder instanceof BlockState) {
            containerBlocks.addAll(getContainerBlocks(((BlockState) holder).getBlock()));
        } else {
            // If not a BlockState, try getting location (might be DoubleChest)
            Location loc = inventory.getLocation();
            if (loc != null) {
                containerBlocks.addAll(getContainerBlocks(loc.getBlock()));
            }
        }

        // If we couldn't find any blocks, it's likely not a protectable container
        if (containerBlocks.isEmpty()) {
            return false;
        }

        // Check if any of the found blocks are locked
        for (Block block : containerBlocks) {
            if (chestLocker.isChestLocked(block)) {
                return true; // Found a locked part, so the whole inventory is protected
            }
        }

        return false; // No part was locked
    }

    // --- Helper Methods (Copied from other listeners/commands) ---
    private Set<Block> getContainerBlocks(Block block) {
        Set<Block> blocks = new HashSet<>();
        if(block == null || !isLockableContainer(block.getType())) {
            return blocks; // Return empty if not a valid start block
        }
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