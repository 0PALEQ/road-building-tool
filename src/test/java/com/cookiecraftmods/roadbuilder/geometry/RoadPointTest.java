package com.cookiecraftmods.roadbuilder.geometry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoadPointTest {
    private static final double TOLERANCE = 1.0e-12;

    @Test
    void rejectsNonFiniteCoordinates() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RoadPoint(Double.NaN, 0.0, 0.0)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RoadPoint(0.0, Double.POSITIVE_INFINITY, 0.0)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RoadPoint(0.0, 0.0, Double.NEGATIVE_INFINITY))
        );
    }

    @Test
    void translatesWithoutMutatingTheOriginalPoint() {
        RoadPoint original = new RoadPoint(1.0, 2.0, 3.0);
        RoadVector translation = new RoadVector(4.0, -2.0, 0.5);

        RoadPoint translated = original.add(translation);

        assertEquals(new RoadPoint(5.0, 0.0, 3.5), translated);
        assertEquals(new RoadPoint(1.0, 2.0, 3.0), original);
        assertNotSame(original, translated);
        assertEquals(original, translated.subtract(translation));
    }

    @Test
    void createsVectorsAndMeasuresThreeDimensionalDistance() {
        RoadPoint start = new RoadPoint(1.0, 2.0, 3.0);
        RoadPoint end = new RoadPoint(4.0, 14.0, 7.0);

        assertEquals(new RoadVector(3.0, 12.0, 4.0), start.vectorTo(end));
        assertEquals(13.0, start.distanceTo(end), TOLERANCE);
        assertEquals(13.0, end.distanceTo(start), TOLERANCE);
    }

    @Test
    void rejectsNullOperandsAndNonFiniteResults() {
        RoadPoint point = new RoadPoint(Double.MAX_VALUE, 0.0, 0.0);

        assertAll(
                () -> assertThrows(NullPointerException.class, () -> point.add(null)),
                () -> assertThrows(NullPointerException.class, () -> point.subtract(null)),
                () -> assertThrows(NullPointerException.class, () -> point.vectorTo(null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> point.add(new RoadVector(Double.MAX_VALUE, 0.0, 0.0)))
        );
    }
}
