package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.geometry.*;
import com.cookiecraftmods.roadbuilder.preset.RoadClass;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoadCurveValidationTest {
    private static RoadPoint p(double x, double z) { return new RoadPoint(x, 64, z); }

    @Test
    void tangentAlignmentDoesNotImplyZeroCurvature() {
        var curve = new CubicBezierGeometry(p(0, 0), p(10, 0), p(20, 10), p(30, 10));
        assertEquals(new RoadVector(1, 0, 0), curve.sample(0).tangent());
        assertEquals(1.0 / 15, curve.curvatureAt(0), 1e-12);
        assertEquals(0, curve.curvatureAt(0.5), 1e-12);
        assertTrue(curve.curvatureAt(0.49) > 0);
        assertTrue(curve.curvatureAt(0.51) > 0);
        assertNull(RoadCurveValidation.validate(curve, 4));
        assertEquals(BlockPlanFailureCode.TOO_SHARP_TURN, RoadCurveValidation.validate(curve, 16));
        assertThrows(IllegalArgumentException.class, () -> curve.curvatureAt(Double.NaN));
    }

    @Test
    void rejectsLoopAndStationaryReversal() {
        var loop = new CubicBezierGeometry(p(0, 0), p(100, 100), p(-100, 100), p(20, 0));
        assertTrue(RoadCurveValidation.selfIntersects(loop));
        assertEquals(BlockPlanFailureCode.SELF_INTERSECTION, RoadCurveValidation.validate(loop, 4));
        var reversal = new CubicBezierGeometry(p(0, 0), p(20, 0), p(-10, 0), p(10, 0));
        assertEquals(BlockPlanFailureCode.TOO_SHARP_TURN, RoadCurveValidation.validate(reversal, 4));
    }

    @Test
    void rejectsStationaryPointBetweenUniformSamples() {
        // x'(t)=100(t-r)^2: no sign reversal, and r is not a dyadic sample location.
        double r = 0.371234567;
        double c = 100 * r * r;
        double b = -100 * r;
        double a = 100.0 / 3;
        var curve = new CubicBezierGeometry(p(0, 0), p(c / 3, 0),
                p((b + 2 * c) / 3, 0), p(a + b + c, 0));
        assertEquals(BlockPlanFailureCode.TOO_SHARP_TURN, RoadCurveValidation.validate(curve, 4));
    }

    @Test
    void automaticArcAndConstrainedSBendAreRegularAndPreserveEndpoints() {
        var arc = FreeformGeometry.create(p(0, 0), p(40, 40), new RoadVector(1, 0, 0), null);
        assertEquals(0, arc.sample(arc.length()).tangent().x(), 1e-12);
        assertNull(RoadCurveValidation.validate(arc, 16));
        var s = FreeformGeometry.create(p(0, 0), p(80, 20),
                new RoadVector(1, 0, 0), new RoadVector(1, 0, 0));
        assertEquals(0, s.horizontalCurvatureAt(0.5), 1e-12);
        assertNull(RoadCurveValidation.validate(s, 16));
        assertEquals(p(80, 20), s.end());
    }

    @Test
    void policyAccountsForRoadWidthClassAndParallelOffsets() {
        assertEquals(4, RoadCurveValidation.minimumRadius(3, RoadClass.LOCAL));
        assertEquals(16, RoadCurveValidation.minimumRadius(3, RoadClass.HIGHWAY));
        assertEquals(32.5, RoadCurveValidation.minimumRadius(64, RoadClass.LOCAL));
        var curve = new CubicBezierGeometry(p(0, 0), p(10, 0), p(20, 10), p(30, 10));
        assertEquals(BlockPlanFailureCode.TOO_SHARP_TURN,
                RoadCurveValidation.validate(new OffsetGeometry(curve, 20, false), 4));
        assertNull(RoadCurveValidation.validate(new CubicBezierGeometry(
                p(0, 0), p(10, 0), p(20, 0), p(30, 0)), 10000));
    }
}
