package me.tuplugin.privatechest;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.Iterator;

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
        Block block = event.getBlock();
        Player player = event.getPlayer();

        if (!isLockableContainer(block)) return;
        if (!chestLocker.isChestLocked(block)) return;

        if (!chestLocker.isOwner(block, player)) {
            event.setCancelled(true);
            player.sendMessage(messages.get("not_your_chest"));
        } else {
            player.sendMessage(messages.get("chest_break_warning"));
            chestLocker.removeProtection(block);
            plugin.getDataManager().saveData();
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        Iterator<Block> it = event.blockList().iterator();
        while (it.hasNext()) {
            Block block = it.next();
            if (isLockableContainer(block) && chestLocker.isChestLocked(block)) {
                it.remove();
            }
        }
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        Iterator<Block> it = event.blockList().iterator();
        while (it.hasNext()) {
            Block block = it.next();
            if (isLockableContainer(block) && chestLocker.isChestLocked(block)) {
                it.remove();
            }
        }
    }

    private boolean isLockableContainer(Block block) {
        Material type = block.getType();
        return type == Material.CHEST || type == Material.TRAPPED_CHEST;
    }
}

