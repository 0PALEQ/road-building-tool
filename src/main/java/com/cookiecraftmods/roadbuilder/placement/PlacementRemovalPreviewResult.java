package com.cookiecraftmods.roadbuilder.placement;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Objects;

public sealed interface PlacementRemovalPreviewResult {
    record Success(
            PlacementOperationId operationId,
            RemovalMode mode,
            RemovalSummary summary
    ) implements PlacementRemovalPreviewResult {
        public Success {
            Objects.requireNonNull(operationId, "operationId");
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(summary, "summary");
        }
    }

    record Failure(PlacementRemovalFailureCode code, List<BlockPos> positions)
            implements PlacementRemovalPreviewResult {
        public Failure {
            Objects.requireNonNull(code, "code");
            positions = List.copyOf(Objects.requireNonNull(positions, "positions"));
        }
    }
}
