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
            messages.send(sender, messages.raw("no_permission"));
            return true;
        }

        if (args.length == 0) {
            messages.send(sender, messages.raw("usage_privatechest"));
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
                messages.send(sender, messages.raw("usage_privatechest"));
                return true;
        }
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadConfig();
        messages.reload();
        // Settings that are read once, such as password hashing and the container list, have to be
        // re-applied explicitly. Everything else is read at the point of use.
        plugin.reloadSettings();
        messages.send(sender, messages.get("reload_success"));
    }

    private void handleStatus(CommandSender sender) {
        DataManager dataManager = plugin.getDataManager();

        messages.send(sender, messages.get("status_header"));
        messages.send(sender, messages.raw("status_storage")
                .replace("{type}", dataManager.getStorageType()));
        messages.send(sender, messages.raw("status_containers")
                .replace("{amount}", String.valueOf(plugin.getProtectionStore().size())));
        messages.send(sender, messages.raw("status_trust")
                .replace("{amount}", String.valueOf(plugin.getTrustManager().getTotalTrustCount())));
        messages.send(sender, messages.raw("status_container_types")
                .replace("{amount}", String.valueOf(plugin.getContainerRegistry().size())));
        messages.send(sender, messages.raw("status_pending")
                .replace("{state}", messages.raw(dataManager.hasPendingChanges()
                        ? "status_pending_yes" : "status_pending_no")));
    }

    private void handleSave(CommandSender sender) {
        boolean saved = plugin.getDataManager().flushNow();
        messages.send(sender, saved ? messages.get("save_success") : messages.get("save_failed"));
    }

    private void handleMigrate(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages.send(sender, messages.raw("usage_migrate"));
            return;
        }

        String target = args[1].toLowerCase();
        messages.send(sender, messages.get("migrate_started").replace("{type}", target));

        DataManager.MigrationResult result = plugin.getDataManager().migrate(target);
        switch (result) {
            case SUCCESS:
                messages.send(sender, messages.get("migrate_success").replace("{type}", target));
                break;
            case ALREADY_ACTIVE:
                messages.send(sender, messages.get("migrate_already_active").replace("{type}", target));
                break;
            case UNKNOWN_TYPE:
                messages.send(sender, messages.raw("usage_migrate"));
                break;
            case FAILED:
            default:
                messages.send(sender, messages.get("migrate_failed"));
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
