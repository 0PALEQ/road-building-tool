package com.cookiecraftmods.roadbuilder.placement;

import java.util.Objects;
import java.util.UUID;

public record PlacementOperationId(UUID value) {
    public PlacementOperationId {
        Objects.requireNonNull(value, "value");
    }

    public static PlacementOperationId create() {
        return new PlacementOperationId(UUID.randomUUID());
    }

    public static PlacementOperationId parse(String value) {
        return new PlacementOperationId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
