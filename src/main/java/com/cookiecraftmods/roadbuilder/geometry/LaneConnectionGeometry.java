package com.cookiecraftmods.roadbuilder.geometry;

public final class LaneConnectionGeometry {
    private LaneConnectionGeometry() { }

    public static ReferenceGeometry align(ReferenceGeometry geometry,
                                          RoadVector entry, RoadVector exit) {
        if (geometry == null || entry == null && exit == null) {
            return geometry;
        }
        RoadPoint first;
        RoadPoint second;
        if (geometry instanceof CubicBezierGeometry cubic) {
            first = cubic.controlPoints().get(1);
            second = cubic.controlPoints().get(2);
        } else if (geometry instanceof QuadraticBezierGeometry quadratic) {
            RoadPoint control = quadratic.controlPoints().get(1);
            first = geometry.start().add(geometry.start().vectorTo(control).scale(2.0 / 3.0));
            second = geometry.end().add(geometry.end().vectorTo(control).scale(2.0 / 3.0));
        } else {
            // A straight tool must remain straight; its endpoint still attaches to the lane.
            return geometry;
        }
        if (entry != null) {
            first = geometry.start().add(entry.normalize().scale(
                    Math.max(0.05, geometry.start().distanceTo(first))));
        }
        if (exit != null) {
            second = geometry.end().subtract(exit.normalize().scale(
                    Math.max(0.05, geometry.end().distanceTo(second))));
        }
        return new CubicBezierGeometry(geometry.start(), first, second, geometry.end());
    }
}
