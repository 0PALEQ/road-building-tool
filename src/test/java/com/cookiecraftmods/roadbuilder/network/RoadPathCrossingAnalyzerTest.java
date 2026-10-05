package com.cookiecraftmods.roadbuilder.network;

import com.cookiecraftmods.roadbuilder.geometry.QuadraticBezierGeometry;
import com.cookiecraftmods.roadbuilder.geometry.ReferenceGeometry;
import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.StraightGeometry;
import com.cookiecraftmods.roadbuilder.preset.LaneComponent;
import com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection;
import com.cookiecraftmods.roadbuilder.preset.LaneType;
import com.cookiecraftmods.roadbuilder.preset.RoadPreset;
import com.cookiecraftmods.roadbuilder.preset.TrafficHandedness;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadPathCrossingAnalyzerTest {
    @Test
    void detectsLineCurveAndCurveCurveCrossings() {
        RoadSegment curve = segment(
                1,
                new QuadraticBezierGeometry(
                        new RoadPoint(0.0, 64.0, 0.0),
                        new RoadPoint(5.0, 64.0, 6.0),
                        new RoadPoint(10.0, 64.0, 0.0)
                )
        );
        var lineCrossings = new RoadPathCrossingAnalyzer().analyze(
                new StraightGeometry(
                        new RoadPoint(5.0, 64.0, -2.0),
                        new RoadPoint(5.0, 64.0, 6.0)
                ),
                List.of(curve)
        );
        assertEquals(1, lineCrossings.size());

        var curveCrossings = new RoadPathCrossingAnalyzer().analyze(
                new QuadraticBezierGeometry(
                        new RoadPoint(0.0, 64.0, 5.0),
                        new RoadPoint(5.0, 64.0, -1.0),
                        new RoadPoint(10.0, 64.0, 5.0)
                ),
                List.of(curve)
        );
        assertTrue(curveCrossings.size() >= 1);
    }

    @Test
    void detectsEndpointOnSegmentAsTJunctionButNotGradeSeparation() {
        RoadSegment trunk = segment(
                1,
                new StraightGeometry(
                        new RoadPoint(0.0, 64.0, 0.0),
                        new RoadPoint(10.0, 64.0, 0.0)
                )
        );
        var candidates = new RoadPathCrossingAnalyzer().analyze(
                new StraightGeometry(
                        new RoadPoint(5.0, 64.0, 0.0),
                        new RoadPoint(5.0, 64.0, 8.0)
                ),
                List.of(trunk)
        );
        assertEquals(1, candidates.size());
        assertEquals(0.0, candidates.get(0).proposedPathFraction());

        var elevatedJoin = new RoadPathCrossingAnalyzer().analyze(
                new StraightGeometry(
                        new RoadPoint(5.0, 70.0, 0.0),
                        new RoadPoint(5.0, 70.0, 8.0)
                ),
                List.of(trunk)
        );
        assertEquals(1, elevatedJoin.size());
        assertEquals(new RoadPoint(5.0, 64.0, 0.0),
                elevatedJoin.get(0).nodePosition());

        assertTrue(new RoadPathCrossingAnalyzer().analyze(
                new StraightGeometry(
                        new RoadPoint(5.0, 71.0, 0.0),
                        new RoadPoint(5.0, 71.0, 8.0)
                ),
                List.of(trunk)
        ).isEmpty());
    }

    @Test
    void detectsEndpointAnywhereAcrossTheRoadSurfaceAndUsesItsCenterlineNode() {
        RoadSegment trunk = segment(
                1,
                new StraightGeometry(
                        new RoadPoint(0.0, 64.0, 0.0),
                        new RoadPoint(10.0, 64.0, 0.0)
                )
        );

        var candidates = new RoadPathCrossingAnalyzer().analyze(
                new StraightGeometry(
                        new RoadPoint(5.0, 64.0, 1.5),
                        new RoadPoint(5.0, 64.0, 8.0)
                ),
                List.of(trunk)
        );

        assertEquals(1, candidates.size());
        assertEquals(0.0, candidates.get(0).proposedPathFraction());
        assertEquals(
                new RoadPoint(5.0, 64.0, 0.0),
                candidates.get(0).nodePosition()
        );

        assertTrue(new RoadPathCrossingAnalyzer().analyze(
                new StraightGeometry(
                        new RoadPoint(5.0, 64.0, 2.1),
                        new RoadPoint(5.0, 64.0, 8.0)
                ),
                List.of(trunk)
        ).isEmpty());
    }

    @Test
    void coalescesSurfaceEndpointsAndCenterlineAtTheSameSplitPosition() {
        RoadSegment trunk = segment(
                1,
                new StraightGeometry(
                        new RoadPoint(0.0, 64.0, 0.0),
                        new RoadPoint(10.0, 64.0, 0.0)
                )
        );

        var candidates = new RoadPathCrossingAnalyzer().analyze(
                new StraightGeometry(
                        new RoadPoint(5.0, 64.0, -1.5),
                        new RoadPoint(5.0, 64.0, 1.5)
                ),
                List.of(trunk)
        );

        assertEquals(1, candidates.size());
        assertEquals(0.5, candidates.get(0).proposedPathFraction());
        assertEquals(0.5, candidates.get(0).crossedPathFraction());
        assertEquals(new RoadPoint(5.0, 64.0, 0.0),
                candidates.get(0).nodePosition());
    }

    private static RoadSegment segment(long id, ReferenceGeometry geometry) {
        RoadPreset preset = new RoadPreset(
                1,
                1,
                3.0,
                new ResourceLocation("minecraft", "stone"),
                List.of(new LaneComponent(
                        LaneType.VEHICLE,
                        2.0,
                        0.0,
                        LaneTravelDirection.FORWARD
                ))
        );
        return RoadSegment.path(
                new RoadSegmentId(new UUID(0, id)),
                new RoadId(new UUID(1, id)),
                new RoadNodeId(new UUID(2, id)),
                new RoadNodeId(new UUID(3, id)),
                geometry,
                new ResourceLocation("roadbuilder", "test"),
                1,
                RoadPresetSnapshot.from(preset),
                TrafficHandedness.RHT,
                List.of(new RoadLaneId(new UUID(4, id)))
        );
    }
}
