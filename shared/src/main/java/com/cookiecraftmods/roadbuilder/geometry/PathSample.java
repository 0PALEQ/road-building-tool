package com.cookiecraftmods.roadbuilder.geometry;

import java.util.Objects;

public record PathSample(double distance, RoadPoint position, RoadVector tangent) {
    private static final double UNIT_TOLERANCE = 1.0e-10;

    public PathSample {
        if (!Double.isFinite(distance) || distance < 0.0) {
            throw new IllegalArgumentException("distance must be finite and non-negative");
        }
        distance = distance == 0.0 ? 0.0 : distance;
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(tangent, "tangent");

        double tangentLength = tangent.length();
        if (!Double.isFinite(tangentLength) || Math.abs(tangentLength - 1.0) > UNIT_TOLERANCE) {
            throw new IllegalArgumentException("tangent must be a unit vector");
        }
    }

        public PathSample offsetLaterally(double lateralDistance) {
        if (!Double.isFinite(lateralDistance)) {
            throw new IllegalArgumentException("lateralDistance must be finite");
        }
        if (lateralDistance == 0.0) {
            return this;
        }

        RoadVector offset = tangent.horizontalLeftUnit().scale(lateralDistance);
        return new PathSample(distance, position.add(offset), tangent);
    }
}
