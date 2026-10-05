package com.cookiecraftmods.roadbuilder.placement;

import java.util.Objects;

public record ConstructionJobBatchResult(
        ConstructionJob job,
        int examinedTransitions,
        int changedBlocks
) {
    public ConstructionJobBatchResult {
        Objects.requireNonNull(job, "job");
        if (examinedTransitions < 0 || changedBlocks < 0
                || changedBlocks > examinedTransitions) {
            throw new IllegalArgumentException("invalid construction-job batch counts");
        }
    }
}
