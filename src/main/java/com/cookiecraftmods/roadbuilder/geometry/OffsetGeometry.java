package com.cookiecraftmods.roadbuilder.geometry;

import java.util.Objects;

public final class OffsetGeometry implements ReferenceGeometry {
    private final ReferenceGeometry reference;
    private final double lateralOffset;
    private final boolean reversed;

    public OffsetGeometry(
            ReferenceGeometry reference,
            double lateralOffset,
            boolean reversed
    ) {
        this.reference = Objects.requireNonNull(reference, "reference");
        if (!Double.isFinite(lateralOffset)) {
            throw new IllegalArgumentException(
                    "lateralOffset must be finite"
            );
        }
        this.lateralOffset = lateralOffset == 0.0 ? 0.0 : lateralOffset;
        this.reversed = reversed;
    }

    public ReferenceGeometry reference() {
        return reference;
    }

    public double lateralOffset() {
        return lateralOffset;
    }

    public boolean reversed() {
        return reversed;
    }

    @Override
    public RoadPoint start() {
        return sample(0.0).position();
    }

    @Override
    public RoadPoint end() {
        return sample(length()).position();
    }

    @Override
    public double length() {
        return reference.length();
    }

    @Override
    public PathSample sample(double distance) {
        requireDistance(distance);
        double referenceDistance = reversed ? length() - distance : distance;
        PathSample source = reference.sample(referenceDistance, lateralOffset);
        RoadVector tangent = reversed
                ? source.tangent().scale(-1.0)
                : source.tangent();
        return new PathSample(distance, source.position(), tangent);
    }

    private void requireDistance(double distance) {
        if (!Double.isFinite(distance)) {
            throw new IllegalArgumentException("distance must be finite");
        }
        if (distance < 0.0 || distance > length()) {
            throw new IllegalArgumentException(
                    "distance must be between 0 and " + length()
            );
        }
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof OffsetGeometry geometry
                && Double.doubleToLongBits(lateralOffset)
                == Double.doubleToLongBits(geometry.lateralOffset)
                && reversed == geometry.reversed
                && reference.equals(geometry.reference);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reference, lateralOffset, reversed);
    }
}
