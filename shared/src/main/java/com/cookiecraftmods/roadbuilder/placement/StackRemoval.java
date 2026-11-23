package com.cookiecraftmods.roadbuilder.placement;

import java.util.Objects;
import java.util.Optional;

public record StackRemoval(
        Optional<PositionChangeStack> updatedStack,
        BlockChangeLayer removedLayer,
        boolean wasTop,
        boolean successorRebased
) {
    public StackRemoval {
        Objects.requireNonNull(updatedStack, "updatedStack");
        Objects.requireNonNull(removedLayer, "removedLayer");
    }
}
