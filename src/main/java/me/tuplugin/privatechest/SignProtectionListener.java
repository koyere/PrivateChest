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
        if (lines.length == 0 || lines[0] == null || !isPrivateTag(lines[0])) {
            return;
        }

        Player player = event.getPlayer();

        // Sign protection has its own permission, so blocking /lockchest does not block signs.
        if (!player.hasPermission("privatechest.sign")) {
            messages.sendRaw(player, "no_permission");
            event.setCancelled(true);
            return;
        }

        Block signBlock = event.getBlock();

        if (!plugin.getWorldPolicy().allowsLocking(player, signBlock.getWorld())) {
            messages.send(player, "world_locking_disabled");
            event.setCancelled(true);
            return;
        }

        Set<Block> chestBlocks = findNearbyChests(signBlock);

        if (chestBlocks.isEmpty()) {
            messages.send(player, "sign_no_chest_found");
            event.setCancelled(true);
            return;
        }

        // Count what would actually be locked, and remember one block so the container type is known.
        int newChestsToLock = 0;
        Block firstChest = null;
        for (Block chestBlock : chestBlocks) {
            if (chestLocker.isChestLocked(chestBlock)) {
                messages.send(player, "sign_chest_already_locked");
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
                messages.sendText(player, limitManager.getSignDeniedMessage(limitCheck));
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
            messages.send(player, "sign_lock_failed");
            event.setCancelled(true);
            return;
        }

        event.setLine(0, messages.raw("sign_tag_locked"));
        event.setLine(1, player.getName());
        event.setLine(2, "");
        event.setLine(3, "");

        ContainerFamily family = firstChest == null
                ? null
                : plugin.getContainerRegistry().getFamily(firstChest.getType());

        messages.send(player, "sign_chest_locked", "container", limitManager.familyName(family));

        String warningMessage = limitManager.getLimitWarningMessage(player, family);
        if (warningMessage != null) {
            messages.sendText(player, warningMessage);
        }

        if (limitManager.areLimitsEnabled() && !player.hasPermission("privatechest.admin")) {
            messages.send(player, "limit_status",
                    "status", limitManager.getLimitStatus(player, family),
                    "container", limitManager.familyName(family));
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
        if (!hasPrivateTag((Sign) state)) {
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
            messages.send(player, "sign_cannot_break");
            return;
        }

        // Ownership is captured before the records are removed. Checking afterwards always reported
        // "not the owner", so owners were shown the admin notice when breaking their own sign.
        boolean wasOwner = chestLocker.isOwner(lockedChest, player);

        for (Block chestBlock : chestBlocks) {
            chestLocker.removeProtection(chestBlock);
        }

        messages.send(player, wasOwner ? "sign_chest_unlocked" : "admin_sign_break_notice");
    }

    /**
     * Every spelling of the tag that this server accepts.
     *
     * <p>Read from the message files, so the tag is part of the translation rather than a constant in
     * the code. A Spanish server can use {@code [Privado]}. Aliases let a server change language
     * without invalidating the signs its players already placed, and the locked form is included so an
     * already-formatted sign is still recognised.</p>
     */
    private Set<String> acceptedTags() {
        Set<String> tags = new HashSet<>();

        addTag(tags, messages.rawOrNull("sign_tag"));
        addTag(tags, messages.rawOrNull("sign_tag_locked"));
        for (String alias : messages.rawList("sign_tag_aliases")) {
            addTag(tags, alias);
        }

        // Never leave a server with no way to lock by sign, whatever the message file says.
        if (tags.isEmpty()) {
            addTag(tags, "[Private]");
        }
        return tags;
    }

    private void addTag(Set<String> tags, String tag) {
        if (tag != null) {
            String normalized = SignCompat.stripFormatting(tag);
            if (!normalized.isEmpty()) {
                tags.add(normalized);
            }
        }
    }

    /**
     * Whether a line is the tag, ignoring case, colour codes and surrounding spaces.
     */
    private boolean isPrivateTag(String line) {
        return acceptedTags().contains(SignCompat.stripFormatting(line));
    }

    /**
     * Whether either side of a sign carries the tag.
     */
    private boolean hasPrivateTag(Sign sign) {
        Set<String> tags = acceptedTags();
        for (String line : SignCompat.getAllLines(sign)) {
            if (tags.contains(SignCompat.stripFormatting(line))) {
                return true;
            }
        }
        return false;
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
