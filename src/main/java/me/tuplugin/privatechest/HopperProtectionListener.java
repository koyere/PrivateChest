package me.tuplugin.privatechest;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashSet;
import java.util.Set;

/**
 * Enhanced protection listener that prevents all forms of automated item extraction
 * from protected containers. This includes hoppers, droppers, dispensers, and
 * prevents placement of these blocks near protected containers.
 * 
 * @since 2.1
 * @author PrivateChest Team
 */
public class HopperProtectionListener implements Listener {

    private final ChestLocker chestLocker;
    private final PrivateChest plugin;
    private final MessageManager messageManager;

    /**
     * Constructs a new HopperProtectionListener.
     * 
     * @param plugin The main plugin instance for accessing configuration and managers
     */
    public HopperProtectionListener(PrivateChest plugin) {
        this.plugin = plugin;
        this.chestLocker = ChestLocker.getInstance();
        this.messageManager = plugin.getMessageManager();
    }

    /**
     * Prevents automated item movement to or from protected containers.
     * This covers hoppers, droppers, dispensers, and any other automated systems.
     * 
     * @param event The inventory move event
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        // Check both the source and the destination inventory for protection
        boolean sourceProtected = isInventoryProtected(event.getSource());
        boolean destinationProtected = isInventoryProtected(event.getDestination());

        // Cancel the event if either inventory belongs to a protected container
        if (sourceProtected || destinationProtected) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevents placement of automated extraction blocks near protected containers.
     * This includes hoppers, droppers, and dispensers within a 2-block radius.
     * 
     * @param event The block place event
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block placedBlock = event.getBlock();
        Material placedType = placedBlock.getType();
        Player player = event.getPlayer();

        // Only check for automated extraction blocks
        if (!isAutomatedExtractionBlock(placedType)) {
            return;
        }

        // Allow admins to bypass placement restrictions
        if (player.hasPermission("privatechest.admin")) {
            return;
        }

        // Check for protected containers within interaction range
        if (hasProtectedContainersNearby(placedBlock, getExtractionRange(placedType))) {
            event.setCancelled(true);
            player.sendMessage(messageManager.get("automated_block_placement_denied")
                    .replace("{block}", getBlockDisplayName(placedType)));
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

    /**
     * Checks if a material represents a lockable container type.
     * Supports chests, trapped chests, barrels, and all shulker box variants.
     * 
     * @param type The material to check
     * @return true if the material is a lockable container
     */
    private boolean isLockableContainer(Material type) {
        // Standard containers
        if (type == Material.CHEST || type == Material.TRAPPED_CHEST || type == Material.BARREL) {
            return true;
        }
        
        // All shulker box variants (handles future additions automatically)
        String typeName = type.name();
        return typeName.contains("SHULKER_BOX");
    }

    /**
     * Checks if a material represents an automated extraction block.
     * 
     * @param type The material to check
     * @return true if the material can automatically extract items
     */
    private boolean isAutomatedExtractionBlock(Material type) {
        return type == Material.HOPPER || type == Material.DROPPER || type == Material.DISPENSER;
    }

    /**
     * Gets the extraction range for different automated blocks.
     * 
     * @param type The automated block type
     * @return The range in blocks that this type can extract from
     */
    private int getExtractionRange(Material type) {
        switch (type) {
            case HOPPER:
                return 1; // Hoppers extract from directly above and can be placed below
            case DROPPER:
            case DISPENSER:
                return 1; // Droppers and dispensers interact with containers they face
            default:
                return 1;
        }
    }

    /**
     * Checks if there are any protected containers within the specified range of a block.
     * 
     * @param centerBlock The block to check around
     * @param range The range in blocks to check
     * @return true if any protected containers are found within range
     */
    private boolean hasProtectedContainersNearby(Block centerBlock, int range) {
        Location centerLoc = centerBlock.getLocation();
        
        // Check all blocks within the specified range
        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    Location checkLoc = centerLoc.clone().add(x, y, z);
                    Block checkBlock = checkLoc.getBlock();
                    
                    // Skip the center block itself
                    if (checkBlock.equals(centerBlock)) {
                        continue;
                    }
                    
                    // Check if this block is a protected container
                    if (isLockableContainer(checkBlock.getType()) && chestLocker.isChestLocked(checkBlock)) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }

    /**
     * Gets a user-friendly display name for a block type.
     * 
     * @param type The material type
     * @return A formatted display name
     */
    private String getBlockDisplayName(Material type) {
        String name = type.name().toLowerCase().replace("_", " ");
        // Capitalize first letter of each word
        String[] words = name.split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.length() > 0) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1));
                }
                result.append(" ");
            }
        }
        return result.toString().trim();
    }
}