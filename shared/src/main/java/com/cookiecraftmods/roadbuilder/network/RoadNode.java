package com.cookiecraftmods.roadbuilder.network;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;

import java.util.Objects;

public record RoadNode(RoadNodeId id, RoadPoint position) {
    public RoadNode {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(position, "position");
    }
}
