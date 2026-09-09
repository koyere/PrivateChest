package me.tuplugin.privatechest.storage;

import java.util.UUID;

/**
 * Immutable record describing a single protected container.
 *
 * <p>Before 3.0 this data lived in three parallel maps (owners, passwords, names) that had to be
 * kept in sync by hand, which meant three lookups per check and a whole class of bugs where one map
 * was updated and another was not. It is now a single value object stored once.</p>
 *
 * <p>The container type is captured when the container is locked. That lets per-type limits and
 * statistics be answered from memory instead of reading the block back from the world, which
 * previously forced chunk loads. It is null for records imported from older versions and is filled
 * in opportunistically the next time the block is seen.</p>
 *
 * @since 3.0
 */
public final class Protection {

    private final UUID owner;
    private final String passwordHash;
    private final String containerType;
    private final String customName;

    public Protection(UUID owner, String passwordHash, String containerType, String customName) {
        if (owner == null) {
            throw new IllegalArgumentException("Protection owner cannot be null");
        }
        if (passwordHash == null) {
            throw new IllegalArgumentException("Protection password cannot be null");
        }
        this.owner = owner;
        this.passwordHash = passwordHash;
        this.containerType = containerType;
        this.customName = customName;
    }

    public UUID getOwner() {
        return owner;
    }

    /**
     * The stored password, either a {@code salt:iterations:hash} digest or, for records not yet
     * migrated, legacy plain text.
     */
    public String getPasswordHash() {
        return passwordHash;
    }

    /**
     * The material name of the container at the time it was locked, or null if unknown.
     */
    public String getContainerType() {
        return containerType;
    }

    /**
     * The admin/player assigned display name, or null if none.
     */
    public String getCustomName() {
        return customName;
    }

    public boolean isOwnedBy(UUID candidate) {
        return candidate != null && owner.equals(candidate);
    }

    public Protection withPasswordHash(String newHash) {
        return new Protection(owner, newHash, containerType, customName);
    }

    public Protection withContainerType(String newType) {
        return new Protection(owner, passwordHash, newType, customName);
    }

    public Protection withCustomName(String newName) {
        return new Protection(owner, passwordHash, containerType, newName);
    }

    @Override
    public String toString() {
        return "Protection{owner=" + owner
                + ", type=" + containerType
                + ", named=" + (customName != null)
                + '}';
    }
}
