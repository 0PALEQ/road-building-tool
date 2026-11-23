package com.cookiecraftmods.roadbuilder.placement;

import java.util.Objects;

public sealed interface RoadPathPlanningResult {
    record Success(ResolvedRoadPathPlan roadPlan)
            implements RoadPathPlanningResult {
        public Success {
            Objects.requireNonNull(roadPlan, "roadPlan");
        }
    }

    record Failure(BlockPlanFailureCode code)
            implements RoadPathPlanningResult {
        public Failure {
            Objects.requireNonNull(code, "code");
        }
    }
}
