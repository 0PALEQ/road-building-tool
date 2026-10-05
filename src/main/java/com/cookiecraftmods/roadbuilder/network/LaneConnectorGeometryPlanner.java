package com.cookiecraftmods.roadbuilder.network;

import com.cookiecraftmods.roadbuilder.geometry.CubicBezierGeometry;
import com.cookiecraftmods.roadbuilder.geometry.PathSample;
import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.RoadVector;

import java.util.Map;
import java.util.Objects;
import java.util.List;

public final class LaneConnectorGeometryPlanner {
    private static final double MINIMUM_TRIM = 0.5;
    private static final double MAXIMUM_TRIM = 32.0;
    private static final double MINIMUM_HANDLE = 0.25;

    public CubicBezierGeometry plan(
            LaneConnector connector,
            Map<RoadLaneId, RoadLane> lanes,
            double fromTrimDistance,
            double toTrimDistance
    ) {
        return planDetailed(
                connector,
                lanes,
                fromTrimDistance,
                toTrimDistance
        ).geometry();
    }

    public ConnectorGeometryPlan planDetailed(
            LaneConnector connector,
            Map<RoadLaneId, RoadLane> lanes,
            double fromTrimDistance,
            double toTrimDistance
    ) {
        Objects.requireNonNull(connector, "connector");
        Objects.requireNonNull(lanes, "lanes");
        RoadLane from = Objects.requireNonNull(
                lanes.get(connector.fromLaneId()),
                "connector from lane is missing"
        );
        RoadLane to = Objects.requireNonNull(
                lanes.get(connector.toLaneId()),
                "connector to lane is missing"
        );

        double fromTrim = boundedTrim(
                fromTrimDistance,
                from.geometry().length()
        );
        double toTrim = boundedTrim(
                toTrimDistance,
                to.geometry().length()
        );
        PathSample start = from.geometry().sample(
                from.geometry().length() - fromTrim
        );
        PathSample end = to.geometry().sample(toTrim);
        double chordLength = start.position().distanceTo(end.position());
        if (chordLength < 1.0e-6) {
            throw new IllegalArgumentException(
                    "connector trim boundaries must be distinct"
            );
        }

        double handleLength = Math.max(
                MINIMUM_HANDLE,
                Math.min(
                        chordLength * 0.45,
                        Math.min(fromTrim, toTrim)
                )
        );
        RoadPoint startControl = start.position().add(
                horizontalUnit(start.tangent()).scale(handleLength)
        );
        RoadPoint endControl = end.position().add(
                horizontalUnit(end.tangent()).scale(-handleLength)
        );
        return new ConnectorGeometryPlan(
                new CubicBezierGeometry(
                        start.position(),
                        startControl,
                        endControl,
                        end.position()
                ),
                from.geometry().length() - fromTrim,
                toTrim
        );
    }

    public CubicBezierGeometry plan(
            LaneConnector connector,
            RoadGraphSnapshot snapshot
    ) {
        return planDetailed(connector, snapshot).geometry();
    }

    public ConnectorGeometryPlan planDetailed(
            LaneConnector connector,
            RoadGraphSnapshot snapshot
    ) {
        Objects.requireNonNull(connector, "connector");
        Objects.requireNonNull(snapshot, "snapshot");
        RoadLane from = requireLane(snapshot, connector.fromLaneId());
        RoadLane to = requireLane(snapshot, connector.toLaneId());
        RoadSegment fromSegment = requireSegment(snapshot, from.segmentId());
        RoadSegment toSegment = requireSegment(snapshot, to.segmentId());
        RoadNodeId nodeId = sharedNode(fromSegment, toSegment);
        if (leavesNode(fromSegment, from, nodeId)
                || !leavesNode(toSegment, to, nodeId)) {
            throw new IllegalArgumentException(
                    "connector lane directions do not cross their shared node"
            );
        }
        RoadNode node = Objects.requireNonNull(
                snapshot.nodes().get(nodeId),
                "connector node is missing"
        );
        List<RoadSegment> incident = snapshot.segments().values().stream()
                .filter(segment -> segment.startNodeId().equals(nodeId)
                        || segment.endNodeId().equals(nodeId))
                .toList();
        JunctionSolveResult result = new JunctionSolver().solve(
                node,
                incident,
                snapshot.junctionDefinitions().get(nodeId)
        );
        if (!(result instanceof JunctionSolveResult.Success solved)) {
            throw new IllegalArgumentException(
                    ((JunctionSolveResult.Failure) result).diagnostic()
            );
        }
        double fromTrim = trim(solved, fromSegment.id());
        double toTrim = trim(solved, toSegment.id());
        return planDetailed(
                connector,
                snapshot.lanes(),
                fromTrim,
                toTrim
        );
    }

    static double boundedTrim(double requested, double laneLength) {
        if (!Double.isFinite(requested) || requested <= 0.0) {
            throw new IllegalArgumentException(
                    "connector trim distance must be finite and positive"
            );
        }
        if (!Double.isFinite(laneLength) || laneLength <= 0.0) {
            throw new IllegalArgumentException(
                    "connector lane length must be finite and positive"
            );
        }
        return Math.min(
                Math.max(MINIMUM_TRIM, Math.min(requested, MAXIMUM_TRIM)),
                laneLength * 0.49
        );
    }

    private static RoadVector horizontalUnit(RoadVector tangent) {
        double horizontalLength = tangent.horizontalLength();
        if (horizontalLength <= 1.0e-9) {
            throw new IllegalArgumentException(
                    "connector lane tangent must have a horizontal direction"
            );
        }
        return new RoadVector(
                tangent.x() / horizontalLength,
                0.0,
                tangent.z() / horizontalLength
        );
    }

    private static RoadLane requireLane(
            RoadGraphSnapshot snapshot,
            RoadLaneId laneId
    ) {
        RoadLane lane = snapshot.lanes().get(laneId);
        if (lane == null) {
            throw new IllegalArgumentException(
                    "connector references missing lane " + laneId
            );
        }
        return lane;
    }

    private static RoadSegment requireSegment(
            RoadGraphSnapshot snapshot,
            RoadSegmentId segmentId
    ) {
        RoadSegment segment = snapshot.segments().get(segmentId);
        if (segment == null) {
            throw new IllegalArgumentException(
                    "connector lane references missing segment " + segmentId
            );
        }
        return segment;
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
                "connector segments do not share a junction node"
        );
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

    private static double trim(
            JunctionSolveResult.Success solved,
            RoadSegmentId segmentId
    ) {
        return solved.approaches().stream()
                .filter(approach ->
                        approach.segment().id().equals(segmentId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "connector segment is absent from junction solve"
                ))
                .trimDistance();
    }

    public record ConnectorGeometryPlan(
            CubicBezierGeometry geometry,
            double fromLaneEndDistance,
            double toLaneStartDistance
    ) {
        public ConnectorGeometryPlan {
            Objects.requireNonNull(geometry, "geometry");
            if (!Double.isFinite(fromLaneEndDistance)
                    || fromLaneEndDistance < 0.0
                    || !Double.isFinite(toLaneStartDistance)
                    || toLaneStartDistance < 0.0) {
                throw new IllegalArgumentException(
                        "connector lane distances must be finite and non-negative"
                );
            }
        }
    }
}
