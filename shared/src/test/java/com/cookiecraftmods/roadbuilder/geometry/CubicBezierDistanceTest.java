package com.cookiecraftmods.roadbuilder.geometry;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CubicBezierDistanceTest {
    @Test void inverseDistanceMatchesSampledPositionsAcrossUnevenCurve() {
        var curve = new CubicBezierGeometry(new RoadPoint(-200, 64, -30),
                new RoadPoint(-190, 90, 180), new RoadPoint(300, 20, -100),
                new RoadPoint(400, 70, 50));
        assertEquals(0, curve.parameterAtDistance(0));
        assertEquals(1, curve.parameterAtDistance(curve.length()));
        double previous = -1;
        for (int i = 0; i <= 2000; i++) {
            double distance = curve.length() * (i / 2000.0);
            double parameter = curve.parameterAtDistance(distance);
            assertTrue(parameter > previous);
            assertTrue(curve.positionAt(parameter).distanceTo(curve.sample(distance).position()) < 1e-9);
            previous = parameter;
        }
        for (double invalid : new double[]{-1, curve.length() + 1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> curve.parameterAtDistance(invalid));
        }
    }
}
