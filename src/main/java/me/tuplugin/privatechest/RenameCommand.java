package me.tuplugin.privatechest;

import java.util.Arrays;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import me.tuplugin.privatechest.util.TabUtils;

/**
 * Command executor for the /renamecontainer command.
 * Allows players to assign custom names to their protected containers.
 * This is a basic implementation suitable for the free version of PrivateChest.
 * 
 * @since 2.1
 * @author PrivateChest Team
 */
public class RenameCommand implements CommandExecutor, TabCompleter {

    /** Words that clear the name instead of setting one. */
    private static final List<String> CLEAR_KEYWORDS = Arrays.asList("remove", "clear", "delete");

    private final PrivateChest plugin;
    private final ContainerNameManager nameManager;
    private final ChestLocker chestLocker;
    private final MessageManager messageManager;

    /**
     * Constructs a new RenameCommand executor.
     * 
     * @param plugin The main plugin instance
     */
    public RenameCommand(PrivateChest plugin) {
        this.plugin = plugin;
        this.nameManager = plugin.getContainerNameManager();
        this.chestLocker = plugin.getChestLocker();
        this.messageManager = plugin.getMessageManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Only players can use this command
        if (!(sender instanceof Player)) {
            messageManager.send(sender, messageManager.raw("console_only_player"));
            return true;
        }

        Player player = (Player) sender;

        // Check permission
        if (!player.hasPermission("privatechest.rename")) {
            messageManager.send(player, messageManager.raw("no_permission"));
            return true;
        }

        // Check for correct usage
        if (args.length == 0) {
            messageManager.send(player, messageManager.get("usage_rename_container"));
            return true;
        }

        Block targetBlock = ContainerUtils.getTargetContainer(player);
        if (targetBlock == null) {
            messageManager.send(player, messageManager.raw("not_a_chest"));
            return true;
        }

        Location targetLocation = targetBlock.getLocation();

        // A single clearing keyword removes the name; anything else is treated as the new name.
        if (args.length == 1 && CLEAR_KEYWORDS.contains(args[0].toLowerCase())) {
            handleRemoveName(player, targetLocation);
        } else {
            handleSetName(player, targetLocation, String.join(" ", args));
        }

        return true;
    }

    /**
     * Handles setting a new name for a container.
     * 
     * @param player The player executing the command
     * @param location The location of the container
     * @param name The desired name for the container
     */
    private void handleSetName(Player player, Location location, String name) {
        ContainerNameManager.NameResult result = nameManager.setContainerName(location, player, name);
        
        if (result.isSuccess()) {
            messageManager.send(player, messageManager.get(result.getMessageKey())
                    .replace("{name}", name)
                    .replace("{container}", getContainerTypeName(location)));
        } else {
            String message = messageManager.get(result.getMessageKey());
            
            // Add details if available
            if (result.getDetails() != null) {
                message = message.replace("{details}", result.getDetails())
                                .replace("{limit}", result.getDetails());
            }
            
            messageManager.send(player, message);
        }
    }

    /**
     * Handles removing a name from a container.
     * 
     * @param player The player executing the command
     * @param location The location of the container
     */
    private void handleRemoveName(Player player, Location location) {
        ContainerNameManager.NameResult result = nameManager.removeContainerName(location, player);
        
        if (result.isSuccess()) {
            messageManager.send(player, messageManager.get(result.getMessageKey())
                    .replace("{container}", getContainerTypeName(location)));
        } else {
            messageManager.send(player, messageManager.get(result.getMessageKey()));
        }
    }

    /**
     * Gets a user-friendly name for the container type at the given location.
     * 
     * @param location The location of the container
     * @return A display name for the container type
     */
    private String getContainerTypeName(Location location) {
        String displayName = nameManager.getDisplayName(location);
        if (displayName != null) {
            return displayName;
        }
        return messageManager.raw("container_generic_name");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player) || !sender.hasPermission("privatechest.rename")) {
            return TabUtils.none();
        }

        if (args.length == 1) {
            // Only the clearing keywords are suggested. A name is free text, so suggesting anything
            // else would just get in the way.
            return TabUtils.filter(CLEAR_KEYWORDS, args[0]);
        }

        return TabUtils.none();
    }
}