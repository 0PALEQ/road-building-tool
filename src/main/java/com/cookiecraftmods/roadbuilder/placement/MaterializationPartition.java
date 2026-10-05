package com.cookiecraftmods.roadbuilder.placement;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Objects;

public record MaterializationPartition(
        PlacementOperationId operationId,
        RoadMaterializationOwner owner,
        List<BlockPos> positions
) {
    public MaterializationPartition {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(owner, "owner");
        positions = List.copyOf(Objects.requireNonNull(positions, "positions"));
        if (positions.isEmpty()) {
            throw new IllegalArgumentException("a materialization partition cannot be empty");
        }
    }
}
