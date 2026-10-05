package com.cookiecraftmods.roadbuilder.geometry;

import java.util.Objects;
import java.util.List;

public final class StraightGeometry implements ReferenceGeometry {
    private final RoadPoint start;
    private final RoadPoint end;
    private final double length;
    private final RoadVector tangent;

    public StraightGeometry(RoadPoint start, RoadPoint end) {
        this.start = Objects.requireNonNull(start, "start");
        this.end = Objects.requireNonNull(end, "end");

        RoadVector displacement = start.vectorTo(end);
        this.length = displacement.length();
        if (length == 0.0 || !Double.isFinite(length)) {
            throw new IllegalArgumentException("start and end must define a finite, non-zero segment");
        }
        if (displacement.horizontalLength() == 0.0) {
            throw new IllegalArgumentException("a road segment must have a horizontal direction");
        }
        this.tangent = displacement.normalize();
    }

    public RoadPoint start() {
        return start;
    }

    public RoadPoint end() {
        return end;
    }

    public List<RoadPoint> controlPoints() {
        return List.of(start, end);
    }

    @Override
    public double length() {
        return length;
    }

    public RoadVector tangent() {
        return tangent;
    }

    @Override
    public PathSample sample(double distance) {
        requireSampleDistance(distance);

        RoadPoint position;
        if (distance == 0.0) {
            position = start;
        } else if (distance == length) {
            position = end;
        } else {
            position = start.add(tangent.scale(distance));
        }
        return new PathSample(distance, position, tangent);
    }

    private void requireSampleDistance(double distance) {
        if (!Double.isFinite(distance)) {
            throw new IllegalArgumentException("distance must be finite");
        }
        if (distance < 0.0 || distance > length) {
            throw new IllegalArgumentException("distance must be between 0 and " + length);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StraightGeometry geometry)) {
            return false;
        }
        return start.equals(geometry.start) && end.equals(geometry.end);
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, end);
    }

    @Override
    public String toString() {
        return "StraightGeometry[start=" + start + ", end=" + end + "]";
    }
}
