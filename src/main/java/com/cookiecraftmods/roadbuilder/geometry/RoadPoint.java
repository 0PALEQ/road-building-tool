package com.cookiecraftmods.roadbuilder.geometry;

import java.util.Objects;

public record RoadPoint(double x, double y, double z) {
    public RoadPoint {
        requireFinite(x, "x");
        requireFinite(y, "y");
        requireFinite(z, "z");
        x = canonicalizeZero(x);
        y = canonicalizeZero(y);
        z = canonicalizeZero(z);
    }

    public RoadPoint add(RoadVector vector) {
        Objects.requireNonNull(vector, "vector");
        return new RoadPoint(x + vector.x(), y + vector.y(), z + vector.z());
    }

    public RoadPoint subtract(RoadVector vector) {
        Objects.requireNonNull(vector, "vector");
        return new RoadPoint(x - vector.x(), y - vector.y(), z - vector.z());
    }

    public RoadVector vectorTo(RoadPoint other) {
        Objects.requireNonNull(other, "other");
        return new RoadVector(other.x - x, other.y - y, other.z - z);
    }

    public double distanceTo(RoadPoint other) {
        return vectorTo(other).length();
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    private static double canonicalizeZero(double value) {
        return value == 0.0 ? 0.0 : value;
    }
}
