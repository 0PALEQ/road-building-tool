package com.cookiecraftmods.roadbuilder.network;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record PreparedSegmentDirectionInversion(
        long expectedRevision,
        RoadSegment original,
        RoadSegment replacement,
        List<LaneConnectorId> removedConnectorIds,
        List<LaneConnector> addedConnectors,
        Map<RoadNodeId, JunctionDefinition> junctionDefinitions
) {
    public PreparedSegmentDirectionInversion {
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
        junctionDefinitions = Map.copyOf(Objects.requireNonNull(
                junctionDefinitions,
                "junctionDefinitions"
        ));
        if (!original.id().equals(replacement.id())
                || !original.roadId().equals(replacement.roadId())
                || !original.startNodeId().equals(replacement.endNodeId())
                || !original.endNodeId().equals(replacement.startNodeId())
                || !original.referenceGeometry().start().equals(
                        replacement.referenceGeometry().end()
                )
                || !original.referenceGeometry().end().equals(
                        replacement.referenceGeometry().start()
                )) {
            throw new IllegalArgumentException(
                    "direction inversion must preserve identity and swap bounds"
            );
        }
    }
}
