package me.tuplugin.privatechest.storage;

import org.bukkit.Location;

import java.util.Map;

/**
 * Interface for different data storage implementations.
 * Allows switching between YAML and SQLite storage systems.
 */
public interface DataStorage {

    /**
     * Initializes the storage system.
     * @return true if initialization was successful, false otherwise.
     */
    boolean initialize();

    /**
     * Loads all chest data from storage.
     * @param owners Map to populate with location -> owner UUID data.
     * @param passwords Map to populate with location -> password data.
     * @return true if loading was successful, false otherwise.
     */
    boolean loadData(Map<Location, String> owners, Map<Location, String> passwords);

    /**
     * Saves all chest data to storage.
     * @param owners Map containing location -> owner UUID data.
     * @param passwords Map containing location -> password data.
     * @return true if saving was successful, false otherwise.
     */
    boolean saveData(Map<Location, String> owners, Map<Location, String> passwords);

    /**
     * Closes the storage connection and cleans up resources.
     */
    void close();

    /**
     * Gets the name of this storage type for logging purposes.
     * @return The storage type name (e.g., "YAML", "SQLite").
     */
    String getStorageType();

    /**
     * Checks if the storage system is available and ready to use.
     * @return true if the storage is ready, false otherwise.
     */
    boolean isReady();
}