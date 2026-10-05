package com.cookiecraftmods.roadbuilder.geometry;

import java.util.List;
import java.util.Objects;

public final class CubicBezierGeometry implements ReferenceGeometry {
    private final RoadPoint start;
    private final RoadPoint startControl;
    private final RoadPoint endControl;
    private final RoadPoint end;
    private final AdaptiveCurveSampler.ArcLengthTable samples;

    public CubicBezierGeometry(
            RoadPoint start,
            RoadPoint startControl,
            RoadPoint endControl,
            RoadPoint end
    ) {
        this.start = Objects.requireNonNull(start, "start");
        this.startControl = Objects.requireNonNull(
                startControl,
                "startControl"
        );
        this.endControl = Objects.requireNonNull(endControl, "endControl");
        this.end = Objects.requireNonNull(end, "end");
        QuadraticBezierGeometry.requireDistinctHorizontalEndpoints(start, end);
        this.samples = AdaptiveCurveSampler.sample(
                this::positionAt,
                this::derivativeAt
        );
    }

    public RoadPoint start() {
        return start;
    }

    public RoadPoint startControl() {
        return startControl;
    }

    public RoadPoint endControl() {
        return endControl;
    }

    public RoadPoint end() {
        return end;
    }

    public List<RoadPoint> controlPoints() {
        return List.of(start, startControl, endControl, end);
    }

    @Override
    public double length() {
        return samples.length();
    }

    public int adaptiveSampleCount() {
        return samples.sampleCount();
    }

    @Override
    public PathSample sample(double distance) {
        return samples.sample(distance);
    }

    private RoadPoint positionAt(double parameter) {
        double inverse = 1.0 - parameter;
        double startWeight = inverse * inverse * inverse;
        double startControlWeight = 3.0 * inverse * inverse * parameter;
        double endControlWeight = 3.0 * inverse * parameter * parameter;
        double endWeight = parameter * parameter * parameter;
        return new RoadPoint(
                start.x() * startWeight
                        + startControl.x() * startControlWeight
                        + endControl.x() * endControlWeight
                        + end.x() * endWeight,
                start.y() * startWeight
                        + startControl.y() * startControlWeight
                        + endControl.y() * endControlWeight
                        + end.y() * endWeight,
                start.z() * startWeight
                        + startControl.z() * startControlWeight
                        + endControl.z() * endControlWeight
                        + end.z() * endWeight
        );
    }

    private RoadVector derivativeAt(double parameter) {
        double inverse = 1.0 - parameter;
        return start.vectorTo(startControl)
                .scale(3.0 * inverse * inverse)
                .add(startControl.vectorTo(endControl)
                        .scale(6.0 * inverse * parameter))
                .add(endControl.vectorTo(end)
                        .scale(3.0 * parameter * parameter));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CubicBezierGeometry geometry)) {
            return false;
        }
        return start.equals(geometry.start)
                && startControl.equals(geometry.startControl)
                && endControl.equals(geometry.endControl)
                && end.equals(geometry.end);
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, startControl, endControl, end);
    }

    @Override
    public String toString() {
        return "CubicBezierGeometry[controlPoints=" + controlPoints() + "]";
    }
}
