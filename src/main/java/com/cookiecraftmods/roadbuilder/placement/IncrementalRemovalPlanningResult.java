package com.cookiecraftmods.roadbuilder.placement;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Objects;

public sealed interface IncrementalRemovalPlanningResult {
    record Success(IncrementalRemovalPlan plan)
            implements IncrementalRemovalPlanningResult {
        public Success {
            Objects.requireNonNull(plan, "plan");
        }
    }

    record Failure(
            PlacementRemovalFailureCode code,
            List<BlockPos> positions
    ) implements IncrementalRemovalPlanningResult {
        public Failure {
            Objects.requireNonNull(code, "code");
            positions = List.copyOf(Objects.requireNonNull(positions, "positions"));
        }
    }
}
