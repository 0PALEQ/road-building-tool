package com.cookiecraftmods.roadbuilder.client.tool;


import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;

import java.util.Objects;

public record RoadSnapGuide(
    RoadPoint start,
    RoadPoint end,

    Kind kind
) {
    public RoadSnapGuide {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(kind, "kind");
        if (Math.hypot(
                end.x() - start.x(),
                end.z() - start.z()
        ) == 0.0) {
            throw new IllegalArgumentException(
                "a snap guide must have a horizontal direction"
            );
        }
    }
    public enum Kind {
        LANE_CONTINUATION,
        NODE_ANGLE
    }
}
