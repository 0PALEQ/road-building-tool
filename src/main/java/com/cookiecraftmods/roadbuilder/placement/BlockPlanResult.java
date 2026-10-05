package com.cookiecraftmods.roadbuilder.placement;

import java.util.Objects;

public sealed interface BlockPlanResult permits BlockPlanResult.Success, BlockPlanResult.Failure {
    record Success(BlockPlacementPlan plan) implements BlockPlanResult {
        public Success {
            Objects.requireNonNull(plan, "plan");
        }
    }

    record Failure(BlockPlanFailureCode code) implements BlockPlanResult {
        public Failure {
            Objects.requireNonNull(code, "code");
        }
    }
}
