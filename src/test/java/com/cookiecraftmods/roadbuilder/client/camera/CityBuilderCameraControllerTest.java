package com.cookiecraftmods.roadbuilder.client.camera;

import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CityBuilderCameraControllerTest {
    @Test
    void waitsForAnUnloadedNeighborTheCollisionCheckCanReach() {
        AABB player = new AABB(13.7, 64.0, 7.7, 14.3, 65.8, 8.3);

        assertFalse(CityBuilderCameraController.hasLoadedMovementChunks(
                player, 1.0, 0.0, (x, z) -> x == 0 && z == 0
        ));
        assertTrue(CityBuilderCameraController.hasLoadedMovementChunks(
                player, 1.0, 0.0, (x, z) -> (x == 0 || x == 1) && z == 0
        ));
    }

    @Test
    void diagonalMovementChecksNeighborChunksAlongThePath() {
        AABB player = new AABB(14.7, 64.0, 14.7, 15.3, 65.8, 15.3);

        assertFalse(CityBuilderCameraController.hasLoadedMovementChunks(
                player, 2.0, 2.0, (x, z) -> x != 0 || z != 1
        ));
    }

    @Test
    void checksTheCorrectChunksAcrossNegativeCoordinates() {
        AABB player = new AABB(-14.3, 64.0, -8.3, -13.7, 65.8, -7.7);

        assertFalse(CityBuilderCameraController.hasLoadedMovementChunks(
                player, -1.0, 0.0, (x, z) -> x == -1 && z == -1
        ));
        assertTrue(CityBuilderCameraController.hasLoadedMovementChunks(
                player, -1.0, 0.0, (x, z) -> (x == -2 || x == -1) && z == -1
        ));
    }
}
