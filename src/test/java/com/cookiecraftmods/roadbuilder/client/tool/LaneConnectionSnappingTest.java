package com.cookiecraftmods.roadbuilder.client.tool;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.RoadVector;
import com.cookiecraftmods.roadbuilder.network.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class LaneConnectionSnappingTest {
    @Test
    void existingToolsOfferLaneSnappingOnlyForTheirEndpoints() {
        for (var tool : List.of(RoadBuilderTool.STRAIGHT_ROAD,
                RoadBuilderTool.SIMPLE_CURVE, RoadBuilderTool.COMPLEX_CURVE)) {
            assertTrue(LaneConnectionSnapping.selectingEndpoint(tool, false, 0));
        }
        assertTrue(LaneConnectionSnapping.selectingEndpoint(RoadBuilderTool.STRAIGHT_ROAD, true, 0));
        assertFalse(LaneConnectionSnapping.selectingEndpoint(RoadBuilderTool.SIMPLE_CURVE, true, 0));
        assertTrue(LaneConnectionSnapping.selectingEndpoint(RoadBuilderTool.SIMPLE_CURVE, true, 1));
        assertFalse(LaneConnectionSnapping.selectingEndpoint(RoadBuilderTool.COMPLEX_CURVE, true, 1));
        assertTrue(LaneConnectionSnapping.selectingEndpoint(RoadBuilderTool.COMPLEX_CURVE, true, 2));
        assertTrue(LaneConnectionSnapping.selectingEndpoint(RoadBuilderTool.FREEHAND_DRAW, false, 0));
        assertFalse(LaneConnectionSnapping.selectingEndpoint(RoadBuilderTool.FREEHAND_DRAW, true, 0));
        assertTrue(LaneConnectionSnapping.selectingEndpoint(RoadBuilderTool.FREEHAND_DRAW, true, 1));
        assertFalse(LaneConnectionSnapping.selectingEndpoint(RoadBuilderTool.FREEHAND_DRAW, true, 2));
        assertFalse(LaneConnectionSnapping.selectingEndpoint(RoadBuilderTool.ROUNDABOUT, false, 0));
    }

    @Test
    void lanePositionAndCenterNodeRemainSeparatelySelectable() {
        var node = new RoadNode(new RoadNodeId(UUID.randomUUID()), new RoadPoint(0, 64, 0));
        var segment = new RoadSegmentId(UUID.randomUUID());
        var port = new AuxiliaryLanePorts.Port(node.id(), segment, RoadLaneId.create(),
                new RoadPoint(0, 64, 3), new RoadVector(1, 0, 0), true, 9);
        var selected = LaneConnectionSnapping.nearest(new RoadPoint(0.2, 64, 2.8),
                List.of(port), List.of(node), 2.5).orElseThrow();
        assertEquals(node.id(), selected.nodeId());
        assertEquals(port.position(), selected.position());
        assertNotEquals(node.position(), selected.position());
        assertTrue(LaneConnectionSnapping.nearest(new RoadPoint(0.2, 64, 0.2),
                List.of(port), List.of(node), 2.5).isEmpty());
        assertTrue(LaneConnectionSnapping.nearest(new RoadPoint(0, 70, 3),
                List.of(port), List.of(node), 2.5).isEmpty());
        assertTrue(LaneConnectionSnapping.nearest(new RoadPoint(30, 64, 3),
                List.of(port), List.of(node), 2.5).isEmpty());
    }
}
