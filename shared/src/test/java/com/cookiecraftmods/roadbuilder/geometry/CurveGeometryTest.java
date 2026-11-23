package com.cookiecraftmods.roadbuilder.geometry;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurveGeometryTest {
    private static final double EPSILON = 1.0e-9;

    @Test
    void quadraticCurveSamplesByArcLengthAndRetainsCanonicalControls() {
        RoadPoint start = new RoadPoint(0.0, 2.0, 0.0);
        RoadPoint control = new RoadPoint(5.0, 4.0, 5.0);
        RoadPoint end = new RoadPoint(10.0, 2.0, 0.0);
        QuadraticBezierGeometry curve = new QuadraticBezierGeometry(
                start,
                control,
                end
        );

        PathSample middle = curve.sample(curve.length() / 2.0);

        RoadPoint normalizedControl = new RoadPoint(5.0, 2.0, 5.0);
        assertEquals(
                List.of(start, normalizedControl, end),
                curve.controlPoints()
        );
        assertEquals(start, curve.sample(0.0).position());
        assertEquals(end, curve.sample(curve.length()).position());
        assertEquals(5.0, middle.position().x(), 1.0e-6);
        assertEquals(2.0, middle.position().y(), 1.0e-6);
        assertEquals(2.5, middle.position().z(), 1.0e-6);
        assertEquals(1.0, middle.tangent().length(), EPSILON);
        assertTrue(curve.length() > start.distanceTo(end));
        assertTrue(curve.adaptiveSampleCount() > 2);
    }

    @Test
    void quadraticControlElevationCannotChangeTheConstantNodeToNodeGrade() {
        RoadPoint start = new RoadPoint(0.0, 2.0, 0.0);
        RoadPoint end = new RoadPoint(12.0, 8.0, 0.0);
        QuadraticBezierGeometry lowControl = new QuadraticBezierGeometry(
                start,
                new RoadPoint(4.0, -100.0, 7.0),
                end
        );
        QuadraticBezierGeometry highControl = new QuadraticBezierGeometry(
                start,
                new RoadPoint(4.0, 100.0, 7.0),
                end
        );

        assertEquals(lowControl, highControl);
        double expectedGrade = (end.y() - start.y())
                / lowControl.horizontalLength();
        for (int index = 0; index <= 8; index++) {
            PathSample sample = lowControl.sample(
                    lowControl.length() * index / 8.0
            );
            double grade = sample.tangent().y()
                    / sample.tangent().horizontalLength();
            assertEquals(expectedGrade, grade, 1.0e-9);
        }
    }

    @Test
    void cubicCurveAdaptiveSamplingCapturesAnSCurveWhoseMidpointIsOnChord() {
        CubicBezierGeometry curve = new CubicBezierGeometry(
                new RoadPoint(0.0, 0.0, 0.0),
                new RoadPoint(0.0, 0.0, 10.0),
                new RoadPoint(10.0, 0.0, -10.0),
                new RoadPoint(10.0, 0.0, 0.0)
        );

        assertTrue(curve.adaptiveSampleCount() > 20);
        assertTrue(curve.length() > 10.0);
        assertEquals(
                new RoadPoint(10.0, 0.0, 0.0),
                curve.sample(curve.length()).position()
        );
        assertEquals(1.0, curve.sample(curve.length() * 0.25).tangent().length(), EPSILON);
    }

    @Test
    void complexCurveInterpolatesEveryAnchorWithContinuousJoinTangents() {
        List<RoadPoint> anchors = List.of(
                new RoadPoint(0.0, 0.0, 0.0),
                new RoadPoint(5.0, 1.0, 4.0),
                new RoadPoint(11.0, 2.0, -2.0),
                new RoadPoint(18.0, 2.0, 3.0)
        );
        MultiCurveGeometry curve = new MultiCurveGeometry(anchors);

        double firstJoin = curve.spans().get(0).length();
        PathSample before = curve.sample(firstJoin - 1.0e-6);
        PathSample join = curve.sample(firstJoin);
        PathSample after = curve.sample(firstJoin + 1.0e-6);

        assertEquals(anchors, curve.anchors());
        assertEquals(anchors.get(1).x(), join.position().x(), 1.0e-8);
        assertEquals(anchors.get(1).y(), join.position().y(), 1.0e-8);
        assertEquals(anchors.get(1).z(), join.position().z(), 1.0e-8);
        assertTrue(before.tangent().dot(after.tangent()) > 0.999);
        assertTrue(curve.adaptiveSampleCount() > anchors.size());
    }

    @Test
    void freehandStrokeFiltersNoiseAndSimplifiesDeterministically() {
        List<RoadPoint> stroke = List.of(
                new RoadPoint(0.0, 0.0, 0.0),
                new RoadPoint(0.01, 0.0, 0.01),
                new RoadPoint(2.0, 0.0, 0.03),
                new RoadPoint(4.0, 0.0, -0.02),
                new RoadPoint(6.0, 0.0, 2.0),
                new RoadPoint(8.0, 0.0, 4.0)
        );

        FreehandGeometry first = new FreehandGeometry(stroke);
        FreehandGeometry same = new FreehandGeometry(stroke);

        assertEquals(first, same);
        assertEquals(stroke.get(0), first.anchors().get(0));
        assertEquals(stroke.get(stroke.size() - 1),
                first.anchors().get(first.anchors().size() - 1));
        assertTrue(first.sourcePoints().size() < stroke.size());
        assertTrue(first.anchors().size() < first.sourcePoints().size());
        assertTrue(first.anchors().size() >= 3);
        assertTrue(first.anchors().stream().anyMatch(point -> point.z() > 1.0));
        assertEquals(stroke.get(0), first.sample(0.0).position());
        assertEquals(
                stroke.get(stroke.size() - 1),
                first.sample(first.length()).position()
        );
    }

    @Test
    void curveValidationRejectsInvalidRoadDirectionsAndSamples() {
        RoadPoint origin = new RoadPoint(0.0, 0.0, 0.0);

        assertThrows(
                IllegalArgumentException.class,
                () -> new QuadraticBezierGeometry(
                        origin,
                        new RoadPoint(2.0, 0.0, 2.0),
                        origin
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new QuadraticBezierGeometry(
                        origin,
                        new RoadPoint(1.0, 2.0, 0.0),
                        new RoadPoint(0.0, 4.0, 0.0)
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new MultiCurveGeometry(List.of(origin, origin, origin))
        );

        QuadraticBezierGeometry valid = new QuadraticBezierGeometry(
                origin,
                new RoadPoint(2.0, 0.0, 2.0),
                new RoadPoint(4.0, 0.0, 0.0)
        );
        assertThrows(IllegalArgumentException.class, () -> valid.sample(-EPSILON));
        assertThrows(
                IllegalArgumentException.class,
                () -> valid.sample(valid.length() + EPSILON)
        );
        assertNotEquals(valid, new QuadraticBezierGeometry(
                origin,
                new RoadPoint(2.0, 0.0, -2.0),
                new RoadPoint(4.0, 0.0, 0.0)
        ));
    }

    @Test
    void lateralOffsetsRemainHorizontalAndFollowTravelDirection() {
        QuadraticBezierGeometry curve = new QuadraticBezierGeometry(
                new RoadPoint(0.0, 0.0, 0.0),
                new RoadPoint(5.0, 2.0, 5.0),
                new RoadPoint(10.0, 4.0, 0.0)
        );
        double distance = curve.length() * 0.4;
        PathSample center = curve.sample(distance);
        PathSample left = curve.sample(distance, 2.0);
        RoadVector offset = center.position().vectorTo(left.position());

        assertEquals(2.0, offset.length(), EPSILON);
        assertEquals(0.0, offset.y(), EPSILON);
        assertEquals(0.0, offset.dot(center.tangent()), EPSILON);
        assertEquals(center.tangent(), left.tangent());
        assertEquals(distance, left.distance(), EPSILON);
    }
}
