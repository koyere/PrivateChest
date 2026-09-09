package me.tuplugin.privatechest;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;

import me.tuplugin.privatechest.container.ContainerFamily;
import me.tuplugin.privatechest.util.SignCompat;

/**
 * Sign-based container protection.
 *
 * <p>Writing {@code [Private]} on a sign next to a container locks it, and breaking that sign unlocks
 * it again. This path shares its limit checking with {@code /lockchest} as of 3.0, so the two can no
 * longer disagree about what a player is allowed to lock.</p>
 */
public class SignProtectionListener implements Listener {

    /** The tag a player writes to lock a container. */
    private static final String PRIVATE_SIGN_TEXT = "[Private]";

    /** How the tag is rewritten once the container is locked. */
    private static final String PRIVATE_SIGN_FORMATTED = "\u00A74[\u00A7cPrivate\u00A74]";

    private final PrivateChest plugin;
    private final ChestLocker chestLocker;
    private final LimitManager limitManager;
    private final MessageManager messages;

    public SignProtectionListener(PrivateChest plugin) {
        this.plugin = plugin;
        this.chestLocker = plugin.getChestLocker();
        this.limitManager = plugin.getLimitManager();
        this.messages = plugin.getMessageManager();
    }

    /**
     * Locks the nearby container when a player writes the tag on a sign.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        if (!plugin.getConfig().getBoolean("enable-sign-protection", true)) {
            return;
        }

        String[] lines = event.getLines();
        if (lines.length == 0 || lines[0] == null) {
            return;
        }
        if (!SignCompat.stripFormatting(lines[0]).equals(SignCompat.stripFormatting(PRIVATE_SIGN_TEXT))) {
            return;
        }

        Player player = event.getPlayer();

        // Sign protection has its own permission, so blocking /lockchest does not block signs.
        if (!player.hasPermission("privatechest.sign")) {
            messages.send(player, messages.raw("no_permission"));
            event.setCancelled(true);
            return;
        }

        Block signBlock = event.getBlock();
        Set<Block> chestBlocks = findNearbyChests(signBlock);

        if (chestBlocks.isEmpty()) {
            messages.send(player, messages.get("sign_no_chest_found"));
            event.setCancelled(true);
            return;
        }

        // Count what would actually be locked, and remember one block so the container type is known.
        int newChestsToLock = 0;
        Block firstChest = null;
        for (Block chestBlock : chestBlocks) {
            if (chestLocker.isChestLocked(chestBlock)) {
                messages.send(player, messages.get("sign_chest_already_locked"));
                event.setCancelled(true);
                return;
            }
            newChestsToLock++;
            if (firstChest == null) {
                firstChest = chestBlock;
            }
        }

        // Same limit check as /lockchest, so the two paths cannot disagree.
        if (firstChest != null) {
            LimitManager.LimitCheck limitCheck =
                    limitManager.checkCanLock(player, firstChest.getType(), newChestsToLock);
            if (!limitCheck.isAllowed()) {
                messages.send(player, limitManager.getSignDeniedMessage(limitCheck));
                event.setCancelled(true);
                return;
            }
        }

        String password = generateSignPassword();

        boolean anyLocked = false;
        for (Block chestBlock : chestBlocks) {
            if (chestLocker.lockChest(chestBlock, player, password)) {
                anyLocked = true;
            }
        }

        if (!anyLocked) {
            messages.send(player, messages.get("sign_lock_failed"));
            event.setCancelled(true);
            return;
        }

        event.setLine(0, PRIVATE_SIGN_FORMATTED);
        event.setLine(1, player.getName());
        event.setLine(2, "");
        event.setLine(3, "");

        ContainerFamily family = firstChest == null
                ? null
                : plugin.getContainerRegistry().getFamily(firstChest.getType());

        messages.send(player, messages.get("sign_chest_locked")
                .replace("{container}", limitManager.familyName(family)));

        String warningMessage = limitManager.getLimitWarningMessage(player, family);
        if (warningMessage != null) {
            messages.send(player, warningMessage);
        }

        if (limitManager.areLimitsEnabled() && !player.hasPermission("privatechest.admin")) {
            messages.send(player, messages.get("limit_status")
                    .replace("{status}", limitManager.getLimitStatus(player, family))
                    .replace("{container}", limitManager.familyName(family)));
        }
    }

    /**
     * Unlocks the container when its owner or an admin breaks the sign.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignBreak(BlockBreakEvent event) {
        if (!plugin.getConfig().getBoolean("enable-sign-protection", true)) {
            return;
        }

        Block block = event.getBlock();
        Player player = event.getPlayer();

        if (!SignCompat.isSign(block.getType())) {
            return;
        }

        BlockState state = block.getState();
        if (!(state instanceof Sign)) {
            return;
        }

        // Checks both sides of the sign. Signs have had a writable back since Minecraft 1.20, and
        // reading only the front meant a tag written on the back was invisible to the plugin.
        if (!SignCompat.hasTag((Sign) state, PRIVATE_SIGN_TEXT)
                && !SignCompat.hasTag((Sign) state, PRIVATE_SIGN_FORMATTED)) {
            return;
        }

        Set<Block> chestBlocks = findNearbyChests(block);
        if (chestBlocks.isEmpty()) {
            return;
        }

        Block lockedChest = null;
        for (Block chestBlock : chestBlocks) {
            if (chestLocker.isChestLocked(chestBlock)) {
                lockedChest = chestBlock;
                break;
            }
        }

        if (lockedChest == null) {
            return;
        }

        if (!chestLocker.isOwner(lockedChest, player) && !player.hasPermission("privatechest.admin")) {
            event.setCancelled(true);
            messages.send(player, messages.get("sign_cannot_break"));
            return;
        }

        // Ownership is captured before the records are removed. Checking afterwards always reported
        // "not the owner", so owners were shown the admin notice when breaking their own sign.
        boolean wasOwner = chestLocker.isOwner(lockedChest, player);

        for (Block chestBlock : chestBlocks) {
            chestLocker.removeProtection(chestBlock);
        }

        messages.send(player, wasOwner
                ? messages.get("sign_chest_unlocked")
                : messages.get("admin_sign_break_notice"));
    }

    /**
     * Finds the containers a sign could be protecting: the block it is attached to, the block below,
     * and the four horizontal neighbours.
     */
    private Set<Block> findNearbyChests(Block signBlock) {
        Set<Block> chestBlocks = new HashSet<>();

        if (signBlock.getBlockData() instanceof WallSign) {
            WallSign wallSign = (WallSign) signBlock.getBlockData();
            Block attachedBlock = signBlock.getRelative(wallSign.getFacing().getOppositeFace());
            addContainer(chestBlocks, attachedBlock);
        }

        addContainer(chestBlocks, signBlock.getRelative(BlockFace.DOWN));

        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            addContainer(chestBlocks, signBlock.getRelative(face));
        }

        return chestBlocks;
    }

    private void addContainer(Set<Block> target, Block candidate) {
        if (ContainerUtils.isLockableContainer(candidate.getType())) {
            target.addAll(ContainerUtils.getContainerBlocks(candidate));
        }
    }

    /**
     * Generates an unguessable password for a sign-locked container.
     *
     * <p>Nobody ever types this. Sign protection identifies the owner by UUID, and the password only
     * exists because every protection record carries one.</p>
     */
    private String generateSignPassword() {
        java.security.SecureRandom random = new java.security.SecureRandom();
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);

        StringBuilder sb = new StringBuilder("sign_");
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
