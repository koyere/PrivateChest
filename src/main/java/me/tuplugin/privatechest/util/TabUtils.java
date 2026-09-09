package me.tuplugin.privatechest.util;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Helpers for building tab completion results.
 *
 * @since 3.0
 */
public final class TabUtils {

    private TabUtils() {
        // Utility class.
    }

    /**
     * Filters options by what the player has typed so far, case-insensitively.
     */
    public static List<String> filter(Collection<String> options, String typed) {
        String prefix = typed == null ? "" : typed.toLowerCase();
        List<String> matches = new ArrayList<>();
        for (String option : options) {
            if (option != null && option.toLowerCase().startsWith(prefix)) {
                matches.add(option);
            }
        }
        Collections.sort(matches);
        return matches;
    }

    /**
     * Online player names the sender is actually allowed to see.
     *
     * <p>Players hidden by a vanish plugin are excluded, so tab completion cannot be used to detect
     * invisible staff. The sender is excluded as well, since none of these commands target yourself.</p>
     */
    public static List<String> visiblePlayerNames(CommandSender sender, String typed) {
        Player viewer = sender instanceof Player ? (Player) sender : null;

        List<String> names = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (viewer != null && (online.equals(viewer) || !viewer.canSee(online))) {
                continue;
            }
            names.add(online.getName());
        }
        return filter(names, typed);
    }

    /**
     * An empty result.
     *
     * <p>Worth returning explicitly: a command with no tab completer falls back to suggesting online
     * player names, which is wrong for arguments like passwords and container names.</p>
     */
    public static List<String> none() {
        return Collections.emptyList();
    }
}
