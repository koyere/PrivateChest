package me.tuplugin.privatechest.commands;

import java.util.ArrayList;
import java.util.HashSet;
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
 * Grants another player access to the sender's protected containers.
 */
public class TrustCommand implements CommandExecutor, TabCompleter {

    private final TrustManager trustManager;
    private final MessageManager messages;

    public TrustCommand(PrivateChest plugin) {
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

        if (!player.hasPermission("privatechest.trust")) {
            messages.sendRaw(player, "no_permission");
            return true;
        }

        if (args.length == 0) {
            messages.sendRaw(player, "usage_trust");
            return true;
        }

        String targetPlayerName = args[0];

        if (targetPlayerName.equalsIgnoreCase("list")) {
            showTrustedPlayers(player);
            return true;
        }

        Player targetPlayer = Bukkit.getPlayer(targetPlayerName);
        if (targetPlayer == null) {
            messages.send(player, "player_not_found", "player", targetPlayerName);
            return true;
        }

        if (player.getUniqueId().equals(targetPlayer.getUniqueId())) {
            messages.send(player, "cannot_trust_yourself");
            return true;
        }

        if (!trustManager.trustPlayer(player, targetPlayer)) {
            messages.send(player, "player_already_trusted", "player", targetPlayer.getName());
            return true;
        }

        messages.send(player, "player_trusted", "player", targetPlayer.getName());

        if (targetPlayer.isOnline()) {
            messages.send(targetPlayer, "trusted_by_player", "player", player.getName());
        }

        return true;
    }

    /**
     * Lists the players the sender has trusted.
     */
    private void showTrustedPlayers(Player player) {
        Set<String> trustedUUIDs = trustManager.getTrustedPlayers(player);

        if (trustedUUIDs.isEmpty()) {
            messages.send(player, "no_trusted_players");
            return;
        }

        messages.send(player, "trusted_players_header");

        for (String uuid : trustedUUIDs) {
            String playerName = trustManager.getPlayerName(uuid);
            if (playerName == null) {
                playerName = messages.rawFor(player, "unknown_player");
            }

            Player trustedPlayer = resolveOnlinePlayer(uuid);
            String status = messages.rawFor(player,
                    trustedPlayer != null && trustedPlayer.isOnline()
                            ? "player_status_online" : "player_status_offline");

            messages.send(player, "trusted_player_entry", "player", playerName, "status", status);
        }

        messages.send(player, "trusted_players_footer", "count", trustedUUIDs.size());
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
        if (!(sender instanceof Player) || !sender.hasPermission("privatechest.trust")) {
            return TabUtils.none();
        }

        if (args.length == 1) {
            // Players who can be trusted, plus the subcommand. Players already trusted are left out,
            // since trusting them again does nothing.
            Set<String> alreadyTrusted = new HashSet<>();
            for (String uuid : trustManager.getTrustedPlayers((Player) sender)) {
                String name = trustManager.getPlayerName(uuid);
                if (name != null) {
                    alreadyTrusted.add(name.toLowerCase());
                }
            }

            List<String> options = new ArrayList<>();
            options.add("list");
            for (String name : TabUtils.visiblePlayerNames(sender, "")) {
                if (!alreadyTrusted.contains(name.toLowerCase())) {
                    options.add(name);
                }
            }
            return TabUtils.filter(options, args[0]);
        }

        return TabUtils.none();
    }
}
