package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.network.RoadPresetSnapshot;
import com.cookiecraftmods.roadbuilder.network.RoadSegment;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Objects;

public final class StraightRoadSegmentMaterializer {
    private final StraightRoadPlanner planner;
    private final RoadPathPlanner pathPlanner;

    public StraightRoadSegmentMaterializer() {
        this(new StraightRoadPlanner(), new RoadPathPlanner());
    }

    public StraightRoadSegmentMaterializer(StraightRoadPlanner planner) {
        this(planner, new RoadPathPlanner());
    }

    public StraightRoadSegmentMaterializer(
            StraightRoadPlanner planner,
            RoadPathPlanner pathPlanner
    ) {
        this.planner = Objects.requireNonNull(planner, "planner");
        this.pathPlanner = Objects.requireNonNull(pathPlanner, "pathPlanner");
    }

    public BlockPlanResult plan(
            ResourceKey<Level> dimension,
            RoadSegment segment,
            ConstructionLimits limits
    ) {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(limits, "limits");

        RoadPresetSnapshot snapshot = segment.presetSnapshot();
        RoadSurfaceResolver.Result resolvedSurface =
                RoadSurfaceResolver.resolve(snapshot.surfaceColumns());
        if (resolvedSurface instanceof RoadSurfaceResolver.Result.Failure failure) {
            return new BlockPlanResult.Failure(failure.code());
        }
        ResolvedRoadSurface surface = ((RoadSurfaceResolver.Result.Success)
                resolvedSurface).surface();
        if (segment.referenceGeometry()
                instanceof com.cookiecraftmods.roadbuilder.geometry.StraightGeometry straight) {
            return planner.plan(
                    new StraightRoadPlanRequest(
                        dimension,
                        straight,
                        surface
                    ),
                    limits.maximumAffectedBlocks(),
                    limits.maximumPlanningCandidates(),
                    ConstructionLimits.UNRESTRICTED_MAXIMUM_GRADE
            );
        }
        return pathPlanner.plan(
                new RoadPathPlanRequest(
                        dimension,
                        segment.referenceGeometry(),
                        surface
                ),
                    limits.maximumAffectedBlocks(),
                    limits.maximumPlanningCandidates(),
                    ConstructionLimits.UNRESTRICTED_MAXIMUM_GRADE
            );
    }
}
