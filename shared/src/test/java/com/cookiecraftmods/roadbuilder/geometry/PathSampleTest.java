package com.cookiecraftmods.roadbuilder.geometry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PathSampleTest {
    @Test
    void acceptsOnlyFiniteNonNegativeDistanceAndAUnitTangent() {
        RoadPoint position = new RoadPoint(1.0, 2.0, 3.0);
        RoadVector tangent = new RoadVector(1.0, 0.0, 0.0);

        assertAll(
                () -> assertEquals(0.0, new PathSample(0.0, position, tangent).distance()),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new PathSample(-1.0, position, tangent)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new PathSample(Double.NaN, position, tangent)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new PathSample(Double.POSITIVE_INFINITY, position, tangent)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new PathSample(0.0, position, new RoadVector(0.5, 0.0, 0.0))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new PathSample(0.0, position, new RoadVector(0.0, 0.0, 0.0))),
                () -> assertThrows(NullPointerException.class,
                        () -> new PathSample(0.0, null, tangent)),
                () -> assertThrows(NullPointerException.class,
                        () -> new PathSample(0.0, position, null))
        );
    }

    @Test
    void offsetsHorizontallyWhilePreservingDistanceAndTangent() {
        RoadVector tangent = new RoadVector(0.6, 0.8, 0.0);
        PathSample sample = new PathSample(12.5, new RoadPoint(4.0, 8.0, 2.0), tangent);

        PathSample left = sample.offsetLaterally(3.0);
        PathSample right = sample.offsetLaterally(-2.0);

        assertAll(
                () -> assertEquals(new RoadPoint(4.0, 8.0, -1.0), left.position()),
                () -> assertEquals(new RoadPoint(4.0, 8.0, 4.0), right.position()),
                () -> assertEquals(12.5, left.distance()),
                () -> assertEquals(tangent, left.tangent()),
                () -> assertEquals(8.0, left.position().y())
        );
    }

    @Test
    void validatesOffsetsAndAvoidsWorkForZero() {
        PathSample horizontal = new PathSample(
                0.0,
                new RoadPoint(0.0, 0.0, 0.0),
                new RoadVector(1.0, 0.0, 0.0)
        );
        PathSample vertical = new PathSample(
                0.0,
                new RoadPoint(0.0, 0.0, 0.0),
                new RoadVector(0.0, 1.0, 0.0)
        );

        assertAll(
                () -> assertSame(horizontal, horizontal.offsetLaterally(0.0)),
                () -> assertSame(vertical, vertical.offsetLaterally(-0.0)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> horizontal.offsetLaterally(Double.NEGATIVE_INFINITY)),
                () -> assertThrows(IllegalStateException.class,
                        () -> vertical.offsetLaterally(1.0))
        );
    }
}
