package com.cookiecraftmods.roadbuilder.network;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class RoadBoundaryRecovery {
    private RoadBoundaryRecovery() {
    }

    public static boolean recover(
            Collection<RoadNodeId> candidates,
            Map<RoadNodeId, RoadNode> nodes,
            Map<RoadSegmentId, RoadSegment> segments,
            Map<LaneConnectorId, LaneConnector> connectors,
            Map<RoadNodeId, JunctionDefinition> definitions
    ) {
        Set<RoadLaneId> affectedLanes = new HashSet<>();
        boolean changed = false;
        for (RoadNodeId nodeId : List.copyOf(candidates)) {
            RoadNode node = nodes.get(nodeId);
            if (node == null) {
                continue;
            }
            List<RoadSegment> incident = segments.values().stream()
                    .filter(segment -> segment.startNodeId().equals(nodeId)
                            || segment.endNodeId().equals(nodeId))
                    .toList();
            if (incident.isEmpty()
                    || RoadNodePortResolver.isValidTopology(node, incident)) {
                continue;
            }
            var reanchored = RoadNodePortResolver.reanchor(node, incident);
            if (reanchored.isPresent()) {
                nodes.put(nodeId, reanchored.orElseThrow());
                changed = true;
                continue;
            }
            List<List<RoadSegment>> groups =
                    RoadNodePortResolver.connectedPortGroups(node, incident);
            JunctionDefinition definition = definitions.remove(nodeId);
            for (int index = 0; index < groups.size(); index++) {
                List<RoadSegment> group = groups.get(index);
                RoadNode anchor = RoadNodePortResolver.isValidTopology(node, group)
                        ? node : RoadNodePortResolver.reanchor(node, group)
                                .orElseThrow(() -> new IllegalArgumentException(
                                        "segment geometry disagrees with its boundary node end caps: "
                                                + nodeId
                                ));
                RoadNodeId replacementId = index == 0 ? nodeId : new RoadNodeId(
                        UUID.nameUUIDFromBytes(("roadbuilder:detached-port:"
                                + nodeId + ":" + group.get(0).id())
                                .getBytes(StandardCharsets.UTF_8))
                );
                if (index != 0 && nodes.containsKey(replacementId)) {
                    throw new IllegalArgumentException("detached boundary ID collision");
                }
                nodes.put(replacementId, new RoadNode(replacementId, anchor.position()));
                Set<RoadSegmentId> groupIds = new HashSet<>();
                for (RoadSegment segment : group) {
                    groupIds.add(segment.id());
                    segment.lanes().forEach(lane -> affectedLanes.add(lane.id()));
                    segments.put(segment.id(), RoadSegment.path(
                            segment.id(), segment.roadId(),
                            segment.startNodeId().equals(nodeId)
                                    ? replacementId : segment.startNodeId(),
                            segment.endNodeId().equals(nodeId)
                                    ? replacementId : segment.endNodeId(),
                            segment.referenceGeometry(), segment.presetId(),
                            segment.presetRevision(), segment.presetSnapshot(),
                            segment.trafficHandedness(),
                            segment.lanes().stream().map(RoadLane::id).toList()
                    ));
                }
                if (definition != null && group.size() > 1) {
                    Map<ApproachKey, JunctionApproachOverride> overrides = new HashMap<>();
                    definition.approachOverrides().forEach((key, value) -> {
                        if (groupIds.contains(key.segmentId())) {
                            overrides.put(key, value);
                        }
                    });
                    definitions.put(replacementId, new JunctionDefinition(
                            replacementId, definition.strategy(), definition.priorityPolicy(),
                            definition.primaryApproachKeys().stream()
                                    .filter(key -> groupIds.contains(key.segmentId())).toList(),
                            definition.crossingPolicy(), overrides, definition.revision() + 1
                    ));
                }
            }
            changed = true;
        }
        if (!affectedLanes.isEmpty()) {
            Map<RoadLaneId, RoadNodeId> starts = new HashMap<>();
            Map<RoadLaneId, RoadNodeId> ends = new HashMap<>();
            for (RoadSegment segment : segments.values()) {
                for (RoadLane lane : segment.lanes()) {
                    boolean forward = lane.component().direction().followsReferenceDirection();
                    starts.put(lane.id(), forward ? segment.startNodeId() : segment.endNodeId());
                    ends.put(lane.id(), forward ? segment.endNodeId() : segment.startNodeId());
                }
            }
            connectors.values().removeIf(connector ->
                    (affectedLanes.contains(connector.fromLaneId())
                            || affectedLanes.contains(connector.toLaneId()))
                            && ends.containsKey(connector.fromLaneId())
                            && starts.containsKey(connector.toLaneId())
                            && !ends.get(connector.fromLaneId()).equals(
                                    starts.get(connector.toLaneId())));
        }
        return changed;
    }
}
