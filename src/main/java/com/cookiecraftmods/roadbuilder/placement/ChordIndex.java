package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ChordIndex {
    private static final double CELL_SIZE = 16;
    private final Map<Cell, List<Integer>> cells = new HashMap<>();

    ChordIndex(List<RoadPoint> points, double radius) {
        for (int i = 0; i + 1 < points.size(); i++) {
            RoadPoint a = points.get(i), b = points.get(i + 1);
            int minX = cell(Math.min(a.x(), b.x()) - radius);
            int maxX = cell(Math.max(a.x(), b.x()) + radius);
            int minZ = cell(Math.min(a.z(), b.z()) - radius);
            int maxZ = cell(Math.max(a.z(), b.z()) + radius);
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cells.computeIfAbsent(new Cell(x, z), ignored -> new ArrayList<>()).add(i);
                }
            }
        }
    }

    List<Integer> candidates(RoadPoint point) {
        return cells.getOrDefault(new Cell(cell(point.x()), cell(point.z())), List.of());
    }

    private static int cell(double coordinate) {
        return (int) Math.floor(coordinate / CELL_SIZE);
    }

    private record Cell(int x, int z) {}
}
