package me.tuplugin.privatechest.storage;

import me.tuplugin.privatechest.PrivateChest;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.io.File;
import java.sql.*;
import java.util.Map;
import java.util.Set;

/**
 * SQLite-based storage implementation for chest data.
 * Provides better performance and reliability for large datasets.
 */
public class SqliteStorage implements DataStorage {

    private final PrivateChest plugin;
    private Connection connection;
    private File databaseFile;

    private static final String TABLE_NAME = "privatechest_data";
    private static final String CREATE_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "world TEXT NOT NULL, " +
                    "x INTEGER NOT NULL, " +
                    "y INTEGER NOT NULL, " +
                    "z INTEGER NOT NULL, " +
                    "owner TEXT NOT NULL, " +
                    "password TEXT NOT NULL, " +
                    "UNIQUE(world, x, y, z)" +
                    ");";

    private static final String INSERT_SQL =
            "INSERT OR REPLACE INTO " + TABLE_NAME + " (world, x, y, z, owner, password) VALUES (?, ?, ?, ?, ?, ?)";

    private static final String SELECT_ALL_SQL =
            "SELECT world, x, y, z, owner, password FROM " + TABLE_NAME;

    private static final String DELETE_SQL =
            "DELETE FROM " + TABLE_NAME + " WHERE world = ? AND x = ? AND y = ? AND z = ?";

    private static final String CLEAR_ALL_SQL =
            "DELETE FROM " + TABLE_NAME;

    public SqliteStorage(PrivateChest plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean initialize() {
        try {
            // Create database file
            databaseFile = new File(plugin.getDataFolder(), "privatechest.db");

            // Load SQLite JDBC driver
            Class.forName("org.sqlite.JDBC");

            // Create connection
            String url = "jdbc:sqlite:" + databaseFile.getAbsolutePath();
            connection = DriverManager.getConnection(url);

            // Create table if it doesn't exist
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(CREATE_TABLE_SQL);
            }

            plugin.getLogger().info("[PrivateChest] SQLite storage initialized successfully.");
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("[PrivateChest] Failed to initialize SQLite storage!");
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean loadData(Map<Location, String> owners, Map<Location, String> passwords) {
        if (!isReady()) {
            plugin.getLogger().warning("[PrivateChest] SQLite storage is not ready!");
            return false;
        }

        try {
            // Clear maps before loading
            owners.clear();
            passwords.clear();

            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(SELECT_ALL_SQL)) {

                int loaded = 0;
                while (rs.next()) {
                    String worldName = rs.getString("world");
                    int x = rs.getInt("x");
                    int y = rs.getInt("y");
                    int z = rs.getInt("z");
                    String owner = rs.getString("owner");
                    String password = rs.getString("password");

                    World world = Bukkit.getWorld(worldName);
                    if (world != null) {
                        Location loc = new Location(world, x, y, z);
                        owners.put(loc, owner);
                        passwords.put(loc, password);
                        loaded++;
                    } else {
                        plugin.getLogger().warning("[PrivateChest] World '" + worldName + "' not found for chest at " + worldName + "," + x + "," + y + "," + z);
                    }
                }

                plugin.getLogger().info("[PrivateChest] Loaded " + loaded + " locked chests from SQLite storage.");
                return true;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[PrivateChest] Failed to load data from SQLite storage!");
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean saveData(Map<Location, String> owners, Map<Location, String> passwords) {
        if (!isReady()) {
            plugin.getLogger().warning("[PrivateChest] SQLite storage is not ready!");
            return false;
        }

        try {
            // Use transaction for better performance and consistency
            connection.setAutoCommit(false);

            // Clear existing data
            try (Statement clearStmt = connection.createStatement()) {
                clearStmt.execute(CLEAR_ALL_SQL);
            }

            // Insert current data
            try (PreparedStatement pstmt = connection.prepareStatement(INSERT_SQL)) {
                for (Map.Entry<Location, String> entry : owners.entrySet()) {
                    Location loc = entry.getKey();
                    String owner = entry.getValue();
                    String password = passwords.get(loc);

                    if (loc.getWorld() != null && password != null) {
                        pstmt.setString(1, loc.getWorld().getName());
                        pstmt.setInt(2, loc.getBlockX());
                        pstmt.setInt(3, loc.getBlockY());
                        pstmt.setInt(4, loc.getBlockZ());
                        pstmt.setString(5, owner);
                        pstmt.setString(6, password);
                        pstmt.addBatch();
                    }
                }
                pstmt.executeBatch();
            }

            // Commit transaction
            connection.commit();
            connection.setAutoCommit(true);

            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("[PrivateChest] Failed to save data to SQLite storage!");
            e.printStackTrace();

            // Rollback on error
            try {
                connection.rollback();
                connection.setAutoCommit(true);
            } catch (SQLException rollbackEx) {
                plugin.getLogger().severe("[PrivateChest] Failed to rollback SQLite transaction!");
                rollbackEx.printStackTrace();
            }
            return false;
        }
    }

    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
                plugin.getLogger().info("[PrivateChest] SQLite storage closed successfully.");
            } catch (SQLException e) {
                plugin.getLogger().warning("[PrivateChest] Error closing SQLite connection: " + e.getMessage());
            } finally {
                connection = null;
            }
        }
    }

    @Override
    public String getStorageType() {
        return "SQLite";
    }

    @Override
    public boolean isReady() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Deletes a specific chest entry from the database.
     * Used for optimized single-entry removal.
     */
    public boolean deleteChest(Location location) {
        if (!isReady() || location.getWorld() == null) {
            return false;
        }

        try (PreparedStatement pstmt = connection.prepareStatement(DELETE_SQL)) {
            pstmt.setString(1, location.getWorld().getName());
            pstmt.setInt(2, location.getBlockX());
            pstmt.setInt(3, location.getBlockY());
            pstmt.setInt(4, location.getBlockZ());

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().warning("[PrivateChest] Failed to delete chest from SQLite: " + e.getMessage());
            return false;
        }
    }

    /**
     * Updates a specific chest entry in the database.
     * Used for optimized single-entry updates.
     */
    public boolean updateChest(Location location, String owner, String password) {
        if (!isReady() || location.getWorld() == null) {
            return false;
        }

        try (PreparedStatement pstmt = connection.prepareStatement(INSERT_SQL)) {
            pstmt.setString(1, location.getWorld().getName());
            pstmt.setInt(2, location.getBlockX());
            pstmt.setInt(3, location.getBlockY());
            pstmt.setInt(4, location.getBlockZ());
            pstmt.setString(5, owner);
            pstmt.setString(6, password);

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().warning("[PrivateChest] Failed to update chest in SQLite: " + e.getMessage());
            return false;
        }
    }

    /**
     * Implementation of the extended loadData method for compatibility.
     * Currently only supports chest data; names and trust relations are ignored.
     * This is a temporary implementation for v2.1 basic compatibility.
     */
    @Override
    public boolean loadData(Map<Location, String> owners, Map<Location, String> passwords,
                           Map<Location, String> containerNames, Map<String, Set<String>> trustRelations) {
        // For now, delegate to the existing method and ignore additional parameters
        // Full SQLite implementation for names and trust will be added in future updates
        return loadData(owners, passwords);
    }

    /**
     * Implementation of the extended saveData method for compatibility.
     * Currently only supports chest data; names and trust relations are ignored.
     * This is a temporary implementation for v2.1 basic compatibility.
     */
    @Override
    public boolean saveData(Map<Location, String> owners, Map<Location, String> passwords,
                           Map<Location, String> containerNames, Map<String, Set<String>> trustRelations) {
        // For now, delegate to the existing method and ignore additional parameters
        // Full SQLite implementation for names and trust will be added in future updates
        return saveData(owners, passwords);
    }
}