package me.tuplugin.privatechest;

import me.tuplugin.privatechest.storage.Protection;
import org.bukkit.block.Block;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Guards player access to protected containers.
 *
 * <p>Two handlers, on purpose. The interaction handler is what normally stops a player and produces
 * the message. The inventory handler is the safety net: it catches every other way a container can be
 * opened, including other plugins opening one on a player's behalf. 2.x only had the first one, so any
 * path that did not go through a right-click bypassed protection entirely.</p>
 */
public class ChestListener implements Listener {

    private final AccessGuard accessGuard;

    public ChestListener(PrivateChest plugin) {
        this.accessGuard = plugin.getAccessGuard();
    }

    /**
     * Blocks opening a protected container by right-clicking it.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChestInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // A right-click fires once per hand. Without this the player is checked twice and receives
        // every message twice.
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null || !accessGuard.mayHaveProtections(clickedBlock)) {
            return;
        }
        if (!ContainerUtils.isLockableContainer(clickedBlock.getType())) {
            return;
        }

        Player player = event.getPlayer();
        Protection protection = accessGuard.findProtection(clickedBlock);
        if (protection == null) {
            return;
        }

        AccessGuard.Access access = accessGuard.check(player, protection);
        if (access.isAllowed()) {
            accessGuard.notifyAccess(player, access, protection);
            return;
        }

        event.setCancelled(true);
        accessGuard.denyAccess(player, protection);
    }

    /**
     * Blocks any other route to a protected container's contents.
     *
     * <p>Deliberately silent about allowed access: the interaction handler has already sent whatever
     * notice applies, and this fires for the same action.</p>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        HumanEntity viewer = event.getPlayer();
        if (!(viewer instanceof Player)) {
            return;
        }

        Protection protection = accessGuard.findProtection(event.getInventory());
        if (protection == null) {
            return;
        }

        Player player = (Player) viewer;
        if (accessGuard.check(player, protection).isAllowed()) {
            return;
        }

        event.setCancelled(true);
        accessGuard.denyAccess(player, protection);
    }
}
