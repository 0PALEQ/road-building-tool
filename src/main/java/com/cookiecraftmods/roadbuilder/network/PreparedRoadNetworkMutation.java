package com.cookiecraftmods.roadbuilder.network;

import java.util.List;
import java.util.Objects;

public record PreparedRoadNetworkMutation(
        long expectedRevision,
        Road road,
        boolean createsRoad,
        List<RoadNode> addedNodes,
        List<PreparedSegmentSplit> segmentSplits,
        List<RoadSegment> placedSegments,
        List<LaneConnectorId> removedConnectorIds,
        List<LaneConnector> addedConnectors
) {
    public PreparedRoadNetworkMutation {
        if (expectedRevision < 0) {
            throw new IllegalArgumentException("expectedRevision must be non-negative");
        }
        Objects.requireNonNull(road, "road");
        addedNodes = List.copyOf(Objects.requireNonNull(addedNodes, "addedNodes"));
        segmentSplits = List.copyOf(Objects.requireNonNull(segmentSplits, "segmentSplits"));
        placedSegments = List.copyOf(Objects.requireNonNull(placedSegments, "placedSegments"));
        removedConnectorIds = List.copyOf(Objects.requireNonNull(
                removedConnectorIds,
                "removedConnectorIds"
        ));
        addedConnectors = List.copyOf(Objects.requireNonNull(
                addedConnectors,
                "addedConnectors"
        ));
        if (placedSegments.isEmpty()) {
            throw new IllegalArgumentException("a road placement must add at least one segment");
        }
    }

    public List<RoadSegment> replacementSegments() {
        return segmentSplits.stream()
                .flatMap(split -> split.replacements().stream())
                .toList();
    }

    public List<RoadSegment> allUpsertedSegments() {
        return java.util.stream.Stream.concat(
                replacementSegments().stream(),
                placedSegments.stream()
        ).toList();
    }
}
