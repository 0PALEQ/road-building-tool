package com.cookiecraftmods.roadbuilder.geometry;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LaneConnectionGeometryTest {
    @Test
    void cubicKeepsEndpointAndHandleLengthWhileAligningAttachedEnd() {
        var start = new RoadPoint(0, 64, 3);
        var end = new RoadPoint(30, 66, 20);
        var original = new CubicBezierGeometry(start, new RoadPoint(8, 65, 10),
                new RoadPoint(20, 65, 18), end);
        var direction = new RoadVector(1, 0.1, 0).normalize();
        var aligned = (CubicBezierGeometry) LaneConnectionGeometry.align(original, direction, null);
        assertEquals(start, aligned.start());
        assertEquals(end, aligned.end());
        assertEquals(original.controlPoints().get(2), aligned.controlPoints().get(2));
        assertEquals(start.distanceTo(original.controlPoints().get(1)),
                start.distanceTo(aligned.controlPoints().get(1)), 1e-9);
        assertEquals(1, aligned.sample(0).tangent().dot(direction), 1e-9);
    }

    @Test
    void automaticFreehandArcMeetsSlopingLaneAtItsTravelAngle() {
        var original = FreeformGeometry.createBalanced(new RoadPoint(0, 64, 0),
                new RoadPoint(60, 70, 20), new RoadVector(1, 0, 0));
        var laneDirection = new RoadVector(1, 0.15, 0).normalize();
        var aligned = (CubicBezierGeometry) LaneConnectionGeometry.align(original, null, laneDirection);
        assertEquals(original.start(), aligned.start());
        assertEquals(original.end(), aligned.end());
        assertEquals(original.startControl(), aligned.startControl());
        assertEquals(1, aligned.sample(aligned.length()).tangent().dot(laneDirection), 1e-9);
        assertEquals(original.end().distanceTo(original.endControl()),
                aligned.end().distanceTo(aligned.endControl()), 1e-9);
        assertEquals(aligned.start().distanceTo(aligned.startControl()),
                aligned.end().distanceTo(aligned.endControl()), 1e-9);
        var both = (CubicBezierGeometry) LaneConnectionGeometry.align(original,
                laneDirection, laneDirection);
        assertEquals(1, both.sample(0).tangent().dot(laneDirection), 1e-9);
        assertEquals(1, both.sample(both.length()).tangent().dot(laneDirection), 1e-9);
        assertSame(original, LaneConnectionGeometry.align(original, null, null));
    }

    @Test
    void simpleCurveCanConstrainBothEndsIndependently() {
        var original = new QuadraticBezierGeometry(new RoadPoint(0, 64, 3),
                new RoadPoint(12, 64, 10), new RoadPoint(30, 64, 20));
        var direction = new RoadVector(1, 0, 0);
        var aligned = LaneConnectionGeometry.align(original, direction, direction);
        assertInstanceOf(CubicBezierGeometry.class, aligned);
        assertEquals(original.start(), aligned.start());
        assertEquals(original.end(), aligned.end());
        assertEquals(1, aligned.sample(0).tangent().dot(direction), 1e-9);
        assertEquals(1, aligned.sample(aligned.length()).tangent().dot(direction), 1e-9);
    }

    @Test
    void unattachedCurvesAndStraightRoadsKeepTheirGeometry() {
        var straight = new StraightGeometry(new RoadPoint(0, 64, 3), new RoadPoint(30, 64, 20));
        assertSame(straight, LaneConnectionGeometry.align(straight, new RoadVector(1, 0, 0), null));
        var curve = new QuadraticBezierGeometry(straight.start(), new RoadPoint(12, 64, 10), straight.end());
        assertSame(curve, LaneConnectionGeometry.align(curve, null, null));
    }
}
