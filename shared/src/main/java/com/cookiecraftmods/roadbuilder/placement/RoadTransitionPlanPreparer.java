package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.network.RoadNode;
import com.cookiecraftmods.roadbuilder.network.RoadSegment;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

public final class RoadTransitionPlanPreparer {
    public BlockPlanResult plan(
            ResourceKey<Level> dimension,
            RoadNode node,
            List<RoadSegment> incident,
            PlacementBlockWorld world,
            Predicate<BlockPos> protectedPosition,
            ConstructionLimits limits
    ) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(protectedPosition, "protectedPosition");
        Objects.requireNonNull(limits, "limits");
        BlockPlanResult surface = new SmoothRoadTransitionMaterializer().plan(
                dimension,
                node,
                incident,
                limits.maximumAffectedBlocks(),
                limits.maximumPlanningCandidates()
        );
        if (!(surface instanceof BlockPlanResult.Success transition)) {
            return surface;
        }
        return complete(transition.plan(), node, incident, world, protectedPosition, limits);
    }

    public BlockPlanResult complete(
            BlockPlacementPlan surfacePlan,
            RoadNode node,
            List<RoadSegment> incident,
            PlacementBlockWorld world,
            Predicate<BlockPos> protectedPosition,
            ConstructionLimits limits
    ) {
        var dimension = surfacePlan.dimension();
        List<RoadStructurePlanner.StructureSpan> structureSpans = incident
                .stream()
                .map(segment -> new RoadStructurePlanner.StructureSpan(
                        segment.referenceGeometry(),
                        segment.presetSnapshot().surfaceBlockWidth(),
                        segment.presetSnapshot().surfaceBands(),
                        segment.presetSnapshot().sunkenRoad()
                ))
                .toList();
        RoadStructurePlanner structurePlanner = new RoadStructurePlanner();
        surfacePlan = structurePlanner.prepareStructureSurface(
                surfacePlan, structureSpans, world, protectedPosition);
        BlockPlanResult structures = structurePlanner.addStructures(
                surfacePlan,
                world,
                structureSpans,
                protectedPosition,
                limits.maximumAffectedBlocks()
        );
        if (!(structures instanceof BlockPlanResult.Success structured)) {
            return structures;
        }
        Set<BlockPos> structurePositions = BlockPositionSets.copyOf(
                structured.plan().positions()
        );
        BlockPlanResult clearance = new RoadClearancePlanner().addClearance(
                surfacePlan,
                world,
                position -> protectedPosition.test(position)
                        || structurePositions.contains(position),
                limits.maximumAffectedBlocks()
        );
        if (!(clearance instanceof BlockPlanResult.Success cleared)) {
            return clearance;
        }
        return new BlockPlanResult.Success(BlockPlacementPlans.mergeLayered(
                dimension,
                List.of(
                        new LayeredBlockPlan(
                                structured.plan(),
                                MaterialLayer.ROAD_STRUCTURE,
                                "transition-structure",
                                node.id().toString()
                        ),
                        new LayeredBlockPlan(
                                cleared.plan(),
                                MaterialLayer.JUNCTION_SURFACE,
                                "transition",
                                node.id().toString()
                        )
                )
        ));
    }
}
