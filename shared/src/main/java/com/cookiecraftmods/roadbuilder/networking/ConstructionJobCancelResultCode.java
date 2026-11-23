package com.cookiecraftmods.roadbuilder.networking;

import java.util.Arrays;
import java.util.Optional;

public enum ConstructionJobCancelResultCode {
    ACCEPTED(0),
    ALREADY_CANCELLING(1),
    ALREADY_CANCELLED(2),
    UNKNOWN_JOB(3),
    NOT_AUTHORIZED(4),
    DIMENSION_MISMATCH(5),
    INVALID_PHASE(6),
    PERSISTENCE_INCOMPATIBLE(7),
    RATE_LIMITED(8);

    private final int wireId;

    ConstructionJobCancelResultCode(int wireId) {
        this.wireId = wireId;
    }

    public int wireId() {
        return wireId;
    }

    public static Optional<ConstructionJobCancelResultCode> fromWireId(int wireId) {
        return Arrays.stream(values())
                .filter(value -> value.wireId == wireId)
                .findFirst();
    }
}
