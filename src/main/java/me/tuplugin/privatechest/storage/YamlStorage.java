package me.tuplugin.privatechest.storage;

import me.tuplugin.privatechest.PrivateChest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * File based storage using data.yml.
 *
 * <p>The on-disk layout is unchanged from 2.x, so upgrades and downgrades of the data file are
 * non-events. Three things did change:</p>
 * <ul>
 *     <li>Writes are atomic. The file is written to a temporary file and then moved into place, so a
 *     crash or a full disk can no longer leave a half-written data.yml behind. Previously an
 *     interrupted save could truncate the file and destroy every protection on the server.</li>
 *     <li>Writes are incremental. Only the records that changed are touched in the configuration
 *     tree, instead of clearing and rebuilding the whole thing on every save.</li>
 *     <li>Records belonging to worlds that are not currently loaded are kept. The old loader
 *     discarded them, and the next save then wrote that loss to disk permanently. Unmounting a world
 *     temporarily, or loading it late with a world manager, no longer destroys its protections.</li>
 * </ul>
 *
 * @since 3.0
 */
public class YamlStorage implements DataStorage {

    /** Marker written into data.yml so future versions can detect the layout in use. */
    private static final int DATA_VERSION = 3;

    private static final String VERSION_PATH = "data-version";
    private static final String CONTAINERS_SECTION = "chests";
    private static final String TRUST_SECTION = "trust";

    /**
     * Path separator used for configuration lookups.
     *
     * <p>Record keys embed the world name ({@code world,x,y,z}). With the default '.' separator, a
     * world named "world.old" was silently split into nested sections and its records were
     * corrupted. A NUL separator cannot occur in a world name, so keys are always treated as
     * literals.</p>
     */
    private static final char PATH_SEPARATOR = '\u0000';

    private final PrivateChest plugin;
    private File dataFile;
    private FileConfiguration dataConfig;

    public YamlStorage(PrivateChest plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean initialize() {
        try {
            dataFile = new File(plugin.getDataFolder(), "data.yml");

            if (!dataFile.exists()) {
                File parent = dataFile.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    plugin.getLogger().severe("[Storage] Could not create the plugin data folder.");
                    return false;
                }
                if (dataFile.createNewFile()) {
                    plugin.getLogger().info("[Storage] Created data.yml.");
                }
            }

            dataConfig = YamlConfiguration.loadConfiguration(dataFile);
            dataConfig.options().pathSeparator(PATH_SEPARATOR);

            backupLegacyFileIfNeeded();
            dataConfig.set(VERSION_PATH, DATA_VERSION);
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("[Storage] Failed to open data.yml: " + e.getMessage());
            return false;
        }
    }

    /**
     * Copies a pre-3.0 data file aside before it is written in the new format.
     * Gives admins a guaranteed rollback point on the first start after updating.
     */
    private void backupLegacyFileIfNeeded() {
        if (dataConfig.contains(VERSION_PATH)) {
            return;
        }
        if (!dataConfig.isConfigurationSection(CONTAINERS_SECTION)) {
            // Brand new or empty file, nothing worth backing up.
            return;
        }

        File backup = new File(plugin.getDataFolder(), "data.yml.v2-backup");
        if (backup.exists()) {
            return;
        }

        try {
            Files.copy(dataFile.toPath(), backup.toPath());
            plugin.getLogger().info("[Storage] Existing data.yml backed up to data.yml.v2-backup "
                    + "before upgrading to the 3.0 format.");
        } catch (IOException e) {
            plugin.getLogger().warning("[Storage] Could not create data.yml.v2-backup: " + e.getMessage());
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

        ConfigurationSection containers = dataConfig.getConfigurationSection(CONTAINERS_SECTION);
        if (containers != null) {
            for (String rawKey : containers.getKeys(false)) {
                ConfigurationSection entry = containers.getConfigurationSection(rawKey);
                if (entry == null) {
                    skipped++;
                    continue;
                }

                ProtectionKey key = ProtectionKey.deserialize(rawKey);
                if (key == null) {
                    plugin.getLogger().warning("[Storage] Skipping malformed container entry: " + rawKey);
                    skipped++;
                    continue;
                }

                String ownerRaw = entry.getString("owner");
                String password = entry.getString("password");
                if (ownerRaw == null || password == null) {
                    plugin.getLogger().warning("[Storage] Skipping incomplete container entry: " + rawKey);
                    skipped++;
                    continue;
                }

                UUID owner;
                try {
                    owner = UUID.fromString(ownerRaw);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("[Storage] Skipping container entry with an invalid owner UUID: "
                            + rawKey);
                    skipped++;
                    continue;
                }

                String type = trimToNull(entry.getString("type"));
                String name = trimToNull(entry.getString("name"));
                protections.put(key, new Protection(owner, password, type, name));
            }
        }

        ConfigurationSection trustSection = dataConfig.getConfigurationSection(TRUST_SECTION);
        if (trustSection != null) {
            for (String ownerUuid : trustSection.getKeys(false)) {
                List<String> trusted = trustSection.getStringList(ownerUuid);
                if (trusted != null && !trusted.isEmpty()) {
                    trust.put(ownerUuid, new HashSet<>(trusted));
                }
            }
        }

        return new LoadResult(protections, trust, skipped);
    }

    @Override
    public boolean writeBatch(Map<ProtectionKey, Protection> protectionUpserts,
                              Set<ProtectionKey> protectionDeletes,
                              Map<String, Set<String>> trustUpserts,
                              Set<String> trustDeletes) {
        if (!isReady()) {
            return false;
        }

        for (Map.Entry<ProtectionKey, Protection> entry : protectionUpserts.entrySet()) {
            writeProtection(entry.getKey(), entry.getValue());
        }
        for (ProtectionKey key : protectionDeletes) {
            dataConfig.set(path(CONTAINERS_SECTION, key.serialize()), null);
        }
        for (Map.Entry<String, Set<String>> entry : trustUpserts.entrySet()) {
            dataConfig.set(path(TRUST_SECTION, entry.getKey()), new ArrayList<>(entry.getValue()));
        }
        for (String owner : trustDeletes) {
            dataConfig.set(path(TRUST_SECTION, owner), null);
        }

        return saveAtomically();
    }

    @Override
    public boolean writeAll(Map<ProtectionKey, Protection> protections, Map<String, Set<String>> trust) {
        if (!isReady()) {
            return false;
        }

        dataConfig.set(CONTAINERS_SECTION, null);
        dataConfig.set(TRUST_SECTION, null);

        if (protections != null) {
            for (Map.Entry<ProtectionKey, Protection> entry : protections.entrySet()) {
                writeProtection(entry.getKey(), entry.getValue());
            }
        }
        if (trust != null) {
            for (Map.Entry<String, Set<String>> entry : trust.entrySet()) {
                if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                    dataConfig.set(path(TRUST_SECTION, entry.getKey()), new ArrayList<>(entry.getValue()));
                }
            }
        }

        return saveAtomically();
    }

    @Override
    public boolean flush() {
        // Every batch is already persisted by saveAtomically().
        return true;
    }

    @Override
    public void close() {
        dataConfig = null;
        dataFile = null;
    }

    @Override
    public String getStorageType() {
        return "YAML";
    }

    @Override
    public boolean isReady() {
        return dataFile != null && dataConfig != null;
    }

    // --- Internals ---

    private void writeProtection(ProtectionKey key, Protection protection) {
        String base = path(CONTAINERS_SECTION, key.serialize());
        dataConfig.set(path(base, "owner"), protection.getOwner().toString());
        dataConfig.set(path(base, "password"), protection.getPasswordHash());
        dataConfig.set(path(base, "type"), protection.getContainerType());
        dataConfig.set(path(base, "name"), protection.getCustomName());
    }

    private static String path(String parent, String child) {
        return parent + PATH_SEPARATOR + child;
    }

    /**
     * Writes to a temporary file and moves it over data.yml, so the real file is either the previous
     * complete version or the new complete version, never a partial write.
     */
    private boolean saveAtomically() {
        dataConfig.set(VERSION_PATH, DATA_VERSION);

        File temp = new File(dataFile.getParentFile(), dataFile.getName() + ".tmp");
        try {
            dataConfig.save(temp);
        } catch (IOException e) {
            plugin.getLogger().severe("[Storage] Could not write data.yml: " + e.getMessage());
            deleteQuietly(temp);
            return false;
        }

        try {
            try {
                Files.move(temp.toPath(), dataFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                // Some filesystems (notably certain network mounts) cannot do atomic moves.
                Files.move(temp.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("[Storage] Could not replace data.yml: " + e.getMessage());
            deleteQuietly(temp);
            return false;
        }
    }

    private void deleteQuietly(File file) {
        try {
            Files.deleteIfExists(file.toPath());
        } catch (IOException ignored) {
            // Nothing useful to do here.
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
