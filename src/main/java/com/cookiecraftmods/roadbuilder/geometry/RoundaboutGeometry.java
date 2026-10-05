package com.cookiecraftmods.roadbuilder.geometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class RoundaboutGeometry implements ReferenceGeometry {
    public static final double MINIMUM_RADIUS = 4.0;
    public static final double MAXIMUM_RADIUS = 256.0;
    private static final double SURFACE_CLEARANCE = 4.0;

    private static final double QUARTER_CIRCLE_HANDLE =
            4.0 * (Math.sqrt(2.0) - 1.0) / 3.0;

    private final RoadPoint center;
    private final RoadPoint radiusPoint;
    private final double radius;
    private final List<RoadPoint> boundaryPoints;
    private final List<CubicBezierGeometry> spans;
    private final double[] cumulativeLengths;
    private final double length;

    public RoundaboutGeometry(RoadPoint center, RoadPoint radiusPoint) {
        this.center = Objects.requireNonNull(center, "center");
        RoadPoint suppliedRadiusPoint = Objects.requireNonNull(
                radiusPoint,
                "radiusPoint"
        );
        double deltaX = suppliedRadiusPoint.x() - center.x();
        double deltaZ = suppliedRadiusPoint.z() - center.z();
        this.radius = Math.hypot(deltaX, deltaZ);
        if (!Double.isFinite(radius)
                || radius < MINIMUM_RADIUS
                || radius > MAXIMUM_RADIUS) {
            throw new IllegalArgumentException(
                    "roundabout radius must be between " + MINIMUM_RADIUS
                            + " and " + MAXIMUM_RADIUS
            );
        }

        double axisX = deltaX / radius;
        double axisZ = deltaZ / radius;
        this.radiusPoint = point(axisX, axisZ);
        this.boundaryPoints = Collections.unmodifiableList(List.of(
                this.radiusPoint,
                point(-axisZ, axisX),
                point(-axisX, -axisZ),
                point(axisZ, -axisX)
        ));
        this.spans = Collections.unmodifiableList(createSpans());
        this.cumulativeLengths = new double[spans.size() + 1];
        double accumulated = 0.0;
        for (int index = 0; index < spans.size(); index++) {
            accumulated += spans.get(index).length();
            cumulativeLengths[index + 1] = accumulated;
        }
        this.length = accumulated;
    }

    public RoadPoint center() {
        return center;
    }

    public RoadPoint radiusPoint() {
        return radiusPoint;
    }

    public double radius() {
        return radius;
    }

    /**
     * Four equally spaced graph ports in traversal order.
     */
    public List<RoadPoint> boundaryPoints() {
        return boundaryPoints;
    }

    /**
     * Four seamless quarter-circle segments in traversal order.
     */
    public List<CubicBezierGeometry> spans() {
        return spans;
    }

    /**
     * Returns the circle spans in the requested world-space circulation
     * direction. Minecraft's positive Z axis points south, so the native
     * traversal is clockwise when viewed from above.
     */
    public List<CubicBezierGeometry> spans(boolean clockwise) {
        if (clockwise) {
            return spans;
        }
        List<CubicBezierGeometry> reversed = new ArrayList<>(spans.size());
        for (int index = spans.size() - 1; index >= 0; index--) {
            CubicBezierGeometry span = spans.get(index);
            reversed.add(new CubicBezierGeometry(
                    span.end(),
                    span.endControl(),
                    span.startControl(),
                    span.start()
            ));
        }
        return Collections.unmodifiableList(reversed);
    }

    public static double minimumRadius(double surfaceWidth) {
        if (!Double.isFinite(surfaceWidth) || surfaceWidth <= 0.0) {
            throw new IllegalArgumentException(
                    "surface width must be finite and greater than zero"
            );
        }
        return Math.max(MINIMUM_RADIUS, surfaceWidth + SURFACE_CLEARANCE);
    }

    @Override
    public RoadPoint start() {
        return radiusPoint;
    }

    @Override
    public RoadPoint end() {
        return radiusPoint;
    }

    @Override
    public double length() {
        return length;
    }

    @Override
    public PathSample sample(double distance) {
        requireDistance(distance);
        if (distance == length) {
            CubicBezierGeometry finalSpan = spans.get(spans.size() - 1);
            PathSample local = finalSpan.sample(finalSpan.length());
            return new PathSample(distance, local.position(), local.tangent());
        }
        int spanIndex = spanAt(distance);
        PathSample local = spans.get(spanIndex).sample(
                distance - cumulativeLengths[spanIndex]
        );
        return new PathSample(distance, local.position(), local.tangent());
    }

    private List<CubicBezierGeometry> createSpans() {
        List<CubicBezierGeometry> result = new ArrayList<>(4);
        double handleLength = radius * QUARTER_CIRCLE_HANDLE;
        for (int index = 0; index < boundaryPoints.size(); index++) {
            RoadPoint start = boundaryPoints.get(index);
            RoadPoint end = boundaryPoints.get(
                    (index + 1) % boundaryPoints.size()
            );
            RoadVector startRadius = center.vectorTo(start).normalize();
            RoadVector endRadius = center.vectorTo(end).normalize();
            RoadVector startTangent = new RoadVector(
                    -startRadius.z(),
                    0.0,
                    startRadius.x()
            );
            RoadVector endTangent = new RoadVector(
                    -endRadius.z(),
                    0.0,
                    endRadius.x()
            );
            result.add(new CubicBezierGeometry(
                    start,
                    start.add(startTangent.scale(handleLength)),
                    end.subtract(endTangent.scale(handleLength)),
                    end
            ));
        }
        return result;
    }

    private RoadPoint point(double unitX, double unitZ) {
        return new RoadPoint(
                center.x() + unitX * radius,
                center.y(),
                center.z() + unitZ * radius
        );
    }

    private int spanAt(double distance) {
        int low = 0;
        int high = spans.size() - 1;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (cumulativeLengths[middle + 1] <= distance) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    private void requireDistance(double distance) {
        if (!Double.isFinite(distance)
                || distance < 0.0
                || distance > length) {
            throw new IllegalArgumentException(
                    "distance must be finite and between 0 and " + length
            );
        }
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof RoundaboutGeometry geometry
                && center.equals(geometry.center)
                && radiusPoint.equals(geometry.radiusPoint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(center, radiusPoint);
    }

    @Override
    public String toString() {
        return "RoundaboutGeometry[center=" + center
                + ", radiusPoint=" + radiusPoint + "]";
    }
}
