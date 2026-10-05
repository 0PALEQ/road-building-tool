package com.cookiecraftmods.roadbuilder.network;

import com.cookiecraftmods.roadbuilder.geometry.CubicBezierGeometry;
import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.StraightGeometry;
import com.cookiecraftmods.roadbuilder.preset.LaneComponent;
import com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection;
import com.cookiecraftmods.roadbuilder.preset.LaneType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LaneConnectorGeometryPlannerTest {
    @Test
    void buildsBoundaryClippedCubicWithLaneAlignedTangents() {
        RoadLaneId fromId = laneId(1);
        RoadLaneId toId = laneId(2);
        RoadLane from = lane(
                fromId,
                segmentId(11),
                new StraightGeometry(
                        new RoadPoint(-10.0, 64.0, 0.0),
                        new RoadPoint(0.0, 64.0, 0.0)
                )
        );
        RoadLane to = lane(
                toId,
                segmentId(12),
                new StraightGeometry(
                        new RoadPoint(0.0, 64.0, 0.0),
                        new RoadPoint(0.0, 64.0, 10.0)
                )
        );

        CubicBezierGeometry geometry = new LaneConnectorGeometryPlanner().plan(
                new LaneConnector(
                        new LaneConnectorId(new UUID(0, 3)),
                        fromId,
                        toId,
                        LaneConnectorMovement.automatic(LaneMovementType.RIGHT)
                ),
                Map.of(fromId, from, toId, to),
                3.0,
                3.0
        );

        assertEquals(new RoadPoint(-3.0, 64.0, 0.0), geometry.start());
        assertEquals(new RoadPoint(0.0, 64.0, 3.0), geometry.end());
        assertTrue(geometry.startControl().x() > geometry.start().x());
        assertTrue(geometry.endControl().z() < geometry.end().z());
        assertTrue(geometry.length() < 10.0);
    }

    @Test
    void clampsTrimBelowHalfOfShortLane() {
        RoadLaneId fromId = laneId(4);
        RoadLaneId toId = laneId(5);
        RoadLane from = lane(
                fromId,
                segmentId(14),
                new StraightGeometry(
                        new RoadPoint(-2.0, 64.0, 0.0),
                        new RoadPoint(0.0, 64.0, 0.0)
                )
        );
        RoadLane to = lane(
                toId,
                segmentId(15),
                new StraightGeometry(
                        new RoadPoint(0.0, 64.0, 0.0),
                        new RoadPoint(0.0, 64.0, 2.0)
                )
        );

        CubicBezierGeometry geometry = new LaneConnectorGeometryPlanner().plan(
                new LaneConnector(
                        new LaneConnectorId(new UUID(0, 6)),
                        fromId,
                        toId
                ),
                Map.of(fromId, from, toId, to),
                20.0,
                20.0
        );

        assertEquals(-0.98, geometry.start().x(), 1.0e-9);
        assertEquals(0.98, geometry.end().z(), 1.0e-9);
    }

    private static RoadLane lane(
            RoadLaneId id,
            RoadSegmentId segmentId,
            StraightGeometry geometry
    ) {
        return new RoadLane(
                id,
                segmentId,
                0,
                new LaneComponent(
                        LaneType.VEHICLE,
                        1.0,
                        0.0,
                        LaneTravelDirection.FORWARD
                ),
                geometry
        );
    }

    private static RoadLaneId laneId(long value) {
        return new RoadLaneId(new UUID(0, value));
    }

    private static RoadSegmentId segmentId(long value) {
        return new RoadSegmentId(new UUID(0, value));
    }
}
