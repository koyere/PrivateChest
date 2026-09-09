package me.tuplugin.privatechest.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import me.tuplugin.privatechest.MessageManager;
import me.tuplugin.privatechest.PrivateChest;
import me.tuplugin.privatechest.TrustManager;
import me.tuplugin.privatechest.util.TabUtils;

public class UntrustCommand implements CommandExecutor, TabCompleter {

    private final PrivateChest plugin;
    private final TrustManager trustManager;
    private final MessageManager messages;

    public UntrustCommand(PrivateChest plugin) {
        this.plugin = plugin;
        this.trustManager = TrustManager.getInstance();
        this.messages = plugin.getMessageManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            messages.send(sender, messages.raw("console_only_player"));
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("privatechest.untrust")) {
            messages.send(player, messages.raw("no_permission"));
            return true;
        }

        if (args.length == 0) {
            messages.send(player, messages.raw("usage_untrust"));
            return true;
        }

        String targetPlayerName = args[0];

        // Handle special case for removing all trusted players
        if (targetPlayerName.equalsIgnoreCase("all")) {
            untrustAllPlayers(player);
            return true;
        }

        // Find target player (online or offline)
        Player targetPlayer = Bukkit.getPlayer(targetPlayerName);
        String targetUUID = null;
        String actualPlayerName = targetPlayerName;

        if (targetPlayer != null) {
            // Player is online
            targetUUID = targetPlayer.getUniqueId().toString();
            actualPlayerName = targetPlayer.getName();
        } else {
            // Try to find offline player by checking current trusted list
            Set<String> trustedUUIDs = trustManager.getTrustedPlayers(player);
            for (String uuid : trustedUUIDs) {
                String trustedPlayerName = trustManager.getPlayerName(uuid);
                if (trustedPlayerName != null && trustedPlayerName.equalsIgnoreCase(targetPlayerName)) {
                    targetUUID = uuid;
                    actualPlayerName = trustedPlayerName;
                    break;
                }
            }
        }

        if (targetUUID == null) {
            messages.send(player, messages.get("player_not_trusted").replace("{player}", targetPlayerName));
            return true;
        }

        // Remove trust relationship
        boolean success = trustManager.untrustPlayer(player.getUniqueId().toString(), targetUUID);

        if (success) {
            // Send confirmation message
            messages.send(player, messages.get("player_untrusted").replace("{player}", actualPlayerName));

            // Notify the untrusted player if they're online
            if (targetPlayer != null && targetPlayer.isOnline()) {
                messages.send(targetPlayer, messages.get("untrusted_by_player").replace("{player}", player.getName()));
            }
        } else {
            messages.send(player, messages.get("player_not_trusted").replace("{player}", actualPlayerName));
        }

        return true;
    }

    /**
     * Removes trust from all players for the command sender.
     */
    private void untrustAllPlayers(Player player) {
        Set<String> trustedUUIDs = trustManager.getTrustedPlayers(player);

        if (trustedUUIDs.isEmpty()) {
            messages.send(player, messages.get("no_trusted_players"));
            return;
        }

        int removedCount = 0;

        // Notify all trusted players that they're being untrusted
        for (String uuid : trustedUUIDs) {
            Player trustedPlayer = resolveOnlinePlayer(uuid);
            if (trustedPlayer != null && trustedPlayer.isOnline()) {
                messages.send(trustedPlayer, messages.get("untrusted_by_player").replace("{player}", player.getName()));
            }

            if (trustManager.untrustPlayer(player.getUniqueId().toString(), uuid)) {
                removedCount++;
            }
        }

        if (removedCount > 0) {
            messages.send(player, messages.get("all_players_untrusted").replace("{count}", String.valueOf(removedCount)));
        } else {
            messages.send(player, messages.get("untrust_all_failed"));
        }
    }

    /**
     * Resolves a stored UUID string to an online player, tolerating malformed entries.
     */
    private Player resolveOnlinePlayer(String uuid) {
        try {
            return Bukkit.getPlayer(java.util.UUID.fromString(uuid));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player) || !sender.hasPermission("privatechest.untrust")) {
            return TabUtils.none();
        }

        if (args.length == 1) {
            // Only players the sender has actually trusted, online or not, plus the subcommand.
            List<String> options = new ArrayList<>();
            options.add("all");
            for (String uuid : trustManager.getTrustedPlayers((Player) sender)) {
                String name = trustManager.getPlayerName(uuid);
                if (name != null) {
                    options.add(name);
                }
            }
            return TabUtils.filter(options, args[0]);
        }

        return TabUtils.none();
    }
}