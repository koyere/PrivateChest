package me.tuplugin.privatechest;

import me.tuplugin.privatechest.container.ContainerFamily;
import me.tuplugin.privatechest.storage.Protection;
import me.tuplugin.privatechest.storage.ProtectionKey;
import me.tuplugin.privatechest.storage.ProtectionStore;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * The single place that decides whether someone may use a protected container.
 *
 * <p>Before 3.0 this logic was copy-pasted across the interaction listener, the break listener, the
 * sign listener and the hopper listener, each with slightly different rules. Anything that guards a
 * container now asks this class, so a new protection path cannot accidentally ship with a weaker
 * check than the others.</p>
 *
 * <p>Every lookup is designed to cost as little as possible on a server with no protections nearby.
 * The chunk index is consulted first, so the common case is one map lookup and no block inspection at
 * all.</p>
 *
 * @since 3.0
 */
public final class AccessGuard {

    private final PrivateChest plugin;
    private final ProtectionStore store;
    private final TrustManager trustManager;

    public AccessGuard(PrivateChest plugin, ProtectionStore store, TrustManager trustManager) {
        this.plugin = plugin;
        this.store = store;
        this.trustManager = trustManager;
    }

    // --- Lookups ---

    /**
     * Whether it is worth inspecting a block at all.
     *
     * <p>Rules out the whole chunk with a single lookup. Used by handlers that fire constantly, such
     * as hopper transfers and piston moves.</p>
     */
    public boolean mayHaveProtections(Block block) {
        if (block == null || store.isEmpty()) {
            return false;
        }
        return store.hasProtectionsInChunk(block.getWorld().getName(),
                block.getX() >> 4, block.getZ() >> 4);
    }

    /**
     * Finds the protection covering a block, including the case where only the other half of a double
     * chest carries the record.
     *
     * @return the protection, or null if the container is not protected
     */
    public Protection findProtection(Block block) {
        if (block == null || store.isEmpty()) {
            return null;
        }

        Protection direct = store.get(ProtectionKey.of(block));
        if (direct != null) {
            return direct;
        }

        // Only inspect block data if the block could actually be half of a double chest.
        if (!ContainerUtils.isLockableContainer(block.getType())) {
            return null;
        }

        Block partner = ContainerUtils.getDoubleChestPartner(block);
        return partner == null ? null : store.get(ProtectionKey.of(partner));
    }

    /**
     * Whether a block belongs to a protected container.
     */
    public boolean isProtected(Block block) {
        return findProtection(block) != null;
    }

    /**
     * Finds the protection covering an inventory's container, if any.
     */
    public Protection findProtection(org.bukkit.inventory.Inventory inventory) {
        if (inventory == null || store.isEmpty()) {
            return null;
        }

        for (Block block : ContainerUtils.getInventoryBlocks(inventory)) {
            Protection protection = store.get(ProtectionKey.of(block));
            if (protection != null) {
                return protection;
            }
        }
        return null;
    }

    // --- Access decisions ---

    /**
     * Decides what a player may do with a container.
     */
    public Access check(Player player, Block block) {
        Protection protection = findProtection(block);
        if (protection == null) {
            return Access.NOT_PROTECTED;
        }
        return check(player, protection);
    }

    /**
     * Decides what a player may do with a known protection.
     */
    public Access check(Player player, Protection protection) {
        if (protection == null) {
            return Access.NOT_PROTECTED;
        }
        if (player == null) {
            return Access.DENIED;
        }

        if (protection.isOwnedBy(player.getUniqueId())) {
            return Access.OWNER;
        }
        if (player.hasPermission("privatechest.admin")) {
            return Access.ADMIN;
        }
        if (trustManager.isTrusted(protection.getOwner().toString(), player.getUniqueId().toString())) {
            return Access.TRUSTED;
        }
        return Access.DENIED;
    }

    /**
     * Sends the notice configured for how a player gained access, if any.
     *
     * <p>Configuration is read at call time rather than cached, so {@code /privatechest reload} takes
     * effect immediately. The old listener captured the configuration object when it was constructed,
     * which meant these toggles only changed after a full restart.</p>
     */
    public void notifyAccess(Player player, Access access, Protection protection) {
        if (player == null) {
            return;
        }

        String key;
        String toggle;
        switch (access) {
            case OWNER:
                key = "owner_access_notice";
                toggle = "notify-owner-on-open";
                break;
            case ADMIN:
                key = "admin_access_notice";
                toggle = "notify-admin-on-open";
                break;
            case TRUSTED:
                key = "trusted_access_notice";
                toggle = "notify-trusted-on-open";
                break;
            default:
                return;
        }

        if (!plugin.getConfig().getBoolean(toggle, true)) {
            return;
        }

        send(player, key, protection);
    }

    /**
     * Tells a player they are not allowed to use a container.
     */
    public void denyAccess(Player player, Protection protection) {
        if (player != null) {
            send(player, "not_your_chest", protection);
        }
    }

    private void send(Player player, String key, Protection protection) {
        MessageManager messages = plugin.getMessageManager();
        messages.send(player, messages.get(key)
                .replace("{container}", describe(protection))
                .replace("{owner}", ownerName(protection)));
    }

    /**
     * A player-facing description of a container: its custom name if it has one, otherwise the
     * translated name of its type.
     *
     * <p>Custom names were storable in 2.x but were never displayed anywhere, so naming a container had
     * no observable effect.</p>
     */
    public String describe(Protection protection) {
        MessageManager messages = plugin.getMessageManager();
        if (protection == null) {
            return messages.raw("container_generic_name");
        }

        if (protection.getCustomName() != null) {
            return protection.getCustomName();
        }

        ContainerFamily family = plugin.getContainerRegistry().getFamilyFromName(protection.getContainerType());
        if (family != null) {
            String translated = messages.rawOrNull("container_type_" + family.getConfigName());
            if (translated != null) {
                return translated;
            }
        }
        return messages.raw("container_generic_name");
    }

    /**
     * The owner's name for display, falling back to the configured placeholder.
     */
    public String ownerName(Protection protection) {
        MessageManager messages = plugin.getMessageManager();
        if (protection == null) {
            return messages.raw("unknown_player");
        }

        String name = trustManager.getPlayerName(protection.getOwner().toString());
        return name != null ? name : messages.raw("unknown_player");
    }

    /**
     * What a player is allowed to do, and why.
     */
    public enum Access {
        /** The container carries no protection. */
        NOT_PROTECTED,
        /** The player owns it. */
        OWNER,
        /** The player is bypassing with privatechest.admin. */
        ADMIN,
        /** The owner has trusted the player. */
        TRUSTED,
        /** The player has no claim to it. */
        DENIED;

        /**
         * Whether the player may use the container.
         */
        public boolean isAllowed() {
            return this != DENIED;
        }

        /**
         * Whether the player may destroy the container and clear its protection.
         */
        public boolean canRemove() {
            return this == NOT_PROTECTED || this == OWNER || this == ADMIN;
        }
    }
}
