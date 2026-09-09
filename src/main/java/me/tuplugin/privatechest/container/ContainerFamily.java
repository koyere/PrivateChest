package me.tuplugin.privatechest.container;

/**
 * Groups container materials that should be treated as one kind.
 *
 * <p>Families exist because material names are not stable enough to key configuration on. A copper
 * chest changes material as it oxidizes, and shulker boxes come in seventeen colours. Limits,
 * statistics and display names all key off the family, so a locked copper chest that weathers from
 * {@code COPPER_CHEST} to {@code OXIDIZED_COPPER_CHEST} stays the same thing as far as the plugin and
 * the server owner's configuration are concerned.</p>
 *
 * <p>This replaces the {@code ContainerType} enum from 2.x, which hardcoded one entry per material,
 * carried English display names, and referenced material constants directly. That last part is why it
 * could never support containers added after the plugin was compiled.</p>
 *
 * @since 3.0
 */
public enum ContainerFamily {

    CHEST("chest"),
    TRAPPED_CHEST("trapped_chest"),
    COPPER_CHEST("copper_chest"),
    BARREL("barrel"),
    SHULKER_BOX("shulker_box"),
    CRAFTER("crafter"),

    /** Anything an admin added to the container list that does not match a known family. */
    OTHER("other");

    private final String configName;

    ContainerFamily(String configName) {
        this.configName = configName;
    }

    /**
     * The name used in config.yml, permission nodes and message keys.
     */
    public String getConfigName() {
        return configName;
    }

    /**
     * Resolves the family from a material name.
     *
     * <p>Order matters. Trapped and copper chests have to be recognised before the generic chest rule,
     * or every variant would collapse into {@link #CHEST}.</p>
     *
     * @param materialName the material name, in any case
     * @return the family, or {@link #OTHER} when nothing matches
     */
    public static ContainerFamily fromMaterialName(String materialName) {
        if (materialName == null) {
            return OTHER;
        }

        String name = materialName.toUpperCase();

        if (name.contains("SHULKER_BOX")) {
            return SHULKER_BOX;
        }
        if (name.contains("COPPER_CHEST")) {
            return COPPER_CHEST;
        }
        if (name.contains("TRAPPED_CHEST")) {
            return TRAPPED_CHEST;
        }
        if (name.contains("CHEST")) {
            return CHEST;
        }
        if (name.contains("BARREL")) {
            return BARREL;
        }
        if (name.contains("CRAFTER")) {
            return CRAFTER;
        }
        return OTHER;
    }

    /**
     * Resolves a family from its configuration name.
     *
     * @return the family, or null if the name is not recognised
     */
    public static ContainerFamily fromConfigName(String configName) {
        if (configName == null) {
            return null;
        }
        for (ContainerFamily family : values()) {
            if (family.configName.equalsIgnoreCase(configName)) {
                return family;
            }
        }
        return null;
    }
}
