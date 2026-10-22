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
        Objects.requireNonNull(dimension, "dimension");
        segments = List.copyOf(Objects.requireNonNull(segments, "segments"));
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(ledger, "ledger");
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
                        surface.segment().presetSnapshot().surfaceBands()
                ))
                .toList();
        mergedSurface = structurePlanner.prepareRailingSurface(
                mergedSurface, structureSpans, world, ledger::isMaterializedRoadPosition);
        BlockPlanResult structures = structurePlanner.addStructures(
                mergedSurface,
                world,
                structureSpans,
                ledger::isMaterializedRoadPosition,
                limits.maximumAffectedBlocks()
        );
        if (!(structures instanceof BlockPlanResult.Success structured)) {
            return structures;
        }
        Set<BlockPos> structurePositions = BlockPositionSets.copyOf(
                structured.plan().positions()
        );
        materialLayers.add(new LayeredBlockPlan(
                structured.plan(),
                MaterialLayer.ROAD_STRUCTURE,
                "road-structure",
                segments.get(0).roadId().toString()
        ));
        RoadTunnelPlanner.Result tunnels = tunnelPlanner.addTunnels(
                mergedSurface,
                segments,
                world,
                ledger::isMaterializedRoadPosition,
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
                position -> ledger.isMaterializedRoadPosition(position)
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
