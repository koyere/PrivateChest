package me.tuplugin.privatechest;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import me.tuplugin.privatechest.enums.ContainerType;

/**
 * Command executor for the /renamecontainer command.
 * Allows players to assign custom names to their protected containers.
 * This is a basic implementation suitable for the free version of PrivateChest.
 * 
 * @since 2.1
 * @author PrivateChest Team
 */
public class RenameCommand implements CommandExecutor {

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
            sender.sendMessage(messageManager.raw("console_only_player"));
            return true;
        }

        Player player = (Player) sender;

        // Check permission
        if (!player.hasPermission("privatechest.rename")) {
            player.sendMessage(messageManager.raw("no_permission"));
            return true;
        }

        // Check for correct usage
        if (args.length == 0) {
            player.sendMessage(messageManager.get("usage_rename_container"));
            return true;
        }

        // Get the block the player is looking at
        Block targetBlock = getTargetBlock(player);
        if (targetBlock == null || !ContainerType.isLockableContainer(targetBlock.getType())) {
            player.sendMessage(messageManager.raw("not_a_chest"));
            return true;
        }

        Location targetLocation = targetBlock.getLocation();

        // Handle different subcommands
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "remove":
            case "clear":
            case "delete":
                handleRemoveName(player, targetLocation);
                break;
            
            default:
                // Treat the first argument as the new name
                String newName = String.join(" ", args);
                handleSetName(player, targetLocation, newName);
                break;
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
            player.sendMessage(messageManager.get(result.getMessageKey())
                    .replace("{name}", name)
                    .replace("{container}", getContainerTypeName(location)));
        } else {
            String message = messageManager.get(result.getMessageKey());
            
            // Add details if available
            if (result.getDetails() != null) {
                message = message.replace("{details}", result.getDetails())
                                .replace("{limit}", result.getDetails());
            }
            
            player.sendMessage(message);
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
            player.sendMessage(messageManager.get(result.getMessageKey())
                    .replace("{container}", getContainerTypeName(location)));
        } else {
            player.sendMessage(messageManager.get(result.getMessageKey()));
        }
    }

    /**
     * Gets the block the player is looking at within a reasonable range.
     * Uses ray tracing for accurate block detection.
     * 
     * @param player The player
     * @return The target block, or null if no valid target
     */
    private Block getTargetBlock(Player player) {
        // Use ray tracing to get the block the player is looking at
        RayTraceResult result = player.rayTraceBlocks(5.0); // 5 block range
        
        if (result != null && result.getHitBlock() != null) {
            return result.getHitBlock();
        }
        
        return null;
    }

    /**
     * Gets a user-friendly name for the container type at the given location.
     * 
     * @param location The location of the container
     * @return A display name for the container type
     */
    private String getContainerTypeName(Location location) {
        if (location.getWorld() == null) {
            return "Container";
        }
        
        Block block = location.getBlock();
        ContainerType type = ContainerType.fromMaterial(block.getType());
        
        if (type != null) {
            return type.getDisplayName();
        }
        
        return "Container";
    }
}