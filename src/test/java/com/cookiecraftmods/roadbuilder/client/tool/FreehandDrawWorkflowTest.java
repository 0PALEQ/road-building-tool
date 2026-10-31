package com.cookiecraftmods.roadbuilder.client.tool;

import com.cookiecraftmods.roadbuilder.geometry.*;
import com.cookiecraftmods.roadbuilder.networking.RoadPathIntentC2SPacket;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FreehandDrawWorkflowTest {
    @Test
    @SuppressWarnings("unchecked")
    void laneEntryDrivesAutomaticExitAndBothLaneTangentsAreSolvedTogether() throws Exception {
        var controller = new StraightRoadToolController();
        set(controller, "activeTool", RoadBuilderTool.FREEHAND_DRAW);
        set(controller, "startTarget", target(0, 0));
        var field = StraightRoadToolController.class.getDeclaredField("pathPoints");
        field.setAccessible(true);
        ((List<CursorTarget>) field.get(controller)).add(target(0, 10));
        var entry = new RoadVector(1, 0, 0);
        set(controller, "startLanePort", port(target(0, 0).roadPoint(), entry));
        var end = target(80, 30);
        assertEquals(FreeformGeometry.createBalanced(target(0, 0).roadPoint(), end.roadPoint(), entry),
                preview(controller, end));
        var exit = new RoadVector(0, 0, 1);
        set(controller, "hoveredLanePort", port(end.roadPoint(), exit));
        var curve = assertInstanceOf(CubicBezierGeometry.class, preview(controller, end));
        assertEquals(FreeformGeometry.create(target(0, 0).roadPoint(), end.roadPoint(), entry, exit), curve);
        assertEquals(entry, curve.sample(0).tangent());
        assertEquals(exit, curve.sample(curve.length()).tangent());
    }

    private static com.cookiecraftmods.roadbuilder.network.AuxiliaryLanePorts.Port port(
            RoadPoint position, RoadVector direction) {
        return new com.cookiecraftmods.roadbuilder.network.AuxiliaryLanePorts.Port(
                new com.cookiecraftmods.roadbuilder.network.RoadNodeId(java.util.UUID.randomUUID()),
                new com.cookiecraftmods.roadbuilder.network.RoadSegmentId(java.util.UUID.randomUUID()),
                com.cookiecraftmods.roadbuilder.network.RoadLaneId.create(), position, direction, true, 9);
    }

    @Test
    @SuppressWarnings("unchecked")
    void automaticHandlesFollowEndpointAndPacketPreservesPreview() throws Exception {
        var controller = new StraightRoadToolController();
        set(controller, "activeTool", RoadBuilderTool.FREEHAND_DRAW);
        set(controller, "startTarget", target(0, 0));
        var field = StraightRoadToolController.class.getDeclaredField("pathPoints");
        field.setAccessible(true);
        var points = (List<CursorTarget>) field.get(controller);
        assertInstanceOf(StraightGeometry.class, preview(controller, target(20, 0)));
        points.add(target(20, 0));
        var curve = assertInstanceOf(CubicBezierGeometry.class, preview(controller, target(40, 20)));
        assertEquals(new RoadVector(1, 0, 0), curve.sample(0).tangent());
        assertEquals(target(40, 20).roadPoint(), curve.end());
        var moved = assertInstanceOf(CubicBezierGeometry.class, preview(controller, target(50, 30)));
        assertNotEquals(curve.end(), moved.end());
        assertNotEquals(curve.endControl(), moved.endControl());
        set(controller, "previewGeometry", curve);
        var canonical = StraightRoadToolController.class.getDeclaredMethod("canonicalPathPoints");
        canonical.setAccessible(true);
        assertEquals(curve.controlPoints(), canonical.invoke(controller));
        var packet = RoadPathIntentC2SPacket.current(java.util.UUID.randomUUID(),
                new net.minecraft.resources.ResourceLocation("minecraft", "overworld"),
                new net.minecraft.resources.ResourceLocation("roadbuilder", "test"),
                1, 0L, RoadPathIntentC2SPacket.PathType.CUBIC, curve.controlPoints());
        assertEquals(curve, packet.geometry());
    }

    @Test
    @SuppressWarnings("unchecked")
    void directionClickControlsOnlyAngleAndContinuationRequiresANewDirection() throws Exception {
        var controller = new StraightRoadToolController();
        set(controller, "activeTool", RoadBuilderTool.FREEHAND_DRAW);
        set(controller, "startTarget", target(0, 0));
        var field = StraightRoadToolController.class.getDeclaredField("pathPoints");
        field.setAccessible(true);
        var points = (List<CursorTarget>) field.get(controller);
        points.add(target(1, 0));
        var near = preview(controller, target(40, 20));
        points.set(0, target(100, 0));
        assertEquals(near, preview(controller, target(40, 20)));
        var clear = StraightRoadToolController.class.getDeclaredMethod("clearSelection");
        clear.setAccessible(true);
        clear.invoke(controller);
        set(controller, "startTarget", target(40, 20));
        assertInstanceOf(StraightGeometry.class, preview(controller, target(80, 60)));
        assertTrue(points.isEmpty());
    }

    @Test
    void incompleteOrInvalidCurveCannotSubmit() throws Exception {
        var controller = new StraightRoadToolController();
        set(controller, "activeTool", RoadBuilderTool.FREEHAND_DRAW);
        var submit = StraightRoadToolController.class.getDeclaredMethod(
                "submitPreview", net.minecraft.client.Minecraft.class);
        submit.setAccessible(true);
        assertDoesNotThrow(() -> submit.invoke(controller, new Object[]{null}));
        var field = StraightRoadToolController.class.getDeclaredField("pathPoints");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var points = (List<CursorTarget>) field.get(controller);
        points.add(target(10, 0));
        set(controller, "previewGeometry", new StraightGeometry(target(0, 0).roadPoint(), target(10, 0).roadPoint()));
        set(controller, "previewProblem", StraightRoadPreviewProblem.planningFailure(
                com.cookiecraftmods.roadbuilder.placement.BlockPlanFailureCode.TOO_SHARP_TURN));
        assertTrue(controller.previewInvalid());
        assertDoesNotThrow(() -> submit.invoke(controller, new Object[]{null}));
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
