package com.cookiecraftmods.roadbuilder.network;

import com.cookiecraftmods.roadbuilder.geometry.PathSample;
import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.RoadVector;
import com.cookiecraftmods.roadbuilder.preset.RoadPreset;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolves reference-line endpoints against the full end cap of an existing
 * road arm. Several differently positioned endpoints can therefore share one
 * logical junction node without moving every road back to the node centre.
 */
public final class RoadNodePortResolver {
    public static final double END_CAP_LONGITUDINAL_TOLERANCE = 1.25;
    public static final double END_CAP_LATERAL_MARGIN = 0.5;

    private static final double HORIZONTAL_TANGENT_EPSILON = 1.0e-9;
    private static final double MAXIMUM_PORT_RADIUS =
            RoadPreset.MAXIMUM_SURFACE_BLOCK_WIDTH / 2.0
                    + END_CAP_LONGITUDINAL_TOLERANCE
                    + END_CAP_LATERAL_MARGIN;

    private RoadNodePortResolver() {
    }

    public static Optional<RoadNode> resolve(
            RoadPoint point,
            Collection<RoadNode> nodes,
            Collection<RoadSegment> segments
    ) {
        Objects.requireNonNull(point, "point");
        Objects.requireNonNull(nodes, "nodes");
        Objects.requireNonNull(segments, "segments");

        Map<RoadNodeId, RoadNode> nodesById = new HashMap<>();
        for (RoadNode node : nodes) {
            RoadNode nonNull = Objects.requireNonNull(
                    node,
                    "nodes contains null"
            );
            nodesById.put(nonNull.id(), nonNull);
        }

        Optional<RoadNode> exact = nodesById.values().stream()
                .filter(node -> node.position().equals(point))
                .min(Comparator.comparing(RoadNode::id));
        if (exact.isPresent()) {
            return exact;
        }

        List<Candidate> candidates = new ArrayList<>();
        for (RoadSegment segment : segments) {
            RoadSegment nonNull = Objects.requireNonNull(
                    segment,
                    "segments contains null"
            );
            addCandidate(
                    candidates,
                    nodesById.get(nonNull.startNodeId()),
                    frame(nonNull, nonNull.startNodeId()),
                    point
            );
            addCandidate(
                    candidates,
                    nodesById.get(nonNull.endNodeId()),
                    frame(nonNull, nonNull.endNodeId()),
                    point
            );
        }
        return candidates.stream()
                .min(Comparator.comparingDouble(Candidate::distance)
                        .thenComparing(candidate -> candidate.node().id()))
                .map(Candidate::node);
    }

    /**
     * Validates that all incident boundary positions form one bounded cluster
     * of touching end caps rooted at the node's canonical position.
     */
    public static boolean isValidTopology(
            RoadNode node,
            Collection<RoadSegment> incidentSegments
    ) {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(incidentSegments, "incidentSegments");
        List<PortFrame> frames = incidentSegments.stream()
                .map(segment -> frame(
                        Objects.requireNonNull(
                                segment,
                                "incidentSegments contains null"
                        ),
                        node.id()
                ))
                .toList();
        if (frames.isEmpty() || frames.stream().anyMatch(Objects::isNull)) {
            return false;
        }
        if (frames.stream().anyMatch(port ->
                horizontalDistance(node.position(), port.endpoint())
                        > MAXIMUM_PORT_RADIUS
                        || Math.abs(node.position().y() - port.endpoint().y())
                        > RoadPathCrossingAnalyzer.DEFAULT_MAXIMUM_VERTICAL_GAP)) {
            return false;
        }

        boolean[] connected = new boolean[frames.size()];
        for (int index = 0; index < frames.size(); index++) {
            PortFrame port = frames.get(index);
            connected[index] = port.endpoint().equals(node.position())
                    || port.contains(node.position());
        }

        boolean changed;
        do {
            changed = false;
            for (int candidate = 0; candidate < frames.size(); candidate++) {
                if (connected[candidate]) {
                    continue;
                }
                for (int anchor = 0; anchor < frames.size(); anchor++) {
                    if (!connected[anchor]) {
                        continue;
                    }
                    PortFrame candidateFrame = frames.get(candidate);
                    PortFrame anchorFrame = frames.get(anchor);
                    if (anchorFrame.contains(candidateFrame.endpoint())
                            || candidateFrame.contains(anchorFrame.endpoint())) {
                        connected[candidate] = true;
                        changed = true;
                        break;
                    }
                }
            }
        } while (changed);

        for (boolean portConnected : connected) {
            if (!portConnected) {
                return false;
            }
        }
        return true;
    }

    public static boolean isCenteredBoundary(
            RoadNode node,
            RoadSegment segment
    ) {
        Objects.requireNonNull(node, "node");
        PortFrame port = frame(
                Objects.requireNonNull(segment, "segment"),
                node.id()
        );
        return port != null && port.endpoint().equals(node.position());
    }

    /**
     * Returns a replacement for the given node (same ID, new position) that
     * restores a valid bounded topology for all incident segments, or empty
     * if the existing topology is already valid or cannot be repaired without
     * moving segments or relaxing validity.
     *
     * <p>Only existing incident endpoints within the original node's radius
     * and vertical limits are considered, so a disconnected or far-flung
     * topology is never silently re-anchored. Candidates are tried in
     * deterministic order: distance from the original node, then segment ID,
     * then end.</p>
     */
    public static Optional<RoadNode> reanchor(
            RoadNode node,
            Collection<RoadSegment> incidentSegments
    ) {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(incidentSegments, "incidentSegments");
        if (isValidTopology(node, incidentSegments)) {
            return Optional.empty();
        }
        List<PortFrame> frames = incidentSegments.stream()
                .map(segment -> frame(
                        Objects.requireNonNull(
                                segment,
                                "incidentSegments contains null"
                        ),
                        node.id()
                ))
                .filter(Objects::nonNull)
                .toList();
        if (frames.isEmpty()) {
            return Optional.empty();
        }
        List<PortFrame> candidates = frames.stream()
                .filter(port -> horizontalDistance(
                        node.position(),
                        port.endpoint()
                ) <= MAXIMUM_PORT_RADIUS
                        && Math.abs(node.position().y() - port.endpoint().y())
                        <= RoadPathCrossingAnalyzer.DEFAULT_MAXIMUM_VERTICAL_GAP)
                .sorted(Comparator
                        .comparingDouble((PortFrame port) ->
                                horizontalDistance(
                                        node.position(),
                                        port.endpoint()
                                ))
                        .thenComparing(port -> port.segmentId())
                        .thenComparing(port -> port.atStart() ? 0 : 1))
                .toList();
        for (PortFrame candidate : candidates) {
            RoadNode reanchored = new RoadNode(
                    node.id(),
                    candidate.endpoint()
            );
            if (isValidTopology(reanchored, incidentSegments)) {
                return Optional.of(reanchored);
            }
        }
        return Optional.empty();
    }

    public static List<List<RoadSegment>> connectedPortGroups(
            RoadNode node,
            Collection<RoadSegment> incidentSegments
    ) {
        List<RoadSegment> remaining = new ArrayList<>(incidentSegments);
        remaining.sort(Comparator.comparing(RoadSegment::id));
        for (RoadSegment segment : remaining) {
            PortFrame port = frame(segment, node.id());
            if (port == null
                    || horizontalDistance(node.position(), port.endpoint())
                            > MAXIMUM_PORT_RADIUS
                    || Math.abs(node.position().y() - port.endpoint().y())
                            > RoadPathCrossingAnalyzer.DEFAULT_MAXIMUM_VERTICAL_GAP) {
                throw new IllegalArgumentException(
                        "segment geometry disagrees with its boundary node end caps: "
                                + node.id()
                );
            }
        }
        List<List<RoadSegment>> groups = new ArrayList<>();
        while (!remaining.isEmpty()) {
            List<RoadSegment> group = new ArrayList<>();
            group.add(remaining.remove(0));
            for (int index = 0; index < group.size(); index++) {
                PortFrame anchor = frame(group.get(index), node.id());
                var iterator = remaining.iterator();
                while (iterator.hasNext()) {
                    RoadSegment candidate = iterator.next();
                    PortFrame port = frame(candidate, node.id());
                    if (anchor.contains(port.endpoint())
                            || port.contains(anchor.endpoint())) {
                        group.add(candidate);
                        iterator.remove();
                    }
                }
            }
            groups.add(List.copyOf(group));
        }
        return List.copyOf(groups);
    }

    private static void addCandidate(
            List<Candidate> candidates,
            RoadNode node,
            PortFrame port,
            RoadPoint point
    ) {
        if (node == null || port == null || !port.contains(point)) {
            return;
        }
        candidates.add(new Candidate(
                node,
                horizontalDistance(port.endpoint(), point)
        ));
    }

    private static PortFrame frame(RoadSegment segment, RoadNodeId nodeId) {
        boolean atStart = segment.startNodeId().equals(nodeId);
        if (!atStart && !segment.endNodeId().equals(nodeId)) {
            return null;
        }
        double distance = atStart ? 0.0 : segment.referenceGeometry().length();
        PathSample sample = segment.referenceGeometry().sample(distance);
        RoadVector tangent = sample.tangent();
        double horizontalLength = tangent.horizontalLength();
        if (horizontalLength <= HORIZONTAL_TANGENT_EPSILON) {
            return null;
        }
        return new PortFrame(
                segment.id(),
                atStart,
                sample.position(),
                tangent.x() / horizontalLength,
                tangent.z() / horizontalLength,
                segment.presetSnapshot().surfaceBlockWidth() / 2.0
        );
    }

    private static double horizontalDistance(RoadPoint first, RoadPoint second) {
        return Math.hypot(first.x() - second.x(), first.z() - second.z());
    }

    private record Candidate(RoadNode node, double distance) {
    }

    private record PortFrame(
            RoadSegmentId segmentId,
            boolean atStart,
            RoadPoint endpoint,
            double unitX,
            double unitZ,
            double halfWidth
    ) {
        boolean contains(RoadPoint point) {
            if (Math.abs(point.y() - endpoint.y())
                    > RoadPathCrossingAnalyzer.DEFAULT_MAXIMUM_VERTICAL_GAP) {
                return false;
            }
            double deltaX = point.x() - endpoint.x();
            double deltaZ = point.z() - endpoint.z();
            double longitudinal = Math.abs(deltaX * unitX + deltaZ * unitZ);
            double lateral = Math.abs(deltaX * unitZ - deltaZ * unitX);
            return longitudinal <= END_CAP_LONGITUDINAL_TOLERANCE
                    && lateral <= halfWidth + END_CAP_LATERAL_MARGIN;
        }
    }
}
