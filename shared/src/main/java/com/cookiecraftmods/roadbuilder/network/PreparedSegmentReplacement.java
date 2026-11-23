package com.cookiecraftmods.roadbuilder.network;

import java.util.List;
import java.util.Objects;

public record PreparedSegmentReplacement(
        long expectedRevision,
        RoadSegment original,
        RoadSegment replacement,
        List<LaneConnectorId> removedConnectorIds,
        List<LaneConnector> addedConnectors
) {
    public PreparedSegmentReplacement {
        if (expectedRevision < 0) {
            throw new IllegalArgumentException("expectedRevision must be non-negative");
        }
        Objects.requireNonNull(original, "original");
        Objects.requireNonNull(replacement, "replacement");
        removedConnectorIds = List.copyOf(Objects.requireNonNull(
                removedConnectorIds, "removedConnectorIds"
        ));
        addedConnectors = List.copyOf(Objects.requireNonNull(
                addedConnectors, "addedConnectors"
        ));
        if (!original.id().equals(replacement.id())
                || !original.roadId().equals(replacement.roadId())
                || !original.startNodeId().equals(replacement.startNodeId())
                || !original.endNodeId().equals(replacement.endNodeId())
                || !original.referenceGeometry().equals(
                        replacement.referenceGeometry())) {
            throw new IllegalArgumentException(
                    "replacement must preserve segment identity and geometry"
            );
        }
    }
}
