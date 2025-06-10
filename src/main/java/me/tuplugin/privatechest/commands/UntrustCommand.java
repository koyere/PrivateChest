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

public class UntrustCommand implements CommandExecutor {

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
            sender.sendMessage(messages.raw("console_only_player"));
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("privatechest.use")) {
            player.sendMessage(messages.raw("no_permission"));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(messages.raw("usage_untrust"));
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
            player.sendMessage(messages.get("player_not_trusted").replace("{player}", targetPlayerName));
            return true;
        }

        // Remove trust relationship
        boolean success = trustManager.untrustPlayer(player.getUniqueId().toString(), targetUUID);

        if (success) {
            // Save trust data
            plugin.getDataManager().saveData();

            // Send confirmation message
            player.sendMessage(messages.get("player_untrusted").replace("{player}", actualPlayerName));

            // Notify the untrusted player if they're online
            if (targetPlayer != null && targetPlayer.isOnline()) {
                targetPlayer.sendMessage(messages.get("untrusted_by_player").replace("{player}", player.getName()));
            }
        } else {
            player.sendMessage(messages.get("player_not_trusted").replace("{player}", actualPlayerName));
        }

        return true;
    }

    /**
     * Removes trust from all players for the command sender.
     */
    private void untrustAllPlayers(Player player) {
        Set<String> trustedUUIDs = trustManager.getTrustedPlayers(player);

        if (trustedUUIDs.isEmpty()) {
            player.sendMessage(messages.get("no_trusted_players"));
            return;
        }

        int removedCount = 0;

        // Notify all trusted players that they're being untrusted
        for (String uuid : trustedUUIDs) {
            Player trustedPlayer = Bukkit.getPlayer(java.util.UUID.fromString(uuid));
            if (trustedPlayer != null && trustedPlayer.isOnline()) {
                trustedPlayer.sendMessage(messages.get("untrusted_by_player").replace("{player}", player.getName()));
            }

            if (trustManager.untrustPlayer(player.getUniqueId().toString(), uuid)) {
                removedCount++;
            }
        }

        if (removedCount > 0) {
            // Save trust data
            plugin.getDataManager().saveData();

            player.sendMessage(messages.get("all_players_untrusted").replace("{count}", String.valueOf(removedCount)));
        } else {
            player.sendMessage(messages.get("untrust_all_failed"));
        }
    }
}