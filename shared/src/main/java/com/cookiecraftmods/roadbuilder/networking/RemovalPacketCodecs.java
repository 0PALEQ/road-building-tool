package com.cookiecraftmods.roadbuilder.networking;

import com.cookiecraftmods.roadbuilder.placement.RemovalMode;

final class RemovalPacketCodecs {
    private static final int SAFE_MODE_WIRE_ID = 0;
    private static final int FORCE_MODE_WIRE_ID = 1;

    private RemovalPacketCodecs() {
    }

    static int modeWireId(RemovalMode mode) {
        return switch (mode) {
            case SAFE -> SAFE_MODE_WIRE_ID;
            case FORCE -> FORCE_MODE_WIRE_ID;
        };
    }

    static RemovalMode modeFromWireId(int wireId) {
        return switch (wireId) {
            case SAFE_MODE_WIRE_ID -> RemovalMode.SAFE;
            case FORCE_MODE_WIRE_ID -> RemovalMode.FORCE;
            default -> throw new IllegalArgumentException("unknown removal mode " + wireId);
        };
    }
}
