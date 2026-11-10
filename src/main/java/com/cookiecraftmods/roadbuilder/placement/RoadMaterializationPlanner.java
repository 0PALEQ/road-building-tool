package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.network.RoadSegment;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class RoadMaterializationPlanner {
    private final StraightRoadSegmentMaterializer segmentMaterializer;
    private final RoadStructurePlanner structurePlanner;
    private final RoadClearancePlanner clearancePlanner;
    private final RoadTunnelPlanner tunnelPlanner;

    public BlockPlanResult plan(
            ResourceKey<Level> dimension,
            com.cookiecraftmods.roadbuilder.network.PreparedRoadNetworkMutation mutation,
            PlacementBlockWorld world,
            PlacementOwnershipLedger ledger,
            ConstructionLimits limits
    ) {
        var result = planRoads(dimension, mutation, world, ledger, limits);
        if (!(result instanceof BlockPlanResult.Success success)) return result;
        return restoreReplacedFootprint(success.plan(), mutation, world, ledger, limits);
    }

    public BlockPlanResult planRoads( //the terrain musn't protect junction excavation cos server rejects the placement
            ResourceKey<Level> dimension,
            com.cookiecraftmods.roadbuilder.network.PreparedRoadNetworkMutation mutation,
            PlacementBlockWorld world, PlacementOwnershipLedger ledger, ConstructionLimits limits) {
        var replaced = replacedOperations(mutation,dimension,ledger,limits);
        if (replaced.isEmpty()) return plan(dimension, mutation.placedSegments(), world, ledger, limits);
        var rebuilding = new MaterializationRebuildWorld(world, ledger, replaced);
        return plan(dimension, mutation.materializedSegments(), rebuilding,
                rebuilding::isMaterializedRoadPosition, limits);
    }

    public BlockPlanResult restoreReplacedFootprint(BlockPlacementPlan plan,
            com.cookiecraftmods.roadbuilder.network.PreparedRoadNetworkMutation mutation,
            PlacementBlockWorld world, PlacementOwnershipLedger ledger, ConstructionLimits limits) {
        if (!mutation.rebuildsExistingGeometry()) return new BlockPlanResult.Success(plan);
        var dimension = plan.dimension();
        var replaced = replacedOperations(mutation, dimension, ledger, limits);
        var rebuilding = new MaterializationRebuildWorld(world, ledger, replaced);
        java.util.Map<BlockPos,PlannedBlock> targets = new java.util.LinkedHashMap<>();
        for (var id : replaced) ledger.operation(id).ifPresent(operation -> operation.positions().forEach(position ->
                targets.put(position,new PlannedBlock(position,rebuilding.blockState(position)))));
        plan.blocks().forEach(block -> targets.put(block.position(),block));
        if (targets.size()>limits.maximumAffectedBlocks()) return new BlockPlanResult.Failure(BlockPlanFailureCode.BLOCK_LIMIT_EXCEEDED);
        return new BlockPlanResult.Success(BlockPlacementPlan.create(dimension,targets.values()));
    }

    public static List<PlacementOperationId> replacedOperations(
            com.cookiecraftmods.roadbuilder.network.PreparedRoadNetworkMutation mutation,
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
        return plan(dimension, segments, world, ledger::isMaterializedRoadPosition, limits);
    }

    public BlockPlanResult plan(
            ResourceKey<Level> dimension,
            List<RoadSegment> segments,
            PlacementBlockWorld world,
            java.util.function.Predicate<BlockPos> existingRoadPosition,
            ConstructionLimits limits
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
                return result;
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
            return structures;
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
            return new BlockPlanResult.Failure(
                    BlockPlanFailureCode.BLOCK_LIMIT_EXCEEDED
            );
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
            return clearance;
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
            return new BlockPlanResult.Failure(
                    BlockPlanFailureCode.BLOCK_LIMIT_EXCEEDED
            );
        }
        return new BlockPlanResult.Success(plan);
    }

    private record SegmentSurface(
            RoadSegment segment,
            BlockPlacementPlan plan
    ) {
    }
}
