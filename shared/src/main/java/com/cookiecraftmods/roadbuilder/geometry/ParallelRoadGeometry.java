package com.cookiecraftmods.roadbuilder.geometry;

import java.util.ArrayList;
import java.util.List;

/** A lateral offset measured along the path's local horizontal normal. */
public final class ParallelRoadGeometry {
    public static final double MAXIMUM_OFFSET = 256;
    private static final int MAX_POINTS = 64;
    private static final double CHORD_TOLERANCE = 0.025;
    private ParallelRoadGeometry() { }

    public static ReferenceGeometry create(ReferenceGeometry source, double offset) {
        if (!Double.isFinite(offset) || Math.abs(offset) > MAXIMUM_OFFSET || Math.abs(offset) < 1) {
            throw new IllegalArgumentException("parallel offset must be between 1 and 256 blocks on either side");
        }
        if (source instanceof StraightGeometry straight) {
            RoadVector shift = source.sample(0).tangent().horizontalLeftUnit().scale(offset);
            return new StraightGeometry(straight.start().add(shift), straight.end().add(shift));
        }
        if (source instanceof RoundaboutGeometry ring) {
            if (ring.radius() + offset < RoundaboutGeometry.MINIMUM_RADIUS) {
                throw new IllegalArgumentException("parallel ring is too small");
            }
            return new RoundaboutGeometry(ring.center(), point(source, 0, offset));
        }
        List<RoadPoint> points = new ArrayList<>();
        points.add(point(source, 0, offset));
        // Short endpoint intervals align the spline's endpoint tangents with
        // the offset path instead of the chord of the entire first/last bend.
        double tip = Math.min(0.001, source.length() / 1000);
        append(source, 0, tip, offset, points, 0);
        for (int i = 0; i < 8; i++) {
            append(source, tip + (source.length() - 2 * tip) * i / 8,
                    tip + (source.length() - 2 * tip) * (i + 1) / 8, offset, points, 0);
        }
        append(source, source.length() - tip, source.length(), offset, points, 0);
        return new MultiCurveGeometry(points);
    }

    private static void append(ReferenceGeometry source, double from, double to,
                               double offset, List<RoadPoint> points, int depth) {
        RoadPoint first = point(source, from, offset);
        RoadPoint last = point(source, to, offset);
        RoadVector chord = first.vectorTo(last);
        boolean subdivide = false;
        for (int i = 1; i <= 3; i++) {
            double fraction = i / 4.0;
            double distance = from + (to - from) * fraction;
            PathSample sample = source.sample(distance);
            // A regular parallel curve must keep travelling forward. An inside
            // offset beyond the local bend radius develops a cusp or reverses.
            if (chord.x() * sample.tangent().x() + chord.z() * sample.tangent().z() <= 1e-10) {
                throw new IllegalArgumentException("parallel offset folds inside this bend");
            }
            if (first.add(chord.scale(fraction)).distanceTo(point(source, distance, offset)) > CHORD_TOLERANCE) {
                subdivide = true;
            }
        }
        if (subdivide) {
            if (depth >= 16 || points.size() >= MAX_POINTS - 1) {
                throw new IllegalArgumentException("parallel curve exceeds the geometry detail limit");
            }
            double middle = (from + to) / 2;
            append(source, from, middle, offset, points, depth + 1);
            append(source, middle, to, offset, points, depth + 1);
        } else {
            if (points.size() >= MAX_POINTS) {
                throw new IllegalArgumentException("parallel curve exceeds the geometry detail limit");
            }
            points.add(last);
        }
    }

    private static RoadPoint point(ReferenceGeometry source, double distance, double offset) {
        return source.sample(distance).offsetLaterally(offset).position();
    }
}
