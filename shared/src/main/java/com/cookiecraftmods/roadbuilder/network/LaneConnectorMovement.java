package com.cookiecraftmods.roadbuilder.network;

import java.util.Objects;

public record LaneConnectorMovement(
        LaneMovementType type,
        boolean allowed,
        boolean manual,
        int priority
) {
    public static final LaneConnectorMovement AUTOMATIC =
            new LaneConnectorMovement(
                    LaneMovementType.MANUAL,
                    true,
                    false,
                    0
            );

    public LaneConnectorMovement {
        Objects.requireNonNull(type, "type");
        if (priority < 0 || priority > 15) {
            throw new IllegalArgumentException(
                    "connector priority must be between 0 and 15"
            );
        }
    }

    public static LaneConnectorMovement automatic(LaneMovementType type) {
        return new LaneConnectorMovement(type, true, false, 0);
    }
}
