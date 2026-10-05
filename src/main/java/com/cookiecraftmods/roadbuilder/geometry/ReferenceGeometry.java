package com.cookiecraftmods.roadbuilder.geometry;

public interface ReferenceGeometry {
        RoadPoint start();

        RoadPoint end();

        double length();

        PathSample sample(double distance);

        default PathSample sample(double distance, double lateralOffset) {
        if (!Double.isFinite(lateralOffset)) {
            throw new IllegalArgumentException("lateralOffset must be finite");
        }
        return sample(distance).offsetLaterally(lateralOffset);
    }
}
