package com.cookiecraftmods.roadbuilder.networking;

import com.cookiecraftmods.roadbuilder.network.ApproachKey;
import com.cookiecraftmods.roadbuilder.network.JunctionApproachOverride;
import com.cookiecraftmods.roadbuilder.network.JunctionCrossingPolicy;
import com.cookiecraftmods.roadbuilder.network.JunctionDefinition;
import com.cookiecraftmods.roadbuilder.network.JunctionSolveResult;
import com.cookiecraftmods.roadbuilder.network.JunctionSolver;
import com.cookiecraftmods.roadbuilder.network.RoadGraph;
import com.cookiecraftmods.roadbuilder.network.RoadNode;
import com.cookiecraftmods.roadbuilder.network.RoadNodeId;
import com.cookiecraftmods.roadbuilder.network.RoadSegment;
import com.cookiecraftmods.roadbuilder.network.RoadSegmentId;
import com.cookiecraftmods.roadbuilder.network.SolvedJunctionApproach;
import com.cookiecraftmods.roadbuilder.placement.BlockPlacementPlan;
import com.cookiecraftmods.roadbuilder.placement.BlockPlacementPlans;
import com.cookiecraftmods.roadbuilder.placement.BlockPlanCommitter;
import com.cookiecraftmods.roadbuilder.placement.BlockPlanResult;
import com.cookiecraftmods.roadbuilder.placement.BlockTransitionStateMatcher;
import com.cookiecraftmods.roadbuilder.placement.ConstructionAuthorizer;
import com.cookiecraftmods.roadbuilder.placement.ConstructionLimits;
import com.cookiecraftmods.roadbuilder.placement.JunctionMarkingMaterializer;
import com.cookiecraftmods.roadbuilder.placement.LayeredBlockPlan;
import com.cookiecraftmods.roadbuilder.placement.MaterialLayer;
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
import com.cookiecraftmods.roadbuilder.placement.RoadTransitionPlanPreparer;
import com.cookiecraftmods.roadbuilder.placement.ServerLevelPlacementBlockWorld;
import com.cookiecraftmods.roadbuilder.placement.SmoothRoadTransitionMaterializer;
import com.cookiecraftmods.roadbuilder.placement.StackRemoval;
import com.cookiecraftmods.roadbuilder.placement.StraightRoadIntersectionMaterializer;
import com.cookiecraftmods.roadbuilder.preset.RoadSurfaceRole;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class CrosswalkToggleEndpoint {
    private final ConstructionAuthorizer authorizer;
    private final ConstructionLimits limits;
    private final BlockPlanCommitter committer;

    public CrosswalkToggleEndpoint(
            ConstructionAuthorizer authorizer,
            ConstructionLimits limits,
            BlockPlanCommitter committer
    ) {
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.committer = Objects.requireNonNull(committer, "committer");
    }

    public PlacementResultS2CPacket handle(
            CrosswalkToggleIntentC2SPacket packet,
            ServerPlayer player,
            PlacementOwnershipLedger ledger,
            RoadGraph graph
    ) {
        if (!authorizer.validatePlayer(player).isAllowed()) {
            return rejected(packet, PlacementResultCode.NOT_AUTHORIZED);
        }
        if (!player.serverLevel().dimension().location()
                        .equals(packet.dimensionId())
                || !graph.dimension().equals(player.serverLevel().dimension())
                || !ledger.dimension().equals(player.serverLevel().dimension())) {
            return rejected(packet, PlacementResultCode.DIMENSION_MISMATCH);
        }
        if (graph.revision() != packet.expectedGraphRevision()) {
            return rejected(packet, PlacementResultCode.GRAPH_REVISION_MISMATCH);
        }

        RoadSegment selected = graph.segment(
                new RoadSegmentId(packet.segmentId())
        ).orElse(null);
        RoadNodeId nodeId = new RoadNodeId(packet.nodeId());
        RoadNode node = graph.node(nodeId).orElse(null);
        if (selected == null || node == null
                || !selected.startNodeId().equals(nodeId)
                && !selected.endNodeId().equals(nodeId)) {
            return rejected(packet, PlacementResultCode.INVALID_GEOMETRY);
        }
        if (selected.presetSnapshot().classification().roadClass()
                == com.cookiecraftmods.roadbuilder.preset.RoadClass.HIGHWAY) {
            return rejected(packet, PlacementResultCode.INVALID_GEOMETRY);
        }
        List<RoadSegment> incident = graph.snapshot().segments().values().stream()
                .filter(segment -> segment.startNodeId().equals(nodeId)
                        || segment.endNodeId().equals(nodeId))
                .toList();
        JunctionDefinition existing = graph.junctionDefinition(nodeId)
                .orElse(null);
        JunctionDefinition supplied = existing == null
                ? JunctionDefinition.automatic(nodeId)
                : existing;
        JunctionSolveResult solvedResult = new JunctionSolver().solve(
                node,
                incident,
                supplied
        );
        if (!(solvedResult instanceof JunctionSolveResult.Success solved)) {
            return rejected(packet, PlacementResultCode.INVALID_GEOMETRY);
        }
        ApproachKey selectedKey = ApproachKey.at(selected, nodeId);
        SolvedJunctionApproach selectedApproach = solved.approaches().stream()
                .filter(approach -> approach.key().equals(selectedKey))
                .findFirst()
                .orElse(null);
        if (selectedApproach == null) {
            return rejected(packet, PlacementResultCode.INVALID_GEOMETRY);
        }
        boolean currentCrosswalk = incident.size() == 1
                && !supplied.approachOverrides().containsKey(selectedKey)
                ? false
                : selectedApproach.crosswalk();
        JunctionDefinition updated = toggledDefinition(
                supplied,
                existing != null,
                solved.approaches(),
                selectedKey,
                !currentCrosswalk
        );
        BlockPlacementPlan desiredPlan;
        try {
            desiredPlan = nodePlan(
                    graph,
                    node,
                    incident,
                    updated,
                    new ServerLevelPlacementBlockWorld(player.serverLevel()),
                    ledger
            );
        } catch (RuntimeException failure) {
            return rejected(packet, PlacementResultCode.MUTATION_FAILED);
        }

        ServerLevelPlacementBlockWorld world =
                new ServerLevelPlacementBlockWorld(player.serverLevel());
        PlacementOperation previous = ledger.operation(
                RoadMaterializationOwner.node(nodeId)
        ).orElse(null);
        if (previous == null) {
            if (desiredPlan == null) {
                return rejected(packet, PlacementResultCode.INVALID_GEOMETRY);
            }
            if (!authorizer.validateExistingFootprint(
                    player,
                    desiredPlan,
                    limits
            ).isAllowed()) {
                return rejected(packet, PlacementResultCode.NOT_AUTHORIZED);
            }
            PlacementCommitResult commit = committer.commitOwned(
                    RoadMaterializationOwner.node(nodeId),
                    desiredPlan,
                    world,
                    ledger
            );
            if (!(commit instanceof PlacementCommitResult.Success success)) {
                return rejected(packet, PlacementResultCode.WORLD_CONFLICT);
            }
            try {
                graph.upsertJunctionDefinition(updated, packet.expectedGraphRevision());
            } catch (RuntimeException failure) {
                new PlacementRemovalService().remove(
                        success.operation().id(),
                        RemovalMode.FORCE,
                        world,
                        ledger
                );
                return rejected(packet, PlacementResultCode.MUTATION_FAILED);
            }
            return PlacementResultS2CPacket.accepted(
                    packet.requestId(),
                    success.operation().id().value(),
                    desiredPlan.affectedBlockCount()
            );
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
            return rejected(packet, PlacementResultCode.NOT_AUTHORIZED);
        }
        PlacementOperationId overlayId = committer.nextOperationId();
        PlacementCommitResult commit = committer.commitRestorationOverlay(
                overlayId,
                overlay,
                world,
                ledger
        );
        if (!(commit instanceof PlacementCommitResult.Success)) {
            return rejected(packet, PlacementResultCode.WORLD_CONFLICT);
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
            graph.upsertJunctionDefinition(updated, packet.expectedGraphRevision());
        } catch (RuntimeException failure) {
            return rejected(packet, PlacementResultCode.MUTATION_FAILED);
        }
        return PlacementResultS2CPacket.accepted(
                packet.requestId(),
                overlayId.value(),
                overlay.affectedBlockCount()
        );
    }

    static JunctionDefinition toggledDefinition(
            JunctionDefinition definition,
            boolean persisted,
            List<SolvedJunctionApproach> approaches,
            ApproachKey selected,
            boolean enabled
    ) {
        Map<ApproachKey, JunctionApproachOverride> overrides =
                new LinkedHashMap<>();
        for (SolvedJunctionApproach approach : approaches) {
            boolean crosswalk = approach.key().equals(selected)
                    ? enabled
                    : approach.crosswalk();
            overrides.put(
                    approach.key(),
                    new JunctionApproachOverride(
                            approach.control(),
                            crosswalk
                    )
            );
        }
        return new JunctionDefinition(
                definition.nodeId(),
                definition.strategy(),
                definition.priorityPolicy(),
                definition.primaryApproachKeys(),
                JunctionCrossingPolicy.AUTO,
                overrides,
                persisted ? definition.revision() + 1 : 1
        );
    }

    private BlockPlacementPlan nodePlan(
            RoadGraph graph,
            RoadNode node,
            List<RoadSegment> incident,
            JunctionDefinition definition,
            ServerLevelPlacementBlockWorld world,
            PlacementOwnershipLedger ledger
    ) {
        if (incident.size() == 1) {
            RoadSegment segment = incident.get(0);
            ApproachKey key = ApproachKey.at(segment, node.id());
            JunctionApproachOverride override = definition
                    .approachOverrides()
                    .get(key);
            if (override == null || !override.crosswalk()) {
                return null;
            }
            BlockPlanResult terminal = new JunctionMarkingMaterializer()
                    .planTerminalCrosswalk(
                            graph.dimension(),
                            node,
                            segment,
                            limits.maximumAffectedBlocks()
                    );
            if (!(terminal instanceof BlockPlanResult.Success success)) {
                throw new IllegalStateException(
                        "terminal crosswalk could not be materialized"
                );
            }
            return success.plan();
        }
        BlockPlacementPlan base = null;
        if (SmoothRoadTransitionMaterializer.supports(node, incident)) {
            BlockPlanResult transition = new RoadTransitionPlanPreparer().plan(
                    graph.dimension(),
                    node,
                    incident,
                    world,
                    ledger::isMaterializedRoadPosition,
                    limits
            );
            if (!(transition instanceof BlockPlanResult.Success success)) {
                throw new IllegalStateException("crosswalk transition failed");
            }
            base = success.plan();
        } else if (incident.size() >= 2) {
            JunctionSolveResult solved = new JunctionSolver().solve(
                    node,
                    incident,
                    definition
            );
            if (!(solved instanceof JunctionSolveResult.Success success)) {
                throw new IllegalStateException("crosswalk junction failed");
            }
            RoadSegment primary = success.approaches().stream()
                    .filter(SolvedJunctionApproach::primary)
                    .map(SolvedJunctionApproach::segment)
                    .findFirst()
                    .orElse(incident.get(0));
            var style = primary.presetSnapshot().intersectionStyle();
            var sidewalk = primary.presetSnapshot().surfaceBands().stream()
                    .filter(band -> band.role() == RoadSurfaceRole.SIDEWALK)
                    .findFirst()
                    .map(band -> band.block())
                    .orElse(style.pavementBlock());
            BlockPlanResult surface = new StraightRoadIntersectionMaterializer()
                    .plan(
                            graph.dimension(),
                            node,
                            incident,
                            style.pavementBlock(),
                            sidewalk,
                            limits.maximumAffectedBlocks(),
                            limits.maximumPlanningCandidates(), definition
                    );
            if (!(surface instanceof BlockPlanResult.Success successSurface)) {
                throw new IllegalStateException("crosswalk surface failed");
            }
            base = successSurface.plan();
        }

        BlockPlanResult markings = new JunctionMarkingMaterializer().plan(
                graph.dimension(),
                node,
                incident,
                definition,
                limits.maximumAffectedBlocks()
        );
        BlockPlacementPlan markingPlan = markings instanceof
                BlockPlanResult.Success success ? success.plan() : null;
        if (base == null) {
            return markingPlan;
        }
        if (markingPlan == null) {
            return base;
        }
        return BlockPlacementPlans.mergeLayered(
                graph.dimension(),
                List.of(
                        new LayeredBlockPlan(
                                base,
                                MaterialLayer.JUNCTION_SURFACE,
                                "node",
                                node.id().toString()
                        ),
                        new LayeredBlockPlan(
                                markingPlan,
                                MaterialLayer.JUNCTION_MARKING,
                                "node-marking",
                                node.id().toString()
                        )
                )
        );
    }

    private static BlockPlacementPlan finalStateOverlay(
            ServerLevelPlacementBlockWorld world,
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
                    BlockTransitionStateMatcher.matchesPlannedTarget(
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

    private static PlacementResultS2CPacket rejected(
            CrosswalkToggleIntentC2SPacket packet,
            PlacementResultCode code
    ) {
        return PlacementResultS2CPacket.rejected(packet.requestId(), code);
    }
}
