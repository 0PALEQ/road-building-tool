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

    public PreparedRoadNetworkMutation withSunkenRoad(boolean enabled) {
        return new PreparedRoadNetworkMutation(expectedRevision, road, createsRoad, addedNodes,
                segmentSplits, placedSegments.stream().map(segment -> RoadSegment.path(
                        segment.id(), segment.roadId(), segment.startNodeId(), segment.endNodeId(),
                        segment.referenceGeometry(), segment.presetId(), segment.presetRevision(),
                        segment.presetSnapshot().withSunkenRoad(enabled), segment.trafficHandedness(),
                        segment.lanes().stream().map(RoadLane::id).toList())).toList(),
                removedConnectorIds, addedConnectors);
    }

    public boolean rebuildsExistingGeometry() {
        return segmentSplits.stream().anyMatch(split -> {
            var children=split.replacements();
            if (!children.get(0).referenceGeometry().start().equals(split.original().referenceGeometry().start())
                    || !children.get(children.size()-1).referenceGeometry().end().equals(split.original().referenceGeometry().end()))
                return false; // Roundabout conversion trims/replaces topology, not elevation.
            for(int i=1;i<children.size();i++) if(!children.get(i-1).endNodeId().equals(children.get(i).startNodeId())) return false;
            return Math.abs(split.original().referenceGeometry().length()
                    - children.stream().mapToDouble(s -> s.referenceGeometry().length()).sum())>1e-6;
        });
    }

    public List<RoadSegment> materializedSegments() {
        return rebuildsExistingGeometry() ? allUpsertedSegments() : placedSegments;
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
