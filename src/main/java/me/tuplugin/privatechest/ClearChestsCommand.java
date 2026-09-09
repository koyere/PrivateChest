package me.tuplugin.privatechest;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Collections;
import java.util.List;

/**
 * Removes protection records whose container no longer exists.
 *
 * <p>Delegates to the same engine as the automatic cleanup, which verifies each position on the
 * thread that owns its chunk and skips chunks that are not loaded. The previous implementation read
 * every recorded position inline, forcing the server to load a chunk for each one, and would fail on
 * Folia as soon as a record belonged to another region.</p>
 */
public class ClearChestsCommand implements CommandExecutor, TabCompleter {

    private final PrivateChest plugin;
    private final MessageManager messages;

    public ClearChestsCommand(PrivateChest plugin) {
        this.plugin = plugin;
        this.messages = plugin.getMessageManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("privatechest.admin")) {
            messages.send(sender, messages.raw("no_permission"));
            return true;
        }

        messages.send(sender, messages.raw("cleaning_start"));

        plugin.getAutoCleanupManager().runCleanup(AutoCleanupManager.CleanupType.MANUAL, report -> {
            if (report.wasAlreadyRunning()) {
                messages.send(sender, messages.raw("cleanup_already_running"));
                return;
            }

            if (report.getCleanedContainers() > 0) {
                messages.send(sender, messages.raw("cleaned_chests")
                        .replace("{amount}", String.valueOf(report.getCleanedContainers())));
            } else {
                messages.send(sender, messages.raw("no_chests_to_clean"));
            }

            if (report.getSkippedUnloaded() > 0) {
                messages.send(sender, messages.raw("clean_skipped_unloaded")
                        .replace("{amount}", String.valueOf(report.getSkippedUnloaded())));
            }
        });

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // No arguments to suggest. Returning an empty list also stops the server from falling back to
        // suggesting online player names.
        return Collections.emptyList();
    }
}
