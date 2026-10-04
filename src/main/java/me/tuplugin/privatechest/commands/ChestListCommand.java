package me.tuplugin.privatechest.commands;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import me.tuplugin.privatechest.AccessGuard;
import me.tuplugin.privatechest.MessageManager;
import me.tuplugin.privatechest.PrivateChest;
import me.tuplugin.privatechest.container.ContainerFamily;
import me.tuplugin.privatechest.container.ContainerRegistry;
import me.tuplugin.privatechest.storage.Protection;
import me.tuplugin.privatechest.storage.ProtectionKey;
import me.tuplugin.privatechest.storage.ProtectionStore;
import me.tuplugin.privatechest.util.SchedulerUtils;
import me.tuplugin.privatechest.util.TabUtils;

/**
 * Lists the protected containers a player owns, with their world and coordinates.
 *
 * <p>This is a separate command rather than {@code /lockchest list} because the only argument of
 * {@code /lockchest} is a password. Reserving a word there would quietly turn a lock attempt with that
 * password into a listing.</p>
 *
 * <p>Everything is answered from the in-memory owner index. No block is read, so the command behaves
 * the same on every thread and never loads a chunk.</p>
 *
 * @since 3.1
 */
public class ChestListCommand implements CommandExecutor, TabCompleter {

    public static final String PERMISSION = "privatechest.list";
    public static final String OTHERS_PERMISSION = "privatechest.list.others";

    /** Small enough that a whole page stays on screen without scrolling the chat, Bedrock included. */
    private static final int PAGE_SIZE = 8;

    /**
     * What counts as a page number. Bounded so it cannot overflow an int.
     *
     * <p>Minecraft names may be all digits, so a lone number is ambiguous. It is read as a page, and
     * staff can still reach a player named "123" with {@code /chestlist 123 1}.</p>
     */
    private static final Pattern PAGE = Pattern.compile("\\d{1,6}");

    /** World, then X, then Z, then Y, so a page reads as a stable walk across the map. */
    private static final Comparator<ProtectionKey> ORDER = Comparator
            .comparing(ProtectionKey::getWorld)
            .thenComparingInt(ProtectionKey::getX)
            .thenComparingInt(ProtectionKey::getZ)
            .thenComparingInt(ProtectionKey::getY);

    private final PrivateChest plugin;
    private final ProtectionStore store;
    private final MessageManager messages;

    public ChestListCommand(PrivateChest plugin) {
        this.plugin = plugin;
        this.store = plugin.getProtectionStore();
        this.messages = plugin.getMessageManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean canListOwn = sender.hasPermission(PERMISSION);
        boolean canListOthers = sender.hasPermission(OTHERS_PERMISSION);

        if (!canListOwn && !canListOthers) {
            messages.sendRaw(sender, "no_permission");
            return true;
        }

        if (args.length == 0) {
            listOwn(sender, label, 1, canListOwn);
            return true;
        }

        if (args.length == 1 && PAGE.matcher(args[0]).matches()) {
            listOwn(sender, label, Integer.parseInt(args[0]), canListOwn);
            return true;
        }

        if (args.length > 2 || (args.length == 2 && !PAGE.matcher(args[1]).matches())) {
            sendUsage(sender, canListOthers);
            return true;
        }

        // A name was given.
        if (!canListOthers) {
            messages.sendRaw(sender, "no_permission");
            return true;
        }

        int page = args.length == 2 ? Integer.parseInt(args[1]) : 1;
        listOther(sender, label, args[0], page);
        return true;
    }

    private void listOwn(CommandSender sender, String label, int page, boolean permitted) {
        if (!(sender instanceof Player)) {
            messages.sendRaw(sender, "console_only_player");
            return;
        }
        if (!permitted) {
            messages.sendRaw(sender, "no_permission");
            return;
        }

        Player player = (Player) sender;
        show(sender, label, player.getUniqueId(), player.getName(), page, false);
    }

    private void listOther(CommandSender sender, String label, String name, int page) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            show(sender, label, online.getUniqueId(), online.getName(), page, true);
            return;
        }

        // An offline name is matched against the players who actually own something, by UUID, rather
        // than through Bukkit.getOfflinePlayer(String). That method is deprecated and can block on a
        // web request to Mojang. Resolving each owner's name may still read player data from disk, so
        // the scan runs off the server thread and only the reply is sent back.
        SchedulerUtils.runAsync(plugin, () -> {
            UUID match = null;
            String matchedName = null;
            for (UUID owner : store.owners()) {
                String ownerName = Bukkit.getOfflinePlayer(owner).getName();
                if (ownerName != null && ownerName.equalsIgnoreCase(name)) {
                    match = owner;
                    matchedName = ownerName;
                    break;
                }
            }

            UUID found = match;
            String foundName = matchedName;
            SchedulerUtils.runGlobal(plugin, () -> {
                if (found == null) {
                    messages.send(sender, "chest_list_player_unknown", "player", name);
                } else {
                    show(sender, label, found, foundName, page, true);
                }
            });
        });
    }

    private void show(CommandSender sender, String label, UUID owner, String ownerName, int page, boolean other) {
        List<Entry> entries = collect(owner);

        if (entries.isEmpty()) {
            if (other) {
                messages.send(sender, "chest_list_empty_other", "player", ownerName);
            } else {
                messages.send(sender, "chest_list_empty");
            }
            return;
        }

        int pages = (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        int current = Math.max(page, 1);
        if (current > pages) {
            messages.send(sender, "chest_list_invalid_page", "page", current, "pages", pages);
            return;
        }

        messages.send(sender, other ? "chest_list_header_other" : "chest_list_header",
                "player", ownerName, "count", entries.size());

        AccessGuard accessGuard = plugin.getAccessGuard();
        String doubleSuffix = messages.rawFor(sender, "chest_list_double");

        int from = (current - 1) * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, entries.size());
        for (Entry entry : entries.subList(from, to)) {
            ProtectionKey key = entry.key;
            // The container goes last: placeholders are replaced in order, and a custom name that
            // happened to contain "{x}" must not be expanded into a coordinate.
            messages.sendRaw(sender, "chest_list_entry",
                    "world", key.getWorld(),
                    "x", key.getX(),
                    "y", key.getY(),
                    "z", key.getZ(),
                    "double", entry.isDouble ? doubleSuffix : "",
                    "container", accessGuard.describe(entry.protection));
        }

        messages.sendRaw(sender, "chest_list_footer", "page", current, "pages", pages);

        if (current < pages) {
            String next = "/" + label + (other ? " " + ownerName : "") + " " + (current + 1);
            messages.sendRaw(sender, "chest_list_next_page", "command", next);
        }
    }

    /**
     * One line per container, in a stable order.
     *
     * <p>Each half of a double chest is stored as its own protection, so the two halves are folded into
     * one entry. Which blocks form a double chest is only knowable from the world, which this command
     * deliberately never reads, so the pairing is inferred: two horizontally adjacent positions at the
     * same height, owned by the same player, of a family that can form a double chest. Two single chests
     * of the same kind placed side by side would be shown as one double chest. That only affects how the
     * list reads, never what is protected.</p>
     */
    private List<Entry> collect(UUID owner) {
        List<ProtectionKey> keys = new ArrayList<>(store.keysByOwner(owner));
        keys.sort(ORDER);

        ContainerRegistry registry = plugin.getContainerRegistry();
        Set<ProtectionKey> remaining = new HashSet<>(keys);
        List<Entry> entries = new ArrayList<>(keys.size());

        for (ProtectionKey key : keys) {
            if (!remaining.remove(key)) {
                continue;
            }

            // The snapshot can be slightly stale. A protection removed since is simply left out.
            Protection protection = store.get(key);
            if (protection == null) {
                continue;
            }

            ContainerFamily family = registry.getFamilyFromName(protection.getContainerType());
            ProtectionKey partner = canFormDouble(family) ? findPartner(key, family, remaining, registry) : null;
            if (partner != null) {
                remaining.remove(partner);
            }

            entries.add(new Entry(key, protection, partner != null));
        }
        return entries;
    }

    private static boolean canFormDouble(ContainerFamily family) {
        return family == ContainerFamily.CHEST
                || family == ContainerFamily.TRAPPED_CHEST
                || family == ContainerFamily.COPPER_CHEST;
    }

    private ProtectionKey findPartner(ProtectionKey key, ContainerFamily family,
                                      Set<ProtectionKey> candidates, ContainerRegistry registry) {
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] offset : offsets) {
            ProtectionKey neighbour = ProtectionKey.of(key.getWorld(),
                    key.getX() + offset[0], key.getY(), key.getZ() + offset[1]);
            if (!candidates.contains(neighbour)) {
                continue;
            }

            // Compared by family, because the two halves of a copper chest can be at different
            // oxidation stages and therefore be different materials.
            Protection other = store.get(neighbour);
            if (other != null && registry.getFamilyFromName(other.getContainerType()) == family) {
                return neighbour;
            }
        }
        return null;
    }

    private void sendUsage(CommandSender sender, boolean canListOthers) {
        messages.sendRaw(sender, canListOthers ? "usage_chestlist_others" : "usage_chestlist");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // Page numbers are not suggested, and only staff are offered player names. Returning an empty
        // list matters: without it the server would suggest player names to everyone.
        if (args.length == 1 && sender.hasPermission(OTHERS_PERMISSION)) {
            return TabUtils.visiblePlayerNames(sender, args[0]);
        }
        return TabUtils.none();
    }

    private static final class Entry {
        private final ProtectionKey key;
        private final Protection protection;
        private final boolean isDouble;

        private Entry(ProtectionKey key, Protection protection, boolean isDouble) {
            this.key = key;
            this.protection = protection;
            this.isDouble = isDouble;
        }
    }
}
