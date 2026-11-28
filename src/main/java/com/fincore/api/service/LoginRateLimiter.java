
package com.fincore.api.service;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.springframework.scheduling.annotation.Scheduled;

@Component
public class LoginRateLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final ConcurrentHashMap<String, AttemptWindow> attempts = new ConcurrentHashMap<>();

    private final Clock clock;

    private static final int LOCK_STRIPES = 256;

    private final ReentrantLock[] loginLocks = createLoginLocks();

    private static ReentrantLock[] createLoginLocks() {

        ReentrantLock[] locks = new ReentrantLock[LOCK_STRIPES];

        for (int i = 0; i < locks.length; i++) {
            locks[i] = new ReentrantLock();
        }

        return locks;
    }

    public <T> T withEmailLock(String email, Supplier<T> operation) {

        String key = email.trim().toLowerCase(Locale.ROOT);

        int index = Math.floorMod(
                key.hashCode(),
                LOCK_STRIPES);

        ReentrantLock lock = loginLocks[index];

        lock.lock();

        try {
            return operation.get();
        } finally {
            lock.unlock();
        }
    }

    public LoginRateLimiter() {
        this(Clock.systemUTC());
    }

    LoginRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public boolean allowRequest(String email) {

        String key = email.trim().toLowerCase(Locale.ROOT);
        Instant now = clock.instant();
        AtomicBoolean allowed = new AtomicBoolean(false);

        attempts.compute(key, (ignored, current) -> {

            if (current == null ||
                    !now.isBefore(current.startedAt().plus(WINDOW))) {

                allowed.set(true);
                return new AttemptWindow(1, now);
            }

            if (current.count() >= MAX_ATTEMPTS) {
                allowed.set(false);
                return current;
            }

            allowed.set(true);

            return new AttemptWindow(
                    current.count() + 1,
                    current.startedAt());
        });

        return allowed.get();
    }

    public boolean isBlocked(String email) {

        String key = email.trim().toLowerCase(Locale.ROOT);
        Instant now = clock.instant();
        AtomicBoolean blocked = new AtomicBoolean(false);

        attempts.computeIfPresent(key, (ignored, current) -> {

            if (!now.isBefore(current.startedAt().plus(WINDOW))) {
                return null;
            }

            blocked.set(current.count() >= MAX_ATTEMPTS);
            return current;
        });

        return blocked.get();
    }

    public void recordFailure(String email) {

        String key = email.trim().toLowerCase(Locale.ROOT);
        Instant now = clock.instant();

        attempts.compute(key, (ignored, current) -> {

            if (current == null ||
                    !now.isBefore(current.startedAt().plus(WINDOW))) {

                return new AttemptWindow(1, now);
            }

            return new AttemptWindow(
                    Math.min(MAX_ATTEMPTS, current.count() + 1),
                    current.startedAt());
        });
    }

    public void clearFailures(String email) {

        String key = email.trim().toLowerCase(Locale.ROOT);
        attempts.remove(key);
    }

    @Scheduled(fixedDelay = 300000)
    int removeExpiredEntries() {

        Instant now = clock.instant();
        java.util.concurrent.atomic.AtomicInteger removed = new java.util.concurrent.atomic.AtomicInteger();

        attempts.forEach((email, ignored) -> {

            attempts.computeIfPresent(email, (key, current) -> {

                if (!now.isBefore(current.startedAt().plus(WINDOW))) {
                    removed.incrementAndGet();
                    return null;
                }

                return current;
            });
        });

        return removed.get();
    }

    int trackedEmailCount() {
        return attempts.size();
    }

    private record AttemptWindow(int count, Instant startedAt) {
    }
}
