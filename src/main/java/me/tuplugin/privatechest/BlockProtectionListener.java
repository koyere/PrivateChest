package me.tuplugin.privatechest;

import me.tuplugin.privatechest.storage.Protection;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.Iterator;
import java.util.List;

/**
 * Protects locked containers from being destroyed or moved.
 *
 * <p>Three protection gaps from 2.x are closed here.</p>
 * <ul>
 *     <li><b>Pistons.</b> Barrels and shulker boxes can be pushed by a piston. Nothing stopped that,
 *     so a griefer could shove a locked barrel one block sideways: the record stayed at the old
 *     coordinates and the container came to rest unprotected and open to anyone.</li>
 *     <li><b>Fire.</b> Nothing stopped a protected container from burning away.</li>
 *     <li><b>Entities changing blocks.</b> Nothing stopped a mob from removing one.</li>
 * </ul>
 */
public class BlockProtectionListener implements Listener {

    private final PrivateChest plugin;
    private final AccessGuard accessGuard;
    private final ChestLocker chestLocker;
    private final MessageManager messages;

    public BlockProtectionListener(PrivateChest plugin) {
        this.plugin = plugin;
        this.accessGuard = plugin.getAccessGuard();
        this.chestLocker = plugin.getChestLocker();
        this.messages = plugin.getMessageManager();
    }

    /**
     * Stops players from breaking containers they have no claim to.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChestBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!accessGuard.mayHaveProtections(block) || !ContainerUtils.isLockableContainer(block.getType())) {
            return;
        }

        Protection protection = accessGuard.findProtection(block);
        if (protection == null) {
            return;
        }

        Player player = event.getPlayer();
        AccessGuard.Access access = accessGuard.check(player, protection);

        // Being trusted grants access to the contents, not permission to destroy the container.
        if (!access.canRemove()) {
            event.setCancelled(true);
            accessGuard.denyAccess(player, protection);
            return;
        }

        String message = access == AccessGuard.Access.OWNER
                ? messages.get("chest_break_warning")
                : messages.get("admin_chest_break_notice");

        messages.send(player, message
                .replace("{container}", accessGuard.describe(protection))
                .replace("{owner}", accessGuard.ownerName(protection)));

        // Only the record at the broken position is cleared. The other half of a double chest keeps
        // its own protection, instead of being silently unlocked along with it.
        chestLocker.removeProtection(block);
    }

    // --- Explosions ---

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        removeProtectedBlocks(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        removeProtectedBlocks(event.blockList());
    }

    /**
     * Drops protected containers from an explosion's affected block list.
     */
    private void removeProtectedBlocks(List<Block> blocks) {
        if (blocks.isEmpty() || plugin.getProtectionStore().isEmpty()) {
            return;
        }

        Iterator<Block> iterator = blocks.iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            if (!ContainerUtils.isLockableContainer(block.getType())) {
                continue;
            }
            if (accessGuard.mayHaveProtections(block) && accessGuard.isProtected(block)) {
                iterator.remove();
            }
        }
    }

    // --- Pistons ---

    /**
     * Stops a piston from pushing a protected container out from under its protection.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (containsProtectedContainer(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    /**
     * Stops a sticky piston from pulling a protected container away.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (containsProtectedContainer(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    private boolean containsProtectedContainer(List<Block> blocks) {
        if (blocks.isEmpty() || plugin.getProtectionStore().isEmpty()) {
            return false;
        }

        for (Block block : blocks) {
            if (!ContainerUtils.isLockableContainer(block.getType())) {
                continue;
            }
            if (accessGuard.mayHaveProtections(block) && accessGuard.isProtected(block)) {
                return true;
            }
        }
        return false;
    }

    // --- Environment ---

    /**
     * Stops a protected container from burning away.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (isProtectedContainer(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /**
     * Stops mobs and other entities from removing a protected container.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (isProtectedContainer(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    private boolean isProtectedContainer(Block block) {
        if (!ContainerUtils.isLockableContainer(block.getType())) {
            return false;
        }
        return accessGuard.mayHaveProtections(block) && accessGuard.isProtected(block);
    }
}
