package com.cookiecraftmods.roadbuilder.network;

import java.util.List;
import java.util.Objects;

public record PreparedRoadAddition(
        long expectedRevision,
        Road road,
        boolean createsRoad,
        List<RoadNode> nodes,
        RoadSegment segment,
        List<LaneConnector> connectors
) {
    public PreparedRoadAddition {
        if (expectedRevision < 0) {
            throw new IllegalArgumentException("expectedRevision must be non-negative");
        }
        Objects.requireNonNull(road, "road");
        nodes = List.copyOf(Objects.requireNonNull(nodes, "nodes"));
        if (nodes.size() > 2) {
            throw new IllegalArgumentException(
                    "a straight-road addition cannot create more than two nodes"
            );
        }
        Objects.requireNonNull(segment, "segment");
        connectors = List.copyOf(Objects.requireNonNull(connectors, "connectors"));
    }
}
