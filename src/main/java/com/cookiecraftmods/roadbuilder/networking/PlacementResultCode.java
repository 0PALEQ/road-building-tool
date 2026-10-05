package com.cookiecraftmods.roadbuilder.networking;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public enum PlacementResultCode {
    ACCEPTED(0),
    UNSUPPORTED_PROTOCOL(1),
    MALFORMED_REQUEST(2),
    DUPLICATE_REQUEST(3),
    DIMENSION_MISMATCH(4),
    UNKNOWN_PRESET(5),
    PRESET_REVISION_MISMATCH(6),
    INVALID_GEOMETRY(7),
    CONSTRUCTION_LIMIT_EXCEEDED(8),
    NOT_AUTHORIZED(9),
    WORLD_CONFLICT(10),
    UNSUPPORTED_WORLD_STATE(11),
    MUTATION_FAILED(12),
    INTERNAL_ERROR(13),
    PERSISTENCE_INCOMPATIBLE(14),
    RATE_LIMITED(15),
    GRAPH_REVISION_MISMATCH(16),
    CONSTRUCTION_BUSY(17);

    private static final Map<Integer, PlacementResultCode> BY_WIRE_ID = createLookup();

    private final int wireId;

    PlacementResultCode(int wireId) {
        this.wireId = wireId;
    }

    public int wireId() {
        return wireId;
    }

    public static Optional<PlacementResultCode> fromWireId(int wireId) {
        return Optional.ofNullable(BY_WIRE_ID.get(wireId));
    }

    private static Map<Integer, PlacementResultCode> createLookup() {
        Map<Integer, PlacementResultCode> lookup = new HashMap<>();
        for (PlacementResultCode code : values()) {
            PlacementResultCode duplicate = lookup.put(code.wireId, code);
            if (duplicate != null) {
                throw new IllegalStateException("duplicate placement result wire id " + code.wireId);
            }
        }
        return Collections.unmodifiableMap(lookup);
    }
}
