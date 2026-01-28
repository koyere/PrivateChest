package me.tuplugin.privatechest;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Chest;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * Handles sign-based chest protection.
 * Allows players to lock chests by placing signs with [Private] on them.
 */
public class SignProtectionListener implements Listener {

    private final PrivateChest plugin;
    private final ChestLocker chestLocker;
    private final LimitManager limitManager;
    private final MessageManager messages;

    // Constants for sign protection
    private static final String PRIVATE_SIGN_TEXT = "[Private]";
    private static final String PRIVATE_SIGN_FORMATTED = "§4[§cPrivate§4]";

    public SignProtectionListener(PrivateChest plugin) {
        this.plugin = plugin;
        this.chestLocker = ChestLocker.getInstance();
        this.limitManager = LimitManager.getInstance();
        this.messages = plugin.getMessageManager();
    }

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        // Check if sign protection is enabled
        if (!plugin.getConfig().getBoolean("enable-sign-protection", true)) {
            return;
        }

        Player player = event.getPlayer();
        String[] lines = event.getLines();

        // Check if first line is [Private]
        if (lines[0] == null || !lines[0].equalsIgnoreCase(PRIVATE_SIGN_TEXT)) {
            return;
        }

        // Check permission
        if (!player.hasPermission("privatechest.use")) {
            player.sendMessage(messages.raw("no_permission"));
            event.setCancelled(true);
            return;
        }

        // Find associated chest
        Block signBlock = event.getBlock();
        Set<Block> chestBlocks = findNearbyChests(signBlock);

        if (chestBlocks.isEmpty()) {
            player.sendMessage(messages.get("sign_no_chest_found"));
            event.setCancelled(true);
            return;
        }

        // Check if any of the chests are already locked
        for (Block chestBlock : chestBlocks) {
            if (chestLocker.isChestLocked(chestBlock)) {
                player.sendMessage(messages.get("sign_chest_already_locked"));
                event.setCancelled(true);
                return;
            }
        }

        // Check if player owns all nearby chests (if any are locked by others)
        for (Block chestBlock : chestBlocks) {
            if (chestLocker.isChestLocked(chestBlock) &&
                    !chestLocker.isOwner(chestBlock, player) &&
                    !player.hasPermission("privatechest.admin")) {
                player.sendMessage(messages.get("sign_not_your_chest"));
                event.setCancelled(true);
                return;
            }
        }

        // Check chest limits (only if not admin)
        if (!player.hasPermission("privatechest.admin") && limitManager.areLimitsEnabled()) {
            // Count how many new chests would be locked
            int newChestsToLock = 0;
            for (Block chestBlock : chestBlocks) {
                if (!chestLocker.isChestLocked(chestBlock)) {
                    newChestsToLock++;
                }
            }

            if (newChestsToLock > 0 && !limitManager.canPlayerLockAdditional(player, newChestsToLock)) {
                int limit = limitManager.getPlayerLimit(player);
                int current = limitManager.getPlayerChestCount(player);

                player.sendMessage(messages.get("sign_limit_exceeded")
                        .replace("{current}", String.valueOf(current))
                        .replace("{limit}", String.valueOf(limit))
                        .replace("{trying}", String.valueOf(newChestsToLock)));
                event.setCancelled(true);
                return;
            }
        }

        // Generate a default password for sign-based protection
        String defaultPassword = generateSignPassword(player, signBlock);

        // Lock all associated chests
        boolean anyLocked = false;
        for (Block chestBlock : chestBlocks) {
            if (chestLocker.lockChest(chestBlock, player, defaultPassword)) {
                anyLocked = true;
            }
        }

        if (anyLocked) {
            // Save data
            plugin.getDataManager().saveData();

            // Format the sign
            event.setLine(0, PRIVATE_SIGN_FORMATTED);
            event.setLine(1, player.getName());
            event.setLine(2, ""); // Empty line
            event.setLine(3, ""); // Empty line

            player.sendMessage(messages.get("sign_chest_locked"));

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
            player.sendMessage(messages.get("sign_lock_failed"));
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSignBreak(BlockBreakEvent event) {
        // Check if sign protection is enabled
        if (!plugin.getConfig().getBoolean("enable-sign-protection", true)) {
            return;
        }

        Block block = event.getBlock();
        Player player = event.getPlayer();

        // Check if it's a sign
        if (!isSign(block.getType())) {
            return;
        }

        BlockState state = block.getState();
        if (!(state instanceof Sign)) {
            return;
        }

        Sign sign = (Sign) state;
        String[] lines = sign.getLines();

        // Check if it's a private sign
        if (lines[0] == null || !lines[0].contains("Private")) {
            return;
        }

        // Find associated chests
        Set<Block> chestBlocks = findNearbyChests(block);
        if (chestBlocks.isEmpty()) {
            return;
        }

        // Check if any chest is locked and if player can break the sign
        Block lockedChest = null;
        for (Block chestBlock : chestBlocks) {
            if (chestLocker.isChestLocked(chestBlock)) {
                lockedChest = chestBlock;
                break;
            }
        }

        if (lockedChest == null) {
            return; // No locked chest, allow sign break
        }

        // Check ownership or admin permission
        if (!chestLocker.isOwner(lockedChest, player) && !player.hasPermission("privatechest.admin")) {
            event.setCancelled(true);
            player.sendMessage(messages.get("sign_cannot_break"));
            return;
        }

        // Allow break and unlock the associated chests
        for (Block chestBlock : chestBlocks) {
            if (chestLocker.isChestLocked(chestBlock)) {
                chestLocker.removeProtection(chestBlock);
            }
        }

        // Save data
        plugin.getDataManager().saveData();

        // Send confirmation
        if (chestLocker.isOwner(lockedChest, player)) {
            player.sendMessage(messages.get("sign_chest_unlocked"));
        } else {
            player.sendMessage(messages.get("admin_sign_break_notice"));
        }
    }

    /**
     * Finds chests near a sign block.
     */
    private Set<Block> findNearbyChests(Block signBlock) {
        Set<Block> chestBlocks = new HashSet<>();

        // Check block the sign is attached to (for wall signs)
        if (signBlock.getBlockData() instanceof WallSign) {
            WallSign wallSign = (WallSign) signBlock.getBlockData();
            BlockFace facing = wallSign.getFacing().getOppositeFace();
            Block attachedBlock = signBlock.getRelative(facing);

            if (isLockableContainer(attachedBlock.getType())) {
                chestBlocks.addAll(getContainerBlocks(attachedBlock));
            }
        }

        // Check block below (for signs on top of chests)
        Block blockBelow = signBlock.getRelative(BlockFace.DOWN);
        if (isLockableContainer(blockBelow.getType())) {
            chestBlocks.addAll(getContainerBlocks(blockBelow));
        }

        // Check adjacent blocks (in all 4 horizontal directions)
        BlockFace[] horizontalFaces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
        for (BlockFace face : horizontalFaces) {
            Block adjacent = signBlock.getRelative(face);
            if (isLockableContainer(adjacent.getType())) {
                chestBlocks.addAll(getContainerBlocks(adjacent));
            }
        }

        return chestBlocks;
    }

    /**
     * Gets all blocks belonging to a container (handles double chests).
     */
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

    /**
     * Helper method for double chest logic.
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
     * Generates a unique password for sign-based protection.
     */
    private String generateSignPassword(Player player, Block signBlock) {
        // Create a password based on player UUID and sign location
        String base = player.getUniqueId().toString() + "_" +
                signBlock.getX() + "_" + signBlock.getY() + "_" + signBlock.getZ();
        return "sign_" + Integer.toHexString(base.hashCode());
    }

    /**
     * Checks if a material is a sign.
     */
    private boolean isSign(Material material) {
        return material.name().contains("SIGN");
    }

    /**
     * Checks if a material is a lockable container.
     */
    private boolean isLockableContainer(Material type) {
        if (type == Material.CHEST || type == Material.TRAPPED_CHEST || type == Material.BARREL) {
            return true;
        }
        // Support all shulker box variants
        return type.name().contains("SHULKER_BOX");
    }
}