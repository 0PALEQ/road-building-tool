package com.cookiecraftmods.roadbuilder.placement;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Objects;

public sealed interface PlacementRemovalResult {
    record Success(
            PlacementOperationId operationId,
            RemovalMode mode,
            RemovalSummary summary
    ) implements PlacementRemovalResult {
        public Success {
            Objects.requireNonNull(operationId, "operationId");
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(summary, "summary");
        }
    }

    record Failure(PlacementRemovalFailureCode code, List<BlockPos> positions)
            implements PlacementRemovalResult {
        public Failure {
            Objects.requireNonNull(code, "code");
            positions = List.copyOf(Objects.requireNonNull(positions, "positions"));
        }

        static Failure at(PlacementRemovalFailureCode code, BlockPos position) {
            return new Failure(code, List.of(Objects.requireNonNull(position, "position").immutable()));
        }

        static Failure withoutPosition(PlacementRemovalFailureCode code) {
            return new Failure(code, List.of());
        }
    }
}
