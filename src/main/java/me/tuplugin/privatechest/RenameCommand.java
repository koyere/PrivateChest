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
 * Assigns a custom name to a protected container.
 *
 * <p>The name is shown wherever the plugin refers to the container, through the {@code {container}}
 * placeholder. In 2.x the name was stored and never displayed anywhere, so renaming had no observable
 * effect.</p>
 *
 * @since 2.1
 */
public class RenameCommand implements CommandExecutor, TabCompleter {

    /** Words that clear the name instead of setting one. */
    private static final List<String> CLEAR_KEYWORDS = Arrays.asList("remove", "clear", "delete");

    private final ContainerNameManager nameManager;
    private final MessageManager messages;

    public RenameCommand(PrivateChest plugin) {
        this.nameManager = plugin.getContainerNameManager();
        this.messages = plugin.getMessageManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            messages.sendRaw(sender, "console_only_player");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("privatechest.rename")) {
            messages.sendRaw(player, "no_permission");
            return true;
        }

        if (args.length == 0) {
            messages.send(player, "usage_rename_container");
            return true;
        }

        Block targetBlock = ContainerUtils.getTargetContainer(player);
        if (targetBlock == null) {
            messages.sendRaw(player, "not_a_chest");
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

    private void handleSetName(Player player, Location location, String name) {
        ContainerNameManager.NameResult result = nameManager.setContainerName(location, player, name);

        messages.send(player, result.getMessageKey(),
                "name", name,
                "container", containerName(location),
                "details", result.getDetails() == null ? "" : result.getDetails(),
                "limit", result.getDetails() == null ? "" : result.getDetails());
    }

    private void handleRemoveName(Player player, Location location) {
        // Read the name before it is cleared, so the confirmation can still refer to it.
        String describedBefore = containerName(location);

        ContainerNameManager.NameResult result = nameManager.removeContainerName(location, player);

        messages.send(player, result.getMessageKey(),
                "container", result.isSuccess() ? describedBefore : containerName(location));
    }

    /**
     * The container's display name, or the generic word if it cannot be determined.
     */
    private String containerName(Location location) {
        String displayName = nameManager.getDisplayName(location);
        return displayName != null ? displayName : messages.raw("container_generic_name");
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
