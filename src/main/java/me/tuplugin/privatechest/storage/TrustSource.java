package me.tuplugin.privatechest.storage;

import java.util.Map;
import java.util.Set;

/**
 * Read-only access to trust relations for the persistence layer.
 *
 * <p>Keeps the storage package independent from the trust implementation, and makes explicit that
 * the persistence worker only ever reads trust data, never mutates it.</p>
 *
 * @since 3.0
 */
public interface TrustSource {

    /**
     * Snapshot of the players trusted by an owner.
     *
     * @param ownerUuid the owner's UUID as a string
     * @return a copy of the trusted UUIDs, empty if the owner trusts nobody
     */
    Set<String> getTrustedSnapshot(String ownerUuid);

    /**
     * Snapshot of every trust relation, keyed by owner UUID string.
     */
    Map<String, Set<String>> snapshotAllTrust();
}
