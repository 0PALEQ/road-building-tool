package com.cookiecraftmods.roadbuilder.network;

import java.util.List;
import java.util.Objects;

public record PreparedSegmentEdit(
        long expectedRevision,
        RoadSegment original,
        RoadSegment replacement,
        List<LaneConnectorId> removedConnectorIds,
        List<LaneConnector> addedConnectors
) {
    public PreparedSegmentEdit {
        if (expectedRevision < 0) {
            throw new IllegalArgumentException(
                    "expectedRevision must be non-negative"
            );
        }
        Objects.requireNonNull(original, "original");
        Objects.requireNonNull(replacement, "replacement");
        removedConnectorIds = List.copyOf(Objects.requireNonNull(
                removedConnectorIds,
                "removedConnectorIds"
        ));
        addedConnectors = List.copyOf(Objects.requireNonNull(
                addedConnectors,
                "addedConnectors"
        ));
        if (!original.id().equals(replacement.id())
                || !original.roadId().equals(replacement.roadId())
                || !original.startNodeId().equals(replacement.startNodeId())
                || !original.endNodeId().equals(replacement.endNodeId())) {
            throw new IllegalArgumentException(
                    "a handle edit must preserve segment, road, and node IDs"
            );
        }
    }
}
