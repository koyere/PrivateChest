package me.tuplugin.privatechest.util;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Counts failed attempts per player and locks them out for a while after too many.
 *
 * <p>Used for password entry. Guessing a container password was previously unlimited and free, and
 * every failed guess was invisible to staff.</p>
 *
 * @since 3.0
 */
public final class AttemptTracker {

    private final Map<UUID, Record> records = new ConcurrentHashMap<>();

    private final int maxAttempts;
    private final long windowMillis;
    private final long lockoutMillis;

    /**
     * @param maxAttempts    failures allowed inside the window before a lockout
     * @param windowSeconds  how long failures are remembered
     * @param lockoutSeconds how long a locked out player must wait
     */
    public AttemptTracker(int maxAttempts, long windowSeconds, long lockoutSeconds) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.windowMillis = Math.max(1L, windowSeconds) * 1000L;
        this.lockoutMillis = Math.max(1L, lockoutSeconds) * 1000L;
    }

    /**
     * Seconds a player still has to wait, or 0 if they may try now.
     */
    public long getRemainingLockoutSeconds(UUID playerId) {
        if (playerId == null) {
            return 0L;
        }

        Record record = records.get(playerId);
        if (record == null) {
            return 0L;
        }

        long remaining = record.lockedUntil - System.currentTimeMillis();
        if (remaining <= 0L) {
            return 0L;
        }
        // Round up, so "1 second left" never displays as 0.
        return (remaining + 999L) / 1000L;
    }

    /**
     * Records a failed attempt.
     *
     * @return true if this failure triggered a lockout
     */
    public boolean recordFailure(UUID playerId) {
        if (playerId == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        Record record = records.computeIfAbsent(playerId, id -> new Record());

        synchronized (record) {
            // Failures outside the window do not count towards a lockout.
            if (now - record.firstFailure > windowMillis) {
                record.failures = 0;
                record.firstFailure = now;
            }

            record.failures++;
            record.lastFailure = now;

            if (record.failures >= maxAttempts) {
                record.lockedUntil = now + lockoutMillis;
                record.failures = 0;
                record.firstFailure = now;
                return true;
            }
            return false;
        }
    }

    /**
     * Clears a player's history after a success.
     */
    public void clear(UUID playerId) {
        if (playerId != null) {
            records.remove(playerId);
        }
    }

    /**
     * Drops entries that are no longer relevant, so the map cannot grow without bound on a long
     * running server.
     */
    public void purgeExpired() {
        long now = System.currentTimeMillis();
        long cutoff = Math.max(windowMillis, lockoutMillis);

        Iterator<Map.Entry<UUID, Record>> iterator = records.entrySet().iterator();
        while (iterator.hasNext()) {
            Record record = iterator.next().getValue();
            synchronized (record) {
                if (record.lockedUntil <= now && now - record.lastFailure > cutoff) {
                    iterator.remove();
                }
            }
        }
    }

    public int size() {
        return records.size();
    }

    private static final class Record {
        private int failures;
        private long firstFailure = System.currentTimeMillis();
        private long lastFailure = System.currentTimeMillis();
        private long lockedUntil;
    }
}
