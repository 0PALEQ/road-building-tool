package com.cookiecraftmods.roadbuilder.networking;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import com.cookiecraftmods.roadbuilder.placement.RemovalMode;
import com.cookiecraftmods.roadbuilder.placement.RemovalSummary;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerRequestSessionStateTest {
    @Test
    void disconnectClearsOnlyThatPlayersReplayAndRateState() {
        PlayerRequestSessionState state = new PlayerRequestSessionState(
                new RequestResultCache<>(4, PlacementResultS2CPacket::requestId),
                new RequestResultCache<>(4, RemovalResultS2CPacket::requestId),
                new RequestResultCache<>(
                        4,
                        ConstructionJobCancelResultS2CPacket::requestId
                ),
                new PlacementRequestRateLimiter(20, 1)
        );
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();
        UUID placementRequest = UUID.randomUUID();
        UUID removalRequest = UUID.randomUUID();
        UUID jobCancelRequest = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();

        PlacementResultS2CPacket placement = PlacementResultS2CPacket.accepted(
                placementRequest,
                operationId,
                7
        );
        RemovalIntentC2SPacket removalIntent = RemovalIntentC2SPacket.current(
                removalRequest,
                MinecraftIds.id("minecraft", "overworld"),
                operationId,
                RemovalRequestAction.PREVIEW,
                RemovalMode.SAFE
        );
        RemovalResultS2CPacket removal = RemovalResultS2CPacket.success(
                removalIntent,
                new RemovalSummary(1, 0, 1, 0, 0, 0, List.of())
        );
        ConstructionJobCancelResultS2CPacket jobCancel =
                new ConstructionJobCancelResultS2CPacket(
                        ConstructionJobCancelResultS2CPacket.CURRENT_PROTOCOL_VERSION,
                        jobCancelRequest,
                        jobId,
                        ConstructionJobCancelResultCode.ACCEPTED
                );
        state.rememberPlacement(firstPlayer, placement);
        state.rememberRemoval(firstPlayer, removal);
        state.rememberJobCancel(firstPlayer, jobCancel);
        state.rememberPlacement(secondPlayer, placement);
        assertTrue(state.tryAcquire(firstPlayer, 0));
        assertTrue(state.tryAcquire(secondPlayer, 0));

        state.disconnect(firstPlayer);

        assertTrue(state.placementResult(firstPlayer, placementRequest).isEmpty());
        assertTrue(state.removalResult(firstPlayer, removalRequest).isEmpty());
        assertTrue(state.jobCancelResult(firstPlayer, jobCancelRequest).isEmpty());
        assertEquals(placement, state.placementResult(secondPlayer, placementRequest).orElseThrow());
        assertTrue(state.tryAcquire(firstPlayer, 0), "disconnect should reset the rate window");
        assertTrue(!state.tryAcquire(secondPlayer, 0), "other player state must remain intact");
    }
}
