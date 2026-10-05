package com.cookiecraftmods.roadbuilder.placement;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Objects;

public sealed interface PlacementCommitResult {
    record Success(PlacementOperation operation, int changedBlockCount) implements PlacementCommitResult {
        public Success {
            Objects.requireNonNull(operation, "operation");
            if (changedBlockCount < 0 || changedBlockCount > operation.positions().size()) {
                throw new IllegalArgumentException("changedBlockCount is outside the operation footprint");
            }
        }
    }

    record Failure(PlacementCommitFailureCode code, List<BlockPos> positions) implements PlacementCommitResult {
        public Failure {
            Objects.requireNonNull(code, "code");
            positions = List.copyOf(Objects.requireNonNull(positions, "positions"));
        }

        public static Failure at(PlacementCommitFailureCode code, BlockPos position) {
            return new Failure(code, List.of(Objects.requireNonNull(position, "position").immutable()));
        }

        public static Failure withoutPosition(PlacementCommitFailureCode code) {
            return new Failure(code, List.of());
        }
    }
}
