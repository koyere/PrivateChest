package me.tuplugin.privatechest;

import java.util.Iterator;
import java.util.Set;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

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

        if (!ContainerUtils.isLockableContainer(blockBeingBroken.getType())) return;

        // Get all blocks of the container (handles double chests)
        Set<Block> containerBlocks = ContainerUtils.getContainerBlocks(blockBeingBroken);
        Block lockedBlock = null;

        // Find if any part of the container is locked
        for (Block part : containerBlocks) {
            if (chestLocker.isChestLocked(part)) {
                lockedBlock = part;
                break;
            }
        }

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

    // --- Explosion Handlers: protect both halves of double chests ---
    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        Iterator<Block> it = event.blockList().iterator();
        while (it.hasNext()) {
            Block block = it.next();
            if (ContainerUtils.isLockableContainer(block.getType()) && isAnyPartLocked(block)) {
                it.remove();
            }
        }
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        Iterator<Block> it = event.blockList().iterator();
        while (it.hasNext()) {
            Block block = it.next();
            if (ContainerUtils.isLockableContainer(block.getType()) && isAnyPartLocked(block)) {
                it.remove();
            }
        }
    }

    /**
     * Checks if any part of a container (including the other half of a double chest) is locked.
     */
    private boolean isAnyPartLocked(Block block) {
        Set<Block> parts = ContainerUtils.getContainerBlocks(block);
        for (Block part : parts) {
            if (chestLocker.isChestLocked(part)) {
                return true;
            }
        }
        return false;
    }
}