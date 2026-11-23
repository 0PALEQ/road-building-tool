package com.cookiecraftmods.roadbuilder.network;

import com.cookiecraftmods.roadbuilder.geometry.RoadVector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class StraightThroughConnectorPlanner {
    private static final double MINIMUM_STRAIGHT_DOT = -Math.cos(Math.toRadians(5.0));

    public List<LaneConnector> plan(
            RoadNodeId nodeId,
            RoadSegment addedSegment,
            Collection<RoadSegment> existingSegments,
            Supplier<LaneConnectorId> idSupplier
    ) {
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(addedSegment, "addedSegment");
        Objects.requireNonNull(existingSegments, "existingSegments");
        Objects.requireNonNull(idSupplier, "idSupplier");
        requireIncident(addedSegment, nodeId);

        List<LaneConnector> result = new ArrayList<>();
        existingSegments.stream()
                .map(segment -> Objects.requireNonNull(
                        segment,
                        "existingSegments contains null"
                ))
                .filter(segment -> segment.trafficHandedness()
                        == addedSegment.trafficHandedness())
                .filter(segment -> isStraightContinuation(
                        nodeId,
                        addedSegment,
                        segment
                ))
                .sorted(Comparator.comparing(RoadSegment::id))
                .forEach(existing -> {
                    match(
                            existing,
                            addedSegment,
                            incomingLanes(existing, nodeId),
                            outgoingLanes(addedSegment, nodeId),
                            addedSegment.trafficHandedness(),
                            idSupplier,
                            result
                    );
                    match(
                            addedSegment,
                            existing,
                            incomingLanes(addedSegment, nodeId),
                            outgoingLanes(existing, nodeId),
                            addedSegment.trafficHandedness(),
                            idSupplier,
                            result
                    );
                });
        return List.copyOf(result);
    }

    public boolean isStraightContinuation(
            RoadNodeId nodeId,
            RoadSegment first,
            RoadSegment second
    ) {
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        RoadVector firstOutward = outwardVector(first, nodeId);
        RoadVector secondOutward = outwardVector(second, nodeId);
        return areStraightOpposite(firstOutward, secondOutward);
    }

    static boolean areStraightOpposite(RoadVector firstOutward, RoadVector secondOutward) {
        Objects.requireNonNull(firstOutward, "firstOutward");
        Objects.requireNonNull(secondOutward, "secondOutward");
        double firstLength = firstOutward.length();
        double secondLength = secondOutward.length();
        if (firstLength == 0.0 || secondLength == 0.0) {
            return false;
        }
        double dot = (firstOutward.x() * secondOutward.x()
                + firstOutward.y() * secondOutward.y()
                + firstOutward.z() * secondOutward.z())
                / (firstLength * secondLength);
        return dot <= MINIMUM_STRAIGHT_DOT;
    }

    private static void match(
            RoadSegment fromSegment,
            RoadSegment toSegment,
            List<RoadLane> incoming,
            List<RoadLane> outgoing,
            com.cookiecraftmods.roadbuilder.preset.TrafficHandedness
                    trafficHandedness,
            Supplier<LaneConnectorId> idSupplier,
            List<LaneConnector> result
    ) {
        var preferredSide = trafficHandedness
                == com.cookiecraftmods.roadbuilder.preset.TrafficHandedness.RHT
                ? LaneConnectorLaneMatcher.PreferredSide.RIGHT
                : LaneConnectorLaneMatcher.PreferredSide.LEFT;
        for (LaneConnectorLaneMatcher.Match candidate
                : LaneConnectorLaneMatcher.match(
                        incoming,
                        outgoing,
                        preferredSide
                )) {
            if (MedianCrossingValidator.crossesMedian(
                    fromSegment,
                    candidate.from(),
                    toSegment,
                    candidate.to(),
                    sharedNode(fromSegment, toSegment)
            )) {
                continue;
            }
            result.add(new LaneConnector(
                    Objects.requireNonNull(idSupplier.get(), "idSupplier returned null"),
                    candidate.from().id(),
                    candidate.to().id(),
                    LaneConnectorMovement.automatic(
                            LaneMovementType.STRAIGHT
                    )
            ));
        }
    }

    private static RoadNodeId sharedNode(
            RoadSegment first,
            RoadSegment second
    ) {
        if (first.startNodeId().equals(second.startNodeId())
                || first.startNodeId().equals(second.endNodeId())) {
            return first.startNodeId();
        }
        if (first.endNodeId().equals(second.startNodeId())
                || first.endNodeId().equals(second.endNodeId())) {
            return first.endNodeId();
        }
        throw new IllegalArgumentException(
                "straight-through segments do not share a node"
        );
    }

    private static List<RoadLane> incomingLanes(RoadSegment segment, RoadNodeId nodeId) {
        return segment.lanes().stream()
                .filter(lane -> !leavesNode(segment, lane, nodeId))
                .toList();
    }

    private static List<RoadLane> outgoingLanes(RoadSegment segment, RoadNodeId nodeId) {
        return segment.lanes().stream()
                .filter(lane -> leavesNode(segment, lane, nodeId))
                .toList();
    }

    private static boolean leavesNode(
            RoadSegment segment,
            RoadLane lane,
            RoadNodeId nodeId
    ) {
        requireIncident(segment, nodeId);
        boolean nodeIsStart = segment.startNodeId().equals(nodeId);
        return nodeIsStart == lane.component().direction().followsReferenceDirection();
    }

    private static RoadVector outwardVector(RoadSegment segment, RoadNodeId nodeId) {
        requireIncident(segment, nodeId);
        boolean atStart = segment.startNodeId().equals(nodeId);
        RoadVector tangent = segment.referenceGeometry().sample(
                atStart ? 0.0 : segment.referenceGeometry().length()
        ).tangent();
        return atStart ? tangent : tangent.scale(-1.0);
    }

    private static void requireIncident(RoadSegment segment, RoadNodeId nodeId) {
        if (!segment.startNodeId().equals(nodeId) && !segment.endNodeId().equals(nodeId)) {
            throw new IllegalArgumentException(
                    "segment " + segment.id() + " is not incident to node " + nodeId
            );
        }
    }

}
