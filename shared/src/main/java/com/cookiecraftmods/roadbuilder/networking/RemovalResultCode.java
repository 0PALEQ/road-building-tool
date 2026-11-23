package com.cookiecraftmods.roadbuilder.networking;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public enum RemovalResultCode {
    SUCCESS(0),
    DIMENSION_MISMATCH(1),
    UNKNOWN_OPERATION(2),
    NOT_AUTHORIZED(3),
    FORCE_RESTORE_NOT_AUTHORIZED(4),
    REMOVAL_LIMIT_EXCEEDED(5),
    OWNERSHIP_CORRUPT(6),
    UNSUPPORTED_WORLD_STATE(7),
    WORLD_CONFLICT(8),
    MUTATION_FAILED(9),
    PERSISTENCE_INCOMPATIBLE(10),
    RATE_LIMITED(11),
    INTERNAL_ERROR(12),
    UNKNOWN_SEGMENT(13),
    STALE_GRAPH(14),
    QUEUED(15);

    private static final Map<Integer, RemovalResultCode> BY_WIRE_ID = createLookup();

    private final int wireId;

    RemovalResultCode(int wireId) {
        this.wireId = wireId;
    }

    public int wireId() {
        return wireId;
    }

    public static Optional<RemovalResultCode> fromWireId(int wireId) {
        return Optional.ofNullable(BY_WIRE_ID.get(wireId));
    }

    private static Map<Integer, RemovalResultCode> createLookup() {
        Map<Integer, RemovalResultCode> lookup = new HashMap<>();
        for (RemovalResultCode code : values()) {
            RemovalResultCode duplicate = lookup.put(code.wireId, code);
            if (duplicate != null) {
                throw new IllegalStateException("duplicate removal result wire id " + code.wireId);
            }
        }
        return Collections.unmodifiableMap(lookup);
    }
}
