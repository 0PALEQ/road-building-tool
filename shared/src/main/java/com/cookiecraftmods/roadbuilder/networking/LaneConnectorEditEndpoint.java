package com.cookiecraftmods.roadbuilder.networking;

import com.cookiecraftmods.roadbuilder.placement.RoadTransitionPlanPreparer;
import com.cookiecraftmods.roadbuilder.placement.MaterializationRebuildWorld;
import com.cookiecraftmods.roadbuilder.placement.PlacementBlockWorld;
import com.cookiecraftmods.roadbuilder.network.JunctionDefinition;
import com.cookiecraftmods.roadbuilder.network.JunctionSolveResult;
import com.cookiecraftmods.roadbuilder.network.JunctionSolver;
import com.cookiecraftmods.roadbuilder.network.LaneConnector;
import com.cookiecraftmods.roadbuilder.network.LaneConnectorId;
import com.cookiecraftmods.roadbuilder.network.LaneConnectorMovement;
import com.cookiecraftmods.roadbuilder.network.LaneMovementType;
import com.cookiecraftmods.roadbuilder.network.RoadGraph;
import com.cookiecraftmods.roadbuilder.network.RoadGraphSnapshot;
import com.cookiecraftmods.roadbuilder.network.RoadLane;
import com.cookiecraftmods.roadbuilder.network.RoadLaneId;
import com.cookiecraftmods.roadbuilder.network.RoadNode;
import com.cookiecraftmods.roadbuilder.network.RoadNodeId;
import com.cookiecraftmods.roadbuilder.network.RoadSegment;
import com.cookiecraftmods.roadbuilder.network.SolvedJunctionApproach;
import com.cookiecraftmods.roadbuilder.placement.BlockPlacementPlan;
import com.cookiecraftmods.roadbuilder.placement.BlockPlanCommitter;
import com.cookiecraftmods.roadbuilder.placement.BlockPlanResult;
import com.cookiecraftmods.roadbuilder.placement.BlockTransitionStateMatcher;
import com.cookiecraftmods.roadbuilder.placement.ConstructionAuthorizer;
import com.cookiecraftmods.roadbuilder.placement.ConstructionLimits;
import com.cookiecraftmods.roadbuilder.placement.MaterializationPartition;
import com.cookiecraftmods.roadbuilder.placement.PlacementCommitResult;
import com.cookiecraftmods.roadbuilder.placement.PlacementOperation;
import com.cookiecraftmods.roadbuilder.placement.PlacementOperationId;
import com.cookiecraftmods.roadbuilder.placement.PlacementOwnershipLedger;
import com.cookiecraftmods.roadbuilder.placement.PlacementRemovalService;
import com.cookiecraftmods.roadbuilder.placement.PlannedBlock;
import com.cookiecraftmods.roadbuilder.placement.PositionChangeStack;
import com.cookiecraftmods.roadbuilder.placement.RemovalMode;
import com.cookiecraftmods.roadbuilder.placement.RoadMaterializationOwner;
import com.cookiecraftmods.roadbuilder.placement.ServerLevelPlacementBlockWorld;
import com.cookiecraftmods.roadbuilder.placement.SmoothRoadTransitionMaterializer;
import com.cookiecraftmods.roadbuilder.placement.StackRemoval;
import com.cookiecraftmods.roadbuilder.placement.StraightRoadIntersectionMaterializer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class LaneConnectorEditEndpoint {
    private final ConstructionAuthorizer authorizer;
    private final ConstructionLimits limits;
    private final BlockPlanCommitter committer;

    public LaneConnectorEditEndpoint(
            ConstructionAuthorizer authorizer,
            ConstructionLimits limits,
            BlockPlanCommitter committer
    ) {
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.committer = Objects.requireNonNull(committer, "committer");
    }

    public PlacementResultCode handle(
            LaneConnectorEditC2SPacket packet,
            ServerPlayer player,
            PlacementOwnershipLedger ledger,
            RoadGraph graph
    ) {
        if (!authorizer.validatePlayer(player).isAllowed()) {
            return PlacementResultCode.NOT_AUTHORIZED;
        }
        if (!player.serverLevel().dimension().location()
                        .equals(packet.dimensionId())
                || !graph.dimension().equals(player.serverLevel().dimension())
                || !ledger.dimension().equals(player.serverLevel().dimension())) {
            return PlacementResultCode.DIMENSION_MISMATCH;
        }
        if (graph.revision() != packet.expectedGraphRevision()) {
            return PlacementResultCode.GRAPH_REVISION_MISMATCH;
        }

        RoadLaneId fromLaneId = new RoadLaneId(packet.fromLaneId());
        RoadLaneId toLaneId = new RoadLaneId(packet.toLaneId());
        RoadGraphSnapshot snapshot = graph.snapshot();
        RoadLane from = snapshot.lanes().get(fromLaneId);
        RoadLane to = snapshot.lanes().get(toLaneId);
        if (from == null || to == null) {
            return PlacementResultCode.INVALID_GEOMETRY;
        }
        RoadSegment fromSegment = snapshot.segments().get(from.segmentId());
        RoadSegment toSegment = snapshot.segments().get(to.segmentId());
        if (fromSegment == null || toSegment == null) {
            return PlacementResultCode.INVALID_GEOMETRY;
        }
        RoadNodeId nodeId = sharedNode(fromSegment, toSegment);
        if (nodeId == null) {
            return PlacementResultCode.INVALID_GEOMETRY;
        }

        LaneConnector existing = snapshot.connectors().values().stream()
                .filter(connector -> connector.fromLaneId().equals(fromLaneId)
                        && connector.toLaneId().equals(toLaneId))
                .findFirst()
                .orElse(null);
        List<LaneConnector> candidateConnectors = new ArrayList<>(
                snapshot.connectors().values()
        );
        candidateConnectors.removeIf(connector ->
                connector.fromLaneId().equals(fromLaneId)
                        && connector.toLaneId().equals(toLaneId));
        LaneConnector edited = editedConnector(existing, fromLaneId, toLaneId);
        candidateConnectors.add(edited);

        BlockPlacementPlan desiredPlan;
        try {
            desiredPlan = nodePlan(
                    graph,
                    nodeId,
                    candidateConnectors,
                    new ServerLevelPlacementBlockWorld(player.serverLevel()),
                    ledger
            );
        } catch (RuntimeException failure) {
            return PlacementResultCode.MUTATION_FAILED;
        }

        ServerLevelPlacementBlockWorld world =
                new ServerLevelPlacementBlockWorld(player.serverLevel());
        PlacementOperation previous = ledger.operation(
                RoadMaterializationOwner.node(nodeId)
        ).orElse(null);
        if (previous == null) {
            if (desiredPlan == null) {
                return applyMutation(
                        graph,
                        packet,
                        fromLaneId,
                        toLaneId,
                        existing
                );
            }
            if (!authorizer.validateExistingFootprint(
                    player,
                    desiredPlan,
                    limits
            ).isAllowed()) {
                return PlacementResultCode.NOT_AUTHORIZED;
            }
            PlacementCommitResult commit = committer.commitOwned(
                    RoadMaterializationOwner.node(nodeId),
                    desiredPlan,
                    world,
                    ledger
            );
            if (!(commit instanceof PlacementCommitResult.Success success)) {
                return PlacementResultCode.WORLD_CONFLICT;
            }
            try {
                applyMutation(
                        graph,
                        packet,
                        fromLaneId,
                        toLaneId,
                        existing
                );
            } catch (RuntimeException failure) {
                new PlacementRemovalService().remove(
                        success.operation().id(),
                        RemovalMode.FORCE,
                        world,
                        ledger
                );
                return PlacementResultCode.MUTATION_FAILED;
            }
            return PlacementResultCode.ACCEPTED;
        }

        List<BlockPlacementPlan> plans = desiredPlan == null
                ? List.of()
                : List.of(desiredPlan);
        BlockPlacementPlan overlay = finalStateOverlay(
                world,
                ledger,
                List.of(previous.id()),
                plans
        );
        if (!authorizer.validateExistingFootprint(
                player,
                overlay,
                limits
        ).isAllowed()) {
            return PlacementResultCode.NOT_AUTHORIZED;
        }
        PlacementOperationId overlayId = committer.nextOperationId();
        PlacementCommitResult commit = committer.commitRestorationOverlay(
                overlayId,
                overlay,
                world,
                ledger
        );
        if (!(commit instanceof PlacementCommitResult.Success)) {
            return PlacementResultCode.WORLD_CONFLICT;
        }
        List<MaterializationPartition> partitions = desiredPlan == null
                ? List.of()
                : List.of(new MaterializationPartition(
                        overlayId,
                        RoadMaterializationOwner.node(nodeId),
                        desiredPlan.positions()
                ));
        try {
            ledger.publishMaterializationRebuild(
                    List.of(previous.id()),
                    overlayId,
                    partitions
            );
            applyMutation(
                    graph,
                    packet,
                    fromLaneId,
                    toLaneId,
                    existing
            );
        } catch (RuntimeException failure) {
            return PlacementResultCode.MUTATION_FAILED;
        }
        return PlacementResultCode.ACCEPTED;
    }

    private static LaneConnector editedConnector(
            LaneConnector existing,
            RoadLaneId fromLaneId,
            RoadLaneId toLaneId
    ) {
        if (existing != null) {
            LaneConnectorMovement movement = existing.movement();
            return new LaneConnector(
                    existing.id(),
                    fromLaneId,
                    toLaneId,
                    new LaneConnectorMovement(
                            movement.type(),
                            !movement.allowed(),
                            true,
                            movement.priority()
                    )
            );
        }
        return new LaneConnector(
                new LaneConnectorId(UUID.randomUUID()),
                fromLaneId,
                toLaneId,
                new LaneConnectorMovement(
                        LaneMovementType.MANUAL,
                        true,
                        true,
                        0
                )
        );
    }

    private static PlacementResultCode applyMutation(
            RoadGraph graph,
            LaneConnectorEditC2SPacket packet,
            RoadLaneId fromLaneId,
            RoadLaneId toLaneId,
            LaneConnector existing
    ) {
        if (existing != null) {
            LaneConnectorMovement movement = existing.movement();
            graph.updateConnectorMovement(
                    existing.id(),
                    packet.expectedGraphRevision(),
                    new LaneConnectorMovement(
                            movement.type(),
                            !movement.allowed(),
                            true,
                            movement.priority()
                    )
            );
        } else {
            graph.putManualConnector(
                    fromLaneId,
                    toLaneId,
                    packet.expectedGraphRevision(),
                    new LaneConnectorMovement(
                            LaneMovementType.MANUAL,
                            true,
                            true,
                            0
                    )
            );
        }
        return PlacementResultCode.ACCEPTED;
    }

    BlockPlacementPlan nodePlan(
            RoadGraph graph,
            RoadNodeId nodeId,
            Collection<LaneConnector> connectors,
            PlacementBlockWorld world,
            PlacementOwnershipLedger ledger
    ) {
        RoadNode node = graph.node(nodeId).orElse(null);
        if (node == null) {
            return null;
        }
        var previous = ledger.operation(RoadMaterializationOwner.node(nodeId));
        var planningWorld = new MaterializationRebuildWorld(
                world, ledger, previous.map(operation -> List.of(operation.id())).orElse(List.of()));
        List<RoadSegment> incident = graph.snapshot().segments().values().stream()
                .filter(segment -> segment.startNodeId().equals(nodeId)
                        || segment.endNodeId().equals(nodeId))
                .toList();
        if (incident.size() < 2
                || SmoothRoadTransitionMaterializer.supports(node, incident)
                || !isJunction(nodeId, incident)) {
            return null;
        }
        JunctionDefinition definition = graph.junctionDefinition(nodeId)
                .orElseGet(() -> JunctionDefinition.automatic(nodeId));
        JunctionSolveResult solved = new JunctionSolver().solve(
                node,
                incident,
                definition
        );
        if (!(solved instanceof JunctionSolveResult.Success success)) {
            throw new IllegalStateException(
                    "lane connector junction could not be solved"
            );
        }
        RoadSegment primary = success.approaches().stream()
                .filter(SolvedJunctionApproach::primary)
                .map(SolvedJunctionApproach::segment)
                .findFirst()
                .orElse(incident.get(0));
        BlockPlanResult surface = new StraightRoadIntersectionMaterializer()
                .planWithMarkings(
                        graph.dimension(),
                        node,
                        incident,
                        primary,
                        limits.maximumAffectedBlocks(),
                        limits.maximumPlanningCandidates(),
                        definition,
                        connectors
                );
        if (!(surface instanceof BlockPlanResult.Success surfaceSuccess)) {
            throw new IllegalStateException(
                    "lane connector junction surface failed"
            );
        }
        return completeNodePlan(surfaceSuccess.plan(), node, incident, planningWorld);
    }

    private BlockPlacementPlan completeNodePlan(BlockPlacementPlan surface, RoadNode node,
            List<RoadSegment> incident,
            MaterializationRebuildWorld world) {
        BlockPlanResult result = new RoadTransitionPlanPreparer()
                .complete(surface, node, incident, world, world::isMaterializedRoadPosition, limits);
        if (!(result instanceof BlockPlanResult.Success success)) {
            throw new IllegalStateException("lane connector junction structures failed");
        }
        return success.plan();
    }

    private static boolean isJunction(
            RoadNodeId nodeId,
            List<RoadSegment> incident
    ) {
        if (incident.size() >= 3) {
            return true;
        }
        if (!com.cookiecraftmods.roadbuilder.network.AuxiliaryLanePorts
                .attachments(nodeId, incident).isEmpty()) return true;
        if (incident.size() != 2) {
            return false;
        }
        var first = outwardTangent(incident.get(0), nodeId);
        var second = outwardTangent(incident.get(1), nodeId);
        double lengths = first.horizontalLength() * second.horizontalLength();
        if (lengths == 0.0) {
            return true;
        }
        double dot = (first.x() * second.x() + first.z() * second.z())
                / lengths;
        return dot > -Math.cos(Math.toRadians(5.0));
    }

    private static com.cookiecraftmods.roadbuilder.geometry.RoadVector
            outwardTangent(RoadSegment segment, RoadNodeId nodeId) {
        boolean start = segment.startNodeId().equals(nodeId);
        var tangent = segment.referenceGeometry().sample(
                start ? 0.0 : segment.referenceGeometry().length()
        ).tangent();
        return start ? tangent : tangent.scale(-1.0);
    }

    private static RoadNodeId sharedNode(
            RoadSegment first,
            RoadSegment second
    ) {
        if (first.startNodeId().equals(second.startNodeId())
                || first.startNodeId().equals(second.endNodeId())) {
            return first.startNodeId();
        }
        if (first.endNodeId().equals(second.startNodeId())
                || first.endNodeId().equals(second.endNodeId())) {
            return first.endNodeId();
        }
        return null;
    }

    static BlockPlacementPlan finalStateOverlay(
            PlacementBlockWorld world,
            PlacementOwnershipLedger ledger,
            Collection<PlacementOperationId> removedIds,
            Collection<BlockPlacementPlan> newPlans
    ) {
        Set<BlockPos> positions = new HashSet<>();
        removedIds.forEach(id -> ledger.operation(id)
                .ifPresent(operation -> positions.addAll(operation.positions())));
        newPlans.forEach(plan -> positions.addAll(plan.positions()));
        Map<BlockPos, BlockState> targets = new LinkedHashMap<>();
        for (BlockPos position : positions) {
            BlockState current = world.blockState(position);
            BlockState expected = ledger.expectedVisibleState(position)
                    .orElse(current);
            targets.put(
                    position,
                    BlockTransitionStateMatcher.matchesUpdateDrivenTarget(
                            current,
                            expected
                    ) ? stateAfterRemoving(
                            ledger,
                            position,
                            removedIds,
                            current
                    ) : current
            );
        }
        for (BlockPlacementPlan plan : newPlans) {
            for (PlannedBlock block : plan.blocks()) {
                targets.put(block.position(), block.targetState());
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
            StackRemoval removal = stack.remove(operationId);
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
}
