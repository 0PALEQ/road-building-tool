package com.cookiecraftmods.roadbuilder.networking;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class PlayerRequestSessionState {
    private final RequestResultCache<PlacementResultS2CPacket> placementResults;
    private final RequestResultCache<RemovalResultS2CPacket> removalResults;
    private final RequestResultCache<ConstructionJobCancelResultS2CPacket> jobCancelResults;
    private final PlacementRequestRateLimiter rateLimiter;

    public PlayerRequestSessionState() {
        this(
                new RequestResultCache<>(PlacementResultS2CPacket::requestId),
                new RequestResultCache<>(RemovalResultS2CPacket::requestId),
                new RequestResultCache<>(ConstructionJobCancelResultS2CPacket::requestId),
                new PlacementRequestRateLimiter()
        );
    }

    PlayerRequestSessionState(
            RequestResultCache<PlacementResultS2CPacket> placementResults,
            RequestResultCache<RemovalResultS2CPacket> removalResults,
            RequestResultCache<ConstructionJobCancelResultS2CPacket> jobCancelResults,
            PlacementRequestRateLimiter rateLimiter
    ) {
        this.placementResults = Objects.requireNonNull(
                placementResults,
                "placementResults"
        );
        this.removalResults = Objects.requireNonNull(removalResults, "removalResults");
        this.jobCancelResults = Objects.requireNonNull(
                jobCancelResults,
                "jobCancelResults"
        );
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter");
    }

    public Optional<PlacementResultS2CPacket> placementResult(
            UUID playerId,
            UUID requestId
    ) {
        return placementResults.find(playerId, requestId);
    }

    public void rememberPlacement(UUID playerId, PlacementResultS2CPacket result) {
        placementResults.remember(playerId, result);
    }

    public Optional<RemovalResultS2CPacket> removalResult(
            UUID playerId,
            UUID requestId
    ) {
        return removalResults.find(playerId, requestId);
    }

    public void rememberRemoval(UUID playerId, RemovalResultS2CPacket result) {
        removalResults.remember(playerId, result);
    }

    public Optional<ConstructionJobCancelResultS2CPacket> jobCancelResult(
            UUID playerId,
            UUID requestId
    ) {
        return jobCancelResults.find(playerId, requestId);
    }

    public void rememberJobCancel(
            UUID playerId,
            ConstructionJobCancelResultS2CPacket result
    ) {
        jobCancelResults.remember(playerId, result);
    }

    public boolean tryAcquire(UUID playerId, int serverTick) {
        return rateLimiter.tryAcquire(playerId, serverTick);
    }

    public void disconnect(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        placementResults.clear(playerId);
        removalResults.clear(playerId);
        jobCancelResults.clear(playerId);
        rateLimiter.clear(playerId);
    }
}
