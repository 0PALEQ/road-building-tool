package com.cookiecraftmods.roadbuilder.networking;

import com.cookiecraftmods.roadbuilder.RoadBuilder;
import com.cookiecraftmods.roadbuilder.config.RoadBuilderServerPolicy;
import com.cookiecraftmods.roadbuilder.config.RoadBuilderServerPolicyLoader;
import com.cookiecraftmods.roadbuilder.network.RoadGraphSnapshot;
import com.cookiecraftmods.roadbuilder.persistence.RoadBuilderPlacementSavedData;
import com.cookiecraftmods.roadbuilder.persistence.RoadBuilderNetworkSavedData;
import com.cookiecraftmods.roadbuilder.persistence.RoadBuilderConstructionJobSavedData;
import com.cookiecraftmods.roadbuilder.persistence.DurableMutationJournal;
import com.cookiecraftmods.roadbuilder.placement.BlockPlanCommitter;
import com.cookiecraftmods.roadbuilder.placement.ConstructionJob;
import com.cookiecraftmods.roadbuilder.placement.ConstructionAuthorizer;
import com.cookiecraftmods.roadbuilder.placement.ConstructionLimits;
import com.cookiecraftmods.roadbuilder.placement.IncrementalRemovalJobService;
import com.cookiecraftmods.roadbuilder.placement.IncrementalPlacementJobService;
import com.cookiecraftmods.roadbuilder.placement.PlacementRemovalService;
import com.cookiecraftmods.roadbuilder.placement.ServerLevelPlacementBlockWorld;
import com.cookiecraftmods.roadbuilder.placement.StraightRoadPlanner;
import com.cookiecraftmods.roadbuilder.placement.StraightRoadPlanningService;
import com.cookiecraftmods.roadbuilder.placement.RoadPathPlanner;
import com.cookiecraftmods.roadbuilder.placement.RoadPathPlanningService;
import com.cookiecraftmods.roadbuilder.preset.RoadPresetResources;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;

import java.util.Optional;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RoadBuilderServerNetworking {
    private static final RoadBuilderServerPolicy SERVER_POLICY =
            RoadBuilderServerPolicyLoader.load();
    private static final PlayerRequestSessionState REQUEST_SESSIONS =
            new PlayerRequestSessionState();
    private static final ConstructionAuthorizer CONSTRUCTION_AUTHORIZER =
            ConstructionAuthorizer.creativeOnly();
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static final StraightRoadPlacementEndpoint STRAIGHT_ROAD_ENDPOINT =
            new StraightRoadPlacementEndpoint(
                    new StraightRoadPlanningService(
                            RoadPresetResources.repository(),
                            new StraightRoadPlanner()
                    ),
                    CONSTRUCTION_AUTHORIZER,
                    SERVER_POLICY.constructionLimits(),
                    new BlockPlanCommitter()
            );
    private static final RoadPathPlacementEndpoint ROAD_PATH_ENDPOINT =
            new RoadPathPlacementEndpoint(
                    new RoadPathPlanningService(
                            RoadPresetResources.repository(),
                            new RoadPathPlanner()
                    ),
                    CONSTRUCTION_AUTHORIZER,
                    SERVER_POLICY.constructionLimits(),
                    new BlockPlanCommitter(),
                    SERVER_POLICY.trafficHandedness()
            );
    private static final PlacementRemovalService REMOVAL_SERVICE =
            new PlacementRemovalService();
    private static final PlacementRemovalEndpoint REMOVAL_ENDPOINT =
            new PlacementRemovalEndpoint(
                    CONSTRUCTION_AUTHORIZER,
                    SERVER_POLICY.constructionLimits(),
                    REMOVAL_SERVICE
            );
    private static final SegmentReplacementEndpoint REPLACEMENT_ENDPOINT =
            new SegmentReplacementEndpoint(
                    RoadPresetResources.repository(),
                    CONSTRUCTION_AUTHORIZER,
                    SERVER_POLICY.constructionLimits(),
                    new BlockPlanCommitter()
            );
    private static final CrosswalkToggleEndpoint CROSSWALK_ENDPOINT =
            new CrosswalkToggleEndpoint(
                    CONSTRUCTION_AUTHORIZER,
                    SERVER_POLICY.constructionLimits(),
                    new BlockPlanCommitter()
            );
    private static final LaneConnectorEditEndpoint LANE_CONNECTOR_EDIT_ENDPOINT =
            new LaneConnectorEditEndpoint(
                    CONSTRUCTION_AUTHORIZER,
                    SERVER_POLICY.constructionLimits(),
                    new BlockPlanCommitter()
            );
    private static final IncrementalRemovalJobService INCREMENTAL_REMOVALS =
            new IncrementalRemovalJobService(REMOVAL_SERVICE);
    private static final IncrementalPlacementJobService INCREMENTAL_PLACEMENTS =
            new IncrementalPlacementJobService();

    private RoadBuilderServerNetworking() {
    }

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) {
            throw new IllegalStateException("Road Builder server networking is already registered");
        }

        boolean registered = ServerPlayNetworking.registerGlobalReceiver(
                StraightRoadIntentC2SPacket.TYPE,
                (packet, player, responseSender) -> receiveStraightRoadIntent(packet, player)
        );
        if (!registered) {
            throw new IllegalStateException("straight-road intent receiver is already registered");
        }
        boolean pathRegistered = ServerPlayNetworking.registerGlobalReceiver(
                RoadPathIntentC2SPacket.TYPE,
                (packet, player, responseSender) ->
                        receiveRoadPathIntent(packet, player)
        );
        if (!pathRegistered) {
            throw new IllegalStateException(
                    "road-path intent receiver is already registered"
            );
        }

        boolean removalRegistered = ServerPlayNetworking.registerGlobalReceiver(
                RemovalIntentC2SPacket.TYPE,
                (packet, player, responseSender) -> receiveRemovalIntent(packet, player)
        );
        if (!removalRegistered) {
            throw new IllegalStateException("removal intent receiver is already registered");
        }

        boolean segmentRemovalRegistered = ServerPlayNetworking.registerGlobalReceiver(
                SegmentRemovalIntentC2SPacket.TYPE,
                (packet, player, responseSender) -> receiveSegmentRemovalIntent(packet, player)
        );
        if (!segmentRemovalRegistered) {
            throw new IllegalStateException(
                    "segment removal intent receiver is already registered"
            );
        }

        boolean segmentReplaceRegistered = ServerPlayNetworking.registerGlobalReceiver(
                SegmentReplaceIntentC2SPacket.TYPE,
                (packet, player, responseSender) ->
                        receiveSegmentReplaceIntent(packet, player)
        );
        if (!segmentReplaceRegistered) {
            throw new IllegalStateException(
                    "segment replace intent receiver is already registered"
            );
        }
        boolean segmentDirectionInvertRegistered =
                ServerPlayNetworking.registerGlobalReceiver(
                        SegmentDirectionInvertIntentC2SPacket.TYPE,
                        (packet, player, responseSender) ->
                                receiveSegmentDirectionInvertIntent(packet, player)
                );
        if (!segmentDirectionInvertRegistered) {
            throw new IllegalStateException(
                    "segment direction invert intent receiver is already registered"
            );
        }
        boolean crosswalkToggleRegistered =
                ServerPlayNetworking.registerGlobalReceiver(
                        CrosswalkToggleIntentC2SPacket.TYPE,
                        (packet, player, responseSender) ->
                                receiveCrosswalkToggleIntent(packet, player)
                );
        if (!crosswalkToggleRegistered) {
            throw new IllegalStateException(
                    "crosswalk toggle intent receiver is already registered"
            );
        }

        boolean laneConnectorEditRegistered =
                ServerPlayNetworking.registerGlobalReceiver(
                        LaneConnectorEditC2SPacket.TYPE,
                        (packet, player, responseSender) ->
                                receiveLaneConnectorEdit(packet, player)
                );
        if (!laneConnectorEditRegistered) {
            throw new IllegalStateException(
                    "lane connector edit receiver is already registered"
            );
        }

        boolean resyncRegistered = ServerPlayNetworking.registerGlobalReceiver(
                RoadGraphResyncC2SPacket.TYPE,
                (packet, player, responseSender) -> receiveRoadGraphResync(packet, player)
        );
        if (!resyncRegistered) {
            throw new IllegalStateException("road graph resync receiver is already registered");
        }

        boolean jobCancelRegistered = ServerPlayNetworking.registerGlobalReceiver(
                ConstructionJobCancelC2SPacket.TYPE,
                (packet, player, responseSender) ->
                        receiveConstructionJobCancel(packet, player)
        );
        if (!jobCancelRegistered) {
            throw new IllegalStateException(
                    "construction-job cancel receiver is already registered"
            );
        }

        boolean presetContentRegistered =
                ServerPlayNetworking.registerGlobalReceiver(
                        PresetContentRequestC2SPacket.TYPE,
                        (packet, player, responseSender) ->
                                RoadPresetNetworking.receiveContentRequest(packet, player)
                );
        if (!presetContentRegistered) {
            throw new IllegalStateException(
                    "preset-content receiver is already registered"
            );
        }
        boolean presetSaveRegistered =
                ServerPlayNetworking.registerGlobalReceiver(
                        PresetSaveIntentC2SPacket.TYPE,
                        (packet, player, responseSender) ->
                                RoadPresetNetworking.receiveSaveIntent(packet, player)
                );
        if (!presetSaveRegistered) {
            throw new IllegalStateException(
                    "preset-save receiver is already registered"
            );
        }
        boolean presetDeleteRegistered =
                ServerPlayNetworking.registerGlobalReceiver(
                        PresetDeleteIntentC2SPacket.TYPE,
                        (packet, player, responseSender) ->
                                RoadPresetNetworking.receiveDeleteIntent(packet, player)
                );
        if (!presetDeleteRegistered) {
            throw new IllegalStateException(
                    "preset-delete receiver is already registered"
            );
        }

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            RoadGraphSynchronizer.sendCurrentSnapshot(handler.player);
            sendCurrentJobProgress(handler.player);
            RoadPresetNetworking.sendCatalog(handler.player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            REQUEST_SESSIONS.disconnect(handler.player.getUUID());
        });
        ServerTickEvents.END_WORLD_TICK.register(
                RoadBuilderServerNetworking::tickConstructionJobs
        );
    }

    public static void broadcastPresetCatalog(MinecraftServer server) {
        RoadPresetNetworking.broadcastCatalog(server);
    }

    private static void receiveStraightRoadIntent(
            StraightRoadIntentC2SPacket packet,
            ServerPlayer player
    ) {
        // Never mutate if this connection cannot receive the operation ID.
        if (!ServerPlayNetworking.canSend(player, PlacementResultS2CPacket.TYPE)) {
            return;
        }

        UUID playerId = player.getUUID();
        PlacementResultS2CPacket cached = REQUEST_SESSIONS
                .placementResult(playerId, packet.requestId())
                .orElse(null);
        if (cached != null) {
            ServerPlayNetworking.send(player, cached);
            return;
        }

        RoadGraphSnapshot graphBefore = RoadGraphSynchronizer.currentSnapshot(player.serverLevel());
        PlacementResultS2CPacket result;
        if (!REQUEST_SESSIONS.tryAcquire(
                playerId,
                player.serverLevel().getServer().getTickCount()
        )) {
            result = PlacementResultS2CPacket.rejected(packet.requestId(), PlacementResultCode.RATE_LIMITED);
        } else {
            result = process(packet, player);
        }
        REQUEST_SESSIONS.rememberPlacement(playerId, result);
        if (result.resultCode() == PlacementResultCode.ACCEPTED) {
            RoadGraphSynchronizer.broadcastMutation(player.serverLevel(), graphBefore);
        }
        if (result.resultCode() == PlacementResultCode.ACCEPTED) {
            result.acceptedPlacement().ifPresent(accepted ->
                    sendJobProgress(
                            player.serverLevel(),
                            accepted.operationId()
                    ));
        }
        ServerPlayNetworking.send(player, result);
    }

    private static void receiveRoadPathIntent(
            RoadPathIntentC2SPacket packet,
            ServerPlayer player
    ) {
        if (!ServerPlayNetworking.canSend(
                player,
                PlacementResultS2CPacket.TYPE
        )) {
            return;
        }
        UUID playerId = player.getUUID();
        PlacementResultS2CPacket cached = REQUEST_SESSIONS
                .placementResult(playerId, packet.requestId())
                .orElse(null);
        if (cached != null) {
            ServerPlayNetworking.send(player, cached);
            return;
        }

        RoadGraphSnapshot graphBefore =
                RoadGraphSynchronizer.currentSnapshot(player.serverLevel());
        PlacementResultS2CPacket result;
        if (!REQUEST_SESSIONS.tryAcquire(
                playerId,
                player.serverLevel().getServer().getTickCount()
        )) {
            result = PlacementResultS2CPacket.rejected(
                    packet.requestId(),
                    PlacementResultCode.RATE_LIMITED
            );
        } else {
            result = process(packet, player);
        }
        REQUEST_SESSIONS.rememberPlacement(playerId, result);
        if (result.resultCode() == PlacementResultCode.ACCEPTED) {
            RoadGraphSynchronizer.broadcastMutation(player.serverLevel(), graphBefore);
        }
        if (result.resultCode() == PlacementResultCode.ACCEPTED) {
            result.acceptedPlacement().ifPresent(accepted ->
                    sendJobProgress(
                            player.serverLevel(),
                            accepted.operationId()
                    ));
        }
        ServerPlayNetworking.send(player, result);
    }

    private static void receiveRemovalIntent(
            RemovalIntentC2SPacket packet,
            ServerPlayer player
    ) {
        // Never mutate if this connection cannot receive conflict/removal accounting.
        if (!ServerPlayNetworking.canSend(player, RemovalResultS2CPacket.TYPE)) {
            return;
        }

        UUID playerId = player.getUUID();
        RemovalResultS2CPacket cached = REQUEST_SESSIONS
                .removalResult(playerId, packet.requestId())
                .orElse(null);
        if (cached != null) {
            ServerPlayNetworking.send(player, cached);
            return;
        }

        RoadGraphSnapshot graphBefore = RoadGraphSynchronizer.currentSnapshot(player.serverLevel());
        RemovalResultS2CPacket result;
        if (!REQUEST_SESSIONS.tryAcquire(
                playerId,
                player.serverLevel().getServer().getTickCount()
        )) {
            result = RemovalResultS2CPacket.rejected(packet, RemovalResultCode.RATE_LIMITED);
        } else {
            result = processRemoval(packet, player);
        }
        REQUEST_SESSIONS.rememberRemoval(playerId, result);
        if (packet.action() == RemovalRequestAction.EXECUTE
                && result.resultCode() == RemovalResultCode.SUCCESS) {
            RoadGraphSynchronizer.broadcastMutation(player.serverLevel(), graphBefore);
        }
        ServerPlayNetworking.send(player, result);
        if (result.resultCode() == RemovalResultCode.QUEUED) {
            sendJobProgress(
                    player.serverLevel(),
                    result.operationId()
            );
        }
    }

    private static void receiveSegmentRemovalIntent(
            SegmentRemovalIntentC2SPacket packet,
            ServerPlayer player
    ) {
        if (!ServerPlayNetworking.canSend(player, RemovalResultS2CPacket.TYPE)) {
            return;
        }

        UUID playerId = player.getUUID();
        RemovalResultS2CPacket cached = REQUEST_SESSIONS
                .removalResult(playerId, packet.requestId())
                .orElse(null);
        if (cached != null) {
            ServerPlayNetworking.send(player, cached);
            return;
        }

        RoadGraphSnapshot graphBefore = RoadGraphSynchronizer.currentSnapshot(player.serverLevel());
        RemovalResultS2CPacket result;
        if (!REQUEST_SESSIONS.tryAcquire(
                playerId,
                player.serverLevel().getServer().getTickCount()
        )) {
            result = RemovalResultS2CPacket.rejected(
                    packet,
                    RemovalResultCode.RATE_LIMITED
            );
        } else {
            result = processSegmentRemoval(packet, player);
        }
        REQUEST_SESSIONS.rememberRemoval(playerId, result);
        if (packet.action() == RemovalRequestAction.EXECUTE
                && result.resultCode() == RemovalResultCode.SUCCESS) {
            RoadGraphSynchronizer.broadcastMutation(player.serverLevel(), graphBefore);
        }
        ServerPlayNetworking.send(player, result);
        if (result.resultCode() == RemovalResultCode.QUEUED) {
            sendJobProgress(
                    player.serverLevel(),
                    result.operationId()
            );
        }
        if (result.resultCode() == RemovalResultCode.STALE_GRAPH) {
            RoadGraphSynchronizer.sendCurrentSnapshot(player);
        }
    }

    private static void receiveSegmentReplaceIntent(
            SegmentReplaceIntentC2SPacket packet,
            ServerPlayer player
    ) {
        if (!ServerPlayNetworking.canSend(player, PlacementResultS2CPacket.TYPE)) {
            return;
        }
        UUID playerId = player.getUUID();
        PlacementResultS2CPacket cached = REQUEST_SESSIONS
                .placementResult(playerId, packet.requestId())
                .orElse(null);
        if (cached != null) {
            ServerPlayNetworking.send(player, cached);
            return;
        }
        RoadGraphSnapshot graphBefore = RoadGraphSynchronizer.currentSnapshot(player.serverLevel());
        PlacementResultS2CPacket result;
        if (!REQUEST_SESSIONS.tryAcquire(
                playerId,
                player.serverLevel().getServer().getTickCount())) {
            result = PlacementResultS2CPacket.rejected(
                    packet.requestId(), PlacementResultCode.RATE_LIMITED
            );
        } else {
            result = process(packet, player);
        }
        REQUEST_SESSIONS.rememberPlacement(playerId, result);
        if (result.resultCode() == PlacementResultCode.ACCEPTED) {
            RoadGraphSynchronizer.broadcastMutation(player.serverLevel(), graphBefore);
        } else if (result.resultCode() == PlacementResultCode.GRAPH_REVISION_MISMATCH) {
            RoadGraphSynchronizer.sendCurrentSnapshot(player);
        }
        ServerPlayNetworking.send(player, result);
    }

    private static void receiveSegmentDirectionInvertIntent(
            SegmentDirectionInvertIntentC2SPacket packet,
            ServerPlayer player
    ) {
        if (!ServerPlayNetworking.canSend(player, PlacementResultS2CPacket.TYPE)) {
            return;
        }
        UUID playerId = player.getUUID();
        PlacementResultS2CPacket cached = REQUEST_SESSIONS
                .placementResult(playerId, packet.requestId())
                .orElse(null);
        if (cached != null) {
            ServerPlayNetworking.send(player, cached);
            return;
        }
        RoadGraphSnapshot graphBefore = RoadGraphSynchronizer.currentSnapshot(player.serverLevel());
        PlacementResultS2CPacket result;
        if (!REQUEST_SESSIONS.tryAcquire(
                playerId,
                player.serverLevel().getServer().getTickCount())) {
            result = PlacementResultS2CPacket.rejected(
                    packet.requestId(), PlacementResultCode.RATE_LIMITED
            );
        } else {
            result = process(packet, player);
        }
        REQUEST_SESSIONS.rememberPlacement(playerId, result);
        if (result.resultCode() == PlacementResultCode.ACCEPTED) {
            RoadGraphSynchronizer.broadcastMutation(player.serverLevel(), graphBefore);
        } else if (result.resultCode() == PlacementResultCode.GRAPH_REVISION_MISMATCH) {
            RoadGraphSynchronizer.sendCurrentSnapshot(player);
        }
        ServerPlayNetworking.send(player, result);
    }

    private static void receiveCrosswalkToggleIntent(
            CrosswalkToggleIntentC2SPacket packet,
            ServerPlayer player
    ) {
        if (!ServerPlayNetworking.canSend(
                player,
                PlacementResultS2CPacket.TYPE
        )) {
            return;
        }
        UUID playerId = player.getUUID();
        PlacementResultS2CPacket cached = REQUEST_SESSIONS
                .placementResult(playerId, packet.requestId())
                .orElse(null);
        if (cached != null) {
            ServerPlayNetworking.send(player, cached);
            return;
        }
        RoadGraphSnapshot graphBefore = RoadGraphSynchronizer.currentSnapshot(
                player.serverLevel()
        );
        PlacementResultS2CPacket result;
        if (!REQUEST_SESSIONS.tryAcquire(
                playerId,
                player.serverLevel().getServer().getTickCount()
        )) {
            result = PlacementResultS2CPacket.rejected(
                    packet.requestId(),
                    PlacementResultCode.RATE_LIMITED
            );
        } else {
            result = process(packet, player);
        }
        REQUEST_SESSIONS.rememberPlacement(playerId, result);
        if (result.resultCode() == PlacementResultCode.ACCEPTED) {
            RoadGraphSynchronizer.broadcastMutation(
                    player.serverLevel(),
                    graphBefore
            );
        } else if (result.resultCode()
                == PlacementResultCode.GRAPH_REVISION_MISMATCH) {
            RoadGraphSynchronizer.sendCurrentSnapshot(player);
        }
        ServerPlayNetworking.send(player, result);
    }

    private static void receiveRoadGraphResync(
            RoadGraphResyncC2SPacket packet,
            ServerPlayer player
    ) {
        if (!player.serverLevel().dimension().location().equals(packet.dimensionId())
                || !ServerPlayNetworking.canSend(player, RoadGraphSnapshotS2CPacket.TYPE)) {
            return;
        }

        RoadBuilderNetworkSavedData savedData = RoadBuilderNetworkSavedData.get(
                player.serverLevel()
        );
        if (!savedData.isCompatible()) {
            RoadBuilder.LOGGER.error(
                    "Cannot synchronize road graph in {} to player {} because saved data is incompatible: {}",
                    packet.dimensionId(),
                    player.getUUID(),
                    savedData.incompatibilityReason().orElse("unknown reason")
            );
            return;
        }
        if (savedData.graph().revision() != packet.knownRevision()) {
            RoadGraphSynchronizer.sendSnapshot(player, savedData.graph().snapshot());
        }
    }

    private static void receiveLaneConnectorEdit(
            LaneConnectorEditC2SPacket packet,
            ServerPlayer player
    ) {
        ServerLevel level = player.serverLevel();
        if (!level.dimension().location().equals(packet.dimensionId())
                || !CONSTRUCTION_AUTHORIZER.validatePlayer(player)
                        .isAllowed()) {
            RoadGraphSynchronizer.sendCurrentSnapshot(player);
            return;
        }
        try {
            RoadBuilderNetworkSavedData savedData =
                    RoadBuilderNetworkSavedData.get(level);
            if (!savedData.isCompatible()) {
                RoadGraphSynchronizer.sendCurrentSnapshot(player);
                return;
            }
            var graph = savedData.graph();
            if (graph.revision() != packet.expectedGraphRevision()) {
                RoadGraphSynchronizer.sendCurrentSnapshot(player);
                return;
            }
            RoadGraphSnapshot before = graph.snapshot();
            PlacementResultCode result = LANE_CONNECTOR_EDIT_ENDPOINT.handle(
                    packet,
                    player,
                    RoadBuilderPlacementSavedData.get(level).ledger(),
                    graph
            );
            if (result == PlacementResultCode.ACCEPTED) {
                RoadGraphSynchronizer.broadcastMutation(level, before);
            } else {
                RoadBuilder.LOGGER.warn(
                        "Rejected lane connector edit from {}: {}",
                        player.getUUID(),
                        result
                );
                RoadGraphSynchronizer.sendCurrentSnapshot(player);
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            RoadBuilder.LOGGER.warn(
                    "Rejected lane connector edit from {}: {}",
                    player.getUUID(),
                    exception.getMessage()
            );
            RoadGraphSynchronizer.sendCurrentSnapshot(player);
        }
    }

    private static void receiveConstructionJobCancel(
            ConstructionJobCancelC2SPacket packet,
            ServerPlayer player
    ) {
        if (!ServerPlayNetworking.canSend(
                player,
                ConstructionJobCancelResultS2CPacket.TYPE
        )) {
            return;
        }

        UUID playerId = player.getUUID();
        ConstructionJobCancelResultS2CPacket cached = REQUEST_SESSIONS
                .jobCancelResult(playerId, packet.requestId())
                .orElse(null);
        if (cached != null) {
            ServerPlayNetworking.send(player, cached);
            return;
        }

        ConstructionJobCancelResultS2CPacket result;
        ConstructionJob updatedJob = null;
        if (!REQUEST_SESSIONS.tryAcquire(
                playerId,
                player.serverLevel().getServer().getTickCount()
        )) {
            result = ConstructionJobCancelResultS2CPacket.current(
                    packet,
                    ConstructionJobCancelResultCode.RATE_LIMITED
            );
        } else if (!player.serverLevel().dimension().location().equals(
                packet.dimensionId()
        )) {
            result = ConstructionJobCancelResultS2CPacket.current(
                    packet,
                    ConstructionJobCancelResultCode.DIMENSION_MISMATCH
            );
        } else {
            ConstructionJobCancellationService.Outcome outcome =
                    new ConstructionJobCancellationService().cancel(
                            packet,
                            playerId,
                            RoadBuilderConstructionJobSavedData.get(
                                    player.serverLevel()
                            )
                    );
            result = outcome.result();
            updatedJob = outcome.updatedJob().orElse(null);
        }

        REQUEST_SESSIONS.rememberJobCancel(playerId, result);
        ServerPlayNetworking.send(player, result);
        if (updatedJob != null && ServerPlayNetworking.canSend(
                player,
                ConstructionJobProgressS2CPacket.TYPE
        )) {
            ServerPlayNetworking.send(
                    player,
                    ConstructionJobProgressS2CPacket.current(
                            packet.dimensionId(),
                            updatedJob
                    )
            );
        }
    }

    private static void sendCurrentJobProgress(ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(
                player,
                ConstructionJobProgressS2CPacket.TYPE
        )) {
            return;
        }
        RoadBuilderConstructionJobSavedData data =
                RoadBuilderConstructionJobSavedData.get(player.serverLevel());
        if (!data.isCompatible()) {
            return;
        }
        for (ConstructionJob job : data.jobs()) {
            if (job.requestedBy().equals(player.getUUID())) {
                ServerPlayNetworking.send(
                        player,
                        ConstructionJobProgressS2CPacket.current(
                                player.serverLevel().dimension().location(),
                                job
                        )
                );
            }
        }
    }

    private static void sendJobProgress(ServerLevel level, UUID jobId) {
        RoadBuilderConstructionJobSavedData data =
                RoadBuilderConstructionJobSavedData.get(level);
        if (!data.isCompatible()) {
            return;
        }
        data.job(jobId).ifPresent(job -> sendJobProgress(level, job));
    }

    private static void sendJobProgress(ServerLevel level, ConstructionJob job) {
        ServerPlayer owner = level.getServer()
                .getPlayerList()
                .getPlayer(job.requestedBy());
        if (owner != null && ServerPlayNetworking.canSend(
                owner,
                ConstructionJobProgressS2CPacket.TYPE
        )) {
            ServerPlayNetworking.send(
                    owner,
                    ConstructionJobProgressS2CPacket.current(
                            level.dimension().location(),
                            job
                    )
            );
        }
    }

    private static void tickConstructionJobs(ServerLevel level) {
        RoadBuilderConstructionJobSavedData jobs =
                RoadBuilderConstructionJobSavedData.get(level);
        if (!jobs.isCompatible()) {
            return;
        }
        RoadBuilderPlacementSavedData placements =
                RoadBuilderPlacementSavedData.get(level);
        RoadBuilderNetworkSavedData network = RoadBuilderNetworkSavedData.get(level);
        if (!placements.isCompatible() || !network.isCompatible()) {
            return;
        }

        int recoveryJobCount = jobs.prepareLoadedJobsForWorldReconciliation();
        if (recoveryJobCount > 0) {
            RoadBuilder.LOGGER.warn(
                    "Reconciling {} journaled Road Builder job(s) in {} from actual world states",
                    recoveryJobCount,
                    level.dimension().location()
            );
        }

        int remainingTransitions = SERVER_POLICY.transitionsPerTick();
        for (ConstructionJob job : jobs.jobs()) {
            if (job.phase()
                    == com.cookiecraftmods.roadbuilder.placement.ConstructionJobPhase.QUARANTINED
                    && job.action()
                    != com.cookiecraftmods.roadbuilder.placement.ConstructionJobAction.PLACE
                    && !IncrementalRemovalJobService.canRecover(
                            job,
                            placements.ledger()
                    )) {
                continue;
            }
            if (remainingTransitions <= 0) {
                break;
            }

            RoadGraphSnapshot graphBefore = network.graph().snapshot();
            ConstructionJob updatedJob;
            int examinedTransitions;
            boolean graphChanged;
            String outcome;
            try {
                if (job.action()
                        == com.cookiecraftmods.roadbuilder.placement.ConstructionJobAction.REMOVE) {
                    IncrementalRemovalJobService.TickResult result =
                            INCREMENTAL_REMOVALS.tick(
                                    job,
                                    remainingTransitions,
                                    new ServerLevelPlacementBlockWorld(level),
                                    placements.ledger(),
                                    network.graph()
                            );
                    updatedJob = result.job();
                    examinedTransitions = result.examinedTransitions();
                    graphChanged = result.graphChanged();
                    outcome = result.outcome().name();
                } else {
                    IncrementalPlacementJobService.TickResult result =
                            INCREMENTAL_PLACEMENTS.tick(
                                    job,
                                    remainingTransitions,
                                    new ServerLevelPlacementBlockWorld(level),
                                    placements.ledger(),
                                    network.graph()
                            );
                    updatedJob = result.job();
                    examinedTransitions = result.examinedTransitions();
                    graphChanged = result.graphChanged();
                    outcome = result.outcome().name();
                }
            } catch (RuntimeException exception) {
                RoadBuilder.LOGGER.error(
                        "Unexpected construction-job tick failure for job {} in {}",
                        job.id(),
                        level.dimension().location(),
                        exception
                );
                continue;
            }
            remainingTransitions -= examinedTransitions;

            switch (outcome) {
                case "UPDATED", "QUARANTINED" -> {
                    jobs.replace(updatedJob);
                    sendJobProgress(level, updatedJob);
                }
                case "COMPLETED" -> {
                    jobs.remove(updatedJob.id());
                    if (graphChanged) {
                        RoadGraphSynchronizer.broadcastMutation(level, graphBefore);
                    }
                    sendJobProgress(level, updatedJob);
                }
                case "CANCELLED" -> {
                    sendJobProgress(level, updatedJob);
                    jobs.remove(updatedJob.id());
                }
                case "SKIPPED" -> {
                    // Action-specific services ignore jobs they do not own.
                }
                default -> throw new IllegalStateException(
                        "unknown construction-job outcome " + outcome
                );
            }
            if (!"SKIPPED".equals(outcome)) {
                try {
                    DurableMutationJournal.flush(level, jobs);
                } catch (java.io.IOException journalFailure) {
                    RoadBuilder.LOGGER.error(
                            "Could not checkpoint durable construction journal after job {} in {}",
                            updatedJob.id(),
                            level.dimension().location(),
                            journalFailure
                    );
                }
            }
        }
    }

    private static PlacementResultS2CPacket process(
            StraightRoadIntentC2SPacket packet,
            ServerPlayer player
    ) {
        try {
            RoadBuilderPlacementSavedData savedData = RoadBuilderPlacementSavedData.get(player.serverLevel());
            if (!savedData.isCompatible()) {
                RoadBuilder.LOGGER.error(
                        "Refusing placement request {} in {} because ownership data is incompatible: {}",
                        packet.requestId(),
                        player.serverLevel().dimension().location(),
                        savedData.incompatibilityReason().orElse("unknown reason")
                );
                return PlacementResultS2CPacket.rejected(
                        packet.requestId(),
                        PlacementResultCode.PERSISTENCE_INCOMPATIBLE
                );
            }
            RoadBuilderNetworkSavedData networkData = RoadBuilderNetworkSavedData.get(
                    player.serverLevel()
            );
            RoadBuilderConstructionJobSavedData jobData =
                    RoadBuilderConstructionJobSavedData.get(player.serverLevel());
            if (!networkData.isCompatible() || !jobData.isCompatible()) {
                RoadBuilder.LOGGER.error(
                        "Refusing placement request {} in {} because graph data is incompatible: {}",
                        packet.requestId(),
                        player.serverLevel().dimension().location(),
                        networkData.incompatibilityReason().orElse("unknown reason")
                );
                return PlacementResultS2CPacket.rejected(
                        packet.requestId(),
                        PlacementResultCode.PERSISTENCE_INCOMPATIBLE
                );
            }
            if (!CONSTRUCTION_AUTHORIZER
                    .validatePlayer(player)
                    .isAllowed()) {
                return PlacementResultS2CPacket.rejected(
                        packet.requestId(),
                        PlacementResultCode.NOT_AUTHORIZED
                );
            }
            if (hasActiveConstructionJob(jobData)) {
                return PlacementResultS2CPacket.rejected(
                        packet.requestId(),
                        PlacementResultCode.CONSTRUCTION_BUSY
                );
            }
            return STRAIGHT_ROAD_ENDPOINT.handle(
                    packet,
                    player,
                    savedData.ledger(),
                    networkData.graph(),
                    jobData
            );
        } catch (RuntimeException exception) {
            RoadBuilder.LOGGER.error(
                    "Unexpected straight-road request failure for request {} from player {}",
                    packet.requestId(),
                    player.getUUID(),
                    exception
            );
            return PlacementResultS2CPacket.rejected(
                    packet.requestId(),
                    PlacementResultCode.INTERNAL_ERROR
            );
        }
    }

    private static PlacementResultS2CPacket process(
            RoadPathIntentC2SPacket packet,
            ServerPlayer player
    ) {
        try {
            RoadBuilderPlacementSavedData savedData =
                    RoadBuilderPlacementSavedData.get(player.serverLevel());
            RoadBuilderNetworkSavedData networkData =
                    RoadBuilderNetworkSavedData.get(player.serverLevel());
            RoadBuilderConstructionJobSavedData jobData =
                    RoadBuilderConstructionJobSavedData.get(
                            player.serverLevel()
                    );
            if (!savedData.isCompatible()
                    || !networkData.isCompatible()
                    || !jobData.isCompatible()) {
                return PlacementResultS2CPacket.rejected(
                        packet.requestId(),
                        PlacementResultCode.PERSISTENCE_INCOMPATIBLE
                );
            }
            if (!CONSTRUCTION_AUTHORIZER
                    .validatePlayer(player)
                    .isAllowed()) {
                return PlacementResultS2CPacket.rejected(
                        packet.requestId(),
                        PlacementResultCode.NOT_AUTHORIZED
                );
            }
            if (hasActiveConstructionJob(jobData)) {
                return PlacementResultS2CPacket.rejected(
                        packet.requestId(),
                        PlacementResultCode.CONSTRUCTION_BUSY
                );
            }
            return ROAD_PATH_ENDPOINT.handle(
                    packet,
                    player,
                    savedData.ledger(),
                    networkData.graph(),
                    jobData
            );
        } catch (RuntimeException exception) {
            RoadBuilder.LOGGER.error(
                    "Unexpected road-path request failure for request {} from player {}",
                    packet.requestId(),
                    player.getUUID(),
                    exception
            );
            return PlacementResultS2CPacket.rejected(
                    packet.requestId(),
                    PlacementResultCode.INTERNAL_ERROR
            );
        }
    }

    private static PlacementResultS2CPacket process(
            SegmentReplaceIntentC2SPacket packet,
            ServerPlayer player
    ) {
        try {
            RoadBuilderPlacementSavedData savedData =
                    RoadBuilderPlacementSavedData.get(player.serverLevel());
            RoadBuilderNetworkSavedData networkData =
                    RoadBuilderNetworkSavedData.get(player.serverLevel());
            RoadBuilderConstructionJobSavedData jobData =
                    RoadBuilderConstructionJobSavedData.get(player.serverLevel());
            if (!savedData.isCompatible()
                    || !networkData.isCompatible()
                    || !jobData.isCompatible()) {
                return PlacementResultS2CPacket.rejected(
                        packet.requestId(),
                        PlacementResultCode.PERSISTENCE_INCOMPATIBLE
                );
            }
            if (hasActiveConstructionJob(jobData)) {
                return PlacementResultS2CPacket.rejected(
                        packet.requestId(), PlacementResultCode.CONSTRUCTION_BUSY
                );
            }
            return REPLACEMENT_ENDPOINT.handle(
                    packet,
                    player,
                    savedData.ledger(),
                    networkData.graph()
            );
        } catch (RuntimeException exception) {
            RoadBuilder.LOGGER.error(
                    "Unexpected segment replacement failure for request {} from player {}",
                    packet.requestId(), player.getUUID(), exception
            );
            return PlacementResultS2CPacket.rejected(
                    packet.requestId(), PlacementResultCode.INTERNAL_ERROR
            );
        }
    }

    private static PlacementResultS2CPacket process(
            SegmentDirectionInvertIntentC2SPacket packet,
            ServerPlayer player
    ) {
        try {
            RoadBuilderPlacementSavedData savedData =
                    RoadBuilderPlacementSavedData.get(player.serverLevel());
            RoadBuilderNetworkSavedData networkData =
                    RoadBuilderNetworkSavedData.get(player.serverLevel());
            RoadBuilderConstructionJobSavedData jobData =
                    RoadBuilderConstructionJobSavedData.get(player.serverLevel());
            if (!savedData.isCompatible()
                    || !networkData.isCompatible()
                    || !jobData.isCompatible()) {
                return PlacementResultS2CPacket.rejected(
                        packet.requestId(),
                        PlacementResultCode.PERSISTENCE_INCOMPATIBLE
                );
            }
            if (hasActiveConstructionJob(jobData)) {
                return PlacementResultS2CPacket.rejected(
                        packet.requestId(), PlacementResultCode.CONSTRUCTION_BUSY
                );
            }
            return REPLACEMENT_ENDPOINT.handleDirectionInversion(
                    packet,
                    player,
                    savedData.ledger(),
                    networkData.graph()
            );
        } catch (RuntimeException exception) {
            RoadBuilder.LOGGER.error(
                    "Unexpected segment direction inversion failure for request {} from player {}",
                    packet.requestId(), player.getUUID(), exception
            );
            return PlacementResultS2CPacket.rejected(
                    packet.requestId(), PlacementResultCode.INTERNAL_ERROR
            );
        }
    }

    private static PlacementResultS2CPacket process(
            CrosswalkToggleIntentC2SPacket packet,
            ServerPlayer player
    ) {
        try {
            RoadBuilderPlacementSavedData savedData =
                    RoadBuilderPlacementSavedData.get(player.serverLevel());
            RoadBuilderNetworkSavedData networkData =
                    RoadBuilderNetworkSavedData.get(player.serverLevel());
            RoadBuilderConstructionJobSavedData jobData =
                    RoadBuilderConstructionJobSavedData.get(
                            player.serverLevel()
                    );
            if (!savedData.isCompatible()
                    || !networkData.isCompatible()
                    || !jobData.isCompatible()) {
                return PlacementResultS2CPacket.rejected(
                        packet.requestId(),
                        PlacementResultCode.PERSISTENCE_INCOMPATIBLE
                );
            }
            if (hasActiveConstructionJob(jobData)) {
                return PlacementResultS2CPacket.rejected(
                        packet.requestId(),
                        PlacementResultCode.CONSTRUCTION_BUSY
                );
            }
            return CROSSWALK_ENDPOINT.handle(
                    packet,
                    player,
                    savedData.ledger(),
                    networkData.graph()
            );
        } catch (RuntimeException exception) {
            RoadBuilder.LOGGER.error(
                    "Unexpected crosswalk toggle failure for request {} from player {}",
                    packet.requestId(),
                    player.getUUID(),
                    exception
            );
            return PlacementResultS2CPacket.rejected(
                    packet.requestId(),
                    PlacementResultCode.INTERNAL_ERROR
            );
        }
    }

    private static RemovalResultS2CPacket processRemoval(
            RemovalIntentC2SPacket packet,
            ServerPlayer player
    ) {
        try {
            RoadBuilderPlacementSavedData savedData = RoadBuilderPlacementSavedData.get(player.serverLevel());
            if (!savedData.isCompatible()) {
                RoadBuilder.LOGGER.error(
                        "Refusing removal {} request {} for operation {} in {} because ownership data is incompatible: {}",
                        packet.action(),
                        packet.requestId(),
                        packet.operationId(),
                        player.serverLevel().dimension().location(),
                        savedData.incompatibilityReason().orElse("unknown reason")
                );
                return RemovalResultS2CPacket.rejected(
                        packet,
                        RemovalResultCode.PERSISTENCE_INCOMPATIBLE
                );
            }
            RoadBuilderNetworkSavedData networkData = RoadBuilderNetworkSavedData.get(
                    player.serverLevel()
            );
            RoadBuilderConstructionJobSavedData jobData =
                    RoadBuilderConstructionJobSavedData.get(player.serverLevel());
            if (!networkData.isCompatible() || !jobData.isCompatible()) {
                RoadBuilder.LOGGER.error(
                        "Refusing removal {} request {} for operation {} in {} because graph data is incompatible: {}",
                        packet.action(),
                        packet.requestId(),
                        packet.operationId(),
                        player.serverLevel().dimension().location(),
                        networkData.incompatibilityReason().orElse("unknown reason")
                );
                return RemovalResultS2CPacket.rejected(
                        packet,
                        RemovalResultCode.PERSISTENCE_INCOMPATIBLE
                );
            }
            if (packet.action() == RemovalRequestAction.EXECUTE
                    && hasActivePlacementJob(jobData)) {
                return RemovalResultS2CPacket.rejected(
                        packet,
                        RemovalResultCode.MUTATION_FAILED
                );
            }
            return REMOVAL_ENDPOINT.handle(
                    packet,
                    player,
                    savedData.ledger(),
                    networkData.graph(),
                    jobData
            );
        } catch (RuntimeException exception) {
            RoadBuilder.LOGGER.error(
                    "Unexpected removal {} request failure for request {}, operation {}, player {}",
                    packet.action(),
                    packet.requestId(),
                    packet.operationId(),
                    player.getUUID(),
                    exception
            );
            return RemovalResultS2CPacket.rejected(packet, RemovalResultCode.INTERNAL_ERROR);
        }
    }

    private static RemovalResultS2CPacket processSegmentRemoval(
            SegmentRemovalIntentC2SPacket packet,
            ServerPlayer player
    ) {
        try {
            RoadBuilderPlacementSavedData savedData = RoadBuilderPlacementSavedData.get(
                    player.serverLevel()
            );
            RoadBuilderNetworkSavedData networkData = RoadBuilderNetworkSavedData.get(
                    player.serverLevel()
            );
            RoadBuilderConstructionJobSavedData jobData =
                    RoadBuilderConstructionJobSavedData.get(player.serverLevel());
            if (!savedData.isCompatible()
                    || !networkData.isCompatible()
                    || !jobData.isCompatible()) {
                RoadBuilder.LOGGER.error(
                        "Refusing segment removal {} request {} for segment {} in {} because saved data is incompatible",
                        packet.action(),
                        packet.requestId(),
                        packet.segmentId(),
                        player.serverLevel().dimension().location()
                );
                return RemovalResultS2CPacket.rejected(
                        packet,
                        RemovalResultCode.PERSISTENCE_INCOMPATIBLE
                );
            }
            if (packet.action() == RemovalRequestAction.EXECUTE
                    && hasActivePlacementJob(jobData)) {
                return RemovalResultS2CPacket.rejected(
                        packet,
                        RemovalResultCode.MUTATION_FAILED
                );
            }
            return REMOVAL_ENDPOINT.handle(
                    packet,
                    player,
                    savedData.ledger(),
                    networkData.graph(),
                    jobData
            );
        } catch (RuntimeException exception) {
            RoadBuilder.LOGGER.error(
                    "Unexpected segment removal {} request failure for request {}, segment {}, player {}",
                    packet.action(),
                    packet.requestId(),
                    packet.segmentId(),
                    player.getUUID(),
                    exception
            );
            return RemovalResultS2CPacket.rejected(packet, RemovalResultCode.INTERNAL_ERROR);
        }
    }

    private static boolean hasActivePlacementJob(
            RoadBuilderConstructionJobSavedData jobs
    ) {
        return jobs.jobs().stream().anyMatch(job ->
                job.action()
                        == com.cookiecraftmods.roadbuilder.placement.ConstructionJobAction.PLACE
                        && job.phase().blocksGraphMutation()
        );
    }

    private static boolean hasActiveConstructionJob(
            RoadBuilderConstructionJobSavedData jobs
    ) {
        return jobs.jobs().stream().anyMatch(job ->
                job.phase().blocksGraphMutation()
        );
    }
}
