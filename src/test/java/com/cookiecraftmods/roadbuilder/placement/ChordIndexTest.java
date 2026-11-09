package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class ChordIndexTest {
    @Test void retainsEveryBoundingBoxCandidateInOriginalOrder() {
        var random = new Random(42);
        List<RoadPoint> points = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            points.add(new RoadPoint(i - 100, 64, 20 * Math.sin(i / 10.0)));
        }
        double radius = 12;
        var index = new ChordIndex(points, radius);
        for (int probe = 0; probe < 2000; probe++) {
            var point = new RoadPoint(random.nextInt(256) - 128, 70, random.nextInt(96) - 48);
            var candidates = index.candidates(point);
            for (int i = 1; i < candidates.size(); i++) {
                assertTrue(candidates.get(i - 1) < candidates.get(i));
            }
            for (int i = 0; i + 1 < points.size(); i++) {
                var a = points.get(i);
                var b = points.get(i + 1);
                if (point.x() >= Math.min(a.x(), b.x()) - radius
                        && point.x() <= Math.max(a.x(), b.x()) + radius
                        && point.z() >= Math.min(a.z(), b.z()) - radius
                        && point.z() <= Math.max(a.z(), b.z()) + radius) {
                    assertTrue(candidates.contains(i), "missing chord " + i);
                }
            }
        }
    }

    @Test void longRoadQueriesOnlyNearbyChords() {
        List<RoadPoint> points = new ArrayList<>();
        for (int i = 0; i <= 10000; i++) points.add(new RoadPoint(i * 2, 64, 0));
        var index = new ChordIndex(points, 12);
        assertTrue(index.candidates(new RoadPoint(10000, 70, 0)).size() < 32);
        assertTrue(index.candidates(new RoadPoint(10000, 70, 100)).isEmpty());
    }
}
