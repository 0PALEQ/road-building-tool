package com.cookiecraftmods.roadbuilder.client.tool;

import com.cookiecraftmods.roadbuilder.geometry.*;
import com.cookiecraftmods.roadbuilder.networking.RoadPathIntentC2SPacket;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ComplexCurveWorkflowTest {
    @Test
    @SuppressWarnings("unchecked")
    void previewProgressesThroughTwoHandlesAndSubmitsTheSameSBend() throws Exception {
        StraightRoadToolController controller = new StraightRoadToolController();
        set(controller, "activeTool", RoadBuilderTool.COMPLEX_CURVE);
        set(controller, "startTarget", target(0, 0));
        var pointsField = StraightRoadToolController.class.getDeclaredField("pathPoints");
        pointsField.setAccessible(true);
        List<CursorTarget> points = (List<CursorTarget>) pointsField.get(controller);
        assertInstanceOf(StraightGeometry.class, preview(controller, target(20, 0)));
        points.add(target(20, 0));
        assertInstanceOf(QuadraticBezierGeometry.class, preview(controller, target(20, 20)));
        points.add(target(20, 20));
        CubicBezierGeometry curve = assertInstanceOf(CubicBezierGeometry.class,
                preview(controller, target(40, 20)));
        assertEquals(new RoadVector(1, 0, 0), curve.sample(0).tangent());
        assertEquals(new RoadVector(1, 0, 0), curve.sample(curve.length()).tangent());
        assertTrue(curve.sample(curve.length() / 2).tangent().z() > 0);
        assertTrue(curve.sample(curve.length() / 4).position().z() < 5);
        assertTrue(curve.sample(curve.length() * 3 / 4).position().z() > 15);
        set(controller, "previewGeometry", curve);
        var canonical = StraightRoadToolController.class.getDeclaredMethod("canonicalPathPoints");
        canonical.setAccessible(true);
        assertEquals(curve.controlPoints(), canonical.invoke(controller));
        var type = StraightRoadToolController.class.getDeclaredMethod("pathType");
        type.setAccessible(true);
        assertEquals(RoadPathIntentC2SPacket.PathType.CUBIC, type.invoke(controller));
    }

    private static ReferenceGeometry preview(StraightRoadToolController controller,
                                             CursorTarget cursor) throws Exception {
        var method = StraightRoadToolController.class.getDeclaredMethod("buildPreviewGeometry", CursorTarget.class);
        method.setAccessible(true);
        return (ReferenceGeometry) method.invoke(controller, cursor);
    }

    private static void set(Object instance, String name, Object value) throws Exception {
        var field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }

    private static CursorTarget target(double x, double z) {
        return new CursorTarget(BlockPos.containing(x, 64, z), new RoadPoint(x, 64, z));
    }
}
