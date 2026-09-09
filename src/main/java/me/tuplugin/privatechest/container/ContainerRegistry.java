package me.tuplugin.privatechest.container;

import me.tuplugin.privatechest.PrivateChest;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Decides which blocks can be protected, resolved at runtime instead of compiled in.
 *
 * <p>2.x carried a fixed list of twenty material constants. That is why copper chests, added in
 * Minecraft 1.21.9, could not be protected at all: the list was written before they existed, and
 * referencing them in code would have broken the build against older server versions.</p>
 *
 * <p>Containers are now resolved by name when the plugin starts, from three sources:</p>
 * <ol>
 *     <li>A built-in list of every container the plugin knows about, including ones that only exist on
 *     newer servers. Names that the running server does not have are skipped quietly, since that is
 *     expected rather than an error.</li>
 *     <li>Automatic discovery by name pattern, so a container added by a future Minecraft version is
 *     protectable the day it ships, without waiting for a plugin update.</li>
 *     <li>Whatever the server owner adds or removes in config.yml.</li>
 * </ol>
 *
 * <p>Membership tests go through an {@link EnumSet}, so the check on the hot event paths is a bit
 * test.</p>
 *
 * @since 3.0
 */
public final class ContainerRegistry {

    /**
     * Containers the plugin ships with knowledge of.
     *
     * <p>Held as names on purpose. Referencing {@code Material.COPPER_CHEST} directly would not
     * compile against the older API this plugin still supports.</p>
     */
    private static final List<String> BUILT_IN = Collections.unmodifiableList(Arrays.asList(
            // Present since 1.13
            "CHEST",
            "TRAPPED_CHEST",
            "BARREL",
            "SHULKER_BOX",
            "WHITE_SHULKER_BOX",
            "ORANGE_SHULKER_BOX",
            "MAGENTA_SHULKER_BOX",
            "LIGHT_BLUE_SHULKER_BOX",
            "YELLOW_SHULKER_BOX",
            "LIME_SHULKER_BOX",
            "PINK_SHULKER_BOX",
            "GRAY_SHULKER_BOX",
            "LIGHT_GRAY_SHULKER_BOX",
            "CYAN_SHULKER_BOX",
            "PURPLE_SHULKER_BOX",
            "BLUE_SHULKER_BOX",
            "BROWN_SHULKER_BOX",
            "GREEN_SHULKER_BOX",
            "RED_SHULKER_BOX",
            "BLACK_SHULKER_BOX",

            // Added in 1.21
            "CRAFTER",

            // Added in 1.21.9, The Copper Age. Oxidises in place, so every stage has to be listed.
            "COPPER_CHEST",
            "EXPOSED_COPPER_CHEST",
            "WEATHERED_COPPER_CHEST",
            "OXIDIZED_COPPER_CHEST",
            "WAXED_COPPER_CHEST",
            "WAXED_EXPOSED_COPPER_CHEST",
            "WAXED_WEATHERED_COPPER_CHEST",
            "WAXED_OXIDIZED_COPPER_CHEST"
    ));

    /**
     * Name patterns used to discover containers this plugin version has never heard of.
     */
    private static final List<String> DEFAULT_DISCOVERY_PATTERNS = Collections.unmodifiableList(
            Arrays.asList("*_CHEST", "*_SHULKER_BOX"));

    /**
     * Never protectable, even if a pattern matches.
     *
     * <p>An ender chest is already private to each player, and protecting one would only break it.</p>
     */
    private static final List<String> DEFAULT_EXCLUDED = Collections.unmodifiableList(
            Collections.singletonList("ENDER_CHEST"));

    private final PrivateChest plugin;

    private Set<Material> lockable = EnumSet.noneOf(Material.class);

    /**
     * Everything recognised as a container, ignoring {@code containers.disabled}.
     *
     * <p>Kept separate so that turning a container type off stops new locks without making the cleanup
     * treat existing protections on that type as orphaned data and delete them.</p>
     */
    private Set<Material> known = EnumSet.noneOf(Material.class);

    private Map<Material, ContainerFamily> families = new EnumMap<>(Material.class);

    public ContainerRegistry(PrivateChest plugin) {
        this.plugin = plugin;
    }

    /**
     * Builds the container list from the configuration and the running server's materials.
     * Safe to call again after a reload.
     */
    public void load() {
        Set<Material> resolved = EnumSet.noneOf(Material.class);
        Map<Material, ContainerFamily> resolvedFamilies = new EnumMap<>(Material.class);

        Set<String> excluded = upperCaseSet(
                plugin.getConfig().getStringList("containers.excluded"), DEFAULT_EXCLUDED);

        // 1. Built-in names, plus anything the owner added.
        List<String> configured = new ArrayList<>(BUILT_IN);
        configured.addAll(plugin.getConfig().getStringList("containers.additional"));

        List<String> unknownNames = new ArrayList<>();
        for (String name : configured) {
            String normalized = normalize(name);
            if (normalized.isEmpty() || excluded.contains(normalized)) {
                continue;
            }

            Material material = Material.matchMaterial(normalized);
            if (material == null || !material.isBlock()) {
                unknownNames.add(normalized);
                continue;
            }
            add(resolved, resolvedFamilies, material);
        }

        // 2. Pattern discovery, so future containers work without a plugin update.
        int discovered = 0;
        if (plugin.getConfig().getBoolean("containers.auto-discover", true)) {
            List<String> patterns = plugin.getConfig().getStringList("containers.discovery-patterns");
            if (patterns.isEmpty()) {
                patterns = DEFAULT_DISCOVERY_PATTERNS;
            }

            for (Material material : Material.values()) {
                if (!material.isBlock() || resolved.contains(material)) {
                    continue;
                }
                String name = material.name();
                if (excluded.contains(name) || !matchesAny(name, patterns)) {
                    continue;
                }
                add(resolved, resolvedFamilies, material);
                discovered++;
            }
        }

        // Everything recognised so far stays recognised, even if it is disabled below.
        Set<Material> recognised = EnumSet.copyOf(resolved.isEmpty()
                ? EnumSet.noneOf(Material.class) : resolved);

        // 3. Explicit removals win over everything, for new locks only.
        int disabled = 0;
        for (String name : plugin.getConfig().getStringList("containers.disabled")) {
            Material material = Material.matchMaterial(normalize(name));
            if (material != null && resolved.remove(material)) {
                disabled++;
            }
        }

        this.lockable = resolved;
        this.known = recognised;
        this.families = resolvedFamilies;

        if (disabled > 0) {
            plugin.getLogger().info("[Containers] " + disabled + " container type(s) disabled for new locks. "
                    + "Existing protections on them are kept.");
        }

        plugin.getLogger().info("[Containers] " + resolved.size() + " container type(s) can be protected"
                + (discovered > 0 ? " (" + discovered + " found automatically on this server version)" : "")
                + ".");

        // Not a warning: the built-in list intentionally includes containers from newer Minecraft
        // versions, and an older server simply will not have them.
        if (!unknownNames.isEmpty() && plugin.getConfig().getBoolean("containers.log-unavailable", false)) {
            plugin.getLogger().info("[Containers] Not available on this server version: "
                    + String.join(", ", unknownNames));
        }
    }

    private void add(Set<Material> target, Map<Material, ContainerFamily> familyMap, Material material) {
        target.add(material);
        familyMap.put(material, ContainerFamily.fromMaterialName(material.name()));
    }

    /**
     * Whether a material can be protected.
     */
    public boolean isLockable(Material material) {
        return material != null && lockable.contains(material);
    }

    /**
     * Whether a material is recognised as a container at all, even one that has been disabled for new
     * locks.
     *
     * <p>Used by the cleanup, which must not treat "the owner turned this type off" as "this container
     * no longer exists" and delete the record.</p>
     */
    public boolean isKnownContainer(Material material) {
        return material != null && known.contains(material);
    }

    /**
     * The family a material belongs to.
     *
     * @return the family, or null if the material is not protectable
     */
    public ContainerFamily getFamily(Material material) {
        return material == null ? null : families.get(material);
    }

    /**
     * The family recorded for a protection, resolved from the stored material name.
     *
     * <p>Works for materials the running server no longer has, which matters when a world is loaded on
     * a different version than it was created on.</p>
     *
     * @return the family, or null if no name was recorded
     */
    public ContainerFamily getFamilyFromName(String materialName) {
        if (materialName == null) {
            return null;
        }

        Material material = Material.matchMaterial(normalize(materialName));
        if (material != null) {
            ContainerFamily known = families.get(material);
            if (known != null) {
                return known;
            }
        }
        return ContainerFamily.fromMaterialName(materialName);
    }

    /**
     * Whether two materials belong to the same family.
     *
     * <p>Used to decide whether two adjacent chests form one double chest. Comparing materials
     * directly is not enough: the two halves of a copper chest can be at different oxidation stages
     * and therefore be different materials while still being one container.</p>
     */
    public boolean isSameFamily(Material first, Material second) {
        ContainerFamily a = getFamily(first);
        ContainerFamily b = getFamily(second);
        return a != null && a == b;
    }

    /**
     * Every protectable material, for diagnostics.
     */
    public Set<Material> getLockableMaterials() {
        return Collections.unmodifiableSet(lockable);
    }

    public int size() {
        return lockable.size();
    }

    // --- Helpers ---

    private static String normalize(String name) {
        if (name == null) {
            return "";
        }
        String trimmed = name.trim().toUpperCase(Locale.ROOT);
        // Accept namespaced names such as "minecraft:chest" as well as plain ones.
        int colon = trimmed.indexOf(':');
        return colon >= 0 ? trimmed.substring(colon + 1) : trimmed;
    }

    private static Set<String> upperCaseSet(List<String> configured, List<String> defaults) {
        Set<String> result = new HashSet<>();
        List<String> source = configured == null || configured.isEmpty() ? defaults : configured;
        for (String value : source) {
            result.add(normalize(value));
        }
        return result;
    }

    /**
     * Matches a material name against simple {@code *} wildcard patterns.
     */
    private static boolean matchesAny(String name, List<String> patterns) {
        for (String rawPattern : patterns) {
            String pattern = normalize(rawPattern);
            if (pattern.isEmpty()) {
                continue;
            }

            if (pattern.startsWith("*") && pattern.endsWith("*") && pattern.length() > 2) {
                if (name.contains(pattern.substring(1, pattern.length() - 1))) {
                    return true;
                }
            } else if (pattern.startsWith("*")) {
                if (name.endsWith(pattern.substring(1))) {
                    return true;
                }
            } else if (pattern.endsWith("*")) {
                if (name.startsWith(pattern.substring(0, pattern.length() - 1))) {
                    return true;
                }
            } else if (name.equals(pattern)) {
                return true;
            }
        }
        return false;
    }
}
