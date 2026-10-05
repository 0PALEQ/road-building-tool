package com.cookiecraftmods.roadbuilder.placement;

import java.util.List;
import java.util.Objects;

public record IncrementalRemovalPlan(
        List<ConstructionJobBlockTransition> transitions,
        RemovalSummary summary
) {
    public IncrementalRemovalPlan {
        transitions = List.copyOf(Objects.requireNonNull(transitions, "transitions"));
        Objects.requireNonNull(summary, "summary");
    }
}
