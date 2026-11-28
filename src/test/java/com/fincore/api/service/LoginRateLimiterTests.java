
package com.fincore.api.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


class LoginRateLimiterTests {

    @Test
    void sixthLoginRequestShouldBeRejected() {

        LoginRateLimiter limiter = new LoginRateLimiter();
        String email = "borrower@example.com";

        for (int i = 1; i <= 5; i++) {
            assertTrue(
                    limiter.allowRequest(email),
                    "Request " + i + " should be allowed"
            );
        }

        assertFalse(
                limiter.allowRequest(email),
                "Sixth request must be rejected"
        );
    }

    @Test
    void emailNormalizationCannotBypassRateLimit() {

        LoginRateLimiter limiter = new LoginRateLimiter();

        String[] emails = {
                "borrower@example.com",
                "BORROWER@example.com",
                " Borrower@Example.com ",
                "borrower@EXAMPLE.COM",
                "BORROWER@EXAMPLE.COM"
        };

        for (String email : emails) {
            assertTrue(limiter.allowRequest(email));
        }

        assertFalse(
                limiter.allowRequest(" borrower@example.com "),
                "Email capitalization or spacing must not bypass the limit"
        );
    }

    @Test
    void rateLimitResetsAfterFifteenMinutes() {

        Instant start = Instant.parse("2026-01-01T10:00:00Z");

        AtomicReference<Instant> currentTime =
                new AtomicReference<>(start);

        Clock clock = new Clock() {

            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return currentTime.get();
            }
        };

        LoginRateLimiter limiter = new LoginRateLimiter(clock);
        String email = "borrower@example.com";

        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.allowRequest(email));
        }

        // Sixth request is rejected.
        assertFalse(limiter.allowRequest(email));

        // Advance time by exactly 15 minutes.
        currentTime.set(start.plus(Duration.ofMinutes(15)));

        // A new window starts.
        assertTrue(limiter.allowRequest(email));
    }

@Test
void differentEmailsHaveIndependentLimits() {

    LoginRateLimiter limiter = new LoginRateLimiter();

    String firstEmail = "borrower1@example.com";
    String secondEmail = "borrower2@example.com";

    for (int i = 0; i < 5; i++) {
        assertTrue(limiter.allowRequest(firstEmail));
    }

    assertFalse(limiter.allowRequest(firstEmail));

    // First borrower's limit must not block another borrower.
    assertTrue(limiter.allowRequest(secondEmail));
}

@Test
void rateLimitResetsOnlyAfterFullFifteenMinutes() {

    java.time.Instant start =
            java.time.Instant.parse("2026-01-01T10:00:00Z");

    java.util.concurrent.atomic.AtomicReference<java.time.Instant> currentTime =
            new java.util.concurrent.atomic.AtomicReference<>(start);

    java.time.Clock clock = new java.time.Clock() {

        @Override
        public java.time.ZoneId getZone() {
            return java.time.ZoneOffset.UTC;
        }

        @Override
        public java.time.Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public java.time.Instant instant() {
            return currentTime.get();
        }
    };

    LoginRateLimiter limiter = new LoginRateLimiter(clock);
    String email = "borrower@example.com";

    for (int i = 0; i < 5; i++) {
        assertTrue(limiter.allowRequest(email));
    }

    currentTime.set(start.plusSeconds(899));

    assertFalse(limiter.allowRequest(email));

    currentTime.set(start.plusSeconds(900));

    assertTrue(limiter.allowRequest(email));
}

@Test
void concurrentRequestsCannotBypassRateLimit() throws Exception {

    LoginRateLimiter limiter = new LoginRateLimiter();

    java.util.concurrent.ExecutorService executor =
            java.util.concurrent.Executors.newFixedThreadPool(10);

    java.util.concurrent.CountDownLatch ready =
            new java.util.concurrent.CountDownLatch(10);

    java.util.concurrent.CountDownLatch start =
            new java.util.concurrent.CountDownLatch(1);

    try {
        java.util.List<java.util.concurrent.Future<Boolean>> results =
                new java.util.ArrayList<>();

        for (int i = 0; i < 10; i++) {
            results.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return limiter.allowRequest("borrower@example.com");
            }));
        }

        assertTrue(ready.await(
                5, java.util.concurrent.TimeUnit.SECONDS
        ));

        start.countDown();

        int allowed = 0;

        for (java.util.concurrent.Future<Boolean> result : results) {
            if (result.get(
                    5, java.util.concurrent.TimeUnit.SECONDS
            )) {
                allowed++;
            }
        }

        org.junit.jupiter.api.Assertions.assertEquals(5, allowed);

    } finally {
        start.countDown();
        executor.shutdownNow();
    }
}

@Test
void expiringOneEmailDoesNotResetAnotherEmail() {

    java.time.Instant start =
            java.time.Instant.parse("2026-01-01T10:00:00Z");

    java.util.concurrent.atomic.AtomicReference<java.time.Instant> currentTime =
            new java.util.concurrent.atomic.AtomicReference<>(start);

    java.time.Clock clock = new java.time.Clock() {

        @Override
        public java.time.ZoneId getZone() {
            return java.time.ZoneOffset.UTC;
        }

        @Override
        public java.time.Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public java.time.Instant instant() {
            return currentTime.get();
        }
    };

    LoginRateLimiter limiter = new LoginRateLimiter(clock);

    String borrowerA = "borrowerA@example.com";
    String borrowerB = "borrowerB@example.com";

    // Borrower A exhausts the limit at 10:00.
    for (int i = 0; i < 5; i++) {
        assertTrue(limiter.allowRequest(borrowerA));
    }

    assertFalse(limiter.allowRequest(borrowerA));

    // Borrower B exhausts the limit at 10:10.
    currentTime.set(start.plusSeconds(600));

    for (int i = 0; i < 5; i++) {
        assertTrue(limiter.allowRequest(borrowerB));
    }

    assertFalse(limiter.allowRequest(borrowerB));

    // At 10:15, A's window expires but B's does not.
    currentTime.set(start.plusSeconds(900));

    assertTrue(limiter.allowRequest(borrowerA));
    assertFalse(limiter.allowRequest(borrowerB));
}

    // TEST 8: Blocked attempts must not extend the window
    @Test
    void blockedRequestsDoNotExtendExpirationWindow() {

        TestClock clock = new TestClock();
        LoginRateLimiter limiter = new LoginRateLimiter(clock);

        String email = "borrower@example.com";

        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.allowRequest(email));
        }

        clock.advanceSeconds(899);

        assertFalse(limiter.allowRequest(email));
        assertFalse(limiter.allowRequest(email));

        clock.advanceSeconds(1);

        assertTrue(limiter.allowRequest(email));
    }

    // TEST 9: Window begins with the first request
    @Test
    void windowStartsFromFirstRequestNotFifth() {

        TestClock clock = new TestClock();
        LoginRateLimiter limiter = new LoginRateLimiter(clock);

        String email = "borrower@example.com";

        // First request at 10:00
        assertTrue(limiter.allowRequest(email));

        // Four more requests at 10:10
        clock.advanceSeconds(600);

        for (int i = 0; i < 4; i++) {
            assertTrue(limiter.allowRequest(email));
        }

        assertFalse(limiter.allowRequest(email));

        // 10:14:59: original window is still active
        clock.advanceSeconds(299);

        assertFalse(limiter.allowRequest(email));

        // 10:15:00: original window expires
        clock.advanceSeconds(1);

        assertTrue(limiter.allowRequest(email));
    }

    // TEST 10: A new window grants exactly five requests
    @Test
    void newWindowAllowsExactlyFiveRequests() {

        TestClock clock = new TestClock();
        LoginRateLimiter limiter = new LoginRateLimiter(clock);

        String email = "borrower@example.com";

        // Exhaust first window
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.allowRequest(email));
        }

        assertFalse(limiter.allowRequest(email));

        // Start second window
        clock.advanceSeconds(900);

        for (int i = 0; i < 5; i++) {
            assertTrue(
                    limiter.allowRequest(email),
                    "New window request " + (i + 1)
            );
        }

        // Sixth request in the new window is blocked
        assertFalse(limiter.allowRequest(email));
    }

    // Shared controllable clock for these three tests
    private static class TestClock extends java.time.Clock {

        private java.time.Instant current =
                java.time.Instant.parse("2026-01-01T10:00:00Z");

        void advanceSeconds(long seconds) {
            current = current.plusSeconds(seconds);
        }

        @Override
        public java.time.Instant instant() {
            return current;
        }

        @Override
        public java.time.ZoneId getZone() {
            return java.time.ZoneOffset.UTC;
        }

        @Override
        public java.time.Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

@Test
void concurrentFailedLoginsCannotExceedLimit() throws Exception {

    LoginRateLimiter limiter = new LoginRateLimiter();

    java.util.concurrent.ExecutorService executor =
            java.util.concurrent.Executors.newFixedThreadPool(10);

    java.util.concurrent.CountDownLatch ready =
            new java.util.concurrent.CountDownLatch(10);

    java.util.concurrent.CountDownLatch start =
            new java.util.concurrent.CountDownLatch(1);

    try {
        java.util.List<java.util.concurrent.Future<Boolean>> results =
                new java.util.ArrayList<>();

        for (int i = 0; i < 10; i++) {

            results.add(executor.submit(() -> {

                ready.countDown();
                start.await();

                return limiter.withEmailLock(
                        "borrower@example.com",
                        () -> {

                            if (limiter.isBlocked(
                                    "borrower@example.com")) {
                                return false;
                            }

                            limiter.recordFailure(
                                    "borrower@example.com");

                            return true;
                        }
                );
            }));
        }

        assertTrue(ready.await(
                5, java.util.concurrent.TimeUnit.SECONDS));

        start.countDown();

        int accepted = 0;

        for (java.util.concurrent.Future<Boolean> result : results) {
            if (result.get(
                    5, java.util.concurrent.TimeUnit.SECONDS)) {
                accepted++;
            }
        }

        org.junit.jupiter.api.Assertions.assertEquals(
                5, accepted);

        assertTrue(limiter.isBlocked(
                "borrower@example.com"));

    } finally {
        start.countDown();
        executor.shutdownNow();
    }
}

@Test
void normalizedEmailsUseSameAuthenticationLock() {

    LoginRateLimiter limiter = new LoginRateLimiter();

    limiter.withEmailLock(
            " BORROWER@EXAMPLE.COM ",
            () -> {

                for (int i = 0; i < 5; i++) {
                    limiter.recordFailure(
                            "borrower@example.com");
                }

                return null;
            }
    );

    boolean blocked = limiter.withEmailLock(
            "borrower@example.com",
            () -> limiter.isBlocked(
                    " BORROWER@example.com ")
    );

    assertTrue(blocked);
}

@Test
void expiredEntriesRemainUntilAccessed() {

    TestClock clock = new TestClock();
    LoginRateLimiter limiter = new LoginRateLimiter(clock);

    String email = "expired@example.com";

    limiter.recordFailure(email);

    clock.advanceSeconds(900);

    // Accessing this email clears its expired failure window.
    assertFalse(limiter.isBlocked(email));

    // A fresh failure should begin a new window.
    for (int i = 0; i < 5; i++) {
        limiter.recordFailure(email);
    }

    assertTrue(limiter.isBlocked(email));
}

@Test
void expiredEntriesRemainStoredWithoutCleanup() {

    TestClock clock = new TestClock();
    LoginRateLimiter limiter = new LoginRateLimiter(clock);

    for (int i = 0; i < 100; i++) {
        limiter.recordFailure("borrower" + i + "@example.com");
    }

    assertEquals(100, limiter.trackedEmailCount());

    clock.advanceSeconds(900);

    assertEquals(100, limiter.trackedEmailCount());
}


@Test
void cleanupRemovesExpiredEntries() {

    TestClock clock = new TestClock();
    LoginRateLimiter limiter = new LoginRateLimiter(clock);

    for (int i = 0; i < 100; i++) {
        limiter.recordFailure("borrower" + i + "@example.com");
    }

    assertEquals(100, limiter.trackedEmailCount());

    clock.advanceSeconds(900);

    assertEquals(100, limiter.removeExpiredEntries());
    assertEquals(0, limiter.trackedEmailCount());
}

@Test
void cleanupPreservesActiveEntries() {

    TestClock clock = new TestClock();
    LoginRateLimiter limiter = new LoginRateLimiter(clock);

    limiter.recordFailure("expired@example.com");

    clock.advanceSeconds(600);

    limiter.recordFailure("active@example.com");

    clock.advanceSeconds(300);

    assertEquals(1, limiter.removeExpiredEntries());
    assertEquals(1, limiter.trackedEmailCount());

    assertFalse(limiter.isBlocked("active@example.com"));
}

@Test
void expiredEntryCleanupIsScheduledEveryFiveMinutes()
        throws Exception {

    java.lang.reflect.Method method =
            LoginRateLimiter.class.getDeclaredMethod(
                    "removeExpiredEntries"
            );

    org.springframework.scheduling.annotation.Scheduled scheduled =
            method.getAnnotation(
                    org.springframework.scheduling.annotation.Scheduled.class
            );

    org.junit.jupiter.api.Assertions.assertNotNull(
            scheduled,
            "Cleanup must have @Scheduled"
    );

    assertEquals(
            300000L,
            scheduled.fixedDelay()
    );
}

@Test
void springSchedulingIsEnabled() {

    org.springframework.scheduling.annotation.EnableScheduling annotation =
            com.fincore.api.FincoreApiApplication.class.getAnnotation(
                    org.springframework.scheduling.annotation.EnableScheduling.class
            );

    org.junit.jupiter.api.Assertions.assertNotNull(
            annotation,
            "Spring scheduling must be enabled"
    );
}

}
