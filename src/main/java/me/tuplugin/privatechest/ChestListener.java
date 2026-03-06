package me.tuplugin.privatechest;

import java.util.Set;

import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;

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
        if (!ContainerUtils.isLockableContainer(clickedBlock.getType())) {
            return;
        }

        // Get all blocks of the container (handles double chests)
        Set<Block> containerBlocks = ContainerUtils.getContainerBlocks(clickedBlock);
        Block lockedBlock = null;

        // Check if ANY part of the container is locked
        for (Block part : containerBlocks) {
            if (chestLocker.isChestLocked(part)) {
                lockedBlock = part; // We found a locked part, use this for checks
                break;
            }
        }

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
}