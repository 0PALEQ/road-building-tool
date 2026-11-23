package com.cookiecraftmods.roadbuilder.geometry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoadVectorTest {
    private static final double TOLERANCE = 1.0e-12;

    @Test
    void rejectsNonFiniteComponents() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RoadVector(Double.NaN, 0.0, 0.0)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RoadVector(0.0, Double.POSITIVE_INFINITY, 0.0)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RoadVector(0.0, 0.0, Double.NEGATIVE_INFINITY))
        );
    }

    @Test
    void calculatesLengthsAndNormalizes() {
        RoadVector vector = new RoadVector(3.0, 12.0, 4.0);
        RoadVector normalized = vector.normalize();

        assertAll(
                () -> assertEquals(13.0, vector.length(), TOLERANCE),
                () -> assertEquals(5.0, vector.horizontalLength()),
                () -> assertEquals(1.0, normalized.length(), TOLERANCE),
                () -> assertEquals(3.0 / 13.0, normalized.x(), TOLERANCE),
                () -> assertEquals(12.0 / 13.0, normalized.y(), TOLERANCE),
                () -> assertEquals(4.0 / 13.0, normalized.z(), TOLERANCE)
        );
    }

    @Test
    void supportsImmutableArithmeticAndDotProducts() {
        RoadVector first = new RoadVector(1.0, 2.0, 3.0);
        RoadVector second = new RoadVector(4.0, -1.0, 2.0);

        assertAll(
                () -> assertEquals(new RoadVector(5.0, 1.0, 5.0), first.add(second)),
                () -> assertEquals(new RoadVector(-3.0, 3.0, 1.0), first.subtract(second)),
                () -> assertEquals(new RoadVector(2.5, 5.0, 7.5), first.scale(2.5)),
                () -> assertEquals(8.0, first.dot(second)),
                () -> assertEquals(new RoadVector(1.0, 2.0, 3.0), first)
        );
    }

    @Test
    void calculatesMinecraftPositiveLeftHorizontalDirection() {
        RoadVector slopedHeading = new RoadVector(3.0, 12.0, 4.0);
        RoadVector left = slopedHeading.horizontalLeftUnit();

        assertAll(
                () -> assertEquals(0.8, left.x(), TOLERANCE),
                () -> assertEquals(0.0, left.y()),
                () -> assertEquals(-0.6, left.z(), TOLERANCE),
                () -> assertEquals(1.0, left.length(), TOLERANCE),
                () -> assertEquals(new RoadVector(0.0, 0.0, -1.0),
                        new RoadVector(1.0, 0.0, 0.0).horizontalLeftUnit()),
                () -> assertEquals(new RoadVector(1.0, 0.0, 0.0),
                        new RoadVector(0.0, 0.0, 1.0).horizontalLeftUnit())
        );
    }

    @Test
    void rejectsDegenerateOrUnboundedOperations() {
        assertAll(
                () -> assertThrows(IllegalStateException.class,
                        () -> new RoadVector(0.0, 0.0, 0.0).normalize()),
                () -> assertThrows(IllegalStateException.class,
                        () -> new RoadVector(0.0, 2.0, 0.0).horizontalLeftUnit()),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RoadVector(1.0, 0.0, 0.0).scale(Double.NaN)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RoadVector(Double.MAX_VALUE, 0.0, 0.0).scale(2.0)),
                () -> assertThrows(ArithmeticException.class,
                        () -> new RoadVector(Double.MAX_VALUE, 0.0, 0.0)
                                .dot(new RoadVector(2.0, 0.0, 0.0))),
                () -> assertThrows(NullPointerException.class,
                        () -> new RoadVector(1.0, 0.0, 0.0).add(null))
        );
    }
}
