package com.cookiecraftmods.roadbuilder.network;

import java.util.Objects;
import java.util.UUID;

public record RoadId(UUID value) implements Comparable<RoadId> {
    public RoadId {
        Objects.requireNonNull(value, "value");
    }

    public static RoadId create() {
        return new RoadId(UUID.randomUUID());
    }

    @Override
    public int compareTo(RoadId other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
