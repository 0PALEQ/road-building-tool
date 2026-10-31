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

    public double parameterAtDistance(double distance) {
        if(distance<0 || distance>length() || !Double.isFinite(distance)) throw new IllegalArgumentException("distance outside curve");
        double low=0,high=1;
        for(int i=0;i<45;i++) { double middle=(low+high)/2; if(samples.distanceAtParameter(middle)<distance) low=middle; else high=middle; }
        return distance==0 ? 0 : distance==length() ? 1 : (low+high)/2;
    }

    public CubicBezierGeometry slice(double from,double to) {
        if(!(from>=0 && to<=1 && from<to)) throw new IllegalArgumentException("invalid curve slice");
        return new CubicBezierGeometry(positionAt(from),positionAt(from).add(derivativeAt(from).scale((to-from)/3)),
                positionAt(to).subtract(derivativeAt(to).scale((to-from)/3)),positionAt(to));
    }

    @Override
    public PathSample sample(double distance) {
        return samples.sample(distance);
    }

    public RoadPoint positionAt(double parameter) {
        requireParameter(parameter);
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

    public RoadVector derivativeAt(double parameter) {
        requireParameter(parameter);
        double inverse = 1.0 - parameter;
        return start.vectorTo(startControl)
                .scale(3.0 * inverse * inverse)
                .add(startControl.vectorTo(endControl)
                        .scale(6.0 * inverse * parameter))
                .add(endControl.vectorTo(end)
                        .scale(3.0 * parameter * parameter));
    }

    public RoadVector secondDerivativeAt(double parameter) {
        requireParameter(parameter);
        return startControl.vectorTo(endControl).subtract(start.vectorTo(startControl))
                .scale(6 * (1 - parameter))
                .add(endControl.vectorTo(end).subtract(startControl.vectorTo(endControl))
                        .scale(6 * parameter));
    }

    /** Spatial curvature; undefined stationary points return positive infinity. */
    public double curvatureAt(double parameter) {
        RoadVector v = derivativeAt(parameter);
        RoadVector a = secondDerivativeAt(parameter);
        if (v.length() < 1e-10) return Double.POSITIVE_INFINITY;
        return Math.hypot(Math.hypot(v.y() * a.z() - v.z() * a.y(),
                v.z() * a.x() - v.x() * a.z()), v.x() * a.y() - v.y() * a.x())
                / Math.pow(v.length(), 3);
    }

    public double horizontalCurvatureAt(double parameter) {
        RoadVector v = derivativeAt(parameter);
        RoadVector a = secondDerivativeAt(parameter);
        if (v.horizontalLength() < 1e-10) return Double.POSITIVE_INFINITY;
        return Math.abs(v.x() * a.z() - v.z() * a.x())
                / Math.pow(v.horizontalLength(), 3);
    }

    private static void requireParameter(double parameter) {
        if (!Double.isFinite(parameter) || parameter < 0 || parameter > 1) {
            throw new IllegalArgumentException("parameter must be between 0 and 1");
        }
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
