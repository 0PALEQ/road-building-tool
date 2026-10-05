package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.preset.RoadPreset;
import com.cookiecraftmods.roadbuilder.preset.RoadPresetLookup;
import java.util.Objects;
import java.util.Optional;

public final class RoadPathPlanningService {
    private final RoadPresetLookup presetLookup;
    private final RoadPathPlanner planner;

    public RoadPathPlanningService(
            RoadPresetLookup presetLookup,
            RoadPathPlanner planner
    ) {
        this.presetLookup = Objects.requireNonNull(
                presetLookup,
                "presetLookup"
        );
        this.planner = Objects.requireNonNull(planner, "planner");
    }

    public RoadPathPlanningResult resolve(
            RoadPathIntent intent,
            ConstructionLimits limits
    ) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(limits, "limits");
        Optional<RoadPreset> resolvedPreset =
                presetLookup.find(intent.presetId());
        if (resolvedPreset.isEmpty()) {
            return failure(BlockPlanFailureCode.UNKNOWN_PRESET);
        }
        RoadPreset preset = resolvedPreset.orElseThrow();
        if (preset.revision() != intent.expectedPresetRevision()) {
            return failure(BlockPlanFailureCode.PRESET_REVISION_MISMATCH);
        }

        RoadSurfaceResolver.Result resolvedSurface =
                RoadSurfaceResolver.resolve(preset.surfaceColumns());
        if (resolvedSurface instanceof RoadSurfaceResolver.Result.Failure failure) {
            return failure(failure.code());
        }
        ResolvedRoadSurface surface = ((RoadSurfaceResolver.Result.Success)
                resolvedSurface).surface();
        BlockPlanResult result = planner.plan(
                new RoadPathPlanRequest(
                        intent.dimension(),
                        intent.geometry(),
                        surface
                ),
                limits.maximumAffectedBlocks(),
                limits.maximumPlanningCandidates(),
                ConstructionLimits.UNRESTRICTED_MAXIMUM_GRADE
        );
        if (result instanceof BlockPlanResult.Failure planningFailure) {
            return failure(planningFailure.code());
        }
        return new RoadPathPlanningResult.Success(new ResolvedRoadPathPlan(
                ((BlockPlanResult.Success) result).plan(),
                intent.geometry(),
                intent.presetId(),
                preset
        ));
    }

    private static RoadPathPlanningResult.Failure failure(
            BlockPlanFailureCode code
    ) {
        return new RoadPathPlanningResult.Failure(code);
    }
}
