package me.tuplugin.privatechest.util;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Chest;

import java.util.HashSet;
import java.util.Set;

/**
 * Utility class for container-related operations.
 * Centralizes common logic to avoid code duplication across listeners and commands.
 * 
 * @since 2.2.1
 */
public final class ContainerUtils {

    private ContainerUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Gets all blocks belonging to a container (1 for single, 2 for double chest).
     * 
     * @param block One block of the container
     * @return A Set containing all blocks of the container
     */
    public static Set<Block> getContainerBlocks(Block block) {
        Set<Block> blocks = new HashSet<>();
        if (block == null || !isLockableContainer(block.getType())) {
            return blocks;
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

    /**
     * Determines the direction of the other half of a double chest.
     * 
     * @param type The chest type (LEFT or RIGHT)
     * @param facing The direction the chest is facing
     * @return The BlockFace direction to the other half, or null if single
     */
    public static BlockFace getOtherChestHalfDirection(Chest.Type type, BlockFace facing) {
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

    /**
     * Checks if a material type is a lockable container.
     * Supports chests, trapped chests, barrels, and all shulker box variants.
     * 
     * @param type The material to check
     * @return true if the material is a lockable container
     */
    public static boolean isLockableContainer(Material type) {
        if (type == null) {
            return false;
        }
        
        // Standard containers
        if (type == Material.CHEST || type == Material.TRAPPED_CHEST || type == Material.BARREL) {
            return true;
        }
        
        // All shulker box variants
        return type.name().contains("SHULKER_BOX");
    }

    /**
     * Checks if a material is a sign (any type).
     * 
     * @param material The material to check
     * @return true if the material is a sign
     */
    public static boolean isSign(Material material) {
        return material != null && material.name().contains("SIGN");
    }
}
