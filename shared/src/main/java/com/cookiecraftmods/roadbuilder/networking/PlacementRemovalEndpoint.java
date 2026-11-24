package com.cookiecraftmods.roadbuilder.networking;

import com.cookiecraftmods.roadbuilder.placement.BlockPositionSets;
import com.cookiecraftmods.roadbuilder.RoadBuilder;
import com.cookiecraftmods.roadbuilder.network.PreparedSegmentRemoval;
import com.cookiecraftmods.roadbuilder.network.RoadGraph;
import com.cookiecraftmods.roadbuilder.network.RoadSegmentId;
import com.cookiecraftmods.roadbuilder.network.RoadSegment;
import com.cookiecraftmods.roadbuilder.network.RoadNode;
import com.cookiecraftmods.roadbuilder.network.RoadNodeId;
import com.cookiecraftmods.roadbuilder.network.JunctionDefinition;
import com.cookiecraftmods.roadbuilder.network.JunctionSolveResult;
import com.cookiecraftmods.roadbuilder.network.JunctionSolver;
import com.cookiecraftmods.roadbuilder.placement.BlockPlacementPlan;
import com.cookiecraftmods.roadbuilder.placement.BlockPlacementPlans;
import com.cookiecraftmods.roadbuilder.placement.BlockPlanCommitter;
import com.cookiecraftmods.roadbuilder.placement.BlockPlanResult;
import com.cookiecraftmods.roadbuilder.placement.BlockTransitionStateMatcher;
import com.cookiecraftmods.roadbuilder.placement.LayeredBlockPlan;
import com.cookiecraftmods.roadbuilder.placement.MaterialLayer;
import com.cookiecraftmods.roadbuilder.placement.MaterializationPartition;
import com.cookiecraftmods.roadbuilder.placement.PlacementCommitResult;
import com.cookiecraftmods.roadbuilder.placement.PlannedBlock;
import com.cookiecraftmods.roadbuilder.placement.PositionChangeStack;
import com.cookiecraftmods.roadbuilder.placement.RoadClearancePlanner;
import com.cookiecraftmods.roadbuilder.placement.StraightRoadIntersectionMaterializer;
import com.cookiecraftmods.roadbuilder.placement.ConstructionAuthorizer;
import com.cookiecraftmods.roadbuilder.placement.ConstructionLimits;
import com.cookiecraftmods.roadbuilder.placement.ConstructionValidationCode;
import com.cookiecraftmods.roadbuilder.placement.ConstructionValidationResult;
import com.cookiecraftmods.roadbuilder.placement.IncrementalRemovalJobService;
import com.cookiecraftmods.roadbuilder.placement.PlacementOperation;
import com.cookiecraftmods.roadbuilder.placement.PlacementOperationId;
import com.cookiecraftmods.roadbuilder.placement.PlacementOwnershipLedger;
import com.cookiecraftmods.roadbuilder.placement.PlacementRemovalFailureCode;
import com.cookiecraftmods.roadbuilder.placement.PlacementRemovalPreviewResult;
import com.cookiecraftmods.roadbuilder.placement.PlacementRemovalResult;
import com.cookiecraftmods.roadbuilder.placement.PlacementRemovalService;
import com.cookiecraftmods.roadbuilder.placement.RoadMaterializationOwner;
import com.cookiecraftmods.roadbuilder.placement.RoadMaterializationResolver;
import com.cookiecraftmods.roadbuilder.placement.RoadStructurePlanner;
import com.cookiecraftmods.roadbuilder.placement.ServerLevelPlacementBlockWorld;
import com.cookiecraftmods.roadbuilder.persistence.RoadBuilderConstructionJobSavedData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class PlacementRemovalEndpoint {
    private final ConstructionAuthorizer authorizer;
    private final ConstructionLimits limits;
    private final PlacementRemovalService removalService;
    private final IncrementalRemovalJobService incrementalRemovalJobs;
    private final BlockPlanCommitter rebuildCommitter =
            new BlockPlanCommitter();

    public PlacementRemovalEndpoint(
            ConstructionAuthorizer authorizer,
            ConstructionLimits limits,
            PlacementRemovalService removalService
    ) {
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.removalService = Objects.requireNonNull(removalService, "removalService");
        this.incrementalRemovalJobs = new IncrementalRemovalJobService(removalService);
    }

    public RemovalResultS2CPacket handle(
            RemovalIntentC2SPacket packet,
            ServerPlayer player,
            PlacementOwnershipLedger ledger
    ) {
        return handle(
                packet,
                player,
                ledger,
                Optional.empty(),
                Optional.empty()
        );
    }

    public RemovalResultS2CPacket handle(
            RemovalIntentC2SPacket packet,
            ServerPlayer player,
            PlacementOwnershipLedger ledger,
            RoadGraph graph
    ) {
        return handle(
                packet,
                player,
                ledger,
                Optional.of(Objects.requireNonNull(graph, "graph")),
                Optional.empty()
        );
    }

    public RemovalResultS2CPacket handle(
            RemovalIntentC2SPacket packet,
            ServerPlayer player,
            PlacementOwnershipLedger ledger,
            RoadGraph graph,
            RoadBuilderConstructionJobSavedData jobs
    ) {
        return handle(
                packet,
                player,
                ledger,
                Optional.of(Objects.requireNonNull(graph, "graph")),
                Optional.of(Objects.requireNonNull(jobs, "jobs"))
        );
    }

    public RemovalResultS2CPacket handle(
            SegmentRemovalIntentC2SPacket packet,
            ServerPlayer player,
            PlacementOwnershipLedger ledger,
            RoadGraph graph
    ) {
        return handleSegment(packet, player, ledger, graph, Optional.empty());
    }

    public RemovalResultS2CPacket handle(
            SegmentRemovalIntentC2SPacket packet,
            ServerPlayer player,
            PlacementOwnershipLedger ledger,
            RoadGraph graph,
            RoadBuilderConstructionJobSavedData jobs
    ) {
        return handleSegment(
                packet,
                player,
                ledger,
                graph,
                Optional.of(Objects.requireNonNull(jobs, "jobs"))
        );
    }

    private RemovalResultS2CPacket handleSegment(
            SegmentRemovalIntentC2SPacket packet,
            ServerPlayer player,
            PlacementOwnershipLedger ledger,
            RoadGraph graph,
            Optional<RoadBuilderConstructionJobSavedData> jobs
    ) {
        Objects.requireNonNull(packet, "packet");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(ledger, "ledger");
        Objects.requireNonNull(graph, "graph");

        ConstructionValidationResult playerValidation = authorizer.validatePlayer(player);
        if (!playerValidation.isAllowed()) {
            return RemovalResultS2CPacket.rejected(
                    packet,
                    mapValidationFailure(playerValidation.code())
            );
        }
        if (!player.serverLevel().dimension().location().equals(packet.dimensionId())
                || !player.serverLevel().dimension().equals(ledger.dimension())
                || !player.serverLevel().dimension().equals(graph.dimension())) {
            return RemovalResultS2CPacket.rejected(
                    packet,
                    RemovalResultCode.DIMENSION_MISMATCH
            );
        }
        if (graph.revision() != packet.expectedGraphRevision()) {
            return RemovalResultS2CPacket.rejected(
                    packet,
                    RemovalResultCode.STALE_GRAPH
            );
        }
        RoadSegmentId segmentId = new RoadSegmentId(packet.segmentId());
        RoadSegment segment = graph.segment(segmentId).orElse(null);
        if (segment == null) {
            return RemovalResultS2CPacket.rejected(packet, RemovalResultCode.UNKNOWN_SEGMENT);
        }
        PlacementOperation operation = new RoadMaterializationResolver(limits)
                .resolve(segment, graph.dimension(), ledger)
                .orElse(null);
        if (operation == null) {
            return RemovalResultS2CPacket.rejected(
                    packet,
                    RemovalResultCode.PERSISTENCE_INCOMPATIBLE
            );
        }

        RemovalIntentC2SPacket resolved = RemovalIntentC2SPacket.current(
                packet.requestId(),
                packet.dimensionId(),
                operation.id().value(),
                packet.action(),
                packet.mode()
        );
        return handle(resolved, player, ledger, Optional.of(graph), jobs);
    }

    private RemovalResultS2CPacket handle(
            RemovalIntentC2SPacket packet,
            ServerPlayer player,
            PlacementOwnershipLedger ledger,
            Optional<RoadGraph> graph,
            Optional<RoadBuilderConstructionJobSavedData> jobs
    ) {
        Objects.requireNonNull(packet, "packet");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(ledger, "ledger");
        Objects.requireNonNull(graph, "graph");

        ConstructionValidationResult playerValidation = authorizer.validatePlayer(player);
        if (!playerValidation.isAllowed()) {
            return rejected(packet, mapValidationFailure(playerValidation.code()));
        }
        if (!player.serverLevel().dimension().location().equals(packet.dimensionId())
                || !player.serverLevel().dimension().equals(ledger.dimension())) {
            return rejected(packet, RemovalResultCode.DIMENSION_MISMATCH);
        }

        PlacementOperationId operationId = new PlacementOperationId(packet.operationId());
        PlacementOperation operation = ledger.operation(operationId).orElse(null);
        if (operation == null) {
            return rejected(packet, RemovalResultCode.UNKNOWN_OPERATION);
        }

        ConstructionValidationResult footprintValidation =
                authorizer.validateExistingFootprint(
                        player,
                        operation,
                        limits
                );
        if (!footprintValidation.isAllowed()) {
            return rejected(packet, mapValidationFailure(footprintValidation.code()));
        }
        PreparedSegmentRemoval graphRemoval = null;
        if (operation.materializationOwner().isPresent()) {
            if (graph.isEmpty()) {
                return rejected(packet, RemovalResultCode.PERSISTENCE_INCOMPATIBLE);
            }
            RoadGraph roadGraph = graph.orElseThrow();
            if (!roadGraph.dimension().equals(player.serverLevel().dimension())) {
                return rejected(packet, RemovalResultCode.DIMENSION_MISMATCH);
            }
            var owner = operation.materializationOwner().orElseThrow();
            if (owner.type() == RoadMaterializationOwner.Type.SEGMENT) {
                if (roadGraph.segment(owner.segmentId()).isPresent()) {
                    graphRemoval = roadGraph.prepareSegmentRemoval(
                            owner.segmentId()
                    );
                }
            }
        }

        ServerLevelPlacementBlockWorld world = new ServerLevelPlacementBlockWorld(player.serverLevel());
        return switch (packet.action()) {
            case PREVIEW -> preview(packet, operationId, world, ledger);
            case EXECUTE -> execute(
                    packet,
                    player,
                    operationId,
                    world,
                    ledger,
                    graph.orElse(null),
                    graphRemoval,
                    jobs
            );
        };
    }

    private RemovalResultS2CPacket preview(
            RemovalIntentC2SPacket packet,
            PlacementOperationId operationId,
            ServerLevelPlacementBlockWorld world,
            PlacementOwnershipLedger ledger
    ) {
        PlacementRemovalPreviewResult result = removalService.preview(
                operationId,
                packet.mode(),
                world,
                ledger
        );
        if (result instanceof PlacementRemovalPreviewResult.Failure failure) {
            return rejected(packet, mapRemovalFailure(failure.code()));
        }
        PlacementRemovalPreviewResult.Success success = (PlacementRemovalPreviewResult.Success) result;
        return RemovalResultS2CPacket.success(packet, success.summary());
    }

    private RemovalResultS2CPacket execute(
            RemovalIntentC2SPacket packet,
            ServerPlayer player,
            PlacementOperationId operationId,
            ServerLevelPlacementBlockWorld world,
            PlacementOwnershipLedger ledger,
            RoadGraph graph,
            PreparedSegmentRemoval graphRemoval,
            Optional<RoadBuilderConstructionJobSavedData> jobs
    ) {
        if (graphRemoval != null && graph != null) {
            Map<RoadNodeId, BlockPlacementPlan> retainedPlans;
            try {
                retainedPlans = retainedJunctionPlans(
                        graph,
                        graphRemoval,
                        world,
                        ledger
                );
            } catch (IllegalStateException invalidRebuild) {
                RoadBuilder.LOGGER.warn(
                        "Could not plan retained junctions while removing {}",
                        operationId,
                        invalidRebuild
                );
                retainedPlans = Map.of();
            }
            List<PlacementOperationId> cleanupOperations =
                    orphanCleanupOperations(
                            graph,
                            graphRemoval,
                            ledger,
                            operationId
                    );
            if (jobs.isPresent()
                    && retainedPlans.isEmpty()
                    && cleanupOperations.isEmpty()) {
                IncrementalRemovalJobService.EnqueueResult enqueue =
                        incrementalRemovalJobs.enqueueIfExpensive(
                                player.getUUID(),
                                operationId,
                                packet.mode(),
                                world,
                                ledger,
                                jobs.orElseThrow()
                        );
                if (enqueue instanceof
                        IncrementalRemovalJobService.EnqueueResult.Failure failure) {
                    return rejected(packet, mapRemovalFailure(failure.code()));
                }
                if (enqueue instanceof
                        IncrementalRemovalJobService.EnqueueResult.Queued queued) {
                    return RemovalResultS2CPacket.queued(packet, queued.summary());
                }
                if (enqueue instanceof
                        IncrementalRemovalJobService.EnqueueResult.AlreadyQueued queued) {
                    return RemovalResultS2CPacket.queued(packet, queued.summary());
                }
            }
            return executeWithMaterializationRebuild(
                    packet,
                    player,
                    operationId,
                    world,
                    ledger,
                    graph,
                    graphRemoval,
                    retainedPlans,
                    cleanupOperations
            );
        }
        List<PlacementOperationId> orphanNodeOperations =
                orphanCleanupOperations(
                        graph,
                        graphRemoval,
                        ledger,
                        operationId
                );
        for (PlacementOperationId nodeOperation : orphanNodeOperations) {
            PlacementRemovalPreviewResult preview = removalService.preview(
                    nodeOperation,
                    packet.mode(),
                    world,
                    ledger
            );
            if (preview instanceof PlacementRemovalPreviewResult.Failure failure) {
                return rejected(packet, mapRemovalFailure(failure.code()));
            }
        }

        if (jobs.isPresent() && orphanNodeOperations.isEmpty()) {
            IncrementalRemovalJobService.EnqueueResult enqueue =
                    incrementalRemovalJobs.enqueueIfExpensive(
                            player.getUUID(),
                            operationId,
                            packet.mode(),
                            world,
                            ledger,
                            jobs.orElseThrow()
                    );
            if (enqueue instanceof IncrementalRemovalJobService.EnqueueResult.Failure failure) {
                return rejected(packet, mapRemovalFailure(failure.code()));
            }
            if (enqueue instanceof IncrementalRemovalJobService.EnqueueResult.Queued queued) {
                return RemovalResultS2CPacket.queued(packet, queued.summary());
            }
            if (enqueue
                    instanceof IncrementalRemovalJobService.EnqueueResult.AlreadyQueued queued) {
                return RemovalResultS2CPacket.queued(packet, queued.summary());
            }
        }

        PlacementRemovalResult result = removalService.remove(
                operationId,
                packet.mode(),
                world,
                ledger
        );
        if (result instanceof PlacementRemovalResult.Failure failure) {
            return rejected(packet, mapRemovalFailure(failure.code()));
        }
        PlacementRemovalResult.Success success = (PlacementRemovalResult.Success) result;
        for (PlacementOperationId nodeOperation : orphanNodeOperations) {
            PlacementRemovalResult nodeRemoval = removalService.remove(
                    nodeOperation,
                    packet.mode(),
                    world,
                    ledger
            );
            if (nodeRemoval instanceof PlacementRemovalResult.Failure failure) {
                return rejected(packet, mapRemovalFailure(failure.code()));
            }
        }
        if (graphRemoval != null) {
            graph.applyRemoval(graphRemoval);
        }
        return RemovalResultS2CPacket.success(packet, success.summary());
    }

    private RemovalResultS2CPacket executeWithMaterializationRebuild(
            RemovalIntentC2SPacket packet,
            ServerPlayer player,
            PlacementOperationId segmentOperationId,
            ServerLevelPlacementBlockWorld world,
            PlacementOwnershipLedger ledger,
            RoadGraph graph,
            PreparedSegmentRemoval graphRemoval,
            Map<RoadNodeId, BlockPlacementPlan> retainedPlans,
            List<PlacementOperationId> orphanNodeOperations
    ) {
        LinkedHashMap<RoadNodeId, PlacementOperation> oldNodeOperations =
                new LinkedHashMap<>();
        retainedPlans.keySet().stream().sorted().forEach(nodeId ->
                ledger.operation(RoadMaterializationOwner.node(nodeId))
                        .ifPresent(operation ->
                                oldNodeOperations.put(nodeId, operation)));
        List<PlacementOperationId> removedOperationIds = new ArrayList<>();
        removedOperationIds.add(segmentOperationId);
        oldNodeOperations.values().forEach(operation ->
                removedOperationIds.add(operation.id()));
        orphanNodeOperations.forEach(operationId -> {
            if (!removedOperationIds.contains(operationId)) {
                removedOperationIds.add(operationId);
            }
        });

        PlacementRemovalPreviewResult segmentPreview = removalService.preview(
                segmentOperationId,
                packet.mode(),
                world,
                ledger
        );
        if (segmentPreview instanceof PlacementRemovalPreviewResult.Failure failure) {
            RoadBuilder.LOGGER.warn(
                    "Strict removal preview failed for {}; using recovery demolition: {}",
                    segmentOperationId,
                    failure
            );
            return completeBestEffortRemoval(
                    packet,
                    segmentOperationId,
                    removedOperationIds,
                    world,
                    ledger,
                    graph,
                    graphRemoval
            );
        }
        var segmentSummary = ((PlacementRemovalPreviewResult.Success) segmentPreview)
                .summary();

        BlockPlacementPlan overlay;
        try {
            overlay = finalStateOverlay(
                    world,
                    ledger,
                    removedOperationIds,
                    retainedPlans.values()
            );
        } catch (RuntimeException rebuildFailure) {
            RoadBuilder.LOGGER.warn(
                    "Could not create junction rebuild while removing {}; using recovery demolition",
                    segmentOperationId,
                    rebuildFailure
            );
            return completeBestEffortRemoval(
                    packet,
                    segmentOperationId,
                    removedOperationIds,
                    world,
                    ledger,
                    graph,
                    graphRemoval
            );
        }
        ConstructionValidationResult validation =
                authorizer.validateExistingFootprint(
                        player,
                        overlay,
                        limits
                );
        if (!validation.isAllowed()) {
            return rejected(
                    packet,
                    mapValidationFailure(validation.code())
            );
        }
        PlacementOperationId overlayId = rebuildCommitter.nextOperationId();
        PlacementCommitResult committed = rebuildCommitter.commitRestorationOverlay(
                overlayId,
                overlay,
                world,
                ledger
        );
        if (!(committed instanceof PlacementCommitResult.Success)) {
            RoadBuilder.LOGGER.warn(
                    "Junction removal overlay {} failed to commit: {}",
                    overlayId,
                    committed
            );
            return completeBestEffortRemoval(
                    packet,
                    segmentOperationId,
                    removedOperationIds,
                    world,
                    ledger,
                    graph,
                    graphRemoval
            );
        }

        List<MaterializationPartition> partitions = new ArrayList<>();
        int partitionIndex = 0;
        for (Map.Entry<RoadNodeId, BlockPlacementPlan> entry
                : retainedPlans.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList()) {
            List<BlockPos> positions = entry.getValue().positions();
            partitions.add(new MaterializationPartition(
                    partitionIndex++ == 0
                            ? overlayId
                            : PlacementOperationId.create(),
                    RoadMaterializationOwner.node(entry.getKey()),
                    positions
            ));
        }
        try {
            ledger.publishMaterializationRebuild(
                    removedOperationIds,
                    overlayId,
                    partitions
            );
        } catch (RuntimeException rejected) {
            RoadBuilder.LOGGER.error(
                    "Junction removal overlay {} could not publish while replacing {}",
                    overlayId,
                    removedOperationIds,
                    rejected
            );
            removalService.removeBestEffort(overlayId, world, ledger);
            return completeBestEffortRemoval(
                    packet,
                    segmentOperationId,
                    removedOperationIds,
                    world,
                    ledger,
                    graph,
                    graphRemoval
            );
        }
        graph.applyRemoval(graphRemoval);
        return RemovalResultS2CPacket.success(
                packet,
                segmentSummary
        );
    }

    private RemovalResultS2CPacket completeBestEffortRemoval(
            RemovalIntentC2SPacket packet,
            PlacementOperationId selectedOperationId,
            Collection<PlacementOperationId> removedOperationIds,
            ServerLevelPlacementBlockWorld world,
            PlacementOwnershipLedger ledger,
            RoadGraph graph,
            PreparedSegmentRemoval graphRemoval
    ) {
        PlacementRemovalResult selectedResult = null;
        for (PlacementOperationId operationId : removedOperationIds) {
            if (ledger.operation(operationId).isEmpty()) {
                continue;
            }
            PlacementRemovalResult result = removalService.removeBestEffort(
                    operationId,
                    world,
                    ledger
            );
            if (operationId.equals(selectedOperationId)) {
                selectedResult = result;
            }
        }
        if (graphRemoval != null) {
            graph.applyRemoval(graphRemoval);
        }
        if (selectedResult instanceof PlacementRemovalResult.Success success) {
            RoadBuilder.LOGGER.info(
                    "Removed road {} using recovery demolition",
                    selectedOperationId
            );
            return RemovalResultS2CPacket.success(packet, success.summary());
        }
        return RemovalResultS2CPacket.success(
                packet,
                new com.cookiecraftmods.roadbuilder.placement.RemovalSummary(
                        0, 0, 0, 0, 0, 0, List.of()
                )
        );
    }

    private BlockPlacementPlan finalStateOverlay(
            ServerLevelPlacementBlockWorld world,
            PlacementOwnershipLedger ledger,
            Collection<PlacementOperationId> removedOperationIds,
            Collection<BlockPlacementPlan> newNodePlans
    ) {
        Set<BlockPos> positions = new HashSet<>();
        for (PlacementOperationId operationId : removedOperationIds) {
            ledger.operation(operationId)
                    .ifPresent(operation ->
                            positions.addAll(operation.positions()));
        }
        newNodePlans.forEach(plan -> positions.addAll(plan.positions()));

        Map<BlockPos, BlockState> targets = new LinkedHashMap<>();
        for (BlockPos position : positions) {
            BlockState current = world.blockState(position);
            BlockState expected = ledger.expectedVisibleState(position)
                    .orElse(current);
            if (!BlockTransitionStateMatcher.matchesUpdateDrivenTarget(
                    current,
                    expected
            )) {
                targets.put(position, current);
                continue;
            }
            targets.put(
                    position,
                    stateAfterRemoving(
                            ledger,
                            position,
                            removedOperationIds,
                            current
                    )
            );
        }
        for (BlockPlacementPlan nodePlan : newNodePlans) {
            for (PlannedBlock block : nodePlan.blocks()) {
                BlockState current = world.blockState(block.position());
                BlockState expected = ledger.expectedVisibleState(
                        block.position()
                ).orElse(current);
                if (BlockTransitionStateMatcher.matchesUpdateDrivenTarget(
                        current,
                        expected
                )) {
                    targets.put(block.position(), block.targetState());
                }
            }
        }
        return BlockPlacementPlan.create(
                world.dimension(),
                targets.entrySet().stream()
                        .map(entry -> new PlannedBlock(
                                entry.getKey(),
                                entry.getValue()
                        ))
                        .toList()
        );
    }

    private static BlockState stateAfterRemoving(
            PlacementOwnershipLedger ledger,
            BlockPos position,
            Collection<PlacementOperationId> operationIds,
            BlockState fallback
    ) {
        PositionChangeStack stack = ledger.stack(position).orElse(null);
        if (stack == null) {
            return fallback;
        }
        BlockState result = stack.top().afterState();
        for (PlacementOperationId operationId : operationIds) {
            if (stack.layer(operationId).isEmpty()) {
                continue;
            }
            var removal = stack.remove(operationId);
            if (removal.updatedStack().isPresent()) {
                stack = removal.updatedStack().orElseThrow();
                result = stack.top().afterState();
            } else {
                result = removal.removedLayer().beforeState();
                break;
            }
        }
        return result;
    }

    private Map<RoadNodeId, BlockPlacementPlan> retainedJunctionPlans(
            RoadGraph graph,
            PreparedSegmentRemoval graphRemoval,
            ServerLevelPlacementBlockWorld world,
            PlacementOwnershipLedger ledger
    ) {
        RoadSegment removed = graph.segment(graphRemoval.segmentId())
                .orElseThrow();
        var snapshot = graph.snapshot();
        RoadGraph remainingGraph = RoadGraph.restore(
                snapshot.dimension(), snapshot.revision(), snapshot.roads().values(),
                snapshot.nodes().values(), snapshot.segments().values(),
                snapshot.connectors().values(), snapshot.junctionDefinitions().values(),
                com.cookiecraftmods.roadbuilder.network.RoadGraphLimits.defaults(), () -> { }
        );
        remainingGraph.applyRemoval(graphRemoval);
        var remainingSnapshot = remainingGraph.snapshot();
        Set<RoadNodeId> affectedNodes = new java.util.LinkedHashSet<>();
        for (RoadSegment segment : snapshot.segments().values()) {
            RoadSegment survivor = remainingSnapshot.segments().get(segment.id());
            if (survivor == null) {
                continue;
            }
            if (segment.startNodeId().equals(removed.startNodeId())
                    || segment.startNodeId().equals(removed.endNodeId())) {
                affectedNodes.add(survivor.startNodeId());
            }
            if (segment.endNodeId().equals(removed.startNodeId())
                    || segment.endNodeId().equals(removed.endNodeId())) {
                affectedNodes.add(survivor.endNodeId());
            }
        }
        LinkedHashMap<RoadNodeId, BlockPlacementPlan> plans = new LinkedHashMap<>();
        for (RoadNodeId nodeId : affectedNodes) {
            List<RoadSegment> remaining = remainingSnapshot.segments().values()
                    .stream()
                    .filter(segment ->
                            segment.startNodeId().equals(nodeId)
                                    || segment.endNodeId().equals(nodeId))
                    .toList();
            if (!isJunction(graph, nodeId, remaining)) {
                continue;
            }
            RoadNode node = remainingSnapshot.nodes().get(nodeId);
            JunctionDefinition definition = remainingSnapshot.junctionDefinitions()
                    .getOrDefault(nodeId, JunctionDefinition.automatic(nodeId));
            JunctionSolveResult solved = new JunctionSolver().solve(
                    node,
                    remaining,
                    definition
            );
            if (!(solved instanceof JunctionSolveResult.Success success)) {
                throw new IllegalStateException(
                        "retained junction could not be solved"
                );
            }
            RoadSegment primary = success.approaches().stream()
                    .filter(approach -> approach.primary())
                    .map(approach -> approach.segment())
                    .findFirst()
                    .orElse(remaining.get(0));
            BlockPlanResult surface =
                    new StraightRoadIntersectionMaterializer().planWithMarkings(
                            graph.dimension(),
                            node,
                            remaining,
                            primary,
                            limits.maximumAffectedBlocks(),
                            limits.maximumPlanningCandidates(), definition
                    );
            if (!(surface instanceof BlockPlanResult.Success surfacePlan)) {
                throw new IllegalStateException(
                        "retained junction surface could not be planned"
                );
            }
            BlockPlacementPlan combined = surfacePlan.plan();
            List<RoadStructurePlanner.StructureSpan> structureSpans = remaining
                    .stream()
                    .map(segment -> new RoadStructurePlanner.StructureSpan(
                            segment.referenceGeometry(),
                            segment.presetSnapshot().surfaceBlockWidth(),
                            segment.presetSnapshot().surfaceBands(),
                            segment.presetSnapshot().sunkenRoad()
                    ))
                    .toList();
            combined = new RoadStructurePlanner().prepareStructureSurface(
                    combined, structureSpans, world, ledger::isMaterializedRoadPosition);
            BlockPlanResult structures = new RoadStructurePlanner()
                    .addStructures(
                            combined,
                            world,
                            structureSpans,
                            ledger::isMaterializedRoadPosition,
                            limits.maximumAffectedBlocks()
                    );
            if (!(structures instanceof BlockPlanResult.Success structured)) {
                throw new IllegalStateException(
                        "retained junction structures could not be planned"
                );
            }
            Set<BlockPos> structurePositions = BlockPositionSets.copyOf(
                    structured.plan().positions()
            );
            BlockPlanResult clearance = new RoadClearancePlanner()
                    .addClearance(
                             combined,
                             world,
                             position -> ledger.isMaterializedRoadPosition(
                                     position
                             ) || structurePositions.contains(position),
                             limits.maximumAffectedBlocks()
                     );
            if (!(clearance instanceof BlockPlanResult.Success cleared)) {
                throw new IllegalStateException(
                        "retained junction clearance could not be planned"
                );
            }
            plans.put(nodeId, BlockPlacementPlans.mergeLayered(
                    graph.dimension(),
                    List.of(
                            new LayeredBlockPlan(
                                    structured.plan(),
                                    MaterialLayer.ROAD_STRUCTURE,
                                    "node-structure",
                                    nodeId.toString()
                            ),
                            new LayeredBlockPlan(
                                    cleared.plan(),
                                    MaterialLayer.JUNCTION_SURFACE,
                                    "node",
                                    nodeId.toString()
                            )
                    )
            ));
        }
        return Map.copyOf(plans);
    }

    private static boolean isJunction(
            RoadGraph graph,
            RoadNodeId nodeId,
            List<RoadSegment> remaining
    ) {
        if (remaining.size() >= 3) {
            return true;
        }
        if (!com.cookiecraftmods.roadbuilder.network.AuxiliaryLanePorts
                .attachments(nodeId, remaining).isEmpty()) return true;
        if (remaining.size() != 2) {
            return false;
        }
        var first = outwardTangent(remaining.get(0), nodeId);
        var second = outwardTangent(remaining.get(1), nodeId);
        double firstLength = first.horizontalLength();
        double secondLength = second.horizontalLength();
        if (firstLength == 0.0 || secondLength == 0.0) {
            return true;
        }
        double dot = (first.x() * second.x() + first.z() * second.z())
                / (firstLength * secondLength);
        return dot > -Math.cos(Math.toRadians(5.0));
    }

    private static List<PlacementOperationId> orphanCleanupOperations(
            RoadGraph graph,
            PreparedSegmentRemoval graphRemoval,
            PlacementOwnershipLedger ledger,
            PlacementOperationId selectedOperationId
    ) {
        if (graphRemoval == null || graph == null) {
            return List.of();
        }
        RoadSegment removed = graph.segment(graphRemoval.segmentId())
                .orElse(null);
        if (removed == null) {
            return List.of();
        }
        Set<BlockPos> selectedPositions = ledger.operation(selectedOperationId)
                .map(operation -> BlockPositionSets.copyOf(operation.positions()))
                .orElse(Set.of());
        java.util.stream.Stream<PlacementOperationId> endpointNodes =
                java.util.stream.Stream.of(
                        removed.startNodeId(),
                        removed.endNodeId()
                )
                .filter(nodeId -> noLongerJunction(
                        graph,
                        nodeId,
                        removed.id()
                ))
                .map(RoadMaterializationOwner::node)
                .map(ledger::operation)
                .flatMap(Optional::stream)
                .map(PlacementOperation::id);
        java.util.stream.Stream<PlacementOperationId> staleOverlays =
                ledger.operations().stream()
                        .filter(operation -> !operation.id().equals(
                                selectedOperationId
                        ))
                        .filter(operation -> operation.materializationOwner()
                                .map(owner -> owner.type()
                                        == RoadMaterializationOwner.Type.NODE
                                        && graph.node(owner.nodeId()).isEmpty())
                                .orElse(true))
                        .filter(operation -> operation.positions().stream()
                                .anyMatch(selectedPositions::contains))
                        .map(PlacementOperation::id);
        return java.util.stream.Stream.concat(endpointNodes, staleOverlays)
                .distinct()
                .toList();
    }

    private static boolean noLongerJunction(
            RoadGraph graph,
            com.cookiecraftmods.roadbuilder.network.RoadNodeId nodeId,
            RoadSegmentId removedId
    ) {
        List<RoadSegment> remaining = graph.snapshot().segments().values()
                .stream()
                .filter(segment -> !segment.id().equals(removedId))
                .filter(segment -> segment.startNodeId().equals(nodeId)
                        || segment.endNodeId().equals(nodeId))
                .toList();
        if (remaining.size() < 2
                || !com.cookiecraftmods.roadbuilder.network.RoadNodePortResolver
                        .isValidTopology(graph.node(nodeId).orElseThrow(), remaining)) {
            return true;
        }
        if (remaining.size() > 2) {
            return false;
        }
        var first = outwardTangent(remaining.get(0), nodeId);
        var second = outwardTangent(remaining.get(1), nodeId);
        double firstLength = first.horizontalLength();
        double secondLength = second.horizontalLength();
        if (firstLength == 0.0 || secondLength == 0.0) {
            return false;
        }
        double dot = (first.x() * second.x() + first.z() * second.z())
                / (firstLength * secondLength);
        return dot <= -Math.cos(Math.toRadians(5.0));
    }

    private static com.cookiecraftmods.roadbuilder.geometry.RoadVector
    outwardTangent(
            RoadSegment segment,
            com.cookiecraftmods.roadbuilder.network.RoadNodeId nodeId
    ) {
        boolean atStart = segment.startNodeId().equals(nodeId);
        var tangent = segment.referenceGeometry().sample(
                atStart ? 0.0 : segment.referenceGeometry().length()
        ).tangent();
        return atStart ? tangent : tangent.scale(-1.0);
    }

    private static RemovalResultS2CPacket rejected(
            RemovalIntentC2SPacket packet,
            RemovalResultCode code
    ) {
        return RemovalResultS2CPacket.rejected(packet, code);
    }

    private static RemovalResultCode mapValidationFailure(ConstructionValidationCode code) {
        return switch (code) {
            case ALLOWED -> throw new IllegalArgumentException("ALLOWED is not a validation failure");
            case WRONG_DIMENSION -> RemovalResultCode.DIMENSION_MISMATCH;
            case BLOCK_LIMIT_EXCEEDED, CHUNK_LIMIT_EXCEEDED ->
                    RemovalResultCode.REMOVAL_LIMIT_EXCEEDED;
            case NOT_CREATIVE, NO_PERMISSION, CHUNK_NOT_LOADED, OUTSIDE_WORLD,
                    OUTSIDE_WORLD_BORDER, TOO_FAR_AWAY -> RemovalResultCode.NOT_AUTHORIZED;
        };
    }

    private static RemovalResultCode mapRemovalFailure(PlacementRemovalFailureCode code) {
        return switch (code) {
            case DIMENSION_MISMATCH -> RemovalResultCode.DIMENSION_MISMATCH;
            case UNKNOWN_OPERATION -> RemovalResultCode.UNKNOWN_OPERATION;
            case CORRUPT_OWNERSHIP -> RemovalResultCode.OWNERSHIP_CORRUPT;
            case FORCE_CURRENT_BLOCK_ENTITY_UNSUPPORTED, FORCE_CURRENT_FLUID_UNSUPPORTED ->
                    RemovalResultCode.UNSUPPORTED_WORLD_STATE;
            case WORLD_CHANGED_DURING_REMOVAL -> RemovalResultCode.WORLD_CONFLICT;
            case WORLD_WRITE_REJECTED, WORLD_WRITE_DID_NOT_STICK, LEDGER_REJECTED,
                    ROLLBACK_INCOMPLETE -> RemovalResultCode.MUTATION_FAILED;
        };
    }
}
