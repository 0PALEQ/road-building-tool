package com.cookiecraftmods.roadbuilder.networking;

import com.cookiecraftmods.roadbuilder.placement.BlockPositionSets;
import com.cookiecraftmods.roadbuilder.RoadBuilder;
import com.cookiecraftmods.roadbuilder.network.PreparedRoadNetworkMutation;
import com.cookiecraftmods.roadbuilder.network.RoadGraph;
import com.cookiecraftmods.roadbuilder.network.RoadNode;
import com.cookiecraftmods.roadbuilder.network.RoadNodeId;
import com.cookiecraftmods.roadbuilder.network.RoadSegment;
import com.cookiecraftmods.roadbuilder.placement.BlockPlanCommitter;
import com.cookiecraftmods.roadbuilder.placement.BlockPlanFailureCode;
import com.cookiecraftmods.roadbuilder.placement.BlockPlanResult;
import com.cookiecraftmods.roadbuilder.placement.BlockPlacementPlan;
import com.cookiecraftmods.roadbuilder.placement.BlockPlacementPlans;
import com.cookiecraftmods.roadbuilder.placement.ConstructionAuthorizer;
import com.cookiecraftmods.roadbuilder.placement.ConstructionLimits;
import com.cookiecraftmods.roadbuilder.placement.ConstructionValidationCode;
import com.cookiecraftmods.roadbuilder.placement.ConstructionValidationResult;
import com.cookiecraftmods.roadbuilder.placement.IncrementalPlacementJobService;
import com.cookiecraftmods.roadbuilder.placement.PlacementCommitFailureCode;
import com.cookiecraftmods.roadbuilder.placement.PlacementCommitResult;
import com.cookiecraftmods.roadbuilder.placement.PlacementOperationId;
import com.cookiecraftmods.roadbuilder.placement.PlacementOperation;
import com.cookiecraftmods.roadbuilder.placement.PlacementOwnershipLedger;
import com.cookiecraftmods.roadbuilder.placement.PlacementRemovalResult;
import com.cookiecraftmods.roadbuilder.placement.PlacementRemovalService;
import com.cookiecraftmods.roadbuilder.placement.RemovalMode;
import com.cookiecraftmods.roadbuilder.placement.ResolvedRoadPathPlan;
import com.cookiecraftmods.roadbuilder.placement.RoadClearancePlanner;
import com.cookiecraftmods.roadbuilder.placement.RoadMaterializationOwner;
import com.cookiecraftmods.roadbuilder.placement.RoadMaterializationPlanner;
import com.cookiecraftmods.roadbuilder.placement.RoadMaterializationRepartitioner;
import com.cookiecraftmods.roadbuilder.placement.RoadStructurePlanner;
import com.cookiecraftmods.roadbuilder.placement.RoadPathIntent;
import com.cookiecraftmods.roadbuilder.placement.RoadPathPlanningResult;
import com.cookiecraftmods.roadbuilder.placement.RoadPathPlanningService;
import com.cookiecraftmods.roadbuilder.placement.ServerLevelPlacementBlockWorld;
import com.cookiecraftmods.roadbuilder.placement.StraightRoadIntersectionMaterializer;
import com.cookiecraftmods.roadbuilder.placement.SmoothRoadTransitionMaterializer;
import com.cookiecraftmods.roadbuilder.placement.RoadTransitionPlanPreparer;
import com.cookiecraftmods.roadbuilder.placement.OperationMaterializationRepartition;
import com.cookiecraftmods.roadbuilder.placement.JunctionMarkingMaterializer;
import com.cookiecraftmods.roadbuilder.persistence.RoadBuilderConstructionJobSavedData;
import com.cookiecraftmods.roadbuilder.preset.RoadSurfaceRole;
import com.cookiecraftmods.roadbuilder.preset.TrafficHandedness;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class RoadPathPlacementEndpoint {
    private final RoadPathPlanningService planningService;
    private final ConstructionAuthorizer authorizer;
    private final ConstructionLimits limits;
    private final BlockPlanCommitter committer;
    private final TrafficHandedness trafficHandedness;
    private final IncrementalPlacementJobService incrementalPlacements =
            new IncrementalPlacementJobService();

    public RoadPathPlacementEndpoint(
            RoadPathPlanningService planningService,
            ConstructionAuthorizer authorizer,
            ConstructionLimits limits,
            BlockPlanCommitter committer,
            TrafficHandedness trafficHandedness
    ) {
        this.planningService = Objects.requireNonNull(
                planningService,
                "planningService"
        );
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.committer = Objects.requireNonNull(committer, "committer");
        this.trafficHandedness = Objects.requireNonNull(
                trafficHandedness,
                "trafficHandedness"
        );
    }

    public PlacementResultS2CPacket handle(
            RoadPathIntentC2SPacket packet,
            ServerPlayer player,
            PlacementOwnershipLedger ledger,
            RoadGraph graph,
            RoadBuilderConstructionJobSavedData jobs
    ) {
        Objects.requireNonNull(packet, "packet");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(ledger, "ledger");
        Objects.requireNonNull(graph, "graph");

        ConstructionValidationResult playerValidation =
                authorizer.validatePlayer(player);
        if (!playerValidation.isAllowed()) {
            return rejected(
                    packet,
                    mapValidationFailure(playerValidation.code())
            );
        }
        if (!player.serverLevel().dimension().location()
                .equals(packet.dimensionId())
                || !player.serverLevel().dimension().equals(graph.dimension())) {
            return rejected(packet, PlacementResultCode.DIMENSION_MISMATCH);
        }
        ResourceKey<Level> dimension = ResourceKey.create(
                Registries.DIMENSION,
                packet.dimensionId()
        );
        RoadPathIntent intent;
        try {
            intent = new RoadPathIntent(
                    dimension,
                    packet.geometry(),
                    packet.presetId(),
                    packet.expectedPresetRevision()
            );
        } catch (IllegalArgumentException invalid) {
            RoadBuilder.LOGGER.warn("Rejected road-path intent", invalid);
            return rejected(packet, PlacementResultCode.INVALID_GEOMETRY);
        }
        RoadPathPlanningResult result =
                planningService.resolve(intent, limits);
        if (result instanceof RoadPathPlanningResult.Failure failure) {
            return rejected(packet, mapPlanFailure(failure.code()));
        }
        ResolvedRoadPathPlan planned =
                ((RoadPathPlanningResult.Success) result).roadPlan();
        PreparedRoadNetworkMutation mutation;
        double minimumRadius=com.cookiecraftmods.roadbuilder.placement.RoadCurveValidation.minimumRadius(
                planned.preset().surfaceBlockWidth(),planned.preset().classification().roadClass());
        try {
            if (packet.parallelOffset() == 0) {
                mutation = graph.prepareRoadNetwork(new com.cookiecraftmods.roadbuilder.placement.RoadOverpassPlanner().adjust(
                        planned.geometry(), planned.preset().surfaceBlockWidth(), graph.snapshot().segments().values(),minimumRadius), planned.presetId(),
                        planned.preset(), trafficHandedness);
            } else {
                var parallel = com.cookiecraftmods.roadbuilder.geometry.ParallelRoadGeometry.create(
                        planned.geometry(), packet.parallelOffset());
                RoadPathPlanningResult parallelResult = planningService.resolve(new RoadPathIntent(
                        dimension, parallel, packet.presetId(), packet.expectedPresetRevision()), limits);
                if (parallelResult instanceof RoadPathPlanningResult.Failure failure) {
                    return rejected(packet, mapPlanFailure(failure.code()));
                }
                mutation = graph.prepareParallelRoadNetwork(
                        new com.cookiecraftmods.roadbuilder.placement.RoadOverpassPlanner().adjust(planned.geometry(),
                                planned.preset().surfaceBlockWidth(), graph.snapshot().segments().values(),minimumRadius),
                        new com.cookiecraftmods.roadbuilder.placement.RoadOverpassPlanner().adjust(parallel,
                                planned.preset().surfaceBlockWidth(), graph.snapshot().segments().values(),minimumRadius),
                        planned.presetId(), planned.preset(), trafficHandedness);
            }
        } catch (IllegalArgumentException invalid) {
            RoadBuilder.LOGGER.warn("Rejected road-path network geometry", invalid);
            return rejected(packet, PlacementResultCode.INVALID_GEOMETRY);
        }

        ServerLevelPlacementBlockWorld world =
                new ServerLevelPlacementBlockWorld(player.serverLevel());
        mutation = mutation.withSunkenRoad(packet.sunkenRoad());
        mutation = graph.withTunnelPortals(mutation, world, ledger::isMaterializedRoadPosition);
        if (mutation.allUpsertedSegments().stream().anyMatch(segment ->
                segment.referenceGeometry().length() < com.cookiecraftmods.roadbuilder.network.RoadSegment.MINIMUM_BUILD_LENGTH)) {
            RoadBuilder.LOGGER.warn("Rejected road-path mutation containing a fragment shorter than {} blocks: {}",
                    RoadSegment.MINIMUM_BUILD_LENGTH, mutation.allUpsertedSegments().stream()
                            .filter(segment -> segment.referenceGeometry().length() < RoadSegment.MINIMUM_BUILD_LENGTH)
                            .limit(4).map(segment -> segment.id() + ": length=" + segment.referenceGeometry().length()
                                    + ", start=" + segment.referenceGeometry().start()
                                    + ", end=" + segment.referenceGeometry().end()).toList());
            return rejected(packet, PlacementResultCode.INVALID_GEOMETRY);
        }
        BlockPlanResult roadMaterialization = new RoadMaterializationPlanner().plan(
                dimension,
                mutation.placedSegments(),
                world,
                ledger,
                limits
        );
        if (roadMaterialization instanceof BlockPlanResult.Failure failure) {
            return rejected(packet, mapPlanFailure(failure.code()));
        }
        BlockPlacementPlan roadPlan =
                ((BlockPlanResult.Success) roadMaterialization).plan();
        Map<RoadNodeId, BlockPlacementPlan> junctionPlans;
        try {
            junctionPlans = prepareJunctionPlans(
                    planned,
                    mutation,
                    dimension,
                    world,
                    graph,
                    ledger,
                    BlockPositionSets.nonAirTargets(roadPlan)
            );
        } catch (JunctionPlanningFailure failure) {
            return rejected(packet, mapPlanFailure(failure.code()));
        }
        List<com.cookiecraftmods.roadbuilder.placement.LayeredBlockPlan>
                materializationPlans = new ArrayList<>();
        materializationPlans.add(new
                com.cookiecraftmods.roadbuilder.placement.LayeredBlockPlan(
                roadPlan,
                com.cookiecraftmods.roadbuilder.placement.MaterialLayer.SEGMENT_SURFACE,
                "segment",
                "proposed"
        ));
        junctionPlans.forEach((nodeId, junctionPlan) ->
                materializationPlans.add(new
                        com.cookiecraftmods.roadbuilder.placement.LayeredBlockPlan(
                        junctionPlan,
                        com.cookiecraftmods.roadbuilder.placement.MaterialLayer.JUNCTION_SURFACE,
                        "node",
                        nodeId.toString()
                )));
        BlockPlacementPlan plan = BlockPlacementPlans.mergeLayered(
                dimension,
                materializationPlans
        );
        if (planned.geometry() instanceof com.cookiecraftmods.roadbuilder.geometry.RoundaboutGeometry ring
                && !mutation.segmentSplits().isEmpty()) {
            plan = com.cookiecraftmods.roadbuilder.placement.RoundaboutIslandPlanner.addIsland(
                    plan, ring, planned.preset().surfaceBlockWidth());
        }
        ConstructionValidationResult planValidation =
                authorizer.validate(player, plan, limits);
        if (!planValidation.isAllowed()) {
            RoadBuilder.LOGGER.warn(
                    "Rejected road-path construction authorization: code={}, blocks={}, bounds={}, player={}",
                    planValidation.code(),
                    plan.affectedBlockCount(),
                    plan.bounds(),
                    player.blockPosition()
            );
            return rejected(
                    packet,
                    mapValidationFailure(planValidation.code())
            );
        }

        PlacementOperationId operationId = committer.nextOperationId();
        Set<RoadNodeId> inheritedJunctionOwners = RoadMaterializationRepartitioner.convertedJunctionOwners(mutation)
                .entrySet().stream().filter(entry -> ledger.operation(RoadMaterializationOwner.node(entry.getKey())).isPresent())
                .map(Map.Entry::getValue).collect(java.util.stream.Collectors.toSet());
        Map<RoadNodeId, BlockPlacementPlan> newlyOwnedJunctionPlans =
                junctionPlans.entrySet().stream()
                        .filter(entry -> !inheritedJunctionOwners.contains(entry.getKey()))
                        .filter(entry -> ledger.operation(
                                RoadMaterializationOwner.node(entry.getKey())
                        ).isEmpty())
                        .collect(java.util.stream.Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue,
                                (first, second) -> first,
                                LinkedHashMap::new
                        ));
        boolean repartitionsPlacement = mutation.placedSegments().size() > 1
                || !mutation.segmentSplits().isEmpty()
                || !newlyOwnedJunctionPlans.isEmpty();
        RoadMaterializationRepartitioner repartitioner =
                new RoadMaterializationRepartitioner(limits);
        List<OperationMaterializationRepartition> repartitions;
        try {
            repartitions = new ArrayList<>(repartitioner.prepareExisting(
                    mutation,
                    dimension,
                    ledger
            ));
        } catch (IllegalStateException incompatible) {
            return rejected(
                    packet,
                    PlacementResultCode.PERSISTENCE_INCOMPATIBLE
            );
        }
        Optional<RoadMaterializationOwner> directOwner = repartitionsPlacement
                ? Optional.empty()
                : Optional.of(RoadMaterializationOwner.segment(
                        mutation.placedSegments().get(0).id()
                ));
        if (repartitionsPlacement) {
            PlacementOperation prepared = PlacementOperation.create(
                    operationId,
                    dimension,
                    plan.positions()
            );
            repartitions.add(repartitioner.prepare(
                    prepared,
                    mutation.placedSegments(),
                    dimension,
                    newlyOwnedJunctionPlans
            ));
        }
        if (jobs != null) {
            IncrementalPlacementJobService.EnqueueResult enqueue =
                    incrementalPlacements.enqueueIfExpensive(
                            player.getUUID(),
                            operationId,
                            plan,
                            directOwner,
                            repartitions,
                            mutation,
                            world,
                            ledger,
                            graph,
                            jobs
                    );
            if (enqueue instanceof
                    IncrementalPlacementJobService.EnqueueResult.Queued queued) {
                return PlacementResultS2CPacket.queued(
                        packet.requestId(),
                        queued.job().id(),
                        queued.job().transitions().size()
                );
            }
            if (enqueue instanceof
                    IncrementalPlacementJobService.EnqueueResult.Failure failure) {
                return rejected(packet, mapCommitFailure(failure.code()));
            }
        }

        PlacementCommitResult commit = repartitionsPlacement
                ? committer.commit(operationId, plan, world, ledger)
                : committer.commit(
                        operationId,
                        directOwner.orElseThrow(),
                        plan,
                        world,
                        ledger
                );
        if (commit instanceof PlacementCommitResult.Failure failure) {
            return rejected(packet, mapCommitFailure(failure.code()));
        }
        PlacementCommitResult.Success success =
                (PlacementCommitResult.Success) commit;
        try {
            if (repartitionsPlacement) {
                ledger.repartitionMaterializations(repartitions);
            }
            graph.applyNetworkMutation(mutation);
        } catch (RuntimeException graphFailure) {
            PlacementRemovalResult rollback = ledger.operation(
                    success.operation().id()
            ).filter(operation -> operation.materializationOwner().isEmpty())
                    .<PlacementRemovalResult>map(ignored ->
                            new PlacementRemovalService().remove(
                            success.operation().id(),
                            RemovalMode.FORCE,
                            world,
                            ledger
                    )).orElse(null);
            return rejected(
                    packet,
                    rollback == null
                            || rollback instanceof PlacementRemovalResult.Success
                            ? PlacementResultCode.MUTATION_FAILED
                            : PlacementResultCode.INTERNAL_ERROR
            );
        }
        return PlacementResultS2CPacket.accepted(
                packet.requestId(),
                success.operation().id().value(),
                success.operation().positions().size()
        );
    }

    private Map<RoadNodeId, BlockPlacementPlan> prepareJunctionPlans(
            ResolvedRoadPathPlan planned,
            PreparedRoadNetworkMutation mutation,
            ResourceKey<Level> dimension,
            ServerLevelPlacementBlockWorld world,
            RoadGraph graph,
            PlacementOwnershipLedger ledger,
            Set<BlockPos> proposedRoadPositions
    ) {
        StraightRoadIntersectionMaterializer materializer =
                new StraightRoadIntersectionMaterializer();
        RoadClearancePlanner clearancePlanner = new RoadClearancePlanner();
        RoadStructurePlanner structurePlanner = new RoadStructurePlanner();
        Map<RoadNodeId, BlockPlacementPlan> plans = new LinkedHashMap<>();
        List<RoadSegment> resultingSegments = resultingSegments(graph, mutation);
        for (RoadNode node : affectedNodes(graph, mutation)) {
            List<RoadSegment> incident = resultingSegments.stream()
                    .filter(segment -> segment.startNodeId().equals(node.id())
                            || segment.endNodeId().equals(node.id()))
                    .toList();
            if (SmoothRoadTransitionMaterializer.supports(node, incident)) {
                BlockPlanResult transition = new RoadTransitionPlanPreparer().plan(
                        dimension,
                        node,
                        incident,
                        world,
                        position -> ledger.isMaterializedRoadPosition(position)
                                || proposedRoadPositions.contains(position),
                        limits
                );
                if (!(transition instanceof BlockPlanResult.Success success)) {
                    throw new JunctionPlanningFailure(
                            ((BlockPlanResult.Failure) transition).code()
                    );
                }
                plans.put(node.id(), success.plan());
                continue;
            }
            if (!StraightRoadIntersectionMaterializer.requiresJunctionSurface(node, incident)) {
                continue;
            }
            var definition = graph.junctionDefinition(node.id())
                    .orElse(com.cookiecraftmods.roadbuilder.network.JunctionDefinition
                            .automatic(node.id()));
            var solved = new com.cookiecraftmods.roadbuilder.network.JunctionSolver()
                    .solve(node, incident, definition);
            if (!(solved instanceof
                    com.cookiecraftmods.roadbuilder.network.JunctionSolveResult.Success
                            solveSuccess)) {
                throw new JunctionPlanningFailure(
                        BlockPlanFailureCode.INVALID_GEOMETRY
                );
            }
            RoadSegment primary = solveSuccess.approaches().stream()
                    .filter(approach -> approach.primary())
                    .map(approach -> approach.segment())
                    .findFirst()
                    .orElse(incident.get(0));
            var pavementBlock = primary.presetSnapshot()
                    .intersectionStyle().pavementBlock();
            var sidewalkBlock = primary.presetSnapshot().surfaceBands().stream()
                    .filter(band -> band.role() == RoadSurfaceRole.SIDEWALK)
                    .findFirst()
                    .map(band -> band.block())
                    .orElse(pavementBlock);
            BlockPlanResult surface = materializer.plan(
                    dimension,
                    node,
                    incident,
                    pavementBlock,
                    sidewalkBlock,
                    limits.maximumAffectedBlocks(),
                    limits.maximumPlanningCandidates(), definition
            );
            if (!(surface instanceof BlockPlanResult.Success surfaceSuccess)) {
                throw new JunctionPlanningFailure(
                        ((BlockPlanResult.Failure) surface).code()
                );
            }
            BlockPlacementPlan decoratedSurface = surfaceSuccess.plan();
            BlockPlanResult markings = new JunctionMarkingMaterializer().plan(
                    dimension,
                    node,
                    incident,
                    definition,
                    limits.maximumAffectedBlocks()
            );
            if (markings instanceof
                    BlockPlanResult.Success markingSuccess) {
                        var roundedEdgePositions = surfaceSuccess.plan()
                                .blocks().stream()
                                .filter(block -> !BuiltInRegistries.BLOCK
                                        .getKey(block.targetState().getBlock())
                                        .equals(pavementBlock))
                                .map(com.cookiecraftmods.roadbuilder.placement.PlannedBlock::position)
                                .toList();
                        var clippedMarkings = BlockPlacementPlans
                                .excludingPositions(
                                        markingSuccess.plan(),
                                        roundedEdgePositions
                                );
                        if (clippedMarkings.isPresent()) {
                            decoratedSurface = BlockPlacementPlans.mergeLayered(
                                    dimension,
                                    List.of(
                                            new com.cookiecraftmods.roadbuilder.placement.LayeredBlockPlan(
                                                    decoratedSurface,
                                                    com.cookiecraftmods.roadbuilder.placement.MaterialLayer.JUNCTION_SURFACE,
                                                    "node",
                                                    node.id().toString()
                                            ),
                                            new com.cookiecraftmods.roadbuilder.placement.LayeredBlockPlan(
                                                    clippedMarkings.orElseThrow(),
                                                    com.cookiecraftmods.roadbuilder.placement.MaterialLayer.JUNCTION_MARKING,
                                                    "node-marking",
                                                    node.id().toString()
                                            )
                                    )
                            );
                        }
            }
            List<RoadStructurePlanner.StructureSpan> structureSpans = incident
                    .stream()
                    .map(segment -> new RoadStructurePlanner.StructureSpan(
                            segment.referenceGeometry(),
                            segment.presetSnapshot().surfaceBlockWidth(),
                            segment.presetSnapshot().surfaceBands(),
                            segment.presetSnapshot().sunkenRoad()
                    ))
                    .toList();
            decoratedSurface = structurePlanner.prepareStructureSurface(
                    decoratedSurface, structureSpans, world,
                    position -> ledger.isMaterializedRoadPosition(position)
                            || proposedRoadPositions.contains(position));
            BlockPlanResult structured = structurePlanner.addStructures(
                    decoratedSurface,
                    world,
                    structureSpans,
                    position -> ledger.isMaterializedRoadPosition(position)
                            || proposedRoadPositions.contains(position),
                    limits.maximumAffectedBlocks()
            );
            if (!(structured instanceof BlockPlanResult.Success structureSuccess)) {
                throw new JunctionPlanningFailure(
                        ((BlockPlanResult.Failure) structured).code()
                );
            }
            Set<BlockPos> structurePositions = BlockPositionSets.copyOf(
                    structureSuccess.plan().positions()
            );
            BlockPlanResult cleared = clearancePlanner.addClearance(
                    decoratedSurface,
                     world,
                     position -> ledger.isMaterializedRoadPosition(position)
                             || proposedRoadPositions.contains(position)
                             || structurePositions.contains(position),
                     limits.maximumAffectedBlocks()
             );
            if (!(cleared instanceof BlockPlanResult.Success clearedSuccess)) {
                throw new JunctionPlanningFailure(
                        ((BlockPlanResult.Failure) cleared).code()
                );
            }
            plans.put(node.id(), BlockPlacementPlans.mergeLayered(
                    dimension,
                    List.of(
                            new com.cookiecraftmods.roadbuilder.placement.LayeredBlockPlan(
                                    structureSuccess.plan(),
                                    com.cookiecraftmods.roadbuilder.placement.MaterialLayer.ROAD_STRUCTURE,
                                    "node-structure",
                                    node.id().toString()
                            ),
                            new com.cookiecraftmods.roadbuilder.placement.LayeredBlockPlan(
                                    clearedSuccess.plan(),
                                    com.cookiecraftmods.roadbuilder.placement.MaterialLayer.JUNCTION_SURFACE,
                                    "node",
                                    node.id().toString()
                            )
                    )
            ));
        }
        return Map.copyOf(plans);
    }

    private static List<RoadNode> affectedNodes(
            RoadGraph graph,
            PreparedRoadNetworkMutation mutation
    ) {
        Map<RoadNodeId, RoadNode> nodes = new LinkedHashMap<>();
        mutation.addedNodes().forEach(node -> nodes.put(node.id(), node));
        Map<RoadNodeId, RoadNode> existing = graph.snapshot().nodes();
        mutation.placedSegments().forEach(segment -> {
            RoadNode start = existing.get(segment.startNodeId());
            RoadNode end = existing.get(segment.endNodeId());
            if (start != null) {
                nodes.putIfAbsent(start.id(), start);
            }
            if (end != null) {
                nodes.putIfAbsent(end.id(), end);
            }
        });
        return List.copyOf(nodes.values());
    }

    private static List<RoadSegment> resultingSegments(
            RoadGraph graph,
            PreparedRoadNetworkMutation mutation
    ) {
        Map<com.cookiecraftmods.roadbuilder.network.RoadSegmentId, RoadSegment>
                segments = new LinkedHashMap<>(graph.snapshot().segments());
        mutation.segmentSplits().forEach(split ->
                segments.remove(split.original().id()));
        mutation.allUpsertedSegments().forEach(segment ->
                segments.put(segment.id(), segment));
        return List.copyOf(segments.values());
    }

    private static final class JunctionPlanningFailure
            extends RuntimeException {
        private final BlockPlanFailureCode code;

        private JunctionPlanningFailure(BlockPlanFailureCode code) {
            this.code = code;
        }

        private BlockPlanFailureCode code() {
            return code;
        }
    }

    private static PlacementResultS2CPacket rejected(
            RoadPathIntentC2SPacket packet,
            PlacementResultCode code
    ) {
        return PlacementResultS2CPacket.rejected(packet.requestId(), code);
    }

    private static PlacementResultCode mapPlanFailure(
            BlockPlanFailureCode code
    ) {
        RoadBuilder.LOGGER.warn("Rejected road-path block plan: {}", code);
        return switch (code) {
            case UNKNOWN_PRESET -> PlacementResultCode.UNKNOWN_PRESET;
            case PRESET_REVISION_MISMATCH ->
                    PlacementResultCode.PRESET_REVISION_MISMATCH;
            case TOO_SHARP_TURN -> PlacementResultCode.TOO_SHARP_TURN;
            case SELF_INTERSECTION -> PlacementResultCode.SELF_INTERSECTION;
            case INVALID_GEOMETRY, COORDINATE_OUT_OF_RANGE ->
                    PlacementResultCode.INVALID_GEOMETRY;
            case BLOCK_LIMIT_EXCEEDED,
                    RASTERIZATION_WORK_LIMIT_EXCEEDED,
                    GRADE_LIMIT_EXCEEDED ->
                    PlacementResultCode.CONSTRUCTION_LIMIT_EXCEEDED;
            case UNKNOWN_SURFACE_BLOCK, AIR_SURFACE_BLOCK ->
                    PlacementResultCode.UNSUPPORTED_WORLD_STATE;
        };
    }

    private static PlacementResultCode mapValidationFailure(
            ConstructionValidationCode code
    ) {
        return switch (code) {
            case ALLOWED -> throw new IllegalArgumentException(
                    "ALLOWED is not a validation failure"
            );
            case WRONG_DIMENSION -> PlacementResultCode.DIMENSION_MISMATCH;
            case BLOCK_LIMIT_EXCEEDED, CHUNK_LIMIT_EXCEEDED ->
                    PlacementResultCode.CONSTRUCTION_LIMIT_EXCEEDED;
            case NOT_CREATIVE, NO_PERMISSION, CHUNK_NOT_LOADED,
                    OUTSIDE_WORLD, OUTSIDE_WORLD_BORDER, TOO_FAR_AWAY ->
                    PlacementResultCode.NOT_AUTHORIZED;
        };
    }

    private static PlacementResultCode mapCommitFailure(
            PlacementCommitFailureCode code
    ) {
        RoadBuilder.LOGGER.warn("Rejected road-path commit: {}", code);
        return switch (code) {
            case DIMENSION_MISMATCH ->
                    PlacementResultCode.DIMENSION_MISMATCH;
            case OWNERSHIP_LIMIT_EXCEEDED ->
                    PlacementResultCode.CONSTRUCTION_LIMIT_EXCEEDED;
            case CURRENT_BLOCK_ENTITY_UNSUPPORTED,
                    TARGET_BLOCK_ENTITY_UNSUPPORTED,
                    CURRENT_FLUID_UNSUPPORTED,
                    TARGET_FLUID_UNSUPPORTED ->
                    PlacementResultCode.UNSUPPORTED_WORLD_STATE;
            case WORLD_CHANGED_DURING_COMMIT ->
                    PlacementResultCode.WORLD_CONFLICT;
            case WORLD_WRITE_REJECTED, WORLD_WRITE_DID_NOT_STICK,
                    ROLLBACK_INCOMPLETE, LEDGER_REJECTED ->
                    PlacementResultCode.MUTATION_FAILED;
            case DUPLICATE_OPERATION_ID ->
                    PlacementResultCode.INTERNAL_ERROR;
        };
    }
}
