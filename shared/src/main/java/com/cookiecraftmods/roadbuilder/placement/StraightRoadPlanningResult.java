package com.cookiecraftmods.roadbuilder.placement;

import java.util.Objects;

public sealed interface StraightRoadPlanningResult {
    record Success(ResolvedStraightRoadPlan roadPlan) implements StraightRoadPlanningResult {
        public Success {
            Objects.requireNonNull(roadPlan, "roadPlan");
        }
    }

    record Failure(BlockPlanFailureCode code) implements StraightRoadPlanningResult {
        public Failure {
            Objects.requireNonNull(code, "code");
        }
    }
}
