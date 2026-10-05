package com.cookiecraftmods.roadbuilder.networking;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacementRequestRateLimiterTest {
    @Test
    void boundsEachPlayerIndependentlyAndResetsAtTheNextWindow() {
        PlacementRequestRateLimiter limiter = new PlacementRequestRateLimiter(20, 2);
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();

        assertTrue(limiter.tryAcquire(firstPlayer, 39));
        assertTrue(limiter.tryAcquire(firstPlayer, 39));
        assertFalse(limiter.tryAcquire(firstPlayer, 39));
        assertTrue(limiter.tryAcquire(secondPlayer, 39));
        assertTrue(limiter.tryAcquire(firstPlayer, 40));
    }

    @Test
    void clearingConnectionStateAllowsAFreshWindowCount() {
        PlacementRequestRateLimiter limiter = new PlacementRequestRateLimiter(20, 1);
        UUID player = UUID.randomUUID();

        assertTrue(limiter.tryAcquire(player, 1));
        assertFalse(limiter.tryAcquire(player, 1));
        limiter.clear(player);
        assertTrue(limiter.tryAcquire(player, 1));
    }
}
