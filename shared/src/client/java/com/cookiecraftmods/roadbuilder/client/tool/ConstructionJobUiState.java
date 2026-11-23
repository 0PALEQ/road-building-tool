package com.cookiecraftmods.roadbuilder.client.tool;

import com.cookiecraftmods.roadbuilder.placement.ConstructionJobPhase;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

final class ConstructionJobUiState {
    private UUID id;
    private ResourceLocation dimension;
    private int completedTransitions = -1;
    private ConstructionJobPhase phase;
    private boolean cancellationRequested;

    boolean isActive() {
        return id != null;
    }

    UUID id() {
        return id;
    }

    ResourceLocation dimension() {
        return dimension;
    }

    void start(
            UUID id,
            ResourceLocation dimension,
            int completedTransitions,
            ConstructionJobPhase phase
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.dimension = dimension;
        this.completedTransitions = completedTransitions;
        this.phase = phase;
        cancellationRequested = false;
    }

    boolean update(ConstructionJobPhase phase, int completedTransitions) {
        if (!isActive()) {
            throw new IllegalStateException("cannot update an inactive job");
        }
        Objects.requireNonNull(phase, "phase");
        if (this.phase == phase && this.completedTransitions == completedTransitions) {
            return false;
        }
        this.phase = phase;
        this.completedTransitions = completedTransitions;
        return true;
    }

    void requestCancellation() {
        if (isActive()) {
            cancellationRequested = true;
        }
    }

    boolean consumeCancellationRequest() {
        if (!cancellationRequested || !isActive() || dimension == null) {
            return false;
        }
        cancellationRequested = false;
        return true;
    }

    void clear() {
        id = null;
        dimension = null;
        completedTransitions = -1;
        phase = null;
        cancellationRequested = false;
    }
}
