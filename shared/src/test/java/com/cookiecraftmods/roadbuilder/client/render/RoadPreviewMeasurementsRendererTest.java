package com.cookiecraftmods.roadbuilder.client.render;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RoadPreviewMeasurementsRendererTest {
    private static final Matrix4f PROJECTION = new Matrix4f()
            .perspective((float) (Math.PI / 2), 2.0F, 0.1F, 100.0F);
    private static final Vec3 CAMERA = new Vec3(100, 200, 300);

    @Test
    void projectsRelativeToCameraIntoScaledHudCoordinates() {
        var center = RoadPreviewMeasurementsRenderer.project(PROJECTION, CAMERA,
                new RoadPoint(100, 200, 290), 400, 200);
        assertEquals(200.0F, center.x(), 0.001F);
        assertEquals(100.0F, center.y(), 0.001F);

        var aboveAndRight = RoadPreviewMeasurementsRenderer.project(PROJECTION, CAMERA,
                new RoadPoint(110, 205, 290), 200, 100);
        assertEquals(150.0F, aboveAndRight.x(), 0.001F);
        assertEquals(25.0F, aboveAndRight.y(), 0.001F);
    }

    @Test
    void omitsPointsBehindTheCameraOrOutsideTheDepthRange() {
        for (double z : new double[] {301.0, 299.95, 100.0}) {
            assertNull(RoadPreviewMeasurementsRenderer.project(PROJECTION, CAMERA,
                    new RoadPoint(100, 200, z), 400, 200));
        }
    }
}
