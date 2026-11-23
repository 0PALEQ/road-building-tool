package com.cookiecraftmods.roadbuilder.network;

import java.util.Objects;
import java.util.UUID;

public record RoadSegmentId(UUID value) implements Comparable<RoadSegmentId> {
    public RoadSegmentId {
        Objects.requireNonNull(value, "value");
    }

    public static RoadSegmentId create() {
        return new RoadSegmentId(UUID.randomUUID());
    }

    @Override
    public int compareTo(RoadSegmentId other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
