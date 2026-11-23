package com.cookiecraftmods.roadbuilder.network;

import com.cookiecraftmods.roadbuilder.preset.TrafficHandedness;

import java.util.Objects;

public record Road(RoadId id, TrafficHandedness trafficHandedness) {
    public Road {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(trafficHandedness, "trafficHandedness");
    }
}
