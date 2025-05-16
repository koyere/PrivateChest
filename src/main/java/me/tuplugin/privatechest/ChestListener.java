package me.tuplugin.privatechest;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;

public class ChestListener implements Listener {

    private final PrivateChest plugin;
    private final ChestLocker chestLocker;
    private final MessageManager messages;
    private final FileConfiguration config;

    public ChestListener(PrivateChest plugin) {
        this.plugin = plugin;
        this.chestLocker = ChestLocker.getInstance();
        this.messages = plugin.getMessageManager();
        this.config = plugin.getConfig();
    }

    @EventHandler
    public void onChestInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null || event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        Player player = event.getPlayer();

        // Check if block is a chest or container
        if (!isLockableContainer(block)) return;

        // Check if chest is locked
        if (!chestLocker.isChestLocked(block)) return;

        // Allow if player is the owner
        if (chestLocker.isOwner(block, player)) {
            if (config.getBoolean("notify-owner-on-open", true)) {
                player.sendMessage(messages.get("owner_access_notice"));
            }
            return;
        }

        // ✅ Allow access if player has admin bypass permission
        if (player.hasPermission("privatechest.admin")) {
            if (config.getBoolean("notify-admin-on-open", true)) {
                player.sendMessage(messages.get("admin_access_notice"));
            }
            return;
        }

        // ❌ Deny access if not owner or admin
        event.setCancelled(true);
        player.sendMessage(messages.get("not_your_chest"));
    }

    private boolean isLockableContainer(Block block) {
        Material type = block.getType();
        return type == Material.CHEST || type == Material.TRAPPED_CHEST || type == Material.BARREL;
    }
}
