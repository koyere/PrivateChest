package me.tuplugin.privatechest;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ReloadCommand implements CommandExecutor {

    private final PrivateChest plugin;
    private final MessageManager messages;

    public ReloadCommand(PrivateChest plugin) {
        this.plugin = plugin;
        this.messages = plugin.getMessageManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("privatechest.admin")) {
            sender.sendMessage(messages.raw("no_permission"));
            return true;
        }

        plugin.reloadConfig();
        plugin.getMessageManager().reloadMessages();

        sender.sendMessage(messages.raw("reload_success"));
        return true;
    }
}
