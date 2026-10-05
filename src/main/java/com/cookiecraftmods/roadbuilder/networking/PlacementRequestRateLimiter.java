package com.cookiecraftmods.roadbuilder.networking;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

final class PlacementRequestRateLimiter {
    static final int DEFAULT_WINDOW_TICKS = 20;
    static final int DEFAULT_MAXIMUM_REQUESTS_PER_WINDOW = 8;

    private final int windowTicks;
    private final int maximumRequestsPerWindow;
    private final Map<UUID, Window> windows = new HashMap<>();

    PlacementRequestRateLimiter() {
        this(DEFAULT_WINDOW_TICKS, DEFAULT_MAXIMUM_REQUESTS_PER_WINDOW);
    }

    PlacementRequestRateLimiter(int windowTicks, int maximumRequestsPerWindow) {
        if (windowTicks <= 0 || maximumRequestsPerWindow <= 0) {
            throw new IllegalArgumentException("rate-limit bounds must be greater than zero");
        }
        this.windowTicks = windowTicks;
        this.maximumRequestsPerWindow = maximumRequestsPerWindow;
    }

    synchronized boolean tryAcquire(UUID playerId, int serverTick) {
        Objects.requireNonNull(playerId, "playerId");
        int windowIndex = Math.floorDiv(serverTick, windowTicks);
        Window current = windows.get(playerId);
        if (current == null || current.index() != windowIndex) {
            windows.put(playerId, new Window(windowIndex, 1));
            return true;
        }
        if (current.count() >= maximumRequestsPerWindow) {
            return false;
        }
        windows.put(playerId, new Window(windowIndex, current.count() + 1));
        return true;
    }

    synchronized void clear(UUID playerId) {
        windows.remove(Objects.requireNonNull(playerId, "playerId"));
    }

    private record Window(int index, int count) {
    }
}
