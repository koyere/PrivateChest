package me.tuplugin.privatechest;

import me.tuplugin.privatechest.util.TabUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Arrays;
import java.util.List;

/**
 * The {@code /privatechest} admin command.
 *
 * <p>Previously this only reloaded, and it did so for any argument at all, including typos. It now
 * routes proper subcommands and exposes the storage tooling that already existed internally but had
 * no way to be invoked: {@code save} and {@code migrate}.</p>
 */
public class PrivateChestCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = Arrays.asList("reload", "status", "save", "migrate");
    private static final List<String> STORAGE_TYPES = Arrays.asList("yaml", "sqlite");

    private final PrivateChest plugin;
    private final MessageManager messages;

    public PrivateChestCommand(PrivateChest plugin) {
        this.plugin = plugin;
        this.messages = plugin.getMessageManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("privatechest.admin")) {
            messages.sendRaw(sender, "no_permission");
            return true;
        }

        if (args.length == 0) {
            messages.sendRaw(sender, "usage_privatechest");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                handleReload(sender);
                return true;
            case "status":
                handleStatus(sender);
                return true;
            case "save":
                handleSave(sender);
                return true;
            case "migrate":
                handleMigrate(sender, args);
                return true;
            default:
                messages.sendRaw(sender, "usage_privatechest");
                return true;
        }
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadConfig();
        messages.reload();
        // Settings that are read once, such as password hashing, the container list and the naming
        // rules, have to be re-applied explicitly. Everything else is read at the point of use.
        plugin.reloadSettings();
        messages.send(sender, "reload_success");
    }

    private void handleStatus(CommandSender sender) {
        DataManager dataManager = plugin.getDataManager();

        messages.send(sender, "status_header");
        messages.sendRaw(sender, "status_storage", "type", dataManager.getStorageType());
        messages.sendRaw(sender, "status_containers", "amount", plugin.getProtectionStore().size());
        messages.sendRaw(sender, "status_trust", "amount", plugin.getTrustManager().getTotalTrustCount());
        messages.sendRaw(sender, "status_container_types", "amount", plugin.getContainerRegistry().size());
        messages.sendRaw(sender, "status_pending", "state",
                messages.rawFor(sender, dataManager.hasPendingChanges()
                        ? "status_pending_yes" : "status_pending_no"));
    }

    private void handleSave(CommandSender sender) {
        boolean saved = plugin.getDataManager().flushNow();
        messages.send(sender, saved ? "save_success" : "save_failed");
    }

    private void handleMigrate(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages.sendRaw(sender, "usage_migrate");
            return;
        }

        String target = args[1].toLowerCase();
        messages.send(sender, "migrate_started", "type", target);

        DataManager.MigrationResult result = plugin.getDataManager().migrate(target);
        switch (result) {
            case SUCCESS:
                messages.send(sender, "migrate_success", "type", target);
                break;
            case ALREADY_ACTIVE:
                messages.send(sender, "migrate_already_active", "type", target);
                break;
            case UNKNOWN_TYPE:
                messages.sendRaw(sender, "usage_migrate");
                break;
            case FAILED:
            default:
                messages.send(sender, "migrate_failed");
                break;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // Nothing is suggested to senders who cannot run the command.
        if (!sender.hasPermission("privatechest.admin")) {
            return TabUtils.none();
        }

        if (args.length == 1) {
            return TabUtils.filter(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("migrate")) {
            return TabUtils.filter(STORAGE_TYPES, args[1]);
        }
        return TabUtils.none();
    }
}
