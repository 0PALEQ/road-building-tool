package com.cookiecraftmods.roadbuilder.networking;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestResultCacheTest {
    @Test
    void replaysARecordedResultAndEvictsTheLeastRecentlyUsedEntry() {
        RequestResultCache<PlacementResultS2CPacket> cache =
                new RequestResultCache<>(2, PlacementResultS2CPacket::requestId);
        UUID playerId = UUID.randomUUID();
        PlacementResultS2CPacket first = rejected(UUID.randomUUID());
        PlacementResultS2CPacket second = rejected(UUID.randomUUID());
        PlacementResultS2CPacket third = rejected(UUID.randomUUID());

        cache.remember(playerId, first);
        cache.remember(playerId, second);
        assertEquals(first, cache.find(playerId, first.requestId()).orElseThrow());
        cache.remember(playerId, third);

        assertTrue(cache.find(playerId, second.requestId()).isEmpty());
        assertEquals(first, cache.find(playerId, first.requestId()).orElseThrow());
        assertEquals(third, cache.find(playerId, third.requestId()).orElseThrow());
    }

    @Test
    void scopesIdsPerPlayerAndClearsDisconnectedPlayerState() {
        RequestResultCache<PlacementResultS2CPacket> cache =
                new RequestResultCache<>(2, PlacementResultS2CPacket::requestId);
        UUID requestId = UUID.randomUUID();
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();
        PlacementResultS2CPacket result = rejected(requestId);

        cache.remember(firstPlayer, result);
        assertTrue(cache.find(secondPlayer, requestId).isEmpty());

        cache.clear(firstPlayer);
        assertTrue(cache.find(firstPlayer, requestId).isEmpty());
    }

    private static PlacementResultS2CPacket rejected(UUID requestId) {
        return PlacementResultS2CPacket.rejected(requestId, PlacementResultCode.NOT_AUTHORIZED);
    }
}
