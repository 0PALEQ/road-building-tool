package com.cookiecraftmods.roadbuilder.client.tool;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StraightRoadPreviewProblemTest {
    @Test
    void rejectsVerticalOnlyEndpointsBeforeStraightGeometryConstruction() {
        RoadPoint start = new RoadPoint(10.5, 64.0, -3.5);

        assertTrue(StraightRoadPreviewProblem.lacksHorizontalDirection(
                start,
                new RoadPoint(10.5, 79.0, -3.5)
        ));
        assertFalse(StraightRoadPreviewProblem.lacksHorizontalDirection(
                start,
                new RoadPoint(11.5, 79.0, -3.5)
        ));
    }
}
