package com.cookiecraftmods.roadbuilder.client.tool;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CursorRaycasterTest {
    private static final double TOLERANCE = 1.0e-12;

    @Test
    void sideFaceHitsSelectTheVisibleBlockAtItsSurfaceElevation() {
        BlockPos position = new BlockPos(12, 70, -4);
        CursorTarget target = CursorRaycaster.target(new BlockHitResult(
                new Vec3(12.0, 70.6, -3.5),
                Direction.WEST,
                position,
                false
        ));

        assertEquals(position, target.blockPosition());
        assertEquals(12.5, target.roadPoint().x());
        assertEquals(70.0, target.roadPoint().y());
        assertEquals(-3.5, target.roadPoint().z());
    }

    @Test
    void screenCenterUsesTheCameraLookDirection() {
        Vec3 direction = CursorRaycaster.projectedDirection(
                new Vec3(0.0, 0.0, 1.0),
                new Vec3(1.0, 0.0, 0.0),
                new Vec3(0.0, 1.0, 0.0),
                0.0,
                0.0,
                16.0 / 9.0,
                70.0
        );

        assertEquals(0.0, direction.x, TOLERANCE);
        assertEquals(0.0, direction.y, TOLERANCE);
        assertEquals(1.0, direction.z, TOLERANCE);
    }

    @Test
    void offCenterRayUsesTheRenderedFovAndFramebufferAspectRatio() {
        Vec3 narrow = CursorRaycaster.projectedDirection(
                new Vec3(0.0, 0.0, 1.0),
                new Vec3(1.0, 0.0, 0.0),
                new Vec3(0.0, 1.0, 0.0),
                1.0,
                0.0,
                16.0 / 9.0,
                50.0
        );
        Vec3 wide = CursorRaycaster.projectedDirection(
                new Vec3(0.0, 0.0, 1.0),
                new Vec3(1.0, 0.0, 0.0),
                new Vec3(0.0, 1.0, 0.0),
                1.0,
                0.0,
                16.0 / 9.0,
                90.0
        );

        assertTrue(wide.x < narrow.x);
        assertEquals(
                -Math.tan(Math.toRadians(90.0) / 2.0) * 16.0 / 9.0,
                wide.x / wide.z,
                TOLERANCE
        );
    }
}
