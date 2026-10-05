package com.cookiecraftmods.roadbuilder.geometry;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReferenceGeometryReverserTest {
    @Test
    void reversesEveryPersistedGeometryWithoutChangingItsShape() {
        List<ReferenceGeometry> geometries = List.of(
                new StraightGeometry(point(0, 0), point(12, 4)),
                new QuadraticBezierGeometry(
                        point(0, 0), point(5, 8), point(12, 4)
                ),
                new CubicBezierGeometry(
                        point(0, 0), point(3, 7), point(9, 8), point(12, 4)
                ),
                new MultiCurveGeometry(List.of(
                        point(0, 0), point(4, 6), point(9, 7), point(12, 4)
                )),
                new FreehandGeometry(List.of(
                        point(0, 0), point(3, 3), point(7, 5), point(12, 4)
                ))
        );

        for (ReferenceGeometry original : geometries) {
            ReferenceGeometry reversed = ReferenceGeometryReverser.reverse(
                    original
            );
            assertEquals(original.end(), reversed.start());
            assertEquals(original.start(), reversed.end());
            assertEquals(original.length(), reversed.length(), 1.0e-6);
            for (int index = 0; index <= 20; index++) {
                double ratio = index / 20.0;
                PathSample expected = original.sample(
                        original.length() * (1.0 - ratio)
                );
                PathSample actual = reversed.sample(
                        reversed.length() * ratio
                );
                assertEquals(expected.position().x(), actual.position().x(),
                        1.0e-5);
                assertEquals(expected.position().y(), actual.position().y(),
                        1.0e-5);
                assertEquals(expected.position().z(), actual.position().z(),
                        1.0e-5);
                assertEquals(-expected.tangent().x(), actual.tangent().x(),
                        1.0e-5);
                assertEquals(-expected.tangent().y(), actual.tangent().y(),
                        1.0e-5);
                assertEquals(-expected.tangent().z(), actual.tangent().z(),
                        1.0e-5);
            }
        }
    }

    @Test
    void reversingTwiceRestoresControlData() {
        List<ReferenceGeometry> geometries = List.of(
                new StraightGeometry(point(0, 0), point(12, 4)),
                new QuadraticBezierGeometry(
                        point(0, 0), point(5, 8), point(12, 4)
                ),
                new CubicBezierGeometry(
                        point(0, 0), point(3, 7), point(9, 8), point(12, 4)
                ),
                new MultiCurveGeometry(List.of(
                        point(0, 0), point(4, 6), point(9, 7), point(12, 4)
                )),
                new FreehandGeometry(List.of(
                        point(0, 0), point(3, 3), point(7, 5), point(12, 4)
                ))
        );

        for (ReferenceGeometry geometry : geometries) {
            assertEquals(
                    geometry,
                    ReferenceGeometryReverser.reverse(
                            ReferenceGeometryReverser.reverse(geometry)
                    )
            );
        }
    }

    private static RoadPoint point(double x, double z) {
        return new RoadPoint(x, 64.0 + x / 12.0, z);
    }
}
