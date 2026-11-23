package com.cookiecraftmods.roadbuilder.network;

import com.cookiecraftmods.roadbuilder.geometry.ReferenceGeometry;
import com.cookiecraftmods.roadbuilder.preset.LaneComponent;

import java.util.Objects;

public record RoadLane(
        RoadLaneId id,
        RoadSegmentId segmentId,
        int componentIndex,
        LaneComponent component,
        ReferenceGeometry geometry
) {
    public RoadLane {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(segmentId, "segmentId");
        if (componentIndex < 0) {
            throw new IllegalArgumentException("componentIndex must be non-negative");
        }
        Objects.requireNonNull(component, "component");
        Objects.requireNonNull(geometry, "geometry");
    }
}
