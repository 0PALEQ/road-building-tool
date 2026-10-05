package com.cookiecraftmods.roadbuilder.network;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class IntersectionTurnConnectorPlanner {
    public List<LaneConnector> plan(
            RoadNodeId nodeId,
            Collection<RoadSegment> incidentSegments,
            Supplier<LaneConnectorId> idSupplier
    ) {
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(incidentSegments, "incidentSegments");
        Objects.requireNonNull(idSupplier, "idSupplier");
        List<RoadSegment> incident = incidentSegments.stream()
                .map(segment -> Objects.requireNonNull(
                        segment,
                        "incidentSegments contains null"
                ))
                .filter(segment -> isIncident(segment, nodeId))
                .sorted(Comparator.comparing(RoadSegment::id))
                .toList();
        if (incident.size() < 2) {
            return List.of();
        }

        StraightThroughConnectorPlanner straight =
                new StraightThroughConnectorPlanner();
        List<LaneConnector> result = new ArrayList<>();
        for (RoadSegment fromSegment : incident) {
            for (RoadSegment toSegment : incident) {
                if (fromSegment.id().equals(toSegment.id())
                        || fromSegment.trafficHandedness()
                        != toSegment.trafficHandedness()
                        || straight.isStraightContinuation(
                        nodeId,
                        fromSegment,
                        toSegment
                ) || isHorizontallyStraight(
                        nodeId,
                        fromSegment,
                        toSegment
                )) {
                    continue;
                }
                match(
                        fromSegment,
                        toSegment,
                        incomingLanes(fromSegment, nodeId),
                        outgoingLanes(toSegment, nodeId),
                        movementType(nodeId, fromSegment, toSegment),
                        idSupplier,
                        result
                );
            }
        }
        return List.copyOf(result);
    }

    private static void match(
            RoadSegment fromSegment,
            RoadSegment toSegment,
            List<RoadLane> incoming,
            List<RoadLane> outgoing,
            LaneMovementType movementType,
            Supplier<LaneConnectorId> idSupplier,
            List<LaneConnector> result
    ) {
        var preferredSide = movementType == LaneMovementType.RIGHT
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
                    Objects.requireNonNull(
                            idSupplier.get(),
                            "idSupplier returned null"
                    ),
                    candidate.from().id(),
                    candidate.to().id(),
                    LaneConnectorMovement.automatic(movementType)
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
                "turn segments do not share a node"
        );
    }

    private static LaneMovementType movementType(
            RoadNodeId nodeId,
            RoadSegment from,
            RoadSegment to
    ) {
        var fromOut = outwardTangent(nodeId, from);
        var toOut = outwardTangent(nodeId, to);
        double incomingX = -fromOut.x();
        double incomingZ = -fromOut.z();
        double cross = incomingX * toOut.z() - incomingZ * toOut.x();
        return cross >= 0.0
                ? LaneMovementType.RIGHT
                : LaneMovementType.LEFT;
    }

    private static boolean isHorizontallyStraight(
            RoadNodeId nodeId,
            RoadSegment first,
            RoadSegment second
    ) {
        var firstOut = outwardTangent(nodeId, first);
        var secondOut = outwardTangent(nodeId, second);
        double lengths = firstOut.horizontalLength()
                * secondOut.horizontalLength();
        if (lengths <= 1.0e-9) {
            return false;
        }
        double dot = (firstOut.x() * secondOut.x()
                + firstOut.z() * secondOut.z()) / lengths;
        return dot <= -Math.cos(Math.toRadians(5.0));
    }

    private static com.cookiecraftmods.roadbuilder.geometry.RoadVector
            outwardTangent(RoadNodeId nodeId, RoadSegment segment) {
        boolean atStart = segment.startNodeId().equals(nodeId);
        var tangent = segment.referenceGeometry().sample(
                atStart ? 0.0 : segment.referenceGeometry().length()
        ).tangent();
        return atStart ? tangent : tangent.scale(-1.0);
    }

    private static List<RoadLane> incomingLanes(
            RoadSegment segment,
            RoadNodeId nodeId
    ) {
        return segment.lanes().stream()
                .filter(lane -> !leavesNode(segment, lane, nodeId))
                .toList();
    }

    private static List<RoadLane> outgoingLanes(
            RoadSegment segment,
            RoadNodeId nodeId
    ) {
        return segment.lanes().stream()
                .filter(lane -> leavesNode(segment, lane, nodeId))
                .toList();
    }

    private static boolean leavesNode(
            RoadSegment segment,
            RoadLane lane,
            RoadNodeId nodeId
    ) {
        boolean nodeIsStart = segment.startNodeId().equals(nodeId);
        return nodeIsStart
                == lane.component().direction().followsReferenceDirection();
    }

    private static boolean isIncident(
            RoadSegment segment,
            RoadNodeId nodeId
    ) {
        return segment.startNodeId().equals(nodeId)
                || segment.endNodeId().equals(nodeId);
    }

}
