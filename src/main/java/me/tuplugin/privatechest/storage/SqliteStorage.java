package me.tuplugin.privatechest.storage;

import me.tuplugin.privatechest.PrivateChest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * SQLite storage backend.
 *
 * <p>This backend was previously incomplete in a way that lost data without saying so: the methods
 * that accepted container names and trust relations ignored both parameters, so any server running
 * {@code storage-type: sqlite} lost every {@code /trust} relation and every container name on each
 * restart. Both are now stored properly.</p>
 *
 * <p>It is also no longer slower than YAML. The old implementation deleted every row and reinserted
 * the whole dataset on each save; it now applies only the rows that changed, inside a transaction,
 * on the persistence worker thread. Write-ahead logging is enabled and the owner column is indexed,
 * which is what makes per-player limit checks cheap.</p>
 *
 * <p>Data written by 2.x is imported automatically on first start, and the old table is kept under a
 * backup name.</p>
 *
 * @since 3.0
 */
public class SqliteStorage implements DataStorage {

    private static final int SCHEMA_VERSION = 3;

    private static final String LEGACY_TABLE = "privatechest_data";
    private static final String LEGACY_BACKUP_TABLE = "privatechest_data_v2_backup";

    private static final String CREATE_PROTECTIONS =
            "CREATE TABLE IF NOT EXISTS protections (" +
                    "world TEXT NOT NULL, " +
                    "x INTEGER NOT NULL, " +
                    "y INTEGER NOT NULL, " +
                    "z INTEGER NOT NULL, " +
                    "owner TEXT NOT NULL, " +
                    "password TEXT NOT NULL, " +
                    "type TEXT, " +
                    "name TEXT, " +
                    "PRIMARY KEY (world, x, y, z))";

    private static final String CREATE_PROTECTIONS_OWNER_INDEX =
            "CREATE INDEX IF NOT EXISTS idx_protections_owner ON protections(owner)";

    private static final String CREATE_TRUST =
            "CREATE TABLE IF NOT EXISTS trust (" +
                    "owner TEXT NOT NULL, " +
                    "trusted TEXT NOT NULL, " +
                    "PRIMARY KEY (owner, trusted))";

    private static final String CREATE_TRUST_TRUSTED_INDEX =
            "CREATE INDEX IF NOT EXISTS idx_trust_trusted ON trust(trusted)";

    private static final String SELECT_PROTECTIONS =
            "SELECT world, x, y, z, owner, password, type, name FROM protections";

    private static final String SELECT_TRUST =
            "SELECT owner, trusted FROM trust";

    private static final String UPSERT_PROTECTION =
            "INSERT OR REPLACE INTO protections (world, x, y, z, owner, password, type, name) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String DELETE_PROTECTION =
            "DELETE FROM protections WHERE world = ? AND x = ? AND y = ? AND z = ?";

    private static final String DELETE_TRUST_OWNER =
            "DELETE FROM trust WHERE owner = ?";

    private static final String INSERT_TRUST =
            "INSERT OR REPLACE INTO trust (owner, trusted) VALUES (?, ?)";

    private final PrivateChest plugin;
    private Connection connection;
    private File databaseFile;

    public SqliteStorage(PrivateChest plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean initialize() {
        try {
            databaseFile = new File(plugin.getDataFolder(), "privatechest.db");

            File parent = databaseFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().severe("[Storage] Could not create the plugin data folder.");
                return false;
            }

            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException e) {
                // Modern drivers register themselves; only older setups need the explicit load.
                plugin.getLogger().fine("[Storage] SQLite driver not loaded explicitly, relying on auto-registration.");
            }

            connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());

            // Detect and back up 2.x data before anything writes to the file, including the pragma
            // that switches on write-ahead logging.
            boolean legacyData = tableExists(LEGACY_TABLE);
            if (legacyData) {
                backupDatabaseFile();
            }

            applyPragmas();
            createSchema();
            if (legacyData) {
                importLegacyTable();
            }
            setSchemaVersion();

            plugin.getLogger().info("[Storage] SQLite ready (" + databaseFile.getName() + ").");
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("[Storage] Failed to open the SQLite database: " + e.getMessage());
            closeQuietly();
            return false;
        }
    }

    private void applyPragmas() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Write-ahead logging keeps writes from blocking reads and survives crashes cleanly.
            stmt.execute("PRAGMA journal_mode=WAL");
            // NORMAL is the right trade-off for game data: durable across crashes, no fsync per write.
            stmt.execute("PRAGMA synchronous=NORMAL");
            stmt.execute("PRAGMA busy_timeout=5000");
            stmt.execute("PRAGMA foreign_keys=ON");
        }
    }

    private void createSchema() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(CREATE_PROTECTIONS);
            stmt.execute(CREATE_PROTECTIONS_OWNER_INDEX);
            stmt.execute(CREATE_TRUST);
            stmt.execute(CREATE_TRUST_TRUSTED_INDEX);
        }
    }

    private void setSchemaVersion() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA user_version=" + SCHEMA_VERSION);
        }
    }

    /**
     * Imports rows written by 2.x and keeps the original table under a backup name.
     */
    private void importLegacyTable() throws SQLException {
        int imported = 0;
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (Statement select = connection.createStatement();
                 ResultSet rs = select.executeQuery("SELECT world, x, y, z, owner, password FROM " + LEGACY_TABLE);
                 PreparedStatement insert = connection.prepareStatement(UPSERT_PROTECTION)) {

                while (rs.next()) {
                    insert.setString(1, rs.getString("world"));
                    insert.setInt(2, rs.getInt("x"));
                    insert.setInt(3, rs.getInt("y"));
                    insert.setInt(4, rs.getInt("z"));
                    insert.setString(5, rs.getString("owner"));
                    insert.setString(6, rs.getString("password"));
                    insert.setString(7, null);
                    insert.setString(8, null);
                    insert.addBatch();
                    imported++;
                }
                insert.executeBatch();
            }

            try (Statement rename = connection.createStatement()) {
                rename.execute("DROP TABLE IF EXISTS " + LEGACY_BACKUP_TABLE);
                rename.execute("ALTER TABLE " + LEGACY_TABLE + " RENAME TO " + LEGACY_BACKUP_TABLE);
            }

            connection.commit();
            plugin.getLogger().info("[Storage] Imported " + imported + " container(s) from the 2.x database. "
                    + "The original table was kept as " + LEGACY_BACKUP_TABLE + ".");
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private void backupDatabaseFile() {
        File backup = new File(plugin.getDataFolder(), databaseFile.getName() + ".v2-backup");
        if (backup.exists()) {
            return;
        }
        try {
            Files.copy(databaseFile.toPath(), backup.toPath());
            plugin.getLogger().info("[Storage] Database backed up to " + backup.getName()
                    + " before upgrading to the 3.0 schema.");
        } catch (IOException e) {
            plugin.getLogger().warning("[Storage] Could not create " + backup.getName() + ": " + e.getMessage());
        }
    }

    private boolean tableExists(String table) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?")) {
            stmt.setString(1, table);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public LoadResult loadAll() {
        if (!isReady()) {
            return null;
        }

        Map<ProtectionKey, Protection> protections = new HashMap<>();
        Map<String, Set<String>> trust = new HashMap<>();
        int skipped = 0;

        try {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(SELECT_PROTECTIONS)) {

                while (rs.next()) {
                    String world = rs.getString("world");
                    ProtectionKey key = ProtectionKey.of(world, rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));
                    String ownerRaw = rs.getString("owner");
                    String password = rs.getString("password");

                    if (key == null || ownerRaw == null || password == null) {
                        skipped++;
                        continue;
                    }

                    UUID owner;
                    try {
                        owner = UUID.fromString(ownerRaw);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("[Storage] Skipping row with an invalid owner UUID at "
                                + key.serialize());
                        skipped++;
                        continue;
                    }

                    protections.put(key, new Protection(owner, password,
                            rs.getString("type"), rs.getString("name")));
                }
            }

            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(SELECT_TRUST)) {

                while (rs.next()) {
                    String owner = rs.getString("owner");
                    String trusted = rs.getString("trusted");
                    if (owner == null || trusted == null) {
                        skipped++;
                        continue;
                    }
                    Set<String> set = trust.get(owner);
                    if (set == null) {
                        set = new HashSet<>();
                        trust.put(owner, set);
                    }
                    set.add(trusted);
                }
            }

            return new LoadResult(protections, trust, skipped);
        } catch (SQLException e) {
            plugin.getLogger().severe("[Storage] Failed to read the SQLite database: " + e.getMessage());
            return null;
        }
    }

    @Override
    public boolean writeBatch(Map<ProtectionKey, Protection> protectionUpserts,
                              Set<ProtectionKey> protectionDeletes,
                              Map<String, Set<String>> trustUpserts,
                              Set<String> trustDeletes) {
        if (!isReady()) {
            return false;
        }

        try {
            connection.setAutoCommit(false);

            if (!protectionUpserts.isEmpty()) {
                try (PreparedStatement stmt = connection.prepareStatement(UPSERT_PROTECTION)) {
                    for (Map.Entry<ProtectionKey, Protection> entry : protectionUpserts.entrySet()) {
                        bindUpsert(stmt, entry.getKey(), entry.getValue());
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                }
            }

            if (!protectionDeletes.isEmpty()) {
                try (PreparedStatement stmt = connection.prepareStatement(DELETE_PROTECTION)) {
                    for (ProtectionKey key : protectionDeletes) {
                        bindKey(stmt, key);
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                }
            }

            // A trust list is replaced wholesale: clear the owner's rows, then write the current set.
            Set<String> ownersToClear = new HashSet<>(trustDeletes);
            ownersToClear.addAll(trustUpserts.keySet());
            if (!ownersToClear.isEmpty()) {
                try (PreparedStatement stmt = connection.prepareStatement(DELETE_TRUST_OWNER)) {
                    for (String owner : ownersToClear) {
                        stmt.setString(1, owner);
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                }
            }

            if (!trustUpserts.isEmpty()) {
                try (PreparedStatement stmt = connection.prepareStatement(INSERT_TRUST)) {
                    for (Map.Entry<String, Set<String>> entry : trustUpserts.entrySet()) {
                        for (String trusted : entry.getValue()) {
                            stmt.setString(1, entry.getKey());
                            stmt.setString(2, trusted);
                            stmt.addBatch();
                        }
                    }
                    stmt.executeBatch();
                }
            }

            connection.commit();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("[Storage] Failed to write to the SQLite database: " + e.getMessage());
            rollbackQuietly();
            return false;
        } finally {
            restoreAutoCommit();
        }
    }

    @Override
    public boolean writeAll(Map<ProtectionKey, Protection> protections, Map<String, Set<String>> trust) {
        if (!isReady()) {
            return false;
        }

        try {
            connection.setAutoCommit(false);

            try (Statement stmt = connection.createStatement()) {
                stmt.execute("DELETE FROM protections");
                stmt.execute("DELETE FROM trust");
            }

            if (protections != null && !protections.isEmpty()) {
                try (PreparedStatement stmt = connection.prepareStatement(UPSERT_PROTECTION)) {
                    for (Map.Entry<ProtectionKey, Protection> entry : protections.entrySet()) {
                        bindUpsert(stmt, entry.getKey(), entry.getValue());
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                }
            }

            if (trust != null && !trust.isEmpty()) {
                try (PreparedStatement stmt = connection.prepareStatement(INSERT_TRUST)) {
                    for (Map.Entry<String, Set<String>> entry : trust.entrySet()) {
                        if (entry.getValue() == null) {
                            continue;
                        }
                        for (String trusted : entry.getValue()) {
                            stmt.setString(1, entry.getKey());
                            stmt.setString(2, trusted);
                            stmt.addBatch();
                        }
                    }
                    stmt.executeBatch();
                }
            }

            connection.commit();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("[Storage] Failed to rewrite the SQLite database: " + e.getMessage());
            rollbackQuietly();
            return false;
        } finally {
            restoreAutoCommit();
        }
    }

    @Override
    public boolean flush() {
        if (!isReady()) {
            return false;
        }
        try (Statement stmt = connection.createStatement()) {
            // Fold the write-ahead log back into the database file so a hard kill loses nothing.
            stmt.execute("PRAGMA wal_checkpoint(PASSIVE)");
            return true;
        } catch (SQLException e) {
            plugin.getLogger().warning("[Storage] SQLite checkpoint failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void close() {
        if (connection == null) {
            return;
        }
        try {
            flush();
            connection.close();
            plugin.getLogger().info("[Storage] SQLite connection closed.");
        } catch (SQLException e) {
            plugin.getLogger().warning("[Storage] Error while closing the SQLite connection: " + e.getMessage());
        } finally {
            connection = null;
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

    // --- Internals ---

    private void bindUpsert(PreparedStatement stmt, ProtectionKey key, Protection protection) throws SQLException {
        stmt.setString(1, key.getWorld());
        stmt.setInt(2, key.getX());
        stmt.setInt(3, key.getY());
        stmt.setInt(4, key.getZ());
        stmt.setString(5, protection.getOwner().toString());
        stmt.setString(6, protection.getPasswordHash());
        stmt.setString(7, protection.getContainerType());
        stmt.setString(8, protection.getCustomName());
    }

    private void bindKey(PreparedStatement stmt, ProtectionKey key) throws SQLException {
        stmt.setString(1, key.getWorld());
        stmt.setInt(2, key.getX());
        stmt.setInt(3, key.getY());
        stmt.setInt(4, key.getZ());
    }

    private void rollbackQuietly() {
        try {
            if (connection != null && !connection.getAutoCommit()) {
                connection.rollback();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[Storage] Rollback failed: " + e.getMessage());
        }
    }

    private void restoreAutoCommit() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[Storage] Could not restore auto-commit: " + e.getMessage());
        }
    }

    private void closeQuietly() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ignored) {
            // Already failing; nothing useful to add.
        } finally {
            connection = null;
        }
    }
}
