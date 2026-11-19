package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.network.PreparedRoadNetworkMutation;
import com.cookiecraftmods.roadbuilder.network.RoadSegment;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

public final class RoadMaterializationPlanner {
    private final StraightRoadSegmentMaterializer segmentMaterializer;
    private final RoadStructurePlanner structurePlanner;
    private final RoadClearancePlanner clearancePlanner;
    private final RoadTunnelPlanner tunnelPlanner;

    public BlockPlanResult plan(
            ResourceKey<Level> dimension,
            PreparedRoadNetworkMutation mutation,
            PlacementBlockWorld world,
            PlacementOwnershipLedger ledger,
            ConstructionLimits limits
    ) {
        var result = planRoads(dimension, mutation, world, ledger, limits);
        if (!(result instanceof BlockPlanResult.Success success)) return result;
        return restoreReplacedFootprint(success.plan(), mutation, world, ledger, limits);
    }

    public BlockPlanResult planRoads(
            ResourceKey<Level> dimension,
            PreparedRoadNetworkMutation mutation,
            PlacementBlockWorld world, PlacementOwnershipLedger ledger, ConstructionLimits limits) {
        return planRoads(dimension, mutation, world, ledger, limits, false).result();
    }

    public PlannedRoads planRoadsForJunctions(
            ResourceKey<Level> dimension,
            PreparedRoadNetworkMutation mutation,
            PlacementBlockWorld world, PlacementOwnershipLedger ledger, ConstructionLimits limits) {
        return planRoads(dimension, mutation, world, ledger, limits, true);
    }

    private PlannedRoads planRoads(
            ResourceKey<Level> dimension,
            PreparedRoadNetworkMutation mutation,
            PlacementBlockWorld world, PlacementOwnershipLedger ledger, ConstructionLimits limits,
            boolean protectJunctionApproaches) {
        var replaced = replacedOperations(mutation, dimension, ledger, limits);
        if (replaced.isEmpty()) {
            return plan(dimension, mutation.placedSegments(), world,
                    position -> ledger.isMaterializedRoadPosition(position, world), limits, protectJunctionApproaches);
        }
        var rebuilding = new MaterializationRebuildWorld(world, ledger, replaced);
        return plan(dimension, mutation.materializedSegments(), rebuilding,
                rebuilding::isMaterializedRoadPosition, limits, protectJunctionApproaches);
    }

    public BlockPlanResult restoreReplacedFootprint(BlockPlacementPlan plan,
            PreparedRoadNetworkMutation mutation,
            PlacementBlockWorld world, PlacementOwnershipLedger ledger, ConstructionLimits limits) {
        if (!mutation.rebuildsExistingGeometry()) return new BlockPlanResult.Success(plan);
        var dimension = plan.dimension();
        var replaced = replacedOperations(mutation, dimension, ledger, limits);
        var rebuilding = new MaterializationRebuildWorld(world, ledger, replaced);
        java.util.Map<BlockPos,PlannedBlock> targets = new java.util.LinkedHashMap<>();
        plan.blocks().forEach(block -> targets.put(block.position(), block));
        for (var id : replaced) ledger.operation(id).ifPresent(operation -> operation.positions().forEach(position ->
                targets.computeIfAbsent(position, key -> new PlannedBlock(key, rebuilding.blockState(key)))));
        if (targets.size()>limits.maximumAffectedBlocks()) return new BlockPlanResult.Failure(BlockPlanFailureCode.BLOCK_LIMIT_EXCEEDED);
        return new BlockPlanResult.Success(BlockPlacementPlan.create(dimension,targets.values()));
    }

    public static List<PlacementOperationId> replacedOperations(
            PreparedRoadNetworkMutation mutation,
            ResourceKey<Level> dimension, PlacementOwnershipLedger ledger, ConstructionLimits limits) {
        var resolver = new RoadMaterializationResolver(limits);
        java.util.Set<PlacementOperationId> ids = new java.util.LinkedHashSet<>();
        mutation.segmentSplits().forEach(split -> resolver.resolve(split.original(),dimension,ledger)
                .map(PlacementOperation::id).ifPresent(ids::add));
        if (mutation.rebuildsExistingGeometry()) mutation.allUpsertedSegments().forEach(segment -> {
            ledger.operation(RoadMaterializationOwner.node(segment.startNodeId())).map(PlacementOperation::id).ifPresent(ids::add);
            ledger.operation(RoadMaterializationOwner.node(segment.endNodeId())).map(PlacementOperation::id).ifPresent(ids::add);
        });
        return List.copyOf(ids);
    }

    public RoadMaterializationPlanner() {
        this(
                new StraightRoadSegmentMaterializer(),
                new RoadStructurePlanner(),
                new RoadClearancePlanner(),
                new RoadTunnelPlanner()
        );
    }

    public RoadMaterializationPlanner(
            StraightRoadSegmentMaterializer segmentMaterializer,
            RoadStructurePlanner structurePlanner,
            RoadClearancePlanner clearancePlanner,
            RoadTunnelPlanner tunnelPlanner
    ) {
        this.segmentMaterializer = Objects.requireNonNull(
                segmentMaterializer,
                "segmentMaterializer"
        );
        this.structurePlanner = Objects.requireNonNull(
                structurePlanner,
                "structurePlanner"
        );
        this.clearancePlanner = Objects.requireNonNull(
                clearancePlanner,
                "clearancePlanner"
        );
        this.tunnelPlanner = Objects.requireNonNull(tunnelPlanner, "tunnelPlanner");
    }

    public RoadMaterializationPlanner(
            StraightRoadSegmentMaterializer segmentMaterializer,
            RoadStructurePlanner structurePlanner,
            RoadClearancePlanner clearancePlanner
    ) {
        this(
                segmentMaterializer,
                structurePlanner,
                clearancePlanner,
                new RoadTunnelPlanner()
        );
    }

    public BlockPlanResult plan(
            ResourceKey<Level> dimension,
            List<RoadSegment> segments,
            PlacementBlockWorld world,
            PlacementOwnershipLedger ledger,
            ConstructionLimits limits
    ) {
        return plan(dimension, segments, world, position -> ledger.isMaterializedRoadPosition(position, world), limits);
    }

    public BlockPlanResult plan(
            ResourceKey<Level> dimension,
            List<RoadSegment> segments,
            PlacementBlockWorld world,
            Predicate<BlockPos> existingRoadPosition,
            ConstructionLimits limits
    ) {
        return plan(dimension, segments, world, existingRoadPosition, limits, false).result();
    }

    private PlannedRoads plan(
            ResourceKey<Level> dimension,
            List<RoadSegment> segments,
            PlacementBlockWorld world,
            Predicate<BlockPos> existingRoadPosition,
            ConstructionLimits limits,
            boolean protectJunctionApproaches
    ) {
        Objects.requireNonNull(dimension, "dimension");
        segments = List.copyOf(Objects.requireNonNull(segments, "segments"));
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(existingRoadPosition, "existingRoadPosition");
        Objects.requireNonNull(limits, "limits");
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("segments must not be empty");
        }

        List<SegmentSurface> surfaces = new ArrayList<>(segments.size());
        List<LayeredBlockPlan> surfaceLayers = new ArrayList<>(segments.size());
        for (RoadSegment segment : segments) {
            BlockPlanResult result = segmentMaterializer.plan(
                    dimension,
                    segment,
                    limits
            );
            if (!(result instanceof BlockPlanResult.Success success)) {
                return new PlannedRoads(result, Set.of());
            }
            surfaces.add(new SegmentSurface(segment, success.plan()));
            surfaceLayers.add(new LayeredBlockPlan(
                    success.plan(),
                    MaterialLayer.SEGMENT_SURFACE,
                    "segment",
                    segment.id().toString()
            ));
        }
        BlockPlacementPlan mergedSurface = BlockPlacementPlans.mergeLayered(
                dimension,
                surfaceLayers
        );

        List<LayeredBlockPlan> materialLayers = new ArrayList<>();
        List<RoadStructurePlanner.StructureSpan> structureSpans = surfaces
                .stream()
                .map(surface -> new RoadStructurePlanner.StructureSpan(
                        surface.segment().referenceGeometry(),
                        surface.segment().presetSnapshot().surfaceBlockWidth(),
                        surface.segment().presetSnapshot().surfaceBands(),
                        surface.segment().presetSnapshot().sunkenRoad()
                ))
                .toList();
        mergedSurface = structurePlanner.prepareStructureSurface(
                mergedSurface, structureSpans, world, existingRoadPosition);
        BlockPlanResult structures = structurePlanner.addStructures(
                mergedSurface,
                world,
                structureSpans,
                existingRoadPosition,
                limits.maximumAffectedBlocks()
        );
        if (!(structures instanceof BlockPlanResult.Success structured)) {
            return new PlannedRoads(structures, Set.of());
        }
        BlockPlacementPlan structurePlan = structured.plan();
        Set<BlockPos> structurePositions = BlockPositionSets.copyOf(structurePlan.positions());
        materialLayers.add(new LayeredBlockPlan(
                structurePlan,
                MaterialLayer.ROAD_STRUCTURE,
                "road-structure",
                segments.get(0).roadId().toString()
        ));
        RoadTunnelPlanner.Result tunnels = tunnelPlanner.addTunnels(
                mergedSurface,
                segments,
                world,
                existingRoadPosition,
                limits.maximumAffectedBlocks()
        );
        if (tunnels.plan().isEmpty()) {
            return new PlannedRoads(new BlockPlanResult.Failure(BlockPlanFailureCode.BLOCK_LIMIT_EXCEEDED), Set.of());
        }
        BlockPlanResult clearance = clearancePlanner.addClearance(
                mergedSurface,
                world,
                position -> existingRoadPosition.test(position)
                        || tunnels.protectedRoofPositions().contains(position)
                        || structurePositions.contains(position),
                limits.maximumAffectedBlocks()
        );
        if (!(clearance instanceof BlockPlanResult.Success cleared)) {
            return new PlannedRoads(clearance, Set.of());
        }
        materialLayers.add(new LayeredBlockPlan(
                cleared.plan(),
                MaterialLayer.SEGMENT_SURFACE,
                "road-surface",
                segments.get(0).roadId().toString()
        ));
        materialLayers.add(new LayeredBlockPlan(
                tunnels.plan().orElseThrow(),
                MaterialLayer.TUNNEL,
                "road-tunnel",
                segments.get(0).roadId().toString()
        ));
        BlockPlacementPlan plan = BlockPlacementPlans.mergeLayered(
                dimension,
                materialLayers
        );
        if (plan.affectedBlockCount() > limits.maximumAffectedBlocks()) {
            return new PlannedRoads(new BlockPlanResult.Failure(BlockPlanFailureCode.BLOCK_LIMIT_EXCEEDED), Set.of());
        }
        Set<BlockPos> protectedPositions = Set.of();
        if (protectJunctionApproaches) {
            Set<BlockPos> surfacePositions = BlockPositionSets.nonAirTargets(mergedSurface);
            protectedPositions = BlockPositionSets.nonAirTargets(plan, block ->
                    surfacePositions.contains(block.position()) || !structurePlanner.isLandscaping(block.targetState()));
        }
        return new PlannedRoads(new BlockPlanResult.Success(plan), protectedPositions);
    }

    public record PlannedRoads(BlockPlanResult result, Set<BlockPos> protectedPositions) {}

    private record SegmentSurface(
            RoadSegment segment,
            BlockPlacementPlan plan
    ) {
    }
}
