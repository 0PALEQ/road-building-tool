package com.cookiecraftmods.roadbuilder.network;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.RoadVector;
import com.cookiecraftmods.roadbuilder.preset.IntersectionControl;

import java.util.Objects;

public record SolvedJunctionApproach(
        ApproachKey key,
        RoadSegment segment,
        RoadPoint boundaryPosition,
        RoadVector outwardTangent,
        RoadVector left,
        double drivableHalfWidth,
        double surfaceHalfWidth,
        double trimDistance,
        IntersectionControl control,
        boolean crosswalk,
        boolean primary
) {
    public SolvedJunctionApproach {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(boundaryPosition, "boundaryPosition");
        Objects.requireNonNull(outwardTangent, "outwardTangent");
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(control, "control");
        if (!Double.isFinite(drivableHalfWidth) || drivableHalfWidth < 0.0
                || !Double.isFinite(surfaceHalfWidth) || surfaceHalfWidth <= 0.0
                || !Double.isFinite(trimDistance) || trimDistance <= 0.0) {
            throw new IllegalArgumentException("invalid junction approach dimensions");
        }
    }
}
