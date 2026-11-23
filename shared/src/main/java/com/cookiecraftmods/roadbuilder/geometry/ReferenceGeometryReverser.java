package com.cookiecraftmods.roadbuilder.geometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ReferenceGeometryReverser {
    private ReferenceGeometryReverser() {
    }

    public static ReferenceGeometry reverse(ReferenceGeometry geometry) {
        Objects.requireNonNull(geometry, "geometry");
        if (geometry instanceof StraightGeometry straight) {
            return new StraightGeometry(straight.end(), straight.start());
        }
        if (geometry instanceof QuadraticBezierGeometry quadratic) {
            return new QuadraticBezierGeometry(
                    quadratic.end(),
                    quadratic.control(),
                    quadratic.start()
            );
        }
        if (geometry instanceof CubicBezierGeometry cubic) {
            return new CubicBezierGeometry(
                    cubic.end(),
                    cubic.endControl(),
                    cubic.startControl(),
                    cubic.start()
            );
        }
        if (geometry instanceof MultiCurveGeometry multi) {
            if(multi.hasExplicitSpans()) return MultiCurveGeometry.fromControlPoints(reversed(multi.controlPoints()));
            return new MultiCurveGeometry(reversed(multi.anchors()));
        }
        if (geometry instanceof FreehandGeometry freehand) {
            return new FreehandGeometry(reversed(freehand.sourcePoints()));
        }
        throw new IllegalArgumentException(
                "unsupported reference geometry "
                        + geometry.getClass().getName()
        );
    }

    private static List<RoadPoint> reversed(List<RoadPoint> points) {
        ArrayList<RoadPoint> result = new ArrayList<>(points);
        Collections.reverse(result);
        return List.copyOf(result);
    }
}
