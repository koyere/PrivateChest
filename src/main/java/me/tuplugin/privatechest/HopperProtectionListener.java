package me.tuplugin.privatechest;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;

/**
 * Stops automated systems from moving items in or out of protected containers.
 *
 * <p>Covers hoppers, droppers, dispensers, hopper minecarts and anything else that produces an item
 * transfer, and prevents those blocks from being placed next to a protected container in the first
 * place.</p>
 *
 * <p>Performance matters more here than anywhere else in the plugin: {@link InventoryMoveItemEvent}
 * fires for every item transfer on the entire server. The 2.x implementation called
 * {@code Block#getState()} on both the source and destination of every single transfer, which builds a
 * full snapshot of the container including a copy of its inventory. That was the plugin's largest
 * per-tick cost. It now starts with one lookup in the protection index, so a transfer with nothing
 * protected in the chunk costs almost nothing.</p>
 *
 * @since 2.1, reworked in 3.0
 */
public class HopperProtectionListener implements Listener {

    private final PrivateChest plugin;
    private final AccessGuard accessGuard;
    private final MessageManager messages;

    public HopperProtectionListener(PrivateChest plugin) {
        this.plugin = plugin;
        this.accessGuard = plugin.getAccessGuard();
        this.messages = plugin.getMessageManager();
    }

    /**
     * Whether the server owner has chosen to let hoppers reach locked containers.
     */
    private boolean isHopperAccessAllowed() {
        return plugin.getConfig().getBoolean("hopper-protection.allow-hopper-access", false);
    }

    /**
     * Blocks automated item movement to or from a protected container.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (plugin.getProtectionStore().isEmpty() || isHopperAccessAllowed()) {
            return;
        }

        if (accessGuard.findProtection(event.getSource()) != null
                || accessGuard.findProtection(event.getDestination()) != null) {
            event.setCancelled(true);
        }
    }

    /**
     * Blocks a hopper or hopper minecart from vacuuming items into a protected container.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryPickup(InventoryPickupItemEvent event) {
        if (plugin.getProtectionStore().isEmpty() || isHopperAccessAllowed()) {
            return;
        }

        if (accessGuard.findProtection(event.getInventory()) != null) {
            event.setCancelled(true);
        }
    }

    /**
     * Stops players from placing automated extraction blocks against someone else's container.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block placedBlock = event.getBlock();
        Material placedType = placedBlock.getType();

        if (!isAutomatedExtractionBlock(placedType)) {
            return;
        }
        if (plugin.getProtectionStore().isEmpty() || isHopperAccessAllowed()) {
            return;
        }

        Player player = event.getPlayer();
        if (player.hasPermission("privatechest.admin")) {
            return;
        }

        if (findInaccessibleContainerNearby(placedBlock, player) != null) {
            event.setCancelled(true);
            messages.send(player, messages.get("automated_block_placement_denied")
                    .replace("{block}", getBlockDisplayName(placedType)));
        }
    }

    /**
     * Looks for a container the player may not use within reach of the placed block.
     *
     * <p>Containers the player owns, is trusted on, or can bypass are ignored, so players can still
     * automate their own storage and storage they have been given access to. 2.x refused the placement
     * next to any locked container, including the player's own.</p>
     */
    private Block findInaccessibleContainerNearby(Block centerBlock, Player player) {
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }

                    Block candidate = centerBlock.getRelative(x, y, z);
                    if (!ContainerUtils.isLockableContainer(candidate.getType())) {
                        continue;
                    }
                    if (!accessGuard.mayHaveProtections(candidate)) {
                        continue;
                    }
                    if (!accessGuard.check(player, candidate).isAllowed()) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private boolean isAutomatedExtractionBlock(Material type) {
        return type == Material.HOPPER || type == Material.DROPPER || type == Material.DISPENSER;
    }

    /**
     * A readable name for a block type, used in the denial message.
     */
    private String getBlockDisplayName(Material type) {
        String translated = messages.rawOrNull("block_name_" + type.name().toLowerCase());
        if (translated != null) {
            return translated;
        }

        // No translation configured: fall back to a tidied version of the material name.
        String name = type.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
