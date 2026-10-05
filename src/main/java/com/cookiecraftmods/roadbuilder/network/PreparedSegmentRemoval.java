package com.cookiecraftmods.roadbuilder.network;

import java.util.List;
import java.util.Objects;

public record PreparedSegmentRemoval(
        long expectedRevision,
        RoadSegmentId segmentId,
        List<RoadNodeId> orphanedNodeIds,
        List<LaneConnectorId> connectorIds,
        boolean removeRoad
) {
    public PreparedSegmentRemoval {
        if (expectedRevision < 0) {
            throw new IllegalArgumentException("expectedRevision must be non-negative");
        }
        Objects.requireNonNull(segmentId, "segmentId");
        orphanedNodeIds = List.copyOf(Objects.requireNonNull(orphanedNodeIds, "orphanedNodeIds"));
        connectorIds = List.copyOf(Objects.requireNonNull(connectorIds, "connectorIds"));
    }
}
