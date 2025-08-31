package me.tuplugin.privatechest.storage;

import org.bukkit.Location;

import java.util.Map;
import java.util.Set;

/**
 * Interface for different data storage implementations.
 * Allows switching between YAML and SQLite storage systems.
 * 
 * @since 2.0 (extended in 2.1)
 * @author PrivateChest Team
 */
public interface DataStorage {

    /**
     * Initializes the storage system.
     * @return true if initialization was successful, false otherwise.
     */
    boolean initialize();

    /**
     * Loads all plugin data from storage.
     * This method loads chest protection data, container names, and trust relationships.
     * 
     * @param owners Map to populate with location -> owner UUID data
     * @param passwords Map to populate with location -> password data
     * @param containerNames Map to populate with location -> custom name data (can be null to skip)
     * @param trustRelations Map to populate with owner UUID -> Set of trusted UUIDs (can be null to skip)
     * @return true if loading was successful, false otherwise
     */
    boolean loadData(Map<Location, String> owners, Map<Location, String> passwords, 
                     Map<Location, String> containerNames, Map<String, Set<String>> trustRelations);

    /**
     * Saves all plugin data to storage.
     * This method saves chest protection data, container names, and trust relationships.
     * 
     * @param owners Map containing location -> owner UUID data
     * @param passwords Map containing location -> password data
     * @param containerNames Map containing location -> custom name data (can be null to skip)
     * @param trustRelations Map containing owner UUID -> Set of trusted UUIDs (can be null to skip)
     * @return true if saving was successful, false otherwise
     */
    boolean saveData(Map<Location, String> owners, Map<Location, String> passwords,
                     Map<Location, String> containerNames, Map<String, Set<String>> trustRelations);

    /**
     * Legacy method for loading only chest data (for backward compatibility).
     * Implementations should delegate to the new loadData method with null parameters.
     * 
     * @param owners Map to populate with location -> owner UUID data
     * @param passwords Map to populate with location -> password data
     * @return true if loading was successful, false otherwise
     * @deprecated Use {@link #loadData(Map, Map, Map, Map)} instead
     */
    @Deprecated
    default boolean loadData(Map<Location, String> owners, Map<Location, String> passwords) {
        return loadData(owners, passwords, null, null);
    }

    /**
     * Legacy method for saving only chest data (for backward compatibility).
     * Implementations should delegate to the new saveData method with null parameters.
     * 
     * @param owners Map containing location -> owner UUID data
     * @param passwords Map containing location -> password data
     * @return true if saving was successful, false otherwise
     * @deprecated Use {@link #saveData(Map, Map, Map, Map)} instead
     */
    @Deprecated
    default boolean saveData(Map<Location, String> owners, Map<Location, String> passwords) {
        return saveData(owners, passwords, null, null);
    }

    /**
     * Closes the storage connection and cleans up resources.
     */
    void close();

    /**
     * Gets the name of this storage type for logging purposes.
     * @return The storage type name (e.g., "YAML", "SQLite")
     */
    String getStorageType();

    /**
     * Checks if the storage system is available and ready to use.
     * @return true if the storage is ready, false otherwise
     */
    boolean isReady();
}