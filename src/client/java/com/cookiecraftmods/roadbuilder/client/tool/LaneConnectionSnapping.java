package com.cookiecraftmods.roadbuilder.client.tool;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.network.AuxiliaryLanePorts;
import com.cookiecraftmods.roadbuilder.network.RoadNode;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

// Lane ports compete with center nodes only during endpoint selection
public final class LaneConnectionSnapping {
    private LaneConnectionSnapping() { }

    public static boolean selectingEndpoint(RoadBuilderTool tool, boolean hasStart, int controls) {
        return switch (tool) {
            case STRAIGHT_ROAD -> true;
            case SIMPLE_CURVE -> !hasStart || controls >= 1;
            case COMPLEX_CURVE -> !hasStart || controls >= 2;
            case FREEHAND_DRAW -> !hasStart || controls == 1;
            default -> false;
        };
    }

    public static Optional<AuxiliaryLanePorts.Port> nearest(RoadPoint cursor,
            Collection<AuxiliaryLanePorts.Port> ports, Collection<RoadNode> nodes, double range) {
        double centerDistance = nodes.stream()
                .filter(node -> Math.abs(node.position().y() - cursor.y()) <= 2)
                .mapToDouble(node -> node.position().distanceTo(cursor)).min().orElse(Double.POSITIVE_INFINITY);
        return ports.stream()
                .filter(port -> Math.hypot(port.position().x() - cursor.x(), port.position().z() - cursor.z()) <= range)
                .filter(port -> Math.abs(port.position().y() - cursor.y()) <= 2)
                .filter(port -> port.position().distanceTo(cursor) < centerDistance)
                .min(Comparator.comparingDouble((AuxiliaryLanePorts.Port port) -> port.position().distanceTo(cursor))
                        .thenComparing(AuxiliaryLanePorts.Port::segmentId)
                        .thenComparing(AuxiliaryLanePorts.Port::laneId));
    }
}
