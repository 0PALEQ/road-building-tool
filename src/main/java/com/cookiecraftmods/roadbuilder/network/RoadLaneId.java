package com.cookiecraftmods.roadbuilder.network;

import java.util.Objects;
import java.util.UUID;

public record RoadLaneId(UUID value) implements Comparable<RoadLaneId> {
    public RoadLaneId {
        Objects.requireNonNull(value, "value");
    }

    public static RoadLaneId create() {
        return new RoadLaneId(UUID.randomUUID());
    }

    @Override
    public int compareTo(RoadLaneId other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
