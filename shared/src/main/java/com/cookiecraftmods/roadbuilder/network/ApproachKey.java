package com.cookiecraftmods.roadbuilder.network;

import java.util.Objects;

public record ApproachKey(
        RoadSegmentId segmentId,
        ApproachEnd end
) implements Comparable<ApproachKey> {
    public ApproachKey {
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(end, "end");
    }

    public static ApproachKey at(RoadSegment segment, RoadNodeId nodeId) {
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(nodeId, "nodeId");
        if (segment.startNodeId().equals(nodeId)) {
            return new ApproachKey(segment.id(), ApproachEnd.START);
        }
        if (segment.endNodeId().equals(nodeId)) {
            return new ApproachKey(segment.id(), ApproachEnd.END);
        }
        throw new IllegalArgumentException("segment is not incident to node");
    }

    @Override
    public int compareTo(ApproachKey other) {
        int bySegment = segmentId.compareTo(other.segmentId);
        return bySegment != 0 ? bySegment : end.compareTo(other.end);
    }
}
