package com.cookiecraftmods.roadbuilder.placement;

import java.util.Arrays;
import java.util.Optional;

public enum ConstructionJobPhase {
    PREPARED("prepared"),
    APPLYING("applying"),
    COMPLETING("completing"),
    CANCELLING("cancelling"),
    CANCELLED("cancelled"),
    QUARANTINED("quarantined");

    private final String serializedName;

    ConstructionJobPhase(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

        public boolean blocksGraphMutation() {
        return switch (this) {
            case PREPARED, APPLYING, COMPLETING, CANCELLING -> true;
            case CANCELLED, QUARANTINED -> false;
        };
    }

    public static Optional<ConstructionJobPhase> fromSerializedName(String name) {
        return Arrays.stream(values())
                .filter(value -> value.serializedName.equals(name))
                .findFirst();
    }
}
