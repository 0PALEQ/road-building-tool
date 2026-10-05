package com.cookiecraftmods.roadbuilder.client.tool;

import com.cookiecraftmods.roadbuilder.network.RoadNode;

import java.util.Objects;

public record RoadNodeMarker(
        RoadNode node,
        double radius,
        boolean junction
) {
    public RoadNodeMarker {
        Objects.requireNonNull(node, "node");
        if (!Double.isFinite(radius) || radius <= 0.0) {
            throw new IllegalArgumentException(
                    "node marker radius must be finite and positive"
            );
        }
    }
}
