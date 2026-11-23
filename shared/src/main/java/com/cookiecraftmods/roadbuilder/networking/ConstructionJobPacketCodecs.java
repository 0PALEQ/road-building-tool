package com.cookiecraftmods.roadbuilder.networking;

import com.cookiecraftmods.roadbuilder.placement.ConstructionJobAction;
import com.cookiecraftmods.roadbuilder.placement.ConstructionJobPhase;

final class ConstructionJobPacketCodecs {
    private ConstructionJobPacketCodecs() {
    }

    static int actionWireId(ConstructionJobAction action) {
        return switch (action) {
            case PLACE -> 0;
            case REMOVE -> 1;
        };
    }

    static ConstructionJobAction actionFromWireId(int wireId) {
        return switch (wireId) {
            case 0 -> ConstructionJobAction.PLACE;
            case 1 -> ConstructionJobAction.REMOVE;
            default -> throw new IllegalArgumentException(
                    "unknown construction job action " + wireId
            );
        };
    }

    static int phaseWireId(ConstructionJobPhase phase) {
        return switch (phase) {
            case PREPARED -> 0;
            case APPLYING -> 1;
            case COMPLETING -> 2;
            case CANCELLING -> 3;
            case CANCELLED -> 4;
            case QUARANTINED -> 5;
        };
    }

    static ConstructionJobPhase phaseFromWireId(int wireId) {
        return switch (wireId) {
            case 0 -> ConstructionJobPhase.PREPARED;
            case 1 -> ConstructionJobPhase.APPLYING;
            case 2 -> ConstructionJobPhase.COMPLETING;
            case 3 -> ConstructionJobPhase.CANCELLING;
            case 4 -> ConstructionJobPhase.CANCELLED;
            case 5 -> ConstructionJobPhase.QUARANTINED;
            default -> throw new IllegalArgumentException(
                    "unknown construction job phase " + wireId
            );
        };
    }
}
