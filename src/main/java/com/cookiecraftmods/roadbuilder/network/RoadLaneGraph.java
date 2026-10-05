package com.cookiecraftmods.roadbuilder.network;

import com.cookiecraftmods.roadbuilder.geometry.ReferenceGeometry;
import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Read-only, integration-facing view of lane paths and their junction edges.
 */
public final class RoadLaneGraph {
    private final RoadGraphSnapshot snapshot;
    private final Map<RoadLaneId, LanePath> lanePaths;
    private final List<ConnectionPoint> connectionPoints;
    private final Map<LaneConnectorId, ConnectorPath> connectorPaths;
    private final Map<RoadLaneId, List<LaneConnectorId>> outgoing;
    private final Map<RoadLaneId, List<LaneConnectorId>> incoming;

    private RoadLaneGraph(
            RoadGraphSnapshot snapshot,
            Map<RoadLaneId, LanePath> lanePaths,
            List<ConnectionPoint> connectionPoints,
            Map<LaneConnectorId, ConnectorPath> connectorPaths,
            Map<RoadLaneId, List<LaneConnectorId>> outgoing,
            Map<RoadLaneId, List<LaneConnectorId>> incoming
    ) {
        this.snapshot = snapshot;
        this.lanePaths = Collections.unmodifiableMap(lanePaths);
        this.connectionPoints = List.copyOf(connectionPoints);
        this.connectorPaths = Collections.unmodifiableMap(connectorPaths);
        this.outgoing = immutableLists(outgoing);
        this.incoming = immutableLists(incoming);
    }

    public static RoadLaneGraph from(RoadGraphSnapshot snapshot) {
        snapshot = Objects.requireNonNull(snapshot, "snapshot");
        Map<LaneConnectorId, ConnectorPath> paths = new LinkedHashMap<>();
        Map<RoadLaneId, List<LaneConnectorId>> outgoing =
                new LinkedHashMap<>();
        Map<RoadLaneId, List<LaneConnectorId>> incoming =
                new LinkedHashMap<>();
        Map<RoadLaneId, Double> laneStarts = new LinkedHashMap<>();
        Map<RoadLaneId, Double> laneEnds = new LinkedHashMap<>();
        snapshot.lanes().forEach((id, lane) -> {
            laneStarts.put(id, 0.0);
            laneEnds.put(id, lane.geometry().length());
        });
        List<ConnectionPoint> connectionPoints = connectionPoints(
                snapshot,
                laneStarts,
                laneEnds
        );
        LaneConnectorGeometryPlanner geometryPlanner =
                new LaneConnectorGeometryPlanner();
        for (LaneConnector connector : snapshot.connectors().values()) {
            if (!connector.movement().allowed()) {
                continue;
            }
            RoadLane from = snapshot.lanes().get(connector.fromLaneId());
            RoadLane to = snapshot.lanes().get(connector.toLaneId());
            if (from == null || to == null) {
                continue;
            }
            RoadNodeId junction = sharedNode(
                    snapshot.segments().get(from.segmentId()),
                    snapshot.segments().get(to.segmentId())
            );
            Optional<LaneConnectorGeometryPlanner.ConnectorGeometryPlan>
                    planned;
            try {
                planned = Optional.of(geometryPlanner.planDetailed(
                        connector,
                        snapshot
                ));
            } catch (IllegalArgumentException exception) {
                planned = Optional.empty();
            }
            Optional<ReferenceGeometry> geometry = planned.map(
                    LaneConnectorGeometryPlanner.ConnectorGeometryPlan::geometry
            );
            paths.put(connector.id(), new ConnectorPath(
                    connector,
                    junction,
                    geometry,
                    planned.isPresent()
                            ? OptionalDouble.of(planned.orElseThrow()
                                    .fromLaneEndDistance())
                            : OptionalDouble.empty(),
                    planned.isPresent()
                            ? OptionalDouble.of(planned.orElseThrow()
                                    .toLaneStartDistance())
                            : OptionalDouble.empty()
            ));
            planned.ifPresent(plan -> {
                laneEnds.computeIfPresent(
                        connector.fromLaneId(),
                        (ignored, current) -> Math.min(
                                current,
                                plan.fromLaneEndDistance()
                        )
                );
                laneStarts.computeIfPresent(
                        connector.toLaneId(),
                        (ignored, current) -> Math.max(
                                current,
                                plan.toLaneStartDistance()
                        )
                );
            });
            outgoing.computeIfAbsent(
                    connector.fromLaneId(), ignored -> new ArrayList<>()
            ).add(connector.id());
            incoming.computeIfAbsent(
                    connector.toLaneId(), ignored -> new ArrayList<>()
            ).add(connector.id());
        }
        Map<RoadLaneId, LanePath> lanePaths = new LinkedHashMap<>();
        snapshot.lanes().forEach((id, lane) -> {
            double start = laneStarts.getOrDefault(id, 0.0);
            double end = laneEnds.getOrDefault(
                    id,
                    lane.geometry().length()
            );
            if (start > end) {
                double meetingPoint = (start + end) / 2.0;
                start = meetingPoint;
                end = meetingPoint;
            }
            lanePaths.put(id, new LanePath(lane, start, end));
        });
        return new RoadLaneGraph(
                snapshot,
                lanePaths,
                connectionPoints,
                paths,
                outgoing,
                incoming
        );
    }

    public RoadGraphSnapshot snapshot() {
        return snapshot;
    }

    public Map<RoadLaneId, RoadLane> lanes() {
        return snapshot.lanes();
    }

    public Map<RoadLaneId, LanePath> lanePaths() {
        return lanePaths;
    }

    public List<ConnectionPoint> connectionPoints() {
        return connectionPoints;
    }

    public boolean canConnect(
            ConnectionPoint source,
            ConnectionPoint target
    ) {
        if (source == null || !source.canConnectTo(target)) {
            return false;
        }
        RoadLane from = snapshot.lanes().get(source.laneId());
        RoadLane to = snapshot.lanes().get(target.laneId());
        if (from == null
                || to == null
                || !LaneConnectionCompatibility.canTransition(
                        from.component().type(),
                        to.component().type()
                )) {
            return false;
        }
        RoadSegment fromSegment = snapshot.segments().get(from.segmentId());
        RoadSegment toSegment = snapshot.segments().get(to.segmentId());
        if (fromSegment == null || toSegment == null) {
            return true;
        }
        return !MedianCrossingValidator.crossesMedian(
                fromSegment,
                from,
                toSegment,
                to,
                source.nodeId()
        );
    }

    public Map<LaneConnectorId, ConnectorPath> connectorPaths() {
        return connectorPaths;
    }

    public List<LaneConnectorId> outgoing(RoadLaneId laneId) {
        return outgoing.getOrDefault(
                Objects.requireNonNull(laneId, "laneId"),
                List.of()
        );
    }

    public List<LaneConnectorId> incoming(RoadLaneId laneId) {
        return incoming.getOrDefault(
                Objects.requireNonNull(laneId, "laneId"),
                List.of()
        );
    }

    public Optional<LaneHit> nearestLane(
            RoadPoint point,
            double maximumDistance
    ) {
        requireSelectionDistance(maximumDistance);
        LaneHit best = null;
        for (LanePath path : lanePaths.values()) {
            RoadLane lane = path.lane();
            SampleHit hit = nearest(
                    point,
                    lane.geometry(),
                    path.startDistance(),
                    path.endDistance()
            );
            if (hit.distance() <= maximumDistance
                    && (best == null || hit.distance() < best.distance())) {
                best = new LaneHit(
                        lane.id(),
                        hit.pathDistance(),
                        hit.position(),
                        hit.distance()
                );
            }
        }
        return Optional.ofNullable(best);
    }

    public Optional<ConnectorHit> nearestConnector(
            RoadPoint point,
            double maximumDistance
    ) {
        requireSelectionDistance(maximumDistance);
        ConnectorHit best = null;
        for (ConnectorPath path : connectorPaths.values()) {
            if (path.geometry().isEmpty()) {
                continue;
            }
            SampleHit hit = nearest(point, path.geometry().orElseThrow());
            if (hit.distance() <= maximumDistance
                    && (best == null || hit.distance() < best.distance())) {
                best = new ConnectorHit(
                        path.connector().id(),
                        hit.pathDistance(),
                        hit.position(),
                        hit.distance()
                );
            }
        }
        return Optional.ofNullable(best);
    }

    private static SampleHit nearest(
            RoadPoint point,
            ReferenceGeometry geometry
    ) {
        return nearest(point, geometry, 0.0, geometry.length());
    }

    private static SampleHit nearest(
            RoadPoint point,
            ReferenceGeometry geometry,
            double startDistance,
            double endDistance
    ) {
        Objects.requireNonNull(point, "point");
        double length = endDistance - startDistance;
        int samples = Math.min(2_048, Math.max(
                1,
                (int) Math.ceil(length / 0.5)
        ));
        SampleHit best = null;
        for (int index = 0; index <= samples; index++) {
            double pathDistance = index == samples
                    ? endDistance
                    : Math.min(
                            endDistance,
                            startDistance + length * index / samples
                    );
            RoadPoint candidate = geometry.sample(pathDistance).position();
            double distance = point.distanceTo(candidate);
            if (best == null || distance < best.distance()) {
                best = new SampleHit(pathDistance, candidate, distance);
            }
        }
        return Objects.requireNonNull(best, "geometry produced no samples");
    }

    private static void requireSelectionDistance(double maximumDistance) {
        if (!Double.isFinite(maximumDistance) || maximumDistance < 0.0) {
            throw new IllegalArgumentException(
                    "maximumDistance must be finite and non-negative"
            );
        }
    }

    private static RoadNodeId sharedNode(
            RoadSegment first,
            RoadSegment second
    ) {
        Objects.requireNonNull(first, "connector source segment is missing");
        Objects.requireNonNull(second, "connector target segment is missing");
        if (first.startNodeId().equals(second.startNodeId())
                || first.startNodeId().equals(second.endNodeId())) {
            return first.startNodeId();
        }
        if (first.endNodeId().equals(second.startNodeId())
                || first.endNodeId().equals(second.endNodeId())) {
            return first.endNodeId();
        }
        throw new IllegalArgumentException(
                "lane connector segments do not share a node"
        );
    }

    private static List<ConnectionPoint> connectionPoints(
            RoadGraphSnapshot snapshot,
            Map<RoadLaneId, Double> laneStarts,
            Map<RoadLaneId, Double> laneEnds
    ) {
        List<ConnectionPoint> points = new ArrayList<>();
        for (RoadNode node : snapshot.nodes().values()) {
            List<RoadSegment> incident = snapshot.segments().values().stream()
                    .filter(segment -> segment.startNodeId().equals(node.id())
                            || segment.endNodeId().equals(node.id()))
                    .toList();
            if (incident.size() < 2) {
                continue;
            }
            JunctionSolveResult result = new JunctionSolver().solve(
                    node,
                    incident,
                    snapshot.junctionDefinitions().get(node.id())
            );
            if (!(result instanceof JunctionSolveResult.Success solved)) {
                continue;
            }
            for (SolvedJunctionApproach approach : solved.approaches()) {
                RoadSegment segment = approach.segment();
                for (RoadLane lane : segment.lanes()) {
                    boolean outgoing = leavesNode(segment, lane, node.id());
                    double trim = LaneConnectorGeometryPlanner.boundedTrim(
                            approach.trimDistance(),
                            lane.geometry().length()
                    );
                    double distance = outgoing
                            ? trim
                            : lane.geometry().length() - trim;
                    points.add(new ConnectionPoint(
                            lane.id(),
                            node.id(),
                            outgoing
                                    ? ConnectionPointKind.OUTGOING
                                    : ConnectionPointKind.INCOMING,
                            distance,
                            lane.geometry().sample(distance).position()
                    ));
                    if (outgoing) {
                        laneStarts.computeIfPresent(
                                lane.id(),
                                (ignored, current) -> Math.max(
                                        current,
                                        distance
                                )
                        );
                    } else {
                        laneEnds.computeIfPresent(
                                lane.id(),
                                (ignored, current) -> Math.min(
                                        current,
                                        distance
                                )
                        );
                    }
                }
            }
        }
        return List.copyOf(points);
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

    private static <K> Map<K, List<LaneConnectorId>> immutableLists(
            Map<K, List<LaneConnectorId>> source
    ) {
        Map<K, List<LaneConnectorId>> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        return Collections.unmodifiableMap(copy);
    }

    public record ConnectorPath(
            LaneConnector connector,
            RoadNodeId junctionNodeId,
            Optional<ReferenceGeometry> geometry,
            OptionalDouble fromLaneEndDistance,
            OptionalDouble toLaneStartDistance
    ) {
        public ConnectorPath {
            Objects.requireNonNull(connector, "connector");
            Objects.requireNonNull(junctionNodeId, "junctionNodeId");
            Objects.requireNonNull(geometry, "geometry");
            Objects.requireNonNull(
                    fromLaneEndDistance,
                    "fromLaneEndDistance"
            );
            Objects.requireNonNull(
                    toLaneStartDistance,
                    "toLaneStartDistance"
            );
        }
    }

    public record LanePath(
            RoadLane lane,
            double startDistance,
            double endDistance
    ) {
        public LanePath {
            Objects.requireNonNull(lane, "lane");
            double length = lane.geometry().length();
            if (!Double.isFinite(startDistance)
                    || !Double.isFinite(endDistance)
                    || startDistance < 0.0
                    || startDistance > endDistance
                    || endDistance > length) {
                throw new IllegalArgumentException(
                        "lane path distances must stay within lane geometry"
                );
            }
        }
    }

    public record ConnectionPoint(
            RoadLaneId laneId,
            RoadNodeId nodeId,
            ConnectionPointKind kind,
            double pathDistance,
            RoadPoint position
    ) {
        public ConnectionPoint {
            Objects.requireNonNull(laneId, "laneId");
            Objects.requireNonNull(nodeId, "nodeId");
            Objects.requireNonNull(kind, "kind");
            if (!Double.isFinite(pathDistance) || pathDistance < 0.0) {
                throw new IllegalArgumentException(
                        "connection point distance must be finite and non-negative"
                );
            }
            Objects.requireNonNull(position, "position");
        }

        public boolean canConnectTo(ConnectionPoint target) {
            return kind == ConnectionPointKind.INCOMING
                    && target != null
                    && target.kind == ConnectionPointKind.OUTGOING
                    && nodeId.equals(target.nodeId)
                    && !laneId.equals(target.laneId);
        }
    }

    public enum ConnectionPointKind {
        INCOMING,
        OUTGOING
    }

    public record LaneHit(
            RoadLaneId laneId,
            double pathDistance,
            RoadPoint position,
            double distance
    ) {
    }

    public record ConnectorHit(
            LaneConnectorId connectorId,
            double pathDistance,
            RoadPoint position,
            double distance
    ) {
    }

    private record SampleHit(
            double pathDistance,
            RoadPoint position,
            double distance
    ) {
    }
}
