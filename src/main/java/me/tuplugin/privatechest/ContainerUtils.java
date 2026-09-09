package me.tuplugin.privatechest;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.util.RayTraceResult;

import me.tuplugin.privatechest.container.ContainerRegistry;

/**
 * Shared container helpers: identification, double chest pairing, and resolving what a player is
 * looking at.
 *
 * <p>Which materials count as containers is no longer decided here. That question is answered by
 * {@link ContainerRegistry}, which resolves it at runtime so containers added by newer Minecraft
 * versions are supported without a plugin update.</p>
 *
 * @since 2.3.1, reworked in 3.0
 */
public final class ContainerUtils {

    /** How far a player can be from a container to target it with a command. */
    public static final double TARGET_RANGE = 5.0D;

    private ContainerUtils() {
        // Utility class, no instantiation.
    }

    private static ContainerRegistry registry() {
        PrivateChest plugin = PrivateChest.getInstance();
        return plugin == null ? null : plugin.getContainerRegistry();
    }

    /**
     * Whether a material can be protected.
     */
    public static boolean isLockableContainer(Material type) {
        ContainerRegistry registry = registry();
        return registry != null && registry.isLockable(type);
    }

    /**
     * Gets every block belonging to a container: one for a single container, two for a double chest.
     *
     * @param block one block of the container
     * @return the container's blocks, empty if the block is not a container
     */
    public static Set<Block> getContainerBlocks(Block block) {
        Set<Block> blocks = new HashSet<>();
        if (block == null || !isLockableContainer(block.getType())) {
            return blocks;
        }
        blocks.add(block);

        Block partner = getDoubleChestPartner(block);
        if (partner != null) {
            blocks.add(partner);
        }
        return blocks;
    }

    /**
     * Finds the other half of a double chest, if this block is one.
     *
     * <p>Reads only block data, never block state. {@code Block#getState()} on a chest builds a full
     * snapshot of the container including a copy of its inventory, which is far too expensive for code
     * that runs on every hopper transfer. Block data is just the placement information.</p>
     *
     * @param block one half of a potential double chest
     * @return the other half, or null if this is not a double chest
     */
    public static Block getDoubleChestPartner(Block block) {
        if (block == null) {
            return null;
        }

        BlockData blockData = block.getBlockData();
        if (!(blockData instanceof Chest)) {
            return null;
        }

        Chest chestData = (Chest) blockData;
        if (chestData.getType() == Chest.Type.SINGLE) {
            return null;
        }

        BlockFace otherHalfDirection = getOtherChestHalfDirection(chestData.getType(), chestData.getFacing());
        if (otherHalfDirection == null) {
            return null;
        }

        Block otherBlock = block.getRelative(otherHalfDirection);

        // The halves must be the same kind of chest, compared by family rather than by material. A
        // trapped chest never pairs with a normal one, but the two halves of a copper chest can be at
        // different oxidation stages and therefore be different materials while still being one
        // container.
        ContainerRegistry registry = registry();
        if (registry == null || !registry.isSameFamily(block.getType(), otherBlock.getType())) {
            return null;
        }

        BlockData otherData = otherBlock.getBlockData();
        return otherData instanceof Chest && ((Chest) otherData).getType() != Chest.Type.SINGLE
                ? otherBlock
                : null;
    }

    /**
     * Determines the direction of the other half of a double chest.
     *
     * @param type   the chest half, left or right
     * @param facing the direction the chest faces
     * @return the direction of the other half, or null if it cannot be determined
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
        }
        if (type == Chest.Type.RIGHT) {
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
     * The container a player is looking at, within {@link #TARGET_RANGE}.
     *
     * <p>Uses ray tracing rather than the deprecated {@code getTargetBlock}, which walked the line of
     * sight block by block and treated anything non-solid as a miss.</p>
     *
     * @return the container block, or null if the player is not looking at one
     */
    public static Block getTargetContainer(Player player) {
        if (player == null) {
            return null;
        }

        RayTraceResult result = player.rayTraceBlocks(TARGET_RANGE);
        if (result == null || result.getHitBlock() == null) {
            return null;
        }

        Block hit = result.getHitBlock();
        return isLockableContainer(hit.getType()) ? hit : null;
    }

    /**
     * Resolves the block or blocks backing an inventory.
     *
     * <p>Double chests are resolved through their two sides rather than through the inventory's
     * location, which sits between the halves and does not identify either of them reliably.</p>
     *
     * @param inventory the inventory to resolve
     * @return the backing blocks, empty if the inventory is not a placed container
     */
    public static Set<Block> getInventoryBlocks(Inventory inventory) {
        Set<Block> blocks = new HashSet<>();
        if (inventory == null) {
            return blocks;
        }

        InventoryHolder holder = inventory.getHolder();

        if (holder instanceof DoubleChest) {
            DoubleChest doubleChest = (DoubleChest) holder;
            addHolderBlock(blocks, doubleChest.getLeftSide());
            addHolderBlock(blocks, doubleChest.getRightSide());
            return blocks;
        }

        if (holder instanceof BlockState) {
            blocks.add(((BlockState) holder).getBlock());
            return blocks;
        }

        Location location = inventory.getLocation();
        if (location != null && location.getWorld() != null) {
            blocks.add(location.getBlock());
        }
        return blocks;
    }

    private static void addHolderBlock(Set<Block> blocks, InventoryHolder holder) {
        if (holder instanceof BlockState) {
            blocks.add(((BlockState) holder).getBlock());
        }
    }

    /**
     * Serializes a location to {@code world:x:y:z}.
     *
     * @return the serialized string, or null if the location or its world is null
     */
    public static String serializeLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return null;
        }
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }
}
