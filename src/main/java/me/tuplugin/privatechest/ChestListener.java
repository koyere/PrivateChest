package me.tuplugin.privatechest;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace; // Importado
import org.bukkit.block.BlockState; // Importado
import org.bukkit.block.data.BlockData; // Importado
import org.bukkit.block.data.type.Chest; // Importado
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.HashSet;
import java.util.Set; // Importado

public class ChestListener implements Listener {

    private final PrivateChest plugin;
    private final ChestLocker chestLocker;
    private final TrustManager trustManager;
    private final MessageManager messages;
    private final FileConfiguration config;

    public ChestListener(PrivateChest plugin) {
        this.plugin = plugin;
        this.chestLocker = ChestLocker.getInstance();
        this.trustManager = TrustManager.getInstance();
        this.messages = plugin.getMessageManager();
        this.config = plugin.getConfig();
    }

    @EventHandler
    public void onChestInteract(PlayerInteractEvent event) {
        // Only handle right-clicks on blocks
        if (event.getClickedBlock() == null || event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        Player player = event.getPlayer();

        // Ensure it's a lockable container
        if (!isLockableContainer(clickedBlock.getType())) {
            return;
        }

        // --- INICIO: Lógica de Cofre Doble ---
        Set<Block> containerBlocks = getContainerBlocks(clickedBlock);
        Block lockedBlock = null;

        // Check if ANY part of the container is locked
        for (Block part : containerBlocks) {
            if (chestLocker.isChestLocked(part)) {
                lockedBlock = part; // We found a locked part, use this for checks
                break;
            }
        }
        // --- FIN: Lógica de Cofre Doble ---

        // If no part is locked, allow access (do nothing)
        if (lockedBlock == null) {
            return;
        }

        // If we reach here, at least one part is locked.
        // Now we check ownership, trust, or admin bypass using the 'lockedBlock'

        // Allow if player is the owner
        if (chestLocker.isOwner(lockedBlock, player)) {
            if (config.getBoolean("notify-owner-on-open", true)) {
                player.sendMessage(messages.get("owner_access_notice"));
            }
            return; // Allow access
        }

        // Allow access if player has admin bypass permission
        if (player.hasPermission("privatechest.admin")) {
            if (config.getBoolean("notify-admin-on-open", true)) {
                player.sendMessage(messages.get("admin_access_notice"));
            }
            return; // Allow access
        }

        // Check if player is trusted by the owner
        String ownerUUID = chestLocker.getOwnerUUID(lockedBlock);
        if (ownerUUID != null && trustManager.isTrusted(ownerUUID, player.getUniqueId().toString())) {
            if (config.getBoolean("notify-trusted-on-open", true)) {
                player.sendMessage(messages.get("trusted_access_notice"));
            }
            return; // Allow access
        }

        // If not owner, not admin, and not trusted, deny access
        event.setCancelled(true);
        player.sendMessage(messages.get("not_your_chest"));
    }

    /**
     * Gets all blocks belonging to a container (1 for single, 2 for double).
     * Uses BlockData approach.
     * @param block One block of the container.
     * @return A Set containing all blocks of the container.
     */
    private Set<Block> getContainerBlocks(Block block) {
        Set<Block> blocks = new HashSet<>();
        blocks.add(block); // Always add the clicked block

        BlockState state = block.getState();
        BlockData blockData = block.getBlockData();

        // Check if it's a Chest and has Chest BlockData
        if (state instanceof org.bukkit.block.Chest && blockData instanceof Chest) {
            Chest chestData = (Chest) blockData;
            Chest.Type chestType = chestData.getType();

            // If it's LEFT or RIGHT, find the other half
            if (chestType != Chest.Type.SINGLE) {
                BlockFace facing = chestData.getFacing();
                BlockFace otherHalfDirection = getOtherChestHalfDirection(chestType, facing);

                if (otherHalfDirection != null) {
                    Block otherBlock = block.getRelative(otherHalfDirection);
                    // Add the other half if it's also a Chest
                    if (otherBlock.getState() instanceof org.bukkit.block.Chest) {
                        blocks.add(otherBlock);
                    }
                }
            }
        }
        return blocks;
    }

    /**
     * Helper method to determine the direction of the other half of a double chest.
     * (Identical to the one in LockCommand)
     */
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
     * Checks if a material type is a lockable container.
     */
    private boolean isLockableContainer(Material type) {
        if (type == Material.CHEST || type == Material.TRAPPED_CHEST || type == Material.BARREL) {
            return true;
        }
        // Support all shulker box variants
        return type.name().contains("SHULKER_BOX");
    }
}