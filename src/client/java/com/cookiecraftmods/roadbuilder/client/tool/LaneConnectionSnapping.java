package com.cookiecraftmods.roadbuilder.client.tool;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.RoadVector;
import com.cookiecraftmods.roadbuilder.network.RoadGraphSnapshot;
import com.cookiecraftmods.roadbuilder.network.AuxiliaryLanePorts;
import com.cookiecraftmods.roadbuilder.network.RoadNode;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

// Lane ports compete with center nodes only during endpoint selection
public final class LaneConnectionSnapping {
    private LaneConnectionSnapping() { }

    /** Continue an open road end, regardless of its reference-line orientation. */
    public static RoadVector openEndDirection(RoadGraphSnapshot graph, RoadPoint position, boolean atStart) {
        if (graph == null) return null;
        var node = graph.nodes().values().stream()
                .filter(candidate -> candidate.position().distanceTo(position) < 1.0e-6)
                .findFirst().orElse(null);
        if (node == null) return null;
        var incident = graph.segments().values().stream()
                .filter(segment -> segment.startNodeId().equals(node.id())
                        || segment.endNodeId().equals(node.id())).toList();
        // Junctions have several possible arms; keep the player's direction there.
        if (incident.size() != 1) return null;
        var segment = incident.get(0);
        var geometry = segment.referenceGeometry();
        boolean segmentStart = segment.startNodeId().equals(node.id());
        var tangent = geometry.sample(segmentStart ? 0 : geometry.length()).tangent();
        return tangent.scale(segmentStart == atStart ? -1 : 1);
    }

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
