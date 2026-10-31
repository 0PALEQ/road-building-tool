package com.cookiecraftmods.roadbuilder.network;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

public final class JunctionConnectorPlanner {
    public List<LaneConnector> plan(
            RoadNodeId nodeId,
            Collection<RoadSegment> incidentSegments,
            Collection<LaneConnector> existingConnectors,
            Supplier<LaneConnectorId> idSupplier
    ) {
        Objects.requireNonNull(nodeId, "nodeId");
        List<RoadSegment> incident = incidentSegments.stream()
                .map(segment -> Objects.requireNonNull(
                        segment,
                        "incidentSegments contains null"
                ))
                .filter(segment -> isIncident(segment, nodeId))
                .sorted(Comparator.comparing(RoadSegment::id))
                .toList();
        Objects.requireNonNull(existingConnectors, "existingConnectors");
        Objects.requireNonNull(idSupplier, "idSupplier");

        Map<RoadLaneId, RoadLane> lanes = new HashMap<>();
        Map<RoadLaneId, RoadSegment> laneSegments = new HashMap<>();
        incident.forEach(segment -> segment.lanes().forEach(lane -> {
            lanes.put(lane.id(), lane);
            laneSegments.put(lane.id(), segment);
        }));

        List<LaneConnector> result = new ArrayList<>();
        Set<String> movements = new HashSet<>();
        existingConnectors.stream()
                .filter(connector -> connector.movement().manual())
                .sorted(Comparator.comparing(LaneConnector::id))
                .filter(connector -> validAtNode(
                        connector,
                        nodeId,
                        lanes,
                        laneSegments
                ))
                .forEach(connector -> add(result, movements, connector));

        StraightThroughConnectorPlanner straight =
                new StraightThroughConnectorPlanner();
        for (int index = 0; index < incident.size(); index++) {
            RoadSegment from = incident.get(index);
            for (LaneConnector connector : straight.plan(
                    nodeId,
                    from,
                    incident.subList(index + 1, incident.size()),
                    idSupplier
            )) {
                add(result, movements, connector);
            }
        }
        for (LaneConnector connector : new IntersectionTurnConnectorPlanner()
                .plan(nodeId, incident, idSupplier)) {
            add(result, movements, connector);
        }

        for (RoadSegment fromSegment : incident) {
            List<RoadLane> incoming = incoming(fromSegment, nodeId);
            for (RoadSegment toSegment : incident) {
                if (fromSegment.id().equals(toSegment.id())
                        || fromSegment.trafficHandedness()
                        != toSegment.trafficHandedness()) {
                    continue;
                }
                List<RoadLane> outgoing = outgoing(toSegment, nodeId);
                if (incoming.size() <= outgoing.size() || outgoing.isEmpty()) {
                    continue;
                }
                for (RoadLane from : incoming) {
                    boolean alreadyConnected = result.stream().anyMatch(connector ->
                            connector.fromLaneId().equals(from.id())
                                    && laneSegments.get(connector.toLaneId())
                                            .id().equals(toSegment.id()));
                    if (alreadyConnected) {
                        continue;
                    }
                    RoadLane nearest = outgoing.stream()
                            .filter(to -> to.component().type()
                                    == from.component().type())
                            .min(Comparator
                                    .comparingDouble((RoadLane to) ->
                                            from.geometry().end().distanceTo(
                                                    to.geometry().start()
                                            ))
                                    .thenComparing(RoadLane::id))
                            .orElse(null);
                    if (nearest != null) {
                        add(result, movements, new LaneConnector(
                                Objects.requireNonNull(
                                        idSupplier.get(),
                                        "idSupplier returned null"
                                ),
                                from.id(),
                                nearest.id(),
                                LaneConnectorMovement.automatic(
                                        LaneMovementType.MERGE
                                )
                        ));
                    }
                }
            }
        }
        for (var attachment : AuxiliaryLanePorts.attachments(nodeId, incident)) {
            RoadLaneId branchLane = attachment.branch().lanes().get(0).id();
            RoadLaneId hostLane = attachment.port().laneId();
            // A side port is a lane-specific connection; keep through-road movements intact.
            result.removeIf(connector -> !connector.movement().manual()
                    && (connector.fromLaneId().equals(branchLane) || connector.toLaneId().equals(branchLane))
                    && !(attachment.atStart()
                            ? connector.fromLaneId().equals(hostLane) && connector.toLaneId().equals(branchLane)
                            : connector.fromLaneId().equals(branchLane) && connector.toLaneId().equals(hostLane)));
            RoadLaneId from = attachment.atStart() ? hostLane : branchLane;
            RoadLaneId to = attachment.atStart() ? branchLane : hostLane;
            if (result.stream().noneMatch(connector -> connector.fromLaneId().equals(from)
                    && connector.toLaneId().equals(to))) {
                result.add(new LaneConnector(idSupplier.get(), from, to,
                        LaneConnectorMovement.automatic(LaneMovementType.MERGE)));
            }
        }
        result.sort(Comparator.comparing(LaneConnector::id));
        return List.copyOf(result);
    }

    private static boolean validAtNode(
            LaneConnector connector,
            RoadNodeId nodeId,
            Map<RoadLaneId, RoadLane> lanes,
            Map<RoadLaneId, RoadSegment> segments
    ) {
        RoadLane from = lanes.get(connector.fromLaneId());
        RoadLane to = lanes.get(connector.toLaneId());
        if (from == null || to == null
                || !LaneConnectionCompatibility.canTransition(
                        from.component().type(),
                        to.component().type()
                )) {
            return false;
        }
        RoadSegment fromSegment = segments.get(from.id());
        RoadSegment toSegment = segments.get(to.id());
        return fromSegment != null
                && toSegment != null
                && !fromSegment.id().equals(toSegment.id())
                && !leavesNode(fromSegment, from, nodeId)
                && leavesNode(toSegment, to, nodeId)
                && !MedianCrossingValidator.crossesMedian(
                        fromSegment,
                        from,
                        toSegment,
                        to,
                        nodeId
                );
    }

    private static void add(
            List<LaneConnector> result,
            Set<String> movements,
            LaneConnector connector
    ) {
        String key = connector.fromLaneId() + "->" + connector.toLaneId();
        if (movements.add(key)) {
            result.add(connector);
        }
    }

    private static List<RoadLane> incoming(
            RoadSegment segment,
            RoadNodeId nodeId
    ) {
        return segment.lanes().stream()
                .filter(lane -> !leavesNode(segment, lane, nodeId))
                .sorted(Comparator
                        .comparingDouble((RoadLane lane) ->
                                lane.component().lateralOffset())
                        .thenComparing(RoadLane::id))
                .toList();
    }

    private static List<RoadLane> outgoing(
            RoadSegment segment,
            RoadNodeId nodeId
    ) {
        return segment.lanes().stream()
                .filter(lane -> leavesNode(segment, lane, nodeId))
                .sorted(Comparator
                        .comparingDouble((RoadLane lane) ->
                                lane.component().lateralOffset())
                        .thenComparing(RoadLane::id))
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
