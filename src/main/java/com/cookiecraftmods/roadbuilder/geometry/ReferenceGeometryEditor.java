package com.cookiecraftmods.roadbuilder.geometry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ReferenceGeometryEditor {
    private static final double MINIMUM_SLICE_FRACTION = 1.0e-9;

    private ReferenceGeometryEditor() {
    }

    public static List<RoadPoint> editableHandles(ReferenceGeometry geometry) {
        Objects.requireNonNull(geometry, "geometry");
        if (geometry instanceof QuadraticBezierGeometry quadratic) {
            return List.of(quadratic.control());
        }
        if (geometry instanceof CubicBezierGeometry cubic) {
            return List.of(cubic.startControl(), cubic.endControl());
        }
        if (geometry instanceof MultiCurveGeometry multi) {
            return multi.anchors().subList(1, multi.anchors().size() - 1);
        }
        if (geometry instanceof FreehandGeometry freehand) {
            List<RoadPoint> anchors = freehand.anchors();
            return anchors.subList(1, anchors.size() - 1);
        }
        return List.of();
    }

    public static ReferenceGeometry moveHandle(
            ReferenceGeometry geometry,
            int handleIndex,
            RoadPoint position
    ) {
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(position, "position");
        if (handleIndex < 0) {
            throw new IllegalArgumentException("handleIndex must be non-negative");
        }
        if (geometry instanceof QuadraticBezierGeometry quadratic) {
            requireHandleIndex(handleIndex, 1);
            return new QuadraticBezierGeometry(
                    quadratic.start(),
                    position,
                    quadratic.end()
            );
        }
        if (geometry instanceof CubicBezierGeometry cubic) {
            requireHandleIndex(handleIndex, 2);
            return new CubicBezierGeometry(
                    cubic.start(),
                    handleIndex == 0 ? position : cubic.startControl(),
                    handleIndex == 1 ? position : cubic.endControl(),
                    cubic.end()
            );
        }
        if (geometry instanceof MultiCurveGeometry multi) {
            List<RoadPoint> anchors = new ArrayList<>(multi.anchors());
            requireHandleIndex(handleIndex, anchors.size() - 2);
            anchors.set(handleIndex + 1, position);
            return new MultiCurveGeometry(anchors);
        }
        if (geometry instanceof FreehandGeometry freehand) {
            List<RoadPoint> anchors = new ArrayList<>(freehand.anchors());
            requireHandleIndex(handleIndex, anchors.size() - 2);
            anchors.set(handleIndex + 1, position);
            return new FreehandGeometry(anchors, 0.0);
        }
        throw new IllegalArgumentException(
                "geometry has no editable curve handles: " + geometry.getClass().getSimpleName()
        );
    }

        public static RoadPoint snapTangentHandle(
            RoadPoint endpoint,
            RoadPoint rawHandle,
            double incrementRadians
    ) {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(rawHandle, "rawHandle");
        if (!Double.isFinite(incrementRadians) || incrementRadians <= 0.0
                || incrementRadians > Math.PI * 2.0) {
            throw new IllegalArgumentException(
                    "incrementRadians must be finite and in (0, 2pi]"
            );
        }
        double deltaX = rawHandle.x() - endpoint.x();
        double deltaZ = rawHandle.z() - endpoint.z();
        double length = Math.hypot(deltaX, deltaZ);
        if (length == 0.0) {
            return rawHandle;
        }
        double angle = Math.atan2(deltaZ, deltaX);
        double snapped = Math.rint(angle / incrementRadians) * incrementRadians;
        return new RoadPoint(
                endpoint.x() + Math.cos(snapped) * length,
                rawHandle.y(),
                endpoint.z() + Math.sin(snapped) * length
        );
    }

    public static List<ReferenceGeometry> split(
            ReferenceGeometry geometry,
            List<Double> fractions,
            List<RoadPoint> splitPoints
    ) {
        Objects.requireNonNull(geometry, "geometry");
        fractions = List.copyOf(Objects.requireNonNull(fractions, "fractions"));
        splitPoints = List.copyOf(Objects.requireNonNull(splitPoints, "splitPoints"));
        if (fractions.size() != splitPoints.size()) {
            throw new IllegalArgumentException(
                    "fractions and splitPoints must have the same size"
            );
        }
        double previous = 0.0;
        for (double fraction : fractions) {
            if (!Double.isFinite(fraction)
                    || fraction <= previous + MINIMUM_SLICE_FRACTION
                    || fraction >= 1.0 - MINIMUM_SLICE_FRACTION) {
                throw new IllegalArgumentException(
                        "split fractions must be finite, strictly increasing, and interior"
                );
            }
            previous = fraction;
        }

        List<ReferenceGeometry> result = new ArrayList<>(fractions.size() + 1);
        RoadPoint start = geometry.start();
        double startFraction = 0.0;
        for (int index = 0; index <= fractions.size(); index++) {
            double endFraction = index == fractions.size()
                    ? 1.0
                    : fractions.get(index);
            RoadPoint end = index == fractions.size()
                    ? geometry.end()
                    : splitPoints.get(index);
            result.add(slice(
                    geometry,
                    startFraction,
                    endFraction,
                    start,
                    end
            ));
            startFraction = endFraction;
            start = end;
        }
        return List.copyOf(result);
    }

    public static ReferenceGeometry retargetEndpoints(
            ReferenceGeometry geometry,
            RoadPoint start,
            RoadPoint end
    ) {
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (geometry.start().equals(start) && geometry.end().equals(end)) {
            return geometry;
        }
        return slice(geometry, 0.0, 1.0, start, end);
    }

    private static ReferenceGeometry slice(
            ReferenceGeometry geometry,
            double startFraction,
            double endFraction,
            RoadPoint start,
            RoadPoint end
    ) {
        if (geometry instanceof StraightGeometry) {
            return new StraightGeometry(start, end);
        }
        double sourceLength = geometry.length();
        PathSample startSample = geometry.sample(sourceLength * startFraction);
        PathSample endSample = geometry.sample(sourceLength * endFraction);
        double spanLength = Math.max(
                start.distanceTo(end),
                sourceLength * (endFraction - startFraction)
        );
        double controlDistance = spanLength / 3.0;
        RoadPoint startControl = start.add(
                startSample.tangent().scale(controlDistance)
        );
        RoadPoint endControl = end.subtract(
                endSample.tangent().scale(controlDistance)
        );
        return new CubicBezierGeometry(
                start,
                startControl,
                endControl,
                end
        );
    }

    private static void requireHandleIndex(int index, int count) {
        if (index >= count) {
            throw new IllegalArgumentException(
                    "handleIndex must be between 0 and " + (count - 1)
            );
        }
    }
}
