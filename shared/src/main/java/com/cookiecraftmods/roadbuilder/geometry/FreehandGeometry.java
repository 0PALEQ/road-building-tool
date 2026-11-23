package com.cookiecraftmods.roadbuilder.geometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class FreehandGeometry implements ReferenceGeometry {
    public static final double DEFAULT_SIMPLIFICATION_TOLERANCE = 0.20;
    private static final double MINIMUM_POINT_SPACING = 0.05;

    private final List<RoadPoint> sourcePoints;
    private final List<RoadPoint> anchors;
    private final ReferenceGeometry delegate;

    public FreehandGeometry(List<RoadPoint> sourcePoints) {
        this(sourcePoints, DEFAULT_SIMPLIFICATION_TOLERANCE);
    }

    public FreehandGeometry(
            List<RoadPoint> sourcePoints,
            double simplificationTolerance
    ) {
        Objects.requireNonNull(sourcePoints, "sourcePoints");
        if (!Double.isFinite(simplificationTolerance)
                || simplificationTolerance < 0.0) {
            throw new IllegalArgumentException(
                    "simplificationTolerance must be finite and non-negative"
            );
        }
        List<RoadPoint> filtered = filter(sourcePoints);
        if (filtered.size() < 2) {
            throw new IllegalArgumentException(
                    "a freehand stroke requires at least two distinct points"
            );
        }
        this.sourcePoints = Collections.unmodifiableList(filtered);
        this.anchors = Collections.unmodifiableList(
                simplify(filtered, simplificationTolerance)
        );
        if (anchors.size() == 2) {
            this.delegate = new StraightGeometry(
                    anchors.get(0),
                    anchors.get(1)
            );
        } else {
            this.delegate = new MultiCurveGeometry(anchors);
        }
    }

    public List<RoadPoint> sourcePoints() {
        return sourcePoints;
    }

    public List<RoadPoint> anchors() {
        return anchors;
    }

    @Override
    public RoadPoint start() {
        return anchors.get(0);
    }

    @Override
    public RoadPoint end() {
        return anchors.get(anchors.size() - 1);
    }

    @Override
    public double length() {
        return delegate.length();
    }

    public int adaptiveSampleCount() {
        if (delegate instanceof MultiCurveGeometry curve) {
            return curve.adaptiveSampleCount();
        }
        return 2;
    }

    @Override
    public PathSample sample(double distance) {
        return delegate.sample(distance);
    }

    private static List<RoadPoint> filter(List<RoadPoint> sourcePoints) {
        ArrayList<RoadPoint> result = new ArrayList<>(sourcePoints.size());
        for (RoadPoint rawPoint : sourcePoints) {
            RoadPoint point = Objects.requireNonNull(
                    rawPoint,
                    "sourcePoints contains null"
            );
            if (result.isEmpty()
                    || result.get(result.size() - 1).distanceTo(point)
                    >= MINIMUM_POINT_SPACING) {
                result.add(point);
            }
        }
        return result;
    }

    private static List<RoadPoint> simplify(
            List<RoadPoint> points,
            double tolerance
    ) {
        if (points.size() <= 2 || tolerance == 0.0) {
            return new ArrayList<>(points);
        }
        boolean[] retained = new boolean[points.size()];
        retained[0] = true;
        retained[points.size() - 1] = true;
        simplifyRange(
                points,
                0,
                points.size() - 1,
                tolerance * tolerance,
                retained
        );
        ArrayList<RoadPoint> result = new ArrayList<>();
        for (int index = 0; index < points.size(); index++) {
            if (retained[index]) {
                result.add(points.get(index));
            }
        }
        return result;
    }

    private static void simplifyRange(
            List<RoadPoint> points,
            int startIndex,
            int endIndex,
            double toleranceSquared,
            boolean[] retained
    ) {
        RoadPoint start = points.get(startIndex);
        RoadPoint end = points.get(endIndex);
        double maximumDistanceSquared = -1.0;
        int furthestIndex = -1;
        for (int index = startIndex + 1; index < endIndex; index++) {
            double distanceSquared = segmentDistanceSquared(
                    points.get(index),
                    start,
                    end
            );
            if (distanceSquared > maximumDistanceSquared) {
                maximumDistanceSquared = distanceSquared;
                furthestIndex = index;
            }
        }
        if (furthestIndex < 0 || maximumDistanceSquared <= toleranceSquared) {
            return;
        }
        retained[furthestIndex] = true;
        simplifyRange(
                points,
                startIndex,
                furthestIndex,
                toleranceSquared,
                retained
        );
        simplifyRange(
                points,
                furthestIndex,
                endIndex,
                toleranceSquared,
                retained
        );
    }

    private static double segmentDistanceSquared(
            RoadPoint point,
            RoadPoint start,
            RoadPoint end
    ) {
        RoadVector segment = start.vectorTo(end);
        double lengthSquared = segment.dot(segment);
        if (lengthSquared == 0.0) {
            double distance = start.distanceTo(point);
            return distance * distance;
        }
        double parameter = start.vectorTo(point).dot(segment) / lengthSquared;
        double clamped = Math.max(0.0, Math.min(1.0, parameter));
        double distance = start.add(segment.scale(clamped)).distanceTo(point);
        return distance * distance;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof FreehandGeometry geometry
                && anchors.equals(geometry.anchors);
    }

    @Override
    public int hashCode() {
        return anchors.hashCode();
    }

    @Override
    public String toString() {
        return "FreehandGeometry[anchors=" + anchors + "]";
    }
}
