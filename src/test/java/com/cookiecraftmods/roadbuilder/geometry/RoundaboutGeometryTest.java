package com.cookiecraftmods.roadbuilder.geometry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoundaboutGeometryTest {
    @Test
    void buildsAClosedCircleFromFourTangentContinuousArcs() {
        RoadPoint center = new RoadPoint(0.5, 64.0, 0.5);
        RoundaboutGeometry geometry = new RoundaboutGeometry(
                center,
                new RoadPoint(10.5, 70.0, 0.5)
        );

        assertEquals(10.0, geometry.radius(), 1.0e-9);
        assertEquals(geometry.start(), geometry.end());
        assertEquals(4, geometry.boundaryPoints().size());
        assertEquals(4, geometry.spans().size());
        assertEquals(center.y(), geometry.radiusPoint().y());
        assertEquals(Math.PI * 20.0, geometry.length(), 0.02);

        for (int index = 0; index <= 256; index++) {
            RoadPoint sample = geometry.sample(
                    geometry.length() * index / 256.0
            ).position();
            assertEquals(10.0, horizontalDistance(center, sample), 0.003);
            assertEquals(center.y(), sample.y(), 1.0e-9);
        }

        for (int index = 0; index < geometry.spans().size(); index++) {
            CubicBezierGeometry current = geometry.spans().get(index);
            CubicBezierGeometry next = geometry.spans().get(
                    (index + 1) % geometry.spans().size()
            );
            RoadVector outgoing = current.sample(current.length()).tangent();
            RoadVector incoming = next.sample(0.0).tangent();
            assertTrue(outgoing.normalize().dot(incoming.normalize())
                    > 1.0 - 1.0e-12);
        }
    }

    @Test
    void rejectsRadiiThatCannotProduceAUsableRing() {
        RoadPoint center = new RoadPoint(0.5, 64.0, 0.5);

        assertThrows(
                IllegalArgumentException.class,
                () -> new RoundaboutGeometry(
                        center,
                        new RoadPoint(3.5, 64.0, 0.5)
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new RoundaboutGeometry(center, center)
        );
    }

    @Test
    void canTraverseTheSameCircleInEitherDirection() {
        RoundaboutGeometry geometry = new RoundaboutGeometry(
                new RoadPoint(0.5, 64.0, 0.5),
                new RoadPoint(12.5, 64.0, 0.5)
        );

        assertTrue(geometry.spans(true).get(0)
                .sample(0.0).tangent().z() > 0.0);
        assertTrue(geometry.spans(false).get(0)
                .sample(0.0).tangent().z() < 0.0);
        assertEquals(
                geometry.spans(true).get(0).start(),
                geometry.spans(false).get(0).start()
        );
    }

    private static double horizontalDistance(
            RoadPoint first,
            RoadPoint second
    ) {
        return Math.hypot(
                first.x() - second.x(),
                first.z() - second.z()
        );
    }
}
