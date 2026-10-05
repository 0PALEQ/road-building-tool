package com.cookiecraftmods.roadbuilder.networking;

import java.util.Objects;
import java.util.UUID;

public record AcceptedPlacement(
        UUID operationId,
        int affectedBlockCount,
        boolean queued
) {
    public static final int MAX_AFFECTED_BLOCK_COUNT = 2_000_000;

    public AcceptedPlacement {
        Objects.requireNonNull(operationId, "operationId");
        if (affectedBlockCount <= 0 || affectedBlockCount > MAX_AFFECTED_BLOCK_COUNT) {
            throw new IllegalArgumentException(
                    "affectedBlockCount must be between 1 and " + MAX_AFFECTED_BLOCK_COUNT
            );
        }
    }

    public AcceptedPlacement(UUID operationId, int affectedBlockCount) {
        this(operationId, affectedBlockCount, false);
    }
}
