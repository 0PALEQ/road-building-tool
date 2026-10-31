package com.cookiecraftmods.roadbuilder.geometry;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FreeformGeometryTest {
    @Test
    void balancesBothHandlesAndEndpointCurvatureForRotatedSlopingArcs() {
        for (double rotation : new double[]{0, 0.4, 1.7, -2.1}) {
            RoadVector forward = new RoadVector(Math.cos(rotation), 0, Math.sin(rotation));
            RoadVector side = new RoadVector(-forward.z(), 0, forward.x());
            RoadPoint start = new RoadPoint(12, 64, -17);
            RoadPoint end = start.add(forward.scale(40)).add(side.scale(20))
                    .add(new RoadVector(0, 9, 0));
            var curve = FreeformGeometry.createBalanced(start, end, forward);
            assertEquals(start.distanceTo(curve.startControl()),
                    end.distanceTo(curve.endControl()), 1e-9);
            var scaled = FreeformGeometry.createBalanced(start, end, forward.scale(50));
            assertEquals(0, curve.startControl().distanceTo(scaled.startControl()), 1e-9);
            assertEquals(0, curve.endControl().distanceTo(scaled.endControl()), 1e-9);
            assertEquals(67, curve.startControl().y(), 1e-9);
            assertEquals(70, curve.endControl().y(), 1e-9);
            assertEquals(curvature(curve, 0), curvature(curve, 1), 1e-9);
            assertEquals(curvature(curve, 0.2), curvature(curve, 0.8), 1e-9);
        }
    }

    @Test
    void alignedDirectionProducesStraightRoadAndCoincidentEndsAreRejected() {
        var start = new RoadPoint(0, 64, 0);
        var curve = FreeformGeometry.createBalanced(start, new RoadPoint(30, 64, 0),
                new RoadVector(10, 0, 0));
        assertEquals(new RoadPoint(10, 64, 0), curve.startControl());
        assertEquals(new RoadPoint(20, 64, 0), curve.endControl());
        assertEquals(0, curvature(curve, 0.5), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> FreeformGeometry.createBalanced(
                start, start, new RoadVector(1, 0, 0)));
    }

    private static double curvature(CubicBezierGeometry curve, double t) {
        var v = curve.derivativeAt(t);
        var a = curve.secondDerivativeAt(t);
        return Math.abs(v.x() * a.z() - v.z() * a.x()) / Math.pow(v.horizontalLength(), 3);
    }
}
