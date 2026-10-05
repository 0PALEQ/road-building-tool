package com.cookiecraftmods.roadbuilder.client.render;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.StraightGeometry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SegmentHighlightSamplingTest {
    @Test
    void reportedCrashLengthNeverProducesAnOutOfBoundsSample() {
        StraightGeometry geometry = new StraightGeometry(
                new RoadPoint(0.0, 0.0, 0.0),
                new RoadPoint(56.04462507680822, 0.0, 0.0)
        );
        double length = geometry.length();
        int sampleCount = SegmentHighlightSampling.sampleCount(length);

        assertEquals(113, sampleCount);
        assertTrue(
                length * sampleCount / sampleCount > length,
                "the reported length must reproduce the former terminal-sample overflow"
        );
        for (int index = 0; index <= sampleCount; index++) {
            double distance = SegmentHighlightSampling.distance(length, sampleCount, index);
            assertTrue(distance >= 0.0 && distance <= length);
            assertDoesNotThrow(() -> geometry.sample(distance));
        }
        assertEquals(
                length,
                SegmentHighlightSampling.distance(length, sampleCount, sampleCount)
        );
    }
}
