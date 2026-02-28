package me.tuplugin.privatechest;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Chest;

import me.tuplugin.privatechest.enums.ContainerType;

/**
 * Shared utility class for container-related operations.
 * Centralizes double chest detection, container validation, and location serialization
 * to eliminate code duplication across listeners and commands.
 *
 * @since 2.3.1
 */
public final class ContainerUtils {

    private ContainerUtils() {
        // Utility class, no instantiation
    }

    /**
     * Gets all blocks belonging to a container (1 for single, 2 for double chest).
     * Uses BlockData approach for accurate double chest detection (Spigot 1.13+).
     *
     * @param block One block of the container
     * @return A Set containing all blocks of the container (never null, never empty if block is valid)
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
     * @param type   The chest type (LEFT or RIGHT)
     * @param facing The direction the chest is facing
     * @return The BlockFace direction to the other half, or null if single/error
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
        if (type == null) return false;
        return ContainerType.isLockableContainer(type);
    }

    /**
     * Checks if a material type is a basic lockable container (chest, trapped chest, barrel).
     * Does not include shulker boxes.
     *
     * @param type The material to check
     * @return true if the material is a basic lockable container
     */
    public static boolean isBasicLockableContainer(Material type) {
        return type == Material.CHEST || type == Material.TRAPPED_CHEST || type == Material.BARREL;
    }

    /**
     * Serializes a Location to a String (world:x:y:z).
     * Returns null if the location or its world is null.
     *
     * @param loc The location to serialize
     * @return The serialized string, or null if location/world is null
     */
    public static String serializeLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return null;
        }
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }
}
