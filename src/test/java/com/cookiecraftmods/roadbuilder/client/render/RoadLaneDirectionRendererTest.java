package com.cookiecraftmods.roadbuilder.client.render;

import com.cookiecraftmods.roadbuilder.geometry.PathSample;
import com.cookiecraftmods.roadbuilder.geometry.OffsetGeometry;
import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.RoadVector;
import com.cookiecraftmods.roadbuilder.geometry.StraightGeometry;
import com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadLaneDirectionRendererTest {
    @Test
    void cachedBoundsContainCurvedBackwardLanesAndExcludeDistantCameras() {
        var curve = new com.cookiecraftmods.roadbuilder.geometry.QuadraticBezierGeometry(
                new RoadPoint(-500, 64, 500), new RoadPoint(0, 64, -500),
                new RoadPoint(500, 64, 500));
        var lane = new OffsetGeometry(curve, 20, true);
        var bounds = RoadLaneDirectionRenderer.geometryBounds(lane);
        for (int i = 0; i <= 100; i++) {
            var point = lane.sample(i == 100 ? lane.length() : lane.length() * i / 100).position();
            org.junit.jupiter.api.Assertions.assertTrue(bounds.contains(point.x(), point.y(), point.z()));
        }
        org.junit.jupiter.api.Assertions.assertFalse(bounds.inflate(150).contains(10000, 64, 10000));
    }

    @Test
    void reversesArrowDirectionForBackwardLanes() {
        PathSample sample = new PathSample(
                4.0,
                new RoadPoint(2.0, 64.0, 3.0),
                new RoadVector(0.6, 0.0, 0.8)
        );

        assertEquals(
                new RoadLaneDirectionRenderer.Direction(0.6, 0.8),
                RoadLaneDirectionRenderer.horizontalDirection(
                        sample,
                        LaneTravelDirection.FORWARD
                )
        );
        assertEquals(
                new RoadLaneDirectionRenderer.Direction(-0.6, -0.8),
                RoadLaneDirectionRenderer.horizontalDirection(
                        sample,
                        LaneTravelDirection.BACKWARD
                )
        );
    }

    @Test
    void finalPathSampleUsesTheExactCurveLength() {
        double length = 30.19985070151116;
        int sampleCount = 41;

        assertTrue(length * sampleCount / sampleCount > length);
        assertEquals(
                Double.doubleToLongBits(length),
                Double.doubleToLongBits(
                        RoadLaneDirectionRenderer.pathSampleDistance(
                                length,
                                sampleCount,
                                sampleCount
                        )
                )
        );
    }

    @Test
    void followsTheTravelOrientedGeometryOfBackwardPresetLanes() {
        OffsetGeometry backwardLane = new OffsetGeometry(
                new StraightGeometry(
                        new RoadPoint(0.5, 64.0, 0.5),
                        new RoadPoint(10.5, 64.0, 0.5)
                ),
                0.0,
                true
        );

        assertEquals(
                new RoadLaneDirectionRenderer.Direction(-1.0, 0.0),
                RoadLaneDirectionRenderer.horizontalDirection(
                        backwardLane.sample(3.0),
                        LaneTravelDirection.FORWARD
                )
        );
    }
}
