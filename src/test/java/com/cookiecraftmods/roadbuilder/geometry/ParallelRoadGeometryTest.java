package com.cookiecraftmods.roadbuilder.geometry;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ParallelRoadGeometryTest {
    @Test
    void gentleFreehandParallelBendsPassConstructionValidation() {
        var road = FreeformGeometry.createBalanced(new RoadPoint(0, 64, 0),
                new RoadPoint(80, 64, 40), new RoadVector(1, 0, 0));
        for (double offset : new double[]{-16, 16}) {
            var parallel = ParallelRoadGeometry.create(road, offset);
            assertNull(com.cookiecraftmods.roadbuilder.placement.RoadCurveValidation.validate(
                    ReferenceGeometryReverser.reverse(parallel), 4), "offset " + offset);
        }
    }

    @Test
    void bendsKeepTheConfiguredNormalDistanceAtBothEndsAndThroughoutThePath() {
        RoadPoint a = new RoadPoint(0, 64, 0);
        RoadPoint b = new RoadPoint(30, 66, 0);
        RoadPoint c = new RoadPoint(30, 68, 30);
        RoadPoint d = new RoadPoint(60, 70, 30);
        for (ReferenceGeometry road : List.of(new StraightGeometry(a, d),
                new QuadraticBezierGeometry(a, b, d), new CubicBezierGeometry(a, b, c, d),
                new MultiCurveGeometry(List.of(a, new RoadPoint(30, 67, 10), d)),
                new FreehandGeometry(List.of(a, new RoadPoint(30, 67, 10), d)))) {
            for (double offset : new double[]{-8, 8}) {
                ReferenceGeometry copy = ParallelRoadGeometry.create(road, offset);
                assertEquals(road.sample(0).offsetLaterally(offset).position(), copy.start());
                assertEquals(road.sample(road.length()).offsetLaterally(offset).position(), copy.end());
                for (int i = 0; i <= 40; i++) {
                    RoadPoint expected = road.sample(road.length() * i / 40).offsetLaterally(offset).position();
                    double nearest = Double.POSITIVE_INFINITY;
                    for (int j = 0; j <= 3000; j++) {
                        nearest = Math.min(nearest, expected.distanceTo(copy.sample(copy.length() * j / 3000).position()));
                    }
                    assertTrue(nearest < 0.08, road.getClass().getSimpleName() + " offset error: " + nearest);
                }
                assertTrue(road.sample(0).tangent().horizontalLeftUnit().dot(
                        copy.sample(0).tangent().horizontalLeftUnit()) > 0.999999);
                assertTrue(road.sample(road.length()).tangent().horizontalLeftUnit().dot(
                        copy.sample(copy.length()).tangent().horizontalLeftUnit()) > 0.999999);
            }
        }
    }

    @Test
    void roundaboutsAreConcentricInsteadOfTranslated() {
        RoundaboutGeometry ring = new RoundaboutGeometry(new RoadPoint(0, 64, 0), new RoadPoint(30, 64, 0));
        for (double offset : new double[]{-8, 8}) {
            RoundaboutGeometry copy = assertInstanceOf(RoundaboutGeometry.class, ParallelRoadGeometry.create(ring, offset));
            assertEquals(ring.center(), copy.center());
            assertEquals(ring.radius() + offset, copy.radius(), 1e-9);
        }
        assertThrows(IllegalArgumentException.class, () -> ParallelRoadGeometry.create(ring, -32));
    }

    @Test
    void rejectsOffsetsThatFoldThroughAnInsideBend() {
        var bend = new QuadraticBezierGeometry(new RoadPoint(0, 64, 0),
                new RoadPoint(20, 64, 0), new RoadPoint(20, 64, 20));
        assertThrows(IllegalArgumentException.class, () -> ParallelRoadGeometry.create(bend, -40));
    }

    @Test
    void rejectsInvalidOffsets() {
        ReferenceGeometry road = new StraightGeometry(new RoadPoint(0, 0, 0), new RoadPoint(40, 0, 0));
        for (double offset : new double[]{0, 0.5, 257, -257, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> ParallelRoadGeometry.create(road, offset));
        }
    }
}
