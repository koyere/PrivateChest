package me.tuplugin.privatechest.enums;

import org.bukkit.Material;

/**
 * Enum representing different types of lockable containers.
 * This enum provides a unified way to handle different container types
 * throughout the plugin, including for naming, limits, and permissions.
 * 
 * @since 2.1
 * @author PrivateChest Team
 */
public enum ContainerType {
    
    /**
     * Standard chest container.
     */
    CHEST("chest", Material.CHEST, "Chest"),
    
    /**
     * Trapped chest container (with redstone functionality).
     */
    TRAPPED_CHEST("trapped_chest", Material.TRAPPED_CHEST, "Trapped Chest"),
    
    /**
     * Barrel container.
     */
    BARREL("barrel", Material.BARREL, "Barrel"),
    
    /**
     * Regular shulker box (undyed).
     */
    SHULKER_BOX("shulker_box", Material.SHULKER_BOX, "Shulker Box"),
    
    /**
     * White shulker box.
     */
    WHITE_SHULKER_BOX("white_shulker_box", Material.WHITE_SHULKER_BOX, "White Shulker Box"),
    
    /**
     * Orange shulker box.
     */
    ORANGE_SHULKER_BOX("orange_shulker_box", Material.ORANGE_SHULKER_BOX, "Orange Shulker Box"),
    
    /**
     * Magenta shulker box.
     */
    MAGENTA_SHULKER_BOX("magenta_shulker_box", Material.MAGENTA_SHULKER_BOX, "Magenta Shulker Box"),
    
    /**
     * Light blue shulker box.
     */
    LIGHT_BLUE_SHULKER_BOX("light_blue_shulker_box", Material.LIGHT_BLUE_SHULKER_BOX, "Light Blue Shulker Box"),
    
    /**
     * Yellow shulker box.
     */
    YELLOW_SHULKER_BOX("yellow_shulker_box", Material.YELLOW_SHULKER_BOX, "Yellow Shulker Box"),
    
    /**
     * Lime shulker box.
     */
    LIME_SHULKER_BOX("lime_shulker_box", Material.LIME_SHULKER_BOX, "Lime Shulker Box"),
    
    /**
     * Pink shulker box.
     */
    PINK_SHULKER_BOX("pink_shulker_box", Material.PINK_SHULKER_BOX, "Pink Shulker Box"),
    
    /**
     * Gray shulker box.
     */
    GRAY_SHULKER_BOX("gray_shulker_box", Material.GRAY_SHULKER_BOX, "Gray Shulker Box"),
    
    /**
     * Light gray shulker box.
     */
    LIGHT_GRAY_SHULKER_BOX("light_gray_shulker_box", Material.LIGHT_GRAY_SHULKER_BOX, "Light Gray Shulker Box"),
    
    /**
     * Cyan shulker box.
     */
    CYAN_SHULKER_BOX("cyan_shulker_box", Material.CYAN_SHULKER_BOX, "Cyan Shulker Box"),
    
    /**
     * Purple shulker box.
     */
    PURPLE_SHULKER_BOX("purple_shulker_box", Material.PURPLE_SHULKER_BOX, "Purple Shulker Box"),
    
    /**
     * Blue shulker box.
     */
    BLUE_SHULKER_BOX("blue_shulker_box", Material.BLUE_SHULKER_BOX, "Blue Shulker Box"),
    
    /**
     * Brown shulker box.
     */
    BROWN_SHULKER_BOX("brown_shulker_box", Material.BROWN_SHULKER_BOX, "Brown Shulker Box"),
    
    /**
     * Green shulker box.
     */
    GREEN_SHULKER_BOX("green_shulker_box", Material.GREEN_SHULKER_BOX, "Green Shulker Box"),
    
    /**
     * Red shulker box.
     */
    RED_SHULKER_BOX("red_shulker_box", Material.RED_SHULKER_BOX, "Red Shulker Box"),
    
    /**
     * Black shulker box.
     */
    BLACK_SHULKER_BOX("black_shulker_box", Material.BLACK_SHULKER_BOX, "Black Shulker Box");

    private final String configName;
    private final Material material;
    private final String displayName;

    /**
     * Constructs a ContainerType enum value.
     * 
     * @param configName The name used in configuration files and permissions
     * @param material The Bukkit material representing this container type
     * @param displayName The user-friendly display name
     */
    ContainerType(String configName, Material material, String displayName) {
        this.configName = configName;
        this.material = material;
        this.displayName = displayName;
    }

    /**
     * Gets the configuration name used in config files and permissions.
     * 
     * @return The configuration name (lowercase with underscores)
     */
    public String getConfigName() {
        return configName;
    }

    /**
     * Gets the Bukkit material representing this container type.
     * 
     * @return The Bukkit material
     */
    public Material getMaterial() {
        return material;
    }

    /**
     * Gets the user-friendly display name.
     * 
     * @return The display name (formatted for users)
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets the base category for this container type.
     * This is used for grouping similar containers (e.g., all shulker boxes).
     * 
     * @return The base category
     */
    public ContainerCategory getCategory() {
        switch (this) {
            case CHEST:
            case TRAPPED_CHEST:
                return ContainerCategory.CHEST;
            case BARREL:
                return ContainerCategory.BARREL;
            default:
                return ContainerCategory.SHULKER_BOX;
        }
    }

    /**
     * Attempts to get a ContainerType from a Bukkit Material.
     * 
     * @param material The material to convert
     * @return The corresponding ContainerType, or null if not a lockable container
     */
    public static ContainerType fromMaterial(Material material) {
        for (ContainerType type : values()) {
            if (type.getMaterial() == material) {
                return type;
            }
        }
        return null;
    }

    /**
     * Attempts to get a ContainerType from a configuration name.
     * 
     * @param configName The configuration name to look up
     * @return The corresponding ContainerType, or null if not found
     */
    public static ContainerType fromConfigName(String configName) {
        if (configName == null) {
            return null;
        }
        
        for (ContainerType type : values()) {
            if (type.getConfigName().equalsIgnoreCase(configName)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Checks if a given material represents a lockable container.
     * 
     * @param material The material to check
     * @return true if the material is a lockable container
     */
    public static boolean isLockableContainer(Material material) {
        return fromMaterial(material) != null;
    }

    /**
     * Enum representing broad categories of containers.
     * Used for simplified configuration and permission handling.
     */
    public enum ContainerCategory {
        CHEST("chest"),
        BARREL("barrel"),
        SHULKER_BOX("shulker_box");

        private final String configName;

        ContainerCategory(String configName) {
            this.configName = configName;
        }

        public String getConfigName() {
            return configName;
        }
    }
}