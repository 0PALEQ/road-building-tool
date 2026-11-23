package com.cookiecraftmods.roadbuilder.network;

import java.util.Objects;
import java.util.UUID;

public record RoadNodeId(UUID value) implements Comparable<RoadNodeId> {
    public RoadNodeId {
        Objects.requireNonNull(value, "value");
    }

    public static RoadNodeId create() {
        return new RoadNodeId(UUID.randomUUID());
    }

    @Override
    public int compareTo(RoadNodeId other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
