package com.cookiecraftmods.roadbuilder.geometry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StraightGeometryTest {
    private static final double TOLERANCE = 1.0e-12;

    @Test
    void derivesFiniteLengthAndUnitTangent() {
        StraightGeometry geometry = new StraightGeometry(
                new RoadPoint(1.0, 2.0, 3.0),
                new RoadPoint(4.0, 14.0, 7.0)
        );

        assertAll(
                () -> assertEquals(13.0, geometry.length(), TOLERANCE),
                () -> assertEquals(1.0, geometry.tangent().length(), TOLERANCE),
                () -> assertEquals(3.0 / 13.0, geometry.tangent().x(), TOLERANCE),
                () -> assertEquals(12.0 / 13.0, geometry.tangent().y(), TOLERANCE),
                () -> assertEquals(4.0 / 13.0, geometry.tangent().z(), TOLERANCE)
        );
    }

    @Test
    void samplesStartMiddleAndEndByThreeDimensionalDistance() {
        RoadPoint start = new RoadPoint(1.0, 2.0, 3.0);
        RoadPoint end = new RoadPoint(4.0, 14.0, 7.0);
        StraightGeometry geometry = new StraightGeometry(start, end);

        PathSample startSample = geometry.sample(0.0);
        PathSample middleSample = geometry.sample(6.5);
        PathSample endSample = geometry.sample(geometry.length());

        assertAll(
                () -> assertSame(start, startSample.position()),
                () -> assertEquals(new RoadPoint(2.5, 8.0, 5.0), middleSample.position()),
                () -> assertEquals(6.5, middleSample.distance()),
                () -> assertEquals(geometry.tangent(), middleSample.tangent()),
                () -> assertSame(end, endSample.position()),
                () -> assertEquals(13.0, endSample.distance(), TOLERANCE)
        );
    }

    @Test
    void appliesPositiveLeftAndNegativeRightOffsetsInTheHorizontalPlane() {
        StraightGeometry eastAndUp = new StraightGeometry(
                new RoadPoint(0.0, 0.0, 0.0),
                new RoadPoint(10.0, 10.0, 0.0)
        );
        double halfway = eastAndUp.length() / 2.0;

        PathSample left = eastAndUp.sample(halfway, 2.0);
        PathSample right = eastAndUp.sample(halfway, -3.0);

        assertAll(
                () -> assertEquals(5.0, left.position().x(), TOLERANCE),
                () -> assertEquals(5.0, left.position().y(), TOLERANCE),
                () -> assertEquals(-2.0, left.position().z(), TOLERANCE),
                () -> assertEquals(3.0, right.position().z(), TOLERANCE),
                () -> assertEquals(halfway, left.distance()),
                () -> assertEquals(eastAndUp.tangent(), left.tangent())
        );
    }

    @Test
    void headingTowardPositiveZOffsetsLeftTowardPositiveX() {
        StraightGeometry south = new StraightGeometry(
                new RoadPoint(0.0, 4.0, 0.0),
                new RoadPoint(0.0, 4.0, 10.0)
        );

        assertEquals(new RoadPoint(2.0, 4.0, 5.0), south.sample(5.0, 2.0).position());
    }

    @Test
    void rejectsInvalidSampleDistancesAndOffsets() {
        StraightGeometry geometry = new StraightGeometry(
                new RoadPoint(0.0, 0.0, 0.0),
                new RoadPoint(10.0, 0.0, 0.0)
        );

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> geometry.sample(-0.001)),
                () -> assertThrows(IllegalArgumentException.class, () -> geometry.sample(10.001)),
                () -> assertThrows(IllegalArgumentException.class, () -> geometry.sample(Double.NaN)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> geometry.sample(Double.POSITIVE_INFINITY)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> geometry.sample(5.0, Double.NEGATIVE_INFINITY))
        );
    }

    @Test
    void rejectsNullDegenerateVerticalAndUnboundedSegments() {
        RoadPoint origin = new RoadPoint(0.0, 0.0, 0.0);

        assertAll(
                () -> assertThrows(NullPointerException.class,
                        () -> new StraightGeometry(null, origin)),
                () -> assertThrows(NullPointerException.class,
                        () -> new StraightGeometry(origin, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new StraightGeometry(origin, origin)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new StraightGeometry(origin, new RoadPoint(0.0, 5.0, 0.0))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new StraightGeometry(
                                new RoadPoint(-Double.MAX_VALUE, 0.0, 0.0),
                                new RoadPoint(Double.MAX_VALUE, 0.0, 0.0)))
        );
    }

    @Test
    void hasValueSemanticsBasedOnItsEndpoints() {
        RoadPoint start = new RoadPoint(1.0, 2.0, 3.0);
        RoadPoint end = new RoadPoint(4.0, 5.0, 6.0);
        StraightGeometry first = new StraightGeometry(start, end);
        StraightGeometry same = new StraightGeometry(start, end);
        StraightGeometry reversed = new StraightGeometry(end, start);

        assertAll(
                () -> assertEquals(first, same),
                () -> assertEquals(first.hashCode(), same.hashCode()),
                () -> assertNotEquals(first, reversed),
                () -> assertEquals("StraightGeometry[start=" + start + ", end=" + end + "]",
                        first.toString())
        );
    }
}
