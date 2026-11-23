package com.cookiecraftmods.roadbuilder.placement;

import java.util.List;
import java.util.Objects;

public record OperationMaterializationRepartition(
        PlacementOperationId sourceOperationId,
        List<MaterializationPartition> partitions
) {
    public OperationMaterializationRepartition {
        Objects.requireNonNull(sourceOperationId, "sourceOperationId");
        partitions = List.copyOf(Objects.requireNonNull(partitions, "partitions"));
        if (partitions.isEmpty()) {
            throw new IllegalArgumentException("repartition must contain at least one partition");
        }
    }
}
