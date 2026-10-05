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
