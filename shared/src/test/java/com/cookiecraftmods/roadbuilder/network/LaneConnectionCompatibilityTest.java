package com.cookiecraftmods.roadbuilder.network;

import com.cookiecraftmods.roadbuilder.preset.LaneType;
import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.StraightGeometry;
import com.cookiecraftmods.roadbuilder.preset.LaneComponent;
import com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LaneConnectionCompatibilityTest {
    @Test
    void allowsBusLanesToMergeIntoAndLeaveSharedVehicleLanes() {
        assertTrue(LaneConnectionCompatibility.canTransition(
                LaneType.BUS,
                LaneType.VEHICLE
        ));
        assertTrue(LaneConnectionCompatibility.canTransition(
                LaneType.VEHICLE,
                LaneType.BUS
        ));
        assertTrue(LaneConnectionCompatibility.canTransition(
                LaneType.BUS,
                LaneType.BUS
        ));
        assertTrue(LaneConnectionCompatibility.canTransition(
                LaneType.VEHICLE,
                LaneType.VEHICLE
        ));
    }

    @Test
    void laneConnectorGraphAcceptsABusToSharedLaneTransition() {
        RoadLane bus = lane(1, LaneType.BUS);
        RoadLane shared = lane(2, LaneType.VEHICLE);
        RoadNodeId nodeId = new RoadNodeId(new UUID(0, 30));
        RoadLaneGraph graph = RoadLaneGraph.from(new RoadGraphSnapshot(
                Level.OVERWORLD,
                0,
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(bus.id(), bus, shared.id(), shared),
                Map.of()
        ));

        assertTrue(graph.canConnect(
                new RoadLaneGraph.ConnectionPoint(
                        bus.id(),
                        nodeId,
                        RoadLaneGraph.ConnectionPointKind.INCOMING,
                        10.0,
                        new RoadPoint(10.0, 64.0, 0.0)
                ),
                new RoadLaneGraph.ConnectionPoint(
                        shared.id(),
                        nodeId,
                        RoadLaneGraph.ConnectionPointKind.OUTGOING,
                        0.0,
                        new RoadPoint(10.0, 64.0, 0.0)
                )
        ));
    }

    private static RoadLane lane(long id, LaneType type) {
        return new RoadLane(
                new RoadLaneId(new UUID(0, id)),
                new RoadSegmentId(new UUID(1, id)),
                0,
                new LaneComponent(
                        type,
                        3.0,
                        0.0,
                        LaneTravelDirection.FORWARD
                ),
                new StraightGeometry(
                        new RoadPoint(0.0, 64.0, 0.0),
                        new RoadPoint(10.0, 64.0, 0.0)
                )
        );
    }
}
