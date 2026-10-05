package com.cookiecraftmods.roadbuilder.client.camera;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CityBuilderCameraMathTest {
    private static final double EPSILON = 1.0E-9;

    @Test
    void horizontalMovementIsHeadingRelativeAndNormalizesDiagonals() {
        CityBuilderCameraMath.HorizontalDelta north =
                CityBuilderCameraMath.horizontalDelta(180.0F, 1.0, 0.0, 2.0);
        CityBuilderCameraMath.HorizontalDelta east =
                CityBuilderCameraMath.horizontalDelta(0.0F, 0.0, 1.0, 2.0);
        CityBuilderCameraMath.HorizontalDelta diagonal =
                CityBuilderCameraMath.horizontalDelta(0.0F, 1.0, 1.0, 2.0);

        assertEquals(0.0, north.x(), EPSILON);
        assertEquals(-2.0, north.z(), EPSILON);
        assertEquals(2.0, east.x(), EPSILON);
        assertEquals(0.0, east.z(), EPSILON);
        assertEquals(2.0, Math.hypot(diagonal.x(), diagonal.z()), EPSILON);
    }

    @Test
    void horizontalMovementInputCanBeInverted() {
        assertEquals(
                -1.0,
                CityBuilderCameraMath.horizontalMovementAxis(true, false, true),
                EPSILON
        );
        assertEquals(
                1.0,
                CityBuilderCameraMath.horizontalMovementAxis(false, true, true),
                EPSILON
        );
        assertEquals(
                1.0,
                CityBuilderCameraMath.horizontalMovementAxis(true, false, false),
                EPSILON
        );
        assertEquals(
                -1.0,
                CityBuilderCameraMath.horizontalMovementAxis(false, true, false),
                EPSILON
        );
    }

    @Test
    void panSpeedScalesWithZoomAndFastModifier() {
        double close = CityBuilderCameraMath.panSpeed(4.0, false);
        double far = CityBuilderCameraMath.panSpeed(96.0, false);

        assertTrue(far > close);
        assertEquals(far * 3.0, CityBuilderCameraMath.panSpeed(96.0, true), EPSILON);
    }

    @Test
    void zoomAndRotationRemainWithinSafeBounds() {
        assertEquals(
                CityBuilderCameraMath.MIN_DISTANCE,
                CityBuilderCameraMath.zoomedDistance(4.0, 100.0),
                EPSILON
        );
        assertEquals(
                CityBuilderCameraMath.MAX_DISTANCE,
                CityBuilderCameraMath.zoomedDistance(96.0, -100.0),
                EPSILON
        );
        assertEquals(
                CityBuilderCameraMath.MIN_PITCH,
                CityBuilderCameraMath.rotatedPitch(55.0F, 10_000.0, 1.0, true),
                0.0F
        );
        assertEquals(
                CityBuilderCameraMath.MAX_PITCH,
                CityBuilderCameraMath.rotatedPitch(55.0F, -10_000.0, 1.0, true),
                0.0F
        );
        assertEquals(
                -170.0F,
                CityBuilderCameraMath.rotatedYaw(170.0F, 20.0, 1.0, true),
                0.0F
        );
    }

    @Test
    void dragRotationUsesTheRequestedInvertedDirections() {
        assertEquals(
                65.0F,
                CityBuilderCameraMath.rotatedYaw(55.0F, 10.0, 1.0, true),
                0.0F
        );
        assertEquals(
                45.0F,
                CityBuilderCameraMath.rotatedPitch(55.0F, 10.0, 1.0, true),
                0.0F
        );
        assertEquals(
                45.0F,
                CityBuilderCameraMath.rotatedYaw(55.0F, 10.0, 1.0, false),
                0.0F
        );
        assertEquals(
                65.0F,
                CityBuilderCameraMath.rotatedPitch(55.0F, 10.0, 1.0, false),
                0.0F
        );
    }
}
