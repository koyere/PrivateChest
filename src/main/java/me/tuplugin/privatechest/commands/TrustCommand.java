package me.tuplugin.privatechest.commands;

import me.tuplugin.privatechest.MessageManager;
import me.tuplugin.privatechest.PrivateChest;
import me.tuplugin.privatechest.TrustManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Set;

public class TrustCommand implements CommandExecutor {

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
            sender.sendMessage(messages.raw("console_only_player"));
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("privatechest.use")) {
            player.sendMessage(messages.raw("no_permission"));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(messages.raw("usage_trust"));
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
            player.sendMessage(messages.get("player_not_found").replace("{player}", targetPlayerName));
            return true;
        }

        // Prevent trusting yourself
        if (player.getUniqueId().equals(targetPlayer.getUniqueId())) {
            player.sendMessage(messages.get("cannot_trust_yourself"));
            return true;
        }

        // Add trust relationship
        boolean success = trustManager.trustPlayer(player, targetPlayer);

        if (success) {
            // Save trust data
            plugin.getDataManager().saveData();

            // Send confirmation messages
            player.sendMessage(messages.get("player_trusted").replace("{player}", targetPlayer.getName()));

            // Notify the trusted player if they're online
            if (targetPlayer.isOnline()) {
                targetPlayer.sendMessage(messages.get("trusted_by_player").replace("{player}", player.getName()));
            }
        } else {
            player.sendMessage(messages.get("player_already_trusted").replace("{player}", targetPlayer.getName()));
        }

        return true;
    }

    /**
     * Shows the list of trusted players to the command sender.
     */
    private void showTrustedPlayers(Player player) {
        Set<String> trustedUUIDs = trustManager.getTrustedPlayers(player);

        if (trustedUUIDs.isEmpty()) {
            player.sendMessage(messages.get("no_trusted_players"));
            return;
        }

        player.sendMessage(messages.get("trusted_players_header"));

        for (String uuid : trustedUUIDs) {
            String playerName = trustManager.getPlayerName(uuid);
            Player trustedPlayer = Bukkit.getPlayer(java.util.UUID.fromString(uuid));
            String status = (trustedPlayer != null && trustedPlayer.isOnline()) ?
                    messages.raw("player_status_online") : messages.raw("player_status_offline");

            player.sendMessage(messages.get("trusted_player_entry")
                    .replace("{player}", playerName)
                    .replace("{status}", status));
        }

        player.sendMessage(messages.get("trusted_players_footer")
                .replace("{count}", String.valueOf(trustedUUIDs.size())));
    }
}