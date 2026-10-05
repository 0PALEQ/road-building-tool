package com.cookiecraftmods.roadbuilder.geometry;

import java.util.Objects;

public record RoadVector(double x, double y, double z) {
    public RoadVector {
        requireFinite(x, "x");
        requireFinite(y, "y");
        requireFinite(z, "z");
        x = canonicalizeZero(x);
        y = canonicalizeZero(y);
        z = canonicalizeZero(z);
    }

    public RoadVector add(RoadVector other) {
        Objects.requireNonNull(other, "other");
        return new RoadVector(x + other.x, y + other.y, z + other.z);
    }

    public RoadVector subtract(RoadVector other) {
        Objects.requireNonNull(other, "other");
        return new RoadVector(x - other.x, y - other.y, z - other.z);
    }

    public RoadVector scale(double factor) {
        requireFinite(factor, "factor");
        return new RoadVector(x * factor, y * factor, z * factor);
    }

    public double dot(RoadVector other) {
        Objects.requireNonNull(other, "other");
        double result = x * other.x + y * other.y + z * other.z;
        if (!Double.isFinite(result)) {
            throw new ArithmeticException("dot product is not finite");
        }
        return result;
    }

    public double length() {
        return Math.hypot(Math.hypot(x, y), z);
    }

    public double horizontalLength() {
        return Math.hypot(x, z);
    }

    public RoadVector normalize() {
        double length = length();
        if (length == 0.0 || !Double.isFinite(length)) {
            throw new IllegalStateException("cannot normalize a zero-length or unbounded vector");
        }
        return scale(1.0 / length);
    }

        public RoadVector horizontalLeftUnit() {
        double horizontalLength = horizontalLength();
        if (horizontalLength == 0.0 || !Double.isFinite(horizontalLength)) {
            throw new IllegalStateException("a horizontal left vector requires a finite horizontal direction");
        }
        return new RoadVector(z / horizontalLength, 0.0, -x / horizontalLength);
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
