package com.cookiecraftmods.roadbuilder.network;

import java.util.Objects;
import java.util.UUID;

public record LaneConnectorId(UUID value) implements Comparable<LaneConnectorId> {
    public LaneConnectorId {
        Objects.requireNonNull(value, "value");
    }

    public static LaneConnectorId create() {
        return new LaneConnectorId(UUID.randomUUID());
    }

    @Override
    public int compareTo(LaneConnectorId other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
