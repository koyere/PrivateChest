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

public class TrustCommand implements CommandExecutor, TabCompleter {

    private final PrivateChest plugin;
    private final TrustManager trustManager;
    private final MessageManager messages;

    public TrustCommand(PrivateChest plugin) {
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

        if (!player.hasPermission("privatechest.trust")) {
            messages.send(player, messages.raw("no_permission"));
            return true;
        }

        if (args.length == 0) {
            messages.send(player, messages.raw("usage_trust"));
            return true;
        }

        String targetPlayerName = args[0];

        // Handle special case for listing trusted players
        if (targetPlayerName.equalsIgnoreCase("list")) {
            showTrustedPlayers(player);
            return true;
        }

        // Find target player
        Player targetPlayer = Bukkit.getPlayer(targetPlayerName);
        if (targetPlayer == null) {
            messages.send(player, messages.get("player_not_found").replace("{player}", targetPlayerName));
            return true;
        }

        // Prevent trusting yourself
        if (player.getUniqueId().equals(targetPlayer.getUniqueId())) {
            messages.send(player, messages.get("cannot_trust_yourself"));
            return true;
        }

        // Add trust relationship
        boolean success = trustManager.trustPlayer(player, targetPlayer);

        if (success) {
            // Send confirmation messages
            messages.send(player, messages.get("player_trusted").replace("{player}", targetPlayer.getName()));

            // Notify the trusted player if they're online
            if (targetPlayer.isOnline()) {
                messages.send(targetPlayer, messages.get("trusted_by_player").replace("{player}", player.getName()));
            }
        } else {
            messages.send(player, messages.get("player_already_trusted").replace("{player}", targetPlayer.getName()));
        }

        return true;
    }

    /**
     * Shows the list of trusted players to the command sender.
     */
    private void showTrustedPlayers(Player player) {
        Set<String> trustedUUIDs = trustManager.getTrustedPlayers(player);

        if (trustedUUIDs.isEmpty()) {
            messages.send(player, messages.get("no_trusted_players"));
            return;
        }

        messages.send(player, messages.get("trusted_players_header"));

        for (String uuid : trustedUUIDs) {
            String playerName = trustManager.getPlayerName(uuid);
            if (playerName == null) {
                playerName = messages.raw("unknown_player");
            }
            Player trustedPlayer = resolveOnlinePlayer(uuid);
            String status = (trustedPlayer != null && trustedPlayer.isOnline()) ?
                    messages.raw("player_status_online") : messages.raw("player_status_offline");

            messages.send(player, messages.get("trusted_player_entry")
                    .replace("{player}", playerName)
                    .replace("{status}", status));
        }

        messages.send(player, messages.get("trusted_players_footer")
                .replace("{count}", String.valueOf(trustedUUIDs.size())));
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
        if (!(sender instanceof Player) || !sender.hasPermission("privatechest.trust")) {
            return TabUtils.none();
        }

        if (args.length == 1) {
            // Players who can be trusted, plus the subcommand. Players already trusted are left out,
            // since trusting them again does nothing.
            Set<String> alreadyTrusted = new java.util.HashSet<>();
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