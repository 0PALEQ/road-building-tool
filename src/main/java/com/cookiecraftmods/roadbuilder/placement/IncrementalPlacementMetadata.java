package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.network.RoadGraphDelta;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record IncrementalPlacementMetadata(
        Optional<RoadMaterializationOwner> directOwner,
        List<OperationMaterializationRepartition> repartitions,
        RoadGraphDelta graphDelta
) {
    public static final int MAXIMUM_REPARTITION_COUNT = 512;
    public static final int MAXIMUM_PARTITION_COUNT = 1_024;
    public static final int MAXIMUM_POSITION_REFERENCE_COUNT = 2_000_000;

    public IncrementalPlacementMetadata {
        directOwner = Objects.requireNonNull(directOwner, "directOwner");
        repartitions = List.copyOf(Objects.requireNonNull(
                repartitions,
                "repartitions"
        ));
        Objects.requireNonNull(graphDelta, "graphDelta");
        if (directOwner.isPresent() == !repartitions.isEmpty()) {
            throw new IllegalArgumentException(
                    "placement metadata must use exactly one ownership publication strategy"
            );
        }
        if (graphDelta.targetRevision() != graphDelta.baseRevision() + 1) {
            throw new IllegalArgumentException(
                    "a placement graph delta must advance exactly one revision"
            );
        }
        if (repartitions.size() > MAXIMUM_REPARTITION_COUNT) {
            throw new IllegalArgumentException(
                    "placement metadata has too many repartitions"
            );
        }
        long partitionCount = 0;
        long positionReferences = 0;
        for (OperationMaterializationRepartition repartition : repartitions) {
            partitionCount += repartition.partitions().size();
            for (MaterializationPartition partition : repartition.partitions()) {
                positionReferences += partition.positions().size();
            }
        }
        if (partitionCount > MAXIMUM_PARTITION_COUNT) {
            throw new IllegalArgumentException(
                    "placement metadata has too many materialization partitions"
            );
        }
        if (positionReferences > MAXIMUM_POSITION_REFERENCE_COUNT) {
            throw new IllegalArgumentException(
                    "placement metadata has too many position references"
            );
        }
    }
}
