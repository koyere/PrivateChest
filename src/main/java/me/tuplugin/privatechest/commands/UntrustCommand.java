package me.tuplugin.privatechest.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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

/**
 * Revokes another player's access to the sender's protected containers.
 */
public class UntrustCommand implements CommandExecutor, TabCompleter {

    private final TrustManager trustManager;
    private final MessageManager messages;

    public UntrustCommand(PrivateChest plugin) {
        this.trustManager = plugin.getTrustManager();
        this.messages = plugin.getMessageManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            messages.sendRaw(sender, "console_only_player");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("privatechest.untrust")) {
            messages.sendRaw(player, "no_permission");
            return true;
        }

        if (args.length == 0) {
            messages.sendRaw(player, "usage_untrust");
            return true;
        }

        String targetPlayerName = args[0];

        if (targetPlayerName.equalsIgnoreCase("all")) {
            untrustAllPlayers(player);
            return true;
        }

        Player targetPlayer = Bukkit.getPlayer(targetPlayerName);
        String targetUUID = null;
        String actualPlayerName = targetPlayerName;

        if (targetPlayer != null) {
            targetUUID = targetPlayer.getUniqueId().toString();
            actualPlayerName = targetPlayer.getName();
        } else {
            // Offline: match against the names of the players currently trusted.
            for (String uuid : trustManager.getTrustedPlayers(player)) {
                String trustedPlayerName = trustManager.getPlayerName(uuid);
                if (trustedPlayerName != null && trustedPlayerName.equalsIgnoreCase(targetPlayerName)) {
                    targetUUID = uuid;
                    actualPlayerName = trustedPlayerName;
                    break;
                }
            }
        }

        if (targetUUID == null) {
            messages.send(player, "player_not_trusted", "player", targetPlayerName);
            return true;
        }

        if (!trustManager.untrustPlayer(player.getUniqueId().toString(), targetUUID)) {
            messages.send(player, "player_not_trusted", "player", actualPlayerName);
            return true;
        }

        messages.send(player, "player_untrusted", "player", actualPlayerName);

        if (targetPlayer != null && targetPlayer.isOnline()) {
            messages.send(targetPlayer, "untrusted_by_player", "player", player.getName());
        }

        return true;
    }

    /**
     * Removes every player from the sender's trusted list.
     */
    private void untrustAllPlayers(Player player) {
        Set<String> trustedUUIDs = trustManager.getTrustedPlayers(player);

        if (trustedUUIDs.isEmpty()) {
            messages.send(player, "no_trusted_players");
            return;
        }

        int removedCount = 0;

        for (String uuid : trustedUUIDs) {
            Player trustedPlayer = resolveOnlinePlayer(uuid);
            if (trustedPlayer != null && trustedPlayer.isOnline()) {
                messages.send(trustedPlayer, "untrusted_by_player", "player", player.getName());
            }

            if (trustManager.untrustPlayer(player.getUniqueId().toString(), uuid)) {
                removedCount++;
            }
        }

        if (removedCount > 0) {
            messages.send(player, "all_players_untrusted", "count", removedCount);
        } else {
            messages.send(player, "untrust_all_failed");
        }
    }

    /**
     * Resolves a stored UUID string to an online player, tolerating malformed entries.
     */
    private Player resolveOnlinePlayer(String uuid) {
        try {
            return Bukkit.getPlayer(UUID.fromString(uuid));
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
