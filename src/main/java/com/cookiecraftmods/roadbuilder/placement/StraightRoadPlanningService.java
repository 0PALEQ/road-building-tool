package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.geometry.StraightGeometry;
import com.cookiecraftmods.roadbuilder.preset.RoadPreset;
import com.cookiecraftmods.roadbuilder.preset.RoadPresetLookup;
import java.util.Objects;
import java.util.Optional;

public final class StraightRoadPlanningService {
    private final RoadPresetLookup presetLookup;
    private final StraightRoadPlanner planner;

    public StraightRoadPlanningService(RoadPresetLookup presetLookup, StraightRoadPlanner planner) {
        this.presetLookup = Objects.requireNonNull(presetLookup, "presetLookup");
        this.planner = Objects.requireNonNull(planner, "planner");
    }

    public BlockPlanResult plan(StraightRoadIntent intent, ConstructionLimits limits) {
        StraightRoadPlanningResult resolved = resolve(intent, limits);
        if (resolved instanceof StraightRoadPlanningResult.Failure failure) {
            return failure(failure.code());
        }
        ResolvedStraightRoadPlan roadPlan = ((StraightRoadPlanningResult.Success) resolved).roadPlan();
        return new BlockPlanResult.Success(roadPlan.blockPlan());
    }

    public StraightRoadPlanningResult resolve(
            StraightRoadIntent intent,
            ConstructionLimits limits
    ) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(limits, "limits");

        Optional<RoadPreset> resolvedPreset = presetLookup.find(intent.presetId());
        if (resolvedPreset.isEmpty()) {
            return resolvedFailure(BlockPlanFailureCode.UNKNOWN_PRESET);
        }

        RoadPreset preset = resolvedPreset.get();
        if (preset.revision() != intent.expectedPresetRevision()) {
            return resolvedFailure(BlockPlanFailureCode.PRESET_REVISION_MISMATCH);
        }

        StraightGeometry geometry;
        try {
            geometry = new StraightGeometry(intent.start(), intent.end());
        } catch (IllegalArgumentException exception) {
            return resolvedFailure(BlockPlanFailureCode.INVALID_GEOMETRY);
        }

        RoadSurfaceResolver.Result resolvedSurface =
                RoadSurfaceResolver.resolve(preset.surfaceColumns());
        if (resolvedSurface instanceof RoadSurfaceResolver.Result.Failure failure) {
            return resolvedFailure(failure.code());
        }
        ResolvedRoadSurface surface = ((RoadSurfaceResolver.Result.Success)
                resolvedSurface).surface();

        BlockPlanResult planned = planner.plan(
                new StraightRoadPlanRequest(
                        intent.dimension(),
                        geometry,
                        surface
                ),
                limits.maximumAffectedBlocks(),
                limits.maximumPlanningCandidates(),
                ConstructionLimits.UNRESTRICTED_MAXIMUM_GRADE
        );
        if (planned instanceof BlockPlanResult.Failure failure) {
            return resolvedFailure(failure.code());
        }
        BlockPlacementPlan blockPlan = ((BlockPlanResult.Success) planned).plan();
        return new StraightRoadPlanningResult.Success(new ResolvedStraightRoadPlan(
                blockPlan,
                geometry,
                intent.presetId(),
                preset
        ));
    }

    private static BlockPlanResult.Failure failure(BlockPlanFailureCode code) {
        return new BlockPlanResult.Failure(code);
    }

    private static StraightRoadPlanningResult.Failure resolvedFailure(
            BlockPlanFailureCode code
    ) {
        return new StraightRoadPlanningResult.Failure(code);
    }
}
