package com.cookiecraftmods.roadbuilder.geometry;

import java.util.List;
import java.util.Objects;

public final class QuadraticBezierGeometry implements ReferenceGeometry {
    private final RoadPoint start;
    private final RoadPoint horizontalControl;
    private final RoadPoint control;
    private final RoadPoint end;
    private final AdaptiveCurveSampler.ArcLengthTable horizontalSamples;
    private final AdaptiveCurveSampler.ArcLengthTable samples;

    public QuadraticBezierGeometry(
            RoadPoint start,
            RoadPoint control,
            RoadPoint end
    ) {
        this.start = Objects.requireNonNull(start, "start");
        RoadPoint suppliedControl = Objects.requireNonNull(control, "control");
        this.end = Objects.requireNonNull(end, "end");
        requireDistinctHorizontalEndpoints(start, end);
        this.horizontalControl = new RoadPoint(
                suppliedControl.x(),
                0.0,
                suppliedControl.z()
        );
        this.horizontalSamples = AdaptiveCurveSampler.sample(
                this::horizontalPositionAt,
                this::horizontalDerivativeAt
        );
        this.control = new RoadPoint(
                suppliedControl.x(),
                elevationAt(0.5),
                suppliedControl.z()
        );
        this.samples = AdaptiveCurveSampler.sample(
                this::positionAt,
                this::derivativeAt
        );
    }

    public RoadPoint start() {
        return start;
    }

    public RoadPoint control() {
        return control;
    }

    public RoadPoint end() {
        return end;
    }

    public List<RoadPoint> controlPoints() {
        return List.of(start, control, end);
    }

    @Override
    public double length() {
        return samples.length();
    }

    public int adaptiveSampleCount() {
        return samples.sampleCount();
    }

    public double horizontalLength() {
        return horizontalSamples.length();
    }

    @Override
    public PathSample sample(double distance) {
        return samples.sample(distance);
    }

    private RoadPoint positionAt(double parameter) {
        RoadPoint horizontal = horizontalPositionAt(parameter);
        return new RoadPoint(
                horizontal.x(),
                elevationAt(parameter),
                horizontal.z()
        );
    }

    private RoadPoint horizontalPositionAt(double parameter) {
        double inverse = 1.0 - parameter;
        return weightedPoint(
                inverse * inverse,
                horizontalPoint(start),
                2.0 * inverse * parameter,
                horizontalControl,
                parameter * parameter,
                horizontalPoint(end)
        );
    }

    private RoadVector derivativeAt(double parameter) {
        RoadVector horizontal = horizontalDerivativeAt(parameter);
        double vertical = (end.y() - start.y())
                * horizontal.horizontalLength()
                / horizontalSamples.length();
        return new RoadVector(horizontal.x(), vertical, horizontal.z());
    }

    private RoadVector horizontalDerivativeAt(double parameter) {
        RoadVector first = horizontalPoint(start).vectorTo(horizontalControl)
                .scale(2.0 * (1.0 - parameter));
        RoadVector second = horizontalControl.vectorTo(horizontalPoint(end))
                .scale(2.0 * parameter);
        return first.add(second);
    }

    private double elevationAt(double parameter) {
        double progress = horizontalSamples.distanceAtParameter(parameter)
                / horizontalSamples.length();
        return start.y() + (end.y() - start.y()) * progress;
    }

    private static RoadPoint horizontalPoint(RoadPoint point) {
        return new RoadPoint(point.x(), 0.0, point.z());
    }

    static RoadPoint weightedPoint(
            double firstWeight,
            RoadPoint first,
            double secondWeight,
            RoadPoint second,
            double thirdWeight,
            RoadPoint third
    ) {
        return new RoadPoint(
                first.x() * firstWeight
                        + second.x() * secondWeight
                        + third.x() * thirdWeight,
                first.y() * firstWeight
                        + second.y() * secondWeight
                        + third.y() * thirdWeight,
                first.z() * firstWeight
                        + second.z() * secondWeight
                        + third.z() * thirdWeight
        );
    }

    static void requireDistinctHorizontalEndpoints(
            RoadPoint start,
            RoadPoint end
    ) {
        RoadVector displacement = start.vectorTo(end);
        if (displacement.length() == 0.0) {
            throw new IllegalArgumentException(
                    "start and end must define a non-zero curve"
            );
        }
        if (displacement.horizontalLength() == 0.0) {
            throw new IllegalArgumentException(
                    "a road curve must have horizontally distinct endpoints"
            );
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof QuadraticBezierGeometry geometry)) {
            return false;
        }
        return start.equals(geometry.start)
                && control.equals(geometry.control)
                && end.equals(geometry.end);
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, control, end);
    }

    @Override
    public String toString() {
        return "QuadraticBezierGeometry[controlPoints="
                + controlPoints() + "]";
    }
}
