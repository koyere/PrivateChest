package me.tuplugin.privatechest;

import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.block.BlockState;

/**
 * Prevents hoppers from interacting with any protected containers.
 */
public class HopperProtectionListener implements Listener {

    private final ChestLocker chestLocker;

    public HopperProtectionListener() {
        this.chestLocker = ChestLocker.getInstance();
    }

    @EventHandler
    public void onInventoryMove(InventoryMoveItemEvent event) {
        Inventory destination = event.getDestination();
        InventoryHolder holder = destination.getHolder();

        if (holder instanceof BlockState) {
            Block block = ((BlockState) holder).getBlock();

            // Cancel if the destination block is protected
            if (chestLocker.isChestLocked(block)) {
                event.setCancelled(true);
            }
        }
    }
}
