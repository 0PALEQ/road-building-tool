package com.cookiecraftmods.roadbuilder.networking;

import com.cookiecraftmods.roadbuilder.network.JunctionDefinition;
import com.cookiecraftmods.roadbuilder.network.JunctionSolveResult;
import com.cookiecraftmods.roadbuilder.network.JunctionSolver;
import com.cookiecraftmods.roadbuilder.network.PreparedSegmentReplacement;
import com.cookiecraftmods.roadbuilder.network.PreparedSegmentDirectionInversion;
import com.cookiecraftmods.roadbuilder.network.RoadGraph;
import com.cookiecraftmods.roadbuilder.network.RoadNode;
import com.cookiecraftmods.roadbuilder.network.RoadNodeId;
import com.cookiecraftmods.roadbuilder.network.RoadSegment;
import com.cookiecraftmods.roadbuilder.network.RoadSegmentId;
import com.cookiecraftmods.roadbuilder.placement.BlockPlacementPlan;
import com.cookiecraftmods.roadbuilder.placement.BlockPlacementPlans;
import com.cookiecraftmods.roadbuilder.placement.BlockPlanCommitter;
import com.cookiecraftmods.roadbuilder.placement.BlockPlanResult;
import com.cookiecraftmods.roadbuilder.placement.BlockTransitionStateMatcher;
import com.cookiecraftmods.roadbuilder.placement.ConstructionAuthorizer;
import com.cookiecraftmods.roadbuilder.placement.ConstructionLimits;
import com.cookiecraftmods.roadbuilder.placement.ConstructionValidationResult;
import com.cookiecraftmods.roadbuilder.placement.JunctionMarkingMaterializer;
import com.cookiecraftmods.roadbuilder.placement.LayeredBlockPlan;
import com.cookiecraftmods.roadbuilder.placement.MaterialLayer;
import com.cookiecraftmods.roadbuilder.placement.MaterializationPartition;
import com.cookiecraftmods.roadbuilder.placement.PlacementCommitResult;
import com.cookiecraftmods.roadbuilder.placement.PlacementOperation;
import com.cookiecraftmods.roadbuilder.placement.PlacementOperationId;
import com.cookiecraftmods.roadbuilder.placement.PlacementOwnershipLedger;
import com.cookiecraftmods.roadbuilder.placement.PlannedBlock;
import com.cookiecraftmods.roadbuilder.placement.PositionChangeStack;
import com.cookiecraftmods.roadbuilder.placement.RoadMaterializationOwner;
import com.cookiecraftmods.roadbuilder.placement.RoadMaterializationPlanner;
import com.cookiecraftmods.roadbuilder.placement.RoadMaterializationResolver;
import com.cookiecraftmods.roadbuilder.placement.ServerLevelPlacementBlockWorld;
import com.cookiecraftmods.roadbuilder.placement.StackRemoval;
import com.cookiecraftmods.roadbuilder.placement.StraightRoadIntersectionMaterializer;
import com.cookiecraftmods.roadbuilder.placement.SmoothRoadTransitionMaterializer;
import com.cookiecraftmods.roadbuilder.placement.RoadTransitionPlanPreparer;
import com.cookiecraftmods.roadbuilder.preset.RoadPreset;
import com.cookiecraftmods.roadbuilder.preset.RoadPresetLookup;
import com.cookiecraftmods.roadbuilder.preset.RoadSurfaceRole;
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

public final class SegmentReplacementEndpoint {
    private final RoadPresetLookup presets;
    private final ConstructionAuthorizer authorizer;
    private final ConstructionLimits limits;
    private final BlockPlanCommitter committer;

    public SegmentReplacementEndpoint(
            RoadPresetLookup presets,
            ConstructionAuthorizer authorizer,
            ConstructionLimits limits,
            BlockPlanCommitter committer
    ) {
        this.presets = Objects.requireNonNull(presets, "presets");
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.committer = Objects.requireNonNull(committer, "committer");
    }

    public PlacementResultS2CPacket handle(
            SegmentReplaceIntentC2SPacket packet,
            ServerPlayer player,
            PlacementOwnershipLedger ledger,
            RoadGraph graph
    ) {
        ConstructionValidationResult playerCheck = authorizer.validatePlayer(player);
        if (!playerCheck.isAllowed()) {
            return rejected(packet, PlacementResultCode.NOT_AUTHORIZED);
        }
        if (!player.serverLevel().dimension().location().equals(packet.dimensionId())
                || !graph.dimension().equals(player.serverLevel().dimension())
                || !ledger.dimension().equals(player.serverLevel().dimension())) {
            return rejected(packet, PlacementResultCode.DIMENSION_MISMATCH);
        }
        if (graph.revision() != packet.expectedGraphRevision()) {
            return rejected(packet, PlacementResultCode.GRAPH_REVISION_MISMATCH);
        }
        RoadPreset preset = presets.find(packet.presetId()).orElse(null);
        if (preset == null) {
            return rejected(packet, PlacementResultCode.UNKNOWN_PRESET);
        }
        if (preset.revision() != packet.expectedPresetRevision()) {
            return rejected(packet, PlacementResultCode.PRESET_REVISION_MISMATCH);
        }
        RoadSegment selected = graph.segment(new RoadSegmentId(packet.segmentId()))
                .orElse(null);
        if (selected == null) {
            return rejected(packet, PlacementResultCode.INVALID_GEOMETRY);
        }
        PlacementOperation oldSegment = new RoadMaterializationResolver(limits)
                .resolve(selected, graph.dimension(), ledger)
                .orElse(null);
        if (oldSegment == null) {
            return rejected(packet, PlacementResultCode.PERSISTENCE_INCOMPATIBLE);
        }

        PreparedSegmentReplacement replacement = graph.prepareSegmentReplacement(
                selected.id(), packet.presetId(), preset
        );
        ServerLevelPlacementBlockWorld world =
                new ServerLevelPlacementBlockWorld(player.serverLevel());
        BlockPlanResult roadResult = new RoadMaterializationPlanner().plan(
                graph.dimension(),
                List.of(replacement.replacement()),
                world,
                ledger,
                limits
        );
        if (!(roadResult instanceof BlockPlanResult.Success road)) {
            return rejected(packet, PlacementResultCode.CONSTRUCTION_LIMIT_EXCEEDED);
        }
        Map<RoadNodeId, BlockPlacementPlan> junctions;
        try {
            junctions = endpointJunctionPlans(
                    graph,
                    replacement.original(),
                    replacement.replacement(),
                    graph.snapshot().junctionDefinitions(),
                    world,
                    ledger
            );
        } catch (RuntimeException failure) {
            return rejected(packet, PlacementResultCode.MUTATION_FAILED);
        }

        List<PlacementOperationId> replacedIds = new ArrayList<>();
        replacedIds.add(oldSegment.id());
        for (RoadNodeId nodeId : List.of(selected.startNodeId(), selected.endNodeId())) {
            ledger.operation(RoadMaterializationOwner.node(nodeId))
                    .map(PlacementOperation::id)
                    .filter(id -> !replacedIds.contains(id))
                    .ifPresent(replacedIds::add);
        }
        List<BlockPlacementPlan> newPlans = new ArrayList<>();
        newPlans.add(road.plan());
        newPlans.addAll(junctions.values());
        BlockPlacementPlan overlay = finalStateOverlay(
                world, ledger, replacedIds, newPlans
        );
        ConstructionValidationResult footprint = authorizer
                .validateExistingFootprint(player, overlay, limits);
        if (!footprint.isAllowed()) {
            return rejected(packet, PlacementResultCode.NOT_AUTHORIZED);
        }
        PlacementOperationId overlayId = committer.nextOperationId();
        PlacementCommitResult commit = committer.commitRestorationOverlay(
                overlayId, overlay, world, ledger
        );
        if (!(commit instanceof PlacementCommitResult.Success)) {
            return rejected(packet, PlacementResultCode.WORLD_CONFLICT);
        }

        List<MaterializationPartition> partitions = new ArrayList<>();
        partitions.add(new MaterializationPartition(
                overlayId,
                RoadMaterializationOwner.segment(selected.id()),
                road.plan().positions()
        ));
        junctions.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> partitions.add(new MaterializationPartition(
                        PlacementOperationId.create(),
                        RoadMaterializationOwner.node(entry.getKey()),
                        entry.getValue().positions()
                )));
        try {
            ledger.publishMaterializationRebuild(replacedIds, overlayId, partitions);
            graph.applySegmentReplacement(replacement);
        } catch (RuntimeException failure) {
            return rejected(packet, PlacementResultCode.MUTATION_FAILED);
        }
        return PlacementResultS2CPacket.accepted(
                packet.requestId(), overlayId.value(), overlay.affectedBlockCount()
        );
    }

    public PlacementResultS2CPacket handleDirectionInversion(
            SegmentDirectionInvertIntentC2SPacket packet,
            ServerPlayer player,
            PlacementOwnershipLedger ledger,
            RoadGraph graph
    ) {
        ConstructionValidationResult playerCheck =
                authorizer.validatePlayer(player);
        if (!playerCheck.isAllowed()) {
            return rejected(packet, PlacementResultCode.NOT_AUTHORIZED);
        }
        if (!player.serverLevel().dimension().location()
                        .equals(packet.dimensionId())
                || !graph.dimension().equals(player.serverLevel().dimension())
                || !ledger.dimension().equals(
                        player.serverLevel().dimension()
                )) {
            return rejected(packet, PlacementResultCode.DIMENSION_MISMATCH);
        }
        if (graph.revision() != packet.expectedGraphRevision()) {
            return rejected(
                    packet,
                    PlacementResultCode.GRAPH_REVISION_MISMATCH
            );
        }
        RoadSegment selected = graph.segment(
                new RoadSegmentId(packet.segmentId())
        ).orElse(null);
        if (selected == null) {
            return rejected(packet, PlacementResultCode.INVALID_GEOMETRY);
        }
        PlacementOperation oldSegment = new RoadMaterializationResolver(limits)
                .resolve(selected, graph.dimension(), ledger)
                .orElse(null);
        if (oldSegment == null) {
            return rejected(
                    packet,
                    PlacementResultCode.PERSISTENCE_INCOMPATIBLE
            );
        }

        PreparedSegmentDirectionInversion inversion = graph
                .prepareSegmentDirectionInversion(selected.id());
        ServerLevelPlacementBlockWorld world =
                new ServerLevelPlacementBlockWorld(player.serverLevel());
        BlockPlanResult roadResult = new RoadMaterializationPlanner().plan(
                graph.dimension(),
                List.of(inversion.replacement()),
                world,
                ledger,
                limits
        );
        if (!(roadResult instanceof BlockPlanResult.Success road)) {
            return rejected(
                    packet,
                    PlacementResultCode.CONSTRUCTION_LIMIT_EXCEEDED
            );
        }
        Map<RoadNodeId, BlockPlacementPlan> junctions;
        try {
            junctions = endpointJunctionPlans(
                    graph,
                    inversion.original(),
                    inversion.replacement(),
                    inversion.junctionDefinitions(),
                    world,
                    ledger
            );
        } catch (RuntimeException failure) {
            return rejected(packet, PlacementResultCode.MUTATION_FAILED);
        }

        List<PlacementOperationId> replacedIds = new ArrayList<>();
        replacedIds.add(oldSegment.id());
        for (RoadNodeId nodeId : List.of(
                selected.startNodeId(),
                selected.endNodeId()
        )) {
            ledger.operation(RoadMaterializationOwner.node(nodeId))
                    .map(PlacementOperation::id)
                    .filter(id -> !replacedIds.contains(id))
                    .ifPresent(replacedIds::add);
        }
        List<BlockPlacementPlan> newPlans = new ArrayList<>();
        newPlans.add(road.plan());
        newPlans.addAll(junctions.values());
        BlockPlacementPlan overlay = finalStateOverlay(
                world,
                ledger,
                replacedIds,
                newPlans
        );
        ConstructionValidationResult footprint = authorizer
                .validateExistingFootprint(player, overlay, limits);
        if (!footprint.isAllowed()) {
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

        List<MaterializationPartition> partitions = new ArrayList<>();
        partitions.add(new MaterializationPartition(
                overlayId,
                RoadMaterializationOwner.segment(selected.id()),
                road.plan().positions()
        ));
        junctions.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> partitions.add(
                        new MaterializationPartition(
                                PlacementOperationId.create(),
                                RoadMaterializationOwner.node(entry.getKey()),
                                entry.getValue().positions()
                        )
                ));
        try {
            ledger.publishMaterializationRebuild(
                    replacedIds,
                    overlayId,
                    partitions
            );
            graph.applySegmentDirectionInversion(inversion);
        } catch (RuntimeException failure) {
            return rejected(packet, PlacementResultCode.MUTATION_FAILED);
        }
        return PlacementResultS2CPacket.accepted(
                packet.requestId(),
                overlayId.value(),
                overlay.affectedBlockCount()
        );
    }

    private Map<RoadNodeId, BlockPlacementPlan> endpointJunctionPlans(
            RoadGraph graph,
            RoadSegment original,
            RoadSegment replacement,
            Map<RoadNodeId, JunctionDefinition> definitions,
            ServerLevelPlacementBlockWorld world,
            PlacementOwnershipLedger ledger
    ) {
        LinkedHashMap<RoadNodeId, BlockPlacementPlan> result = new LinkedHashMap<>();
        for (RoadNodeId nodeId : List.of(
                replacement.startNodeId(),
                replacement.endNodeId())) {
            List<RoadSegment> incident = graph.snapshot().segments().values().stream()
                    .map(segment -> segment.id().equals(original.id())
                            ? replacement : segment)
                    .filter(segment -> segment.startNodeId().equals(nodeId)
                            || segment.endNodeId().equals(nodeId))
                    .toList();
            RoadNode node = graph.node(nodeId).orElseThrow();
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
                    throw new IllegalStateException(
                            "replacement transition could not be materialized"
                    );
                }
                result.put(nodeId, success.plan());
                continue;
            }
            if (!isJunction(nodeId, incident)) {
                continue;
            }
            JunctionDefinition definition = definitions.getOrDefault(
                    nodeId,
                    JunctionDefinition.automatic(nodeId)
            );
            JunctionSolveResult solved = new JunctionSolver().solve(
                    node, incident, definition
            );
            if (!(solved instanceof JunctionSolveResult.Success success)) {
                throw new IllegalStateException("replacement junction could not be solved");
            }
            RoadSegment primary = success.approaches().stream()
                    .filter(approach -> approach.primary())
                    .map(approach -> approach.segment())
                    .findFirst().orElse(incident.get(0));
            var style = primary.presetSnapshot().intersectionStyle();
            var sidewalk = primary.presetSnapshot().surfaceBands().stream()
                    .filter(band -> band.role() == RoadSurfaceRole.SIDEWALK)
                    .findFirst().map(band -> band.block())
                    .orElse(style.pavementBlock());
            BlockPlanResult surface = new StraightRoadIntersectionMaterializer().plan(
                    graph.dimension(), node, incident,
                    style.pavementBlock(), sidewalk,
                    limits.maximumAffectedBlocks(),
                    limits.maximumPlanningCandidates(), definition
            );
            if (!(surface instanceof BlockPlanResult.Success surfacePlan)) {
                throw new IllegalStateException("replacement junction surface failed");
            }
            BlockPlacementPlan combined = surfacePlan.plan();
            BlockPlanResult markings = new JunctionMarkingMaterializer().plan(
                    graph.dimension(), node, incident, definition,
                    limits.maximumAffectedBlocks()
            );
            if (markings instanceof BlockPlanResult.Success markingPlan) {
                combined = BlockPlacementPlans.mergeLayered(
                        graph.dimension(),
                        List.of(
                                new LayeredBlockPlan(combined,
                                        MaterialLayer.JUNCTION_SURFACE,
                                        "node", nodeId.toString()),
                                new LayeredBlockPlan(markingPlan.plan(),
                                        MaterialLayer.JUNCTION_MARKING,
                                        "node-marking", nodeId.toString())
                        )
                );
            }
            result.put(nodeId, combined);
        }
        return Map.copyOf(result);
    }

    private static boolean isJunction(RoadNodeId nodeId, List<RoadSegment> incident) {
        if (incident.size() >= 3) return true;
        if (incident.size() != 2) return false;
        var first = outwardTangent(incident.get(0), nodeId);
        var second = outwardTangent(incident.get(1), nodeId);
        double lengths = first.horizontalLength() * second.horizontalLength();
        if (lengths == 0.0) return true;
        double dot = (first.x() * second.x() + first.z() * second.z()) / lengths;
        return dot > -Math.cos(Math.toRadians(5.0));
    }

    private static com.cookiecraftmods.roadbuilder.geometry.RoadVector outwardTangent(
            RoadSegment segment, RoadNodeId nodeId) {
        boolean start = segment.startNodeId().equals(nodeId);
        var tangent = segment.referenceGeometry().sample(
                start ? 0.0 : segment.referenceGeometry().length()
        ).tangent();
        return start ? tangent : tangent.scale(-1.0);
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
            BlockState expected = ledger.expectedVisibleState(position).orElse(current);
            targets.put(position, BlockTransitionStateMatcher.matchesPlannedTarget(
                    current, expected)
                    ? stateAfterRemoving(ledger, position, removedIds, current)
                    : current);
        }
        for (BlockPlacementPlan plan : newPlans) {
            for (PlannedBlock block : plan.blocks()) {
                targets.put(block.position(), block.targetState());
            }
        }
        return BlockPlacementPlan.create(
                world.dimension(),
                targets.entrySet().stream()
                        .map(entry -> new PlannedBlock(entry.getKey(), entry.getValue()))
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
        if (stack == null) return fallback;
        BlockState result = stack.top().afterState();
        for (PlacementOperationId operationId : operationIds) {
            if (stack.layer(operationId).isEmpty()) continue;
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
            SegmentReplaceIntentC2SPacket packet,
            PlacementResultCode code
    ) {
        return PlacementResultS2CPacket.rejected(packet.requestId(), code);
    }

    private static PlacementResultS2CPacket rejected(
            SegmentDirectionInvertIntentC2SPacket packet,
            PlacementResultCode code
    ) {
        return PlacementResultS2CPacket.rejected(packet.requestId(), code);
    }
}
