package com.cookiecraftmods.roadbuilder.placement;

import java.util.Arrays;
import java.util.Optional;

public enum ConstructionJobAction {
    PLACE("place"),
    REMOVE("remove");

    private final String serializedName;

    ConstructionJobAction(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static Optional<ConstructionJobAction> fromSerializedName(String name) {
        return Arrays.stream(values())
                .filter(value -> value.serializedName.equals(name))
                .findFirst();
    }
}
