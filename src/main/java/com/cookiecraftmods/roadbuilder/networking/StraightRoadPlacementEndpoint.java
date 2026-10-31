package com.cookiecraftmods.roadbuilder.networking;

import com.cookiecraftmods.roadbuilder.placement.BlockPositionSets;
import com.cookiecraftmods.roadbuilder.RoadBuilder;
import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.network.PreparedRoadNetworkMutation;
import com.cookiecraftmods.roadbuilder.network.PreparedSegmentSplit;
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
import com.cookiecraftmods.roadbuilder.placement.PlacementCommitFailureCode;
import com.cookiecraftmods.roadbuilder.placement.PlacementCommitResult;
import com.cookiecraftmods.roadbuilder.placement.PlacementOwnershipLedger;
import com.cookiecraftmods.roadbuilder.placement.PlacementOperation;
import com.cookiecraftmods.roadbuilder.placement.PlacementOperationId;
import com.cookiecraftmods.roadbuilder.placement.MaterializationPartition;
import com.cookiecraftmods.roadbuilder.placement.IncrementalPlacementJobService;
import com.cookiecraftmods.roadbuilder.placement.OperationMaterializationRepartition;
import com.cookiecraftmods.roadbuilder.placement.JunctionMarkingMaterializer;
import com.cookiecraftmods.roadbuilder.placement.PlacementRemovalResult;
import com.cookiecraftmods.roadbuilder.placement.PlacementRemovalService;
import com.cookiecraftmods.roadbuilder.placement.RemovalMode;
import com.cookiecraftmods.roadbuilder.placement.ResolvedStraightRoadPlan;
import com.cookiecraftmods.roadbuilder.placement.RoadClearancePlanner;
import com.cookiecraftmods.roadbuilder.placement.RoadMaterializationOwner;
import com.cookiecraftmods.roadbuilder.placement.RoadMaterializationPlanner;
import com.cookiecraftmods.roadbuilder.placement.RoadMaterializationResolver;
import com.cookiecraftmods.roadbuilder.placement.RoadStructurePlanner;
import com.cookiecraftmods.roadbuilder.placement.ServerLevelPlacementBlockWorld;
import com.cookiecraftmods.roadbuilder.placement.StraightRoadIntent;
import com.cookiecraftmods.roadbuilder.placement.StraightRoadIntersectionMaterializer;
import com.cookiecraftmods.roadbuilder.placement.SmoothRoadTransitionMaterializer;
import com.cookiecraftmods.roadbuilder.placement.RoadTransitionPlanPreparer;
import com.cookiecraftmods.roadbuilder.placement.StraightRoadPlanningService;
import com.cookiecraftmods.roadbuilder.placement.StraightRoadPlanningResult;
import com.cookiecraftmods.roadbuilder.placement.StraightRoadSegmentMaterializer;
import net.minecraft.core.BlockPos;
import com.cookiecraftmods.roadbuilder.preset.RoadSurfaceRole;
import com.cookiecraftmods.roadbuilder.preset.TrafficHandedness;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import com.cookiecraftmods.roadbuilder.persistence.RoadBuilderConstructionJobSavedData;

import java.util.Objects;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;

public final class StraightRoadPlacementEndpoint {
    private final StraightRoadPlanningService planningService;
    private final ConstructionAuthorizer authorizer;
    private final ConstructionLimits limits;
    private final BlockPlanCommitter committer;
    private final TrafficHandedness trafficHandedness;
    private final IncrementalPlacementJobService incrementalPlacements;

    public StraightRoadPlacementEndpoint(
            StraightRoadPlanningService planningService,
            ConstructionAuthorizer authorizer,
            ConstructionLimits limits,
            BlockPlanCommitter committer
    ) {
        this(planningService, authorizer, limits, committer, TrafficHandedness.RHT);
    }

    public StraightRoadPlacementEndpoint(
            StraightRoadPlanningService planningService,
            ConstructionAuthorizer authorizer,
            ConstructionLimits limits,
            BlockPlanCommitter committer,
            TrafficHandedness trafficHandedness
    ) {
        this.planningService = Objects.requireNonNull(planningService, "planningService");
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.committer = Objects.requireNonNull(committer, "committer");
        this.trafficHandedness = Objects.requireNonNull(trafficHandedness, "trafficHandedness");
        this.incrementalPlacements = new IncrementalPlacementJobService();
    }

    public PlacementResultS2CPacket handle(
            StraightRoadIntentC2SPacket packet,
            ServerPlayer player,
            PlacementOwnershipLedger ledger,
            RoadGraph graph
    ) {
        return handle(packet, player, ledger, graph, null);
    }

    public PlacementResultS2CPacket handle(
            StraightRoadIntentC2SPacket packet,
            ServerPlayer player,
            PlacementOwnershipLedger ledger,
            RoadGraph graph,
            RoadBuilderConstructionJobSavedData jobs
    ) {
        Objects.requireNonNull(packet, "packet");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(ledger, "ledger");
        Objects.requireNonNull(graph, "graph");

        ConstructionValidationResult playerValidation = authorizer.validatePlayer(player);
        if (!playerValidation.isAllowed()) {
            return rejected(packet, mapValidationFailure(playerValidation.code()));
        }
        if (!player.serverLevel().dimension().location().equals(packet.dimensionId())) {
            return rejected(packet, PlacementResultCode.DIMENSION_MISMATCH);
        }
        if (!player.serverLevel().dimension().equals(graph.dimension())) {
            return rejected(packet, PlacementResultCode.DIMENSION_MISMATCH);
        }
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, packet.dimensionId());
        StraightRoadIntent intent = new StraightRoadIntent(
                dimension,
                new RoadPoint(packet.startX(), packet.startY(), packet.startZ()),
                new RoadPoint(packet.endX(), packet.endY(), packet.endZ()),
                packet.presetId(),
                packet.expectedPresetRevision()
        );
        StraightRoadPlanningResult planningResult = planningService.resolve(intent, limits);
        if (planningResult instanceof StraightRoadPlanningResult.Failure failure) {
            return rejected(packet, mapPlanFailure(failure.code()));
        }

        ResolvedStraightRoadPlan planned = ((StraightRoadPlanningResult.Success) planningResult)
                .roadPlan();
        PreparedRoadNetworkMutation graphMutation;
        try {
            graphMutation = graph.prepareRoadNetwork(
                new com.cookiecraftmods.roadbuilder.placement.RoadOverpassPlanner().adjust(
                        planned.geometry(), planned.preset().surfaceBlockWidth(), graph.snapshot().segments().values()),
                planned.presetId(),
                planned.preset(),
                trafficHandedness
        );
        } catch (IllegalArgumentException invalid) {
            return rejected(packet, PlacementResultCode.INVALID_GEOMETRY);
        }
        ServerLevelPlacementBlockWorld world = new ServerLevelPlacementBlockWorld(
                player.serverLevel()
        );
        graphMutation = graph.withTunnelPortals(graphMutation, world, ledger::isMaterializedRoadPosition);
        if (graphMutation.allUpsertedSegments().stream().anyMatch(segment ->
                segment.referenceGeometry().length() < com.cookiecraftmods.roadbuilder.network.RoadSegment.MINIMUM_BUILD_LENGTH)) {
            return rejected(packet, PlacementResultCode.INVALID_GEOMETRY);
        }
        BlockPlanResult clearedRoadResult = new RoadMaterializationPlanner().plan(
                dimension,
                graphMutation.placedSegments(),
                world,
                ledger,
                limits
        );
        if (!(clearedRoadResult instanceof BlockPlanResult.Success clearedRoad)) {
            return rejected(
                    packet,
                    mapPlanFailure(((BlockPlanResult.Failure) clearedRoadResult).code())
            );
        }

        Map<RoadNodeId, BlockPlacementPlan> intersectionPlans;
        try {
            intersectionPlans = prepareIntersectionPlans(
                    planned,
                    graphMutation,
                    player.serverLevel().dimension(),
                    world,
                    graph,
                    ledger,
                    BlockPositionSets.copyOf(clearedRoad.plan().positions())
            );
        } catch (IntersectionPlanningFailure failure) {
            return rejected(packet, mapPlanFailure(failure.code()));
        }
        List<com.cookiecraftmods.roadbuilder.placement.LayeredBlockPlan>
                materializationPlans = new ArrayList<>();
        materializationPlans.add(new
                com.cookiecraftmods.roadbuilder.placement.LayeredBlockPlan(
                clearedRoad.plan(),
                com.cookiecraftmods.roadbuilder.placement.MaterialLayer.SEGMENT_SURFACE,
                "segment",
                "proposed"
        ));
        intersectionPlans.forEach((nodeId, junctionPlan) ->
                materializationPlans.add(new
                        com.cookiecraftmods.roadbuilder.placement.LayeredBlockPlan(
                        junctionPlan,
                        com.cookiecraftmods.roadbuilder.placement.MaterialLayer.JUNCTION_SURFACE,
                        "node",
                        nodeId.toString()
                )));
        BlockPlacementPlan materializationPlan =
                BlockPlacementPlans.mergeLayered(
                player.serverLevel().dimension(),
                materializationPlans
        );
        ConstructionValidationResult planValidation = authorizer.validate(
                player,
                materializationPlan,
                limits
        );
        if (!planValidation.isAllowed()) {
            RoadBuilder.LOGGER.warn(
                    "Rejected straight-road construction authorization: code={}, blocks={}, bounds={}, player={}",
                    planValidation.code(),
                    materializationPlan.affectedBlockCount(),
                    materializationPlan.bounds(),
                    player.blockPosition()
            );
            return rejected(packet, mapValidationFailure(planValidation.code()));
        }

        Map<RoadNodeId, BlockPlacementPlan> newlyOwnedIntersectionPlans =
                intersectionPlans.entrySet().stream()
                        .filter(entry -> ledger.operation(
                                RoadMaterializationOwner.node(entry.getKey())
                        ).isEmpty())
                        .collect(java.util.stream.Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue,
                                (first, second) -> first,
                                LinkedHashMap::new
                        ));
        boolean repartitionsPlacement = !graphMutation.segmentSplits().isEmpty()
                || !newlyOwnedIntersectionPlans.isEmpty();
        List<OperationMaterializationRepartition> existingRepartitions;
        try {
            existingRepartitions = prepareExistingRepartitions(
                    graphMutation,
                    player.serverLevel().dimension(),
                    ledger
            );
        } catch (IllegalStateException exception) {
            return rejected(packet, PlacementResultCode.PERSISTENCE_INCOMPATIBLE);
        }

        PlacementOperationId operationId = committer.nextOperationId();
        Optional<RoadMaterializationOwner> directOwner = repartitionsPlacement
                ? Optional.empty()
                : Optional.of(RoadMaterializationOwner.segment(
                        graphMutation.placedSegments().get(0).id()
                ));
        List<OperationMaterializationRepartition> allRepartitions =
                new ArrayList<>(existingRepartitions);
        if (repartitionsPlacement) {
            PlacementOperation preparedOperation = PlacementOperation.create(
                    operationId,
                    player.serverLevel().dimension(),
                    materializationPlan.positions()
            );
            allRepartitions.add(prepareRepartition(
                    preparedOperation,
                    graphMutation.placedSegments(),
                    player.serverLevel().dimension(),
                    newlyOwnedIntersectionPlans
            ));
        }

        if (jobs != null) {
            IncrementalPlacementJobService.EnqueueResult enqueue =
                    incrementalPlacements.enqueueIfExpensive(
                            player.getUUID(),
                            operationId,
                            materializationPlan,
                            directOwner,
                            allRepartitions,
                            graphMutation,
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

        PlacementCommitResult commitResult = repartitionsPlacement
                ? committer.commit(operationId, materializationPlan, world, ledger)
                : committer.commit(
                        operationId,
                        directOwner.orElseThrow(),
                        materializationPlan,
                        world,
                        ledger
                );
        if (commitResult instanceof PlacementCommitResult.Failure failure) {
            return rejected(packet, mapCommitFailure(failure.code()));
        }

        PlacementCommitResult.Success success = (PlacementCommitResult.Success) commitResult;
        try {
            if (repartitionsPlacement) {
                ledger.repartitionMaterializations(allRepartitions);
            }
            graph.applyNetworkMutation(graphMutation);
        } catch (RuntimeException graphFailure) {
            // Before repartition, the committed source operation can be
            // rolled back normally. Once repartition succeeds, graph
            // prevalidation makes applyNetworkMutation deterministic.
            if (ledger.operation(success.operation().id()).isPresent()
                    && ledger.operation(success.operation().id()).orElseThrow()
                    .materializationOwner().isEmpty()) {
                PlacementRemovalResult rollback = new PlacementRemovalService().remove(
                        success.operation().id(),
                        RemovalMode.FORCE,
                        world,
                        ledger
                );
                if (rollback instanceof PlacementRemovalResult.Failure) {
                    return rejected(packet, PlacementResultCode.INTERNAL_ERROR);
                }
            }
            return rejected(packet, PlacementResultCode.MUTATION_FAILED);
        }
        return PlacementResultS2CPacket.accepted(
                packet.requestId(),
                success.operation().id().value(),
                success.operation().positions().size()
        );
    }

    private OperationMaterializationRepartition prepareRepartition(
            PlacementOperation source,
            List<RoadSegment> children,
            ResourceKey<Level> dimension,
            Map<RoadNodeId, BlockPlacementPlan> nodePlans
    ) {
        Objects.requireNonNull(nodePlans, "nodePlans");
        List<Set<BlockPos>> childPositions = new ArrayList<>(children.size());
        StraightRoadSegmentMaterializer materializer = new StraightRoadSegmentMaterializer();
        for (RoadSegment child : children) {
            BlockPlanResult result = materializer.plan(dimension, child, limits);
            if (!(result instanceof BlockPlanResult.Success success)) {
                throw new IllegalStateException("could not rematerialize split child");
            }
            childPositions.add(new HashSet<>(success.plan().positions()));
        }

        List<List<BlockPos>> partitions = new ArrayList<>(children.size());
        for (int index = 0; index < children.size(); index++) {
            partitions.add(new ArrayList<>());
        }
        for (BlockPos position : source.positions()) {
            if (nodePlans.values().stream().anyMatch(plan ->
                    plan.positions().contains(position))) {
                continue;
            }
            boolean assigned = false;
            for (int index = 0; index < children.size(); index++) {
                if (childPositions.get(index).contains(position)) {
                    partitions.get(index).add(position);
                    assigned = true;
                }
            }
            if (!assigned) {
                partitions.get(nearestChild(position, children)).add(position);
            }
        }
        for (int index = 0; index < partitions.size(); index++) {
            if (!partitions.get(index).isEmpty()) {
                continue;
            }
            RoadSegment child = children.get(index);
            BlockPos closest = source.positions().stream()
                    .min(java.util.Comparator.comparingDouble(position ->
                            distanceSquared(position, child)))
                    .orElseThrow();
            partitions.get(index).add(closest);
        }

        List<MaterializationPartition> materializations =
                new ArrayList<>(children.size());
        for (int index = 0; index < children.size(); index++) {
            materializations.add(new MaterializationPartition(
                    index == 0 ? source.id() : PlacementOperationId.create(),
                    RoadMaterializationOwner.segment(children.get(index).id()),
                    partitions.get(index)
            ));
        }
        for (Map.Entry<RoadNodeId, BlockPlacementPlan> entry : nodePlans.entrySet()) {
            materializations.add(new MaterializationPartition(
                    PlacementOperationId.create(),
                    RoadMaterializationOwner.node(entry.getKey()),
                    entry.getValue().positions()
            ));
        }
        return new OperationMaterializationRepartition(
                source.id(),
                materializations
        );
    }

    private List<OperationMaterializationRepartition> prepareExistingRepartitions(
            PreparedRoadNetworkMutation mutation,
            ResourceKey<Level> dimension,
            PlacementOwnershipLedger ledger
    ) {
        List<OperationMaterializationRepartition> repartitions =
                new ArrayList<>(mutation.segmentSplits().size());
        for (PreparedSegmentSplit split : mutation.segmentSplits()) {
            PlacementOperation source = new RoadMaterializationResolver(limits)
                    .resolve(split.original(), dimension, ledger)
                    .orElseThrow(() -> new IllegalStateException(
                            "crossed segment has no materialization history"
                    ));
            repartitions.add(prepareRepartition(
                    source,
                    split.replacements(),
                    dimension,
                    Map.of()
            ));
        }
        return List.copyOf(repartitions);
    }

    private Map<RoadNodeId, BlockPlacementPlan> prepareIntersectionPlans(
            ResolvedStraightRoadPlan planned,
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
                    throw new IntersectionPlanningFailure(
                            ((BlockPlanResult.Failure) transition).code()
                    );
                }
                plans.put(node.id(), success.plan());
                continue;
            }
            if (incident.size() < 3) {
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
                throw new IntersectionPlanningFailure(
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
            BlockPlanResult surfaceResult = materializer.plan(
                    dimension,
                    node,
                    incident,
                    pavementBlock,
                    sidewalkBlock,
                    limits.maximumAffectedBlocks(),
                    limits.maximumPlanningCandidates(), definition
            );
            if (!(surfaceResult instanceof BlockPlanResult.Success surface)) {
                throw new IntersectionPlanningFailure(
                        ((BlockPlanResult.Failure) surfaceResult).code()
                );
            }
            BlockPlacementPlan decoratedSurface = surface.plan();
            BlockPlanResult markings = new JunctionMarkingMaterializer().plan(
                    dimension,
                    node,
                    incident,
                    definition,
                    limits.maximumAffectedBlocks()
            );
            if (markings instanceof
                    BlockPlanResult.Success markingSuccess) {
                var roundedEdgePositions = surface.plan().blocks().stream()
                        .filter(block -> !BuiltInRegistries.BLOCK.getKey(
                                block.targetState().getBlock()
                        ).equals(pavementBlock))
                        .map(com.cookiecraftmods.roadbuilder.placement.PlannedBlock::position)
                        .toList();
                var clippedMarkings = BlockPlacementPlans.excludingPositions(
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
                            segment.presetSnapshot().surfaceBands()
                    ))
                    .toList();
            decoratedSurface = structurePlanner.prepareStructureSurface(
                    decoratedSurface, structureSpans, world,
                    position -> ledger.isMaterializedRoadPosition(position)
                            || proposedRoadPositions.contains(position));
            BlockPlanResult structuredResult = structurePlanner.addStructures(
                    decoratedSurface,
                    world,
                    structureSpans,
                    position -> ledger.isMaterializedRoadPosition(position)
                            || proposedRoadPositions.contains(position),
                    limits.maximumAffectedBlocks()
            );
            if (!(structuredResult instanceof
                    BlockPlanResult.Success structured)) {
                throw new IntersectionPlanningFailure(
                        ((BlockPlanResult.Failure) structuredResult).code()
                );
            }
            Set<BlockPos> structurePositions = BlockPositionSets.copyOf(
                    structured.plan().positions()
            );
            BlockPlanResult clearedResult = clearancePlanner.addClearance(
                    decoratedSurface,
                     world,
                     position -> ledger.isMaterializedRoadPosition(position)
                             || proposedRoadPositions.contains(position)
                             || structurePositions.contains(position),
                     limits.maximumAffectedBlocks()
             );
            if (!(clearedResult instanceof BlockPlanResult.Success cleared)) {
                throw new IntersectionPlanningFailure(
                        ((BlockPlanResult.Failure) clearedResult).code()
                );
            }
            plans.put(node.id(), BlockPlacementPlans.mergeLayered(
                    dimension,
                    List.of(
                            new com.cookiecraftmods.roadbuilder.placement.LayeredBlockPlan(
                                    structured.plan(),
                                    com.cookiecraftmods.roadbuilder.placement.MaterialLayer.ROAD_STRUCTURE,
                                    "node-structure",
                                    node.id().toString()
                            ),
                            new com.cookiecraftmods.roadbuilder.placement.LayeredBlockPlan(
                                    cleared.plan(),
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

    private static int nearestChild(BlockPos position, List<RoadSegment> children) {
        int nearest = 0;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (int index = 0; index < children.size(); index++) {
            double distance = distanceSquared(position, children.get(index));
            if (distance < nearestDistance) {
                nearest = index;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private static double distanceSquared(BlockPos position, RoadSegment segment) {
        double x = position.getX() + 0.5;
        double z = position.getZ() + 0.5;
        var start = segment.referenceGeometry().start();
        var end = segment.referenceGeometry().end();
        double deltaX = end.x() - start.x();
        double deltaZ = end.z() - start.z();
        double lengthSquared = deltaX * deltaX + deltaZ * deltaZ;
        double parameter = ((x - start.x()) * deltaX + (z - start.z()) * deltaZ)
                / lengthSquared;
        double clamped = Math.max(0.0, Math.min(1.0, parameter));
        double nearestX = start.x() + deltaX * clamped;
        double nearestZ = start.z() + deltaZ * clamped;
        double xDistance = x - nearestX;
        double zDistance = z - nearestZ;
        return xDistance * xDistance + zDistance * zDistance;
    }

    private static PlacementResultS2CPacket rejected(
            StraightRoadIntentC2SPacket packet,
            PlacementResultCode code
    ) {
        return PlacementResultS2CPacket.rejected(packet.requestId(), code);
    }

    private static PlacementResultCode mapPlanFailure(BlockPlanFailureCode code) {
        return switch (code) {
            case UNKNOWN_PRESET -> PlacementResultCode.UNKNOWN_PRESET;
            case PRESET_REVISION_MISMATCH -> PlacementResultCode.PRESET_REVISION_MISMATCH;
            case TOO_SHARP_TURN -> PlacementResultCode.TOO_SHARP_TURN;
            case SELF_INTERSECTION -> PlacementResultCode.SELF_INTERSECTION;
            case INVALID_GEOMETRY, COORDINATE_OUT_OF_RANGE ->
                    PlacementResultCode.INVALID_GEOMETRY;
            case BLOCK_LIMIT_EXCEEDED, RASTERIZATION_WORK_LIMIT_EXCEEDED,
                    GRADE_LIMIT_EXCEEDED ->
                    PlacementResultCode.CONSTRUCTION_LIMIT_EXCEEDED;
            case UNKNOWN_SURFACE_BLOCK, AIR_SURFACE_BLOCK -> PlacementResultCode.UNSUPPORTED_WORLD_STATE;
        };
    }

    private static PlacementResultCode mapValidationFailure(ConstructionValidationCode code) {
        return switch (code) {
            case ALLOWED -> throw new IllegalArgumentException("ALLOWED is not a validation failure");
            case WRONG_DIMENSION -> PlacementResultCode.DIMENSION_MISMATCH;
            case BLOCK_LIMIT_EXCEEDED, CHUNK_LIMIT_EXCEEDED ->
                    PlacementResultCode.CONSTRUCTION_LIMIT_EXCEEDED;
            case NOT_CREATIVE, NO_PERMISSION, CHUNK_NOT_LOADED, OUTSIDE_WORLD,
                    OUTSIDE_WORLD_BORDER, TOO_FAR_AWAY -> PlacementResultCode.NOT_AUTHORIZED;
        };
    }

    private static PlacementResultCode mapCommitFailure(PlacementCommitFailureCode code) {
        return switch (code) {
            case DIMENSION_MISMATCH -> PlacementResultCode.DIMENSION_MISMATCH;
            case OWNERSHIP_LIMIT_EXCEEDED -> PlacementResultCode.CONSTRUCTION_LIMIT_EXCEEDED;
            case CURRENT_BLOCK_ENTITY_UNSUPPORTED, TARGET_BLOCK_ENTITY_UNSUPPORTED,
                    CURRENT_FLUID_UNSUPPORTED, TARGET_FLUID_UNSUPPORTED ->
                    PlacementResultCode.UNSUPPORTED_WORLD_STATE;
            case WORLD_CHANGED_DURING_COMMIT -> PlacementResultCode.WORLD_CONFLICT;
            case WORLD_WRITE_REJECTED, WORLD_WRITE_DID_NOT_STICK, ROLLBACK_INCOMPLETE,
                    LEDGER_REJECTED -> PlacementResultCode.MUTATION_FAILED;
            case DUPLICATE_OPERATION_ID -> PlacementResultCode.INTERNAL_ERROR;
        };
    }

    private static final class IntersectionPlanningFailure
            extends RuntimeException {
        private final BlockPlanFailureCode code;

        private IntersectionPlanningFailure(BlockPlanFailureCode code) {
            this.code = Objects.requireNonNull(code, "code");
        }

        private BlockPlanFailureCode code() {
            return code;
        }
    }
}
