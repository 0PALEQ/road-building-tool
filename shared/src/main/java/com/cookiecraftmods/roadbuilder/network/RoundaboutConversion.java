package com.cookiecraftmods.roadbuilder.network;

import com.cookiecraftmods.roadbuilder.geometry.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pure geometry planning for replacing a junction by a circulating carriageway. */
final class RoundaboutConversion {
    record Approach(RoadSegment source, boolean atStart, RoadPoint port, ReferenceGeometry trimmed) { }
    record Plan(List<CubicBezierGeometry> spans, List<Approach> approaches) { }

    static Plan plan(RoundaboutGeometry ring, RoadNode center, List<RoadSegment> incident,
                     boolean clockwise) {
        List<Approach> approaches = new ArrayList<>();
        List<Double> angles = new ArrayList<>();
        for (RoadSegment segment : incident) {
            boolean atStart = segment.startNodeId().equals(center.id());
            ReferenceGeometry path = segment.referenceGeometry();
            double length = path.length();
            double low = 0;
            double high = 0;
            // Find the first outward crossing, including curved approaches.
            for (double distance = 0.25; distance <= length + 0.25; distance += 0.25) {
                high = Math.min(length, distance);
                RoadPoint point = path.sample(atStart ? high : length - high).position();
                if (horizontalDistance(point, ring.center()) >= ring.radius()) break;
                low = high;
            }
            if (high >= length - RoadSegment.MINIMUM_BUILD_LENGTH || low == high) {
                throw new IllegalArgumentException("roundabout exceeds the available approach length");
            }
            for (int i = 0; i < 40; i++) {
                double mid = (low + high) / 2;
                RoadPoint point = path.sample(atStart ? mid : length - mid).position();
                if (horizontalDistance(point, ring.center()) < ring.radius()) low = mid;
                else high = mid;
            }
            double cut = atStart ? high : length - high;
            RoadPoint crossing = path.sample(cut).position();
            double angle = Math.atan2(crossing.z() - ring.center().z(), crossing.x() - ring.center().x());
            if (angle < 0) angle += Math.PI * 2;
            angles.add(angle);
            RoadPoint port = point(ring, angle);
            List<ReferenceGeometry> parts = ReferenceGeometryEditor.split(path, List.of(cut / length), List.of(port));
            approaches.add(new Approach(segment, atStart, port, parts.get(atStart ? 1 : 0)));
        }
        angles.sort(Comparator.naturalOrder());
        List<Double> boundaries = new ArrayList<>();
        for (int i = 0; i < angles.size(); i++) {
            double start = angles.get(i);
            double end = i + 1 < angles.size() ? angles.get(i + 1) : angles.get(0) + Math.PI * 2;
            if ((end - start) * ring.radius() < RoadSegment.MINIMUM_BUILD_LENGTH) {
                throw new IllegalArgumentException("roundabout approaches are too close together");
            }
            int count = (int) Math.ceil((end - start) / (Math.PI / 2));
            for (int j = 0; j < count; j++) boundaries.add(start + (end - start) * j / count);
        }
        List<CubicBezierGeometry> spans = new ArrayList<>();
        for (int i = 0; i < boundaries.size(); i++) {
            double a = boundaries.get(i);
            double b = i + 1 < boundaries.size() ? boundaries.get(i + 1) : boundaries.get(0) + Math.PI * 2;
            RoadPoint start = point(ring, a), end = point(ring, b);
            double handle = 4.0 / 3.0 * Math.tan((b - a) / 4) * ring.radius();
            spans.add(new CubicBezierGeometry(start,
                    start.add(new RoadVector(-Math.sin(a), 0, Math.cos(a)).scale(handle)),
                    end.subtract(new RoadVector(-Math.sin(b), 0, Math.cos(b)).scale(handle)), end));
        }
        if (!clockwise) {
            List<CubicBezierGeometry> reversed = new ArrayList<>();
            for (int i = spans.size() - 1; i >= 0; i--) {
                var span = spans.get(i);
                reversed.add(new CubicBezierGeometry(span.end(), span.endControl(), span.startControl(), span.start()));
            }
            spans = reversed;
        }
        return new Plan(List.copyOf(spans), List.copyOf(approaches));
    }

    private static RoadPoint point(RoundaboutGeometry ring, double angle) {
        return new RoadPoint(ring.center().x() + Math.cos(angle) * ring.radius(), ring.center().y(),
                ring.center().z() + Math.sin(angle) * ring.radius());
    }

    private static double horizontalDistance(RoadPoint a, RoadPoint b) {
        return Math.hypot(a.x() - b.x(), a.z() - b.z());
    }
}
