package com.cookiecraftmods.roadbuilder.client.tool;

import com.cookiecraftmods.roadbuilder.geometry.OffsetGeometry;
import com.cookiecraftmods.roadbuilder.geometry.ReferenceGeometry;
import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.network.RoadGraphSnapshot;
import com.cookiecraftmods.roadbuilder.network.RoadNode;
import com.cookiecraftmods.roadbuilder.network.RoadSegment;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
// This makes distant roads never reach the expensive snap solver
final class RoadSnapIndex {
    private static final double CELL_SIZE = 64.0;
    private final Map<Cell, Set<RoadSegment>> segments = new HashMap<>();
    private final Map<Cell, Set<RoadNode>> nodes = new HashMap<>();

    RoadSnapIndex(RoadGraphSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        for (RoadNode node : snapshot.nodes().values()) {
            nodes.computeIfAbsent(cell(node.position()), ignored -> new LinkedHashSet<>()).add(node);
        }
        for (RoadSegment segment : snapshot.segments().values()) {
            ReferenceGeometry geometry = segment.referenceGeometry();
            double margin = segment.presetSnapshot().surfaceBlockWidth() / 2.0 + CELL_SIZE;
            while (geometry instanceof OffsetGeometry offset) {
                margin += Math.abs(offset.lateralOffset());
                geometry = offset.reference();
            }
            int steps = Math.max(1, (int) Math.ceil(geometry.length() / CELL_SIZE));
            for (int i = 0; i <= steps; i++) {
                RoadPoint point = geometry.sample(i == steps ? geometry.length()
                        : geometry.length() * i / steps).position();
                add(segment, point, margin);
            }
        }
    }

    private void add(RoadSegment segment, RoadPoint point, double radius) {
        for (int x = coordinate(point.x() - radius); x <= coordinate(point.x() + radius); x++) {
            for (int z = coordinate(point.z() - radius); z <= coordinate(point.z() + radius); z++) {
                segments.computeIfAbsent(new Cell(x, z), ignored -> new LinkedHashSet<>()).add(segment);
            }
        }
    }

    List<RoadNode> nodesNear(RoadPoint point) {
        return nodesNear(point, 32.0);
    }

    List<RoadNode> nodesNear(RoadPoint point, double range) {
        return List.copyOf(query(nodes, point, Math.max(32.0, range)));
    }

    List<RoadSegment> segmentsNear(RoadPoint point, RoadPoint start, double range) {
        Set<RoadSegment> result = query(segments, point, Math.max(32.0, range));
        if (start != null) {
            result.addAll(query(segments, start, 1.0));
        }
        return List.copyOf(result);
    }

    private static <T> Set<T> query(Map<Cell, Set<T>> index, RoadPoint point, double radius) {
        Set<T> result = new LinkedHashSet<>();
        for (int x = coordinate(point.x() - radius); x <= coordinate(point.x() + radius); x++) {
            for (int z = coordinate(point.z() - radius); z <= coordinate(point.z() + radius); z++) {
                Set<T> values = index.get(new Cell(x, z));
                if (values != null) {
                    result.addAll(values);
                }
            }
        }
        return result;
    }

    private static Cell cell(RoadPoint point) {
        return new Cell(coordinate(point.x()), coordinate(point.z()));
    }

    private static int coordinate(double value) {
        return (int) Math.floor(value / CELL_SIZE);
    }

    private record Cell(int x, int z) { }
}
