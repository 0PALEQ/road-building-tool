package com.cookiecraftmods.roadbuilder.client.tool;

import java.util.Objects;

final class PlacementContinuation {
    private CursorTarget endpoint;
    private CursorTarget queuedTarget;

    void submitted(CursorTarget endpoint) {
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        queuedTarget = null;
    }

    boolean isPending() {
        return endpoint != null;
    }

    CursorTarget endpoint() {
        return endpoint;
    }

    void queue(CursorTarget target) {
        if (!isPending()) {
            throw new IllegalStateException("cannot queue without a submitted endpoint");
        }
        queuedTarget = Objects.requireNonNull(target, "target");
    }

    void clearQueuedTarget() {
        queuedTarget = null;
    }

    Completion complete() {
        Completion completion = new Completion(endpoint, queuedTarget);
        clear();
        return completion;
    }

    void clear() {
        endpoint = null;
        queuedTarget = null;
    }

    record Completion(CursorTarget endpoint, CursorTarget queuedTarget) {
    }
}
