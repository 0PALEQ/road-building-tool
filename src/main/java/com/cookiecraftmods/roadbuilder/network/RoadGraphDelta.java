package com.cookiecraftmods.roadbuilder.network;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record RoadGraphDelta(
        ResourceKey<Level> dimension,
        long baseRevision,
        long targetRevision,
        Map<RoadId, Road> roads,
        List<RoadId> removedRoadIds,
        Map<RoadNodeId, RoadNode> nodes,
        List<RoadNodeId> removedNodeIds,
        Map<RoadSegmentId, RoadSegment> segments,
        List<RoadSegmentId> removedSegmentIds,
        Map<LaneConnectorId, LaneConnector> connectors,
        List<LaneConnectorId> removedConnectorIds,
        Map<RoadNodeId, JunctionDefinition> junctionDefinitions,
        List<RoadNodeId> removedJunctionDefinitionNodeIds
) {
    public RoadGraphDelta(
            ResourceKey<Level> dimension,
            long baseRevision,
            long targetRevision,
            Map<RoadId, Road> roads,
            List<RoadId> removedRoadIds,
            Map<RoadNodeId, RoadNode> nodes,
            List<RoadNodeId> removedNodeIds,
            Map<RoadSegmentId, RoadSegment> segments,
            List<RoadSegmentId> removedSegmentIds,
            Map<LaneConnectorId, LaneConnector> connectors,
            List<LaneConnectorId> removedConnectorIds
    ) {
        this(
                dimension,
                baseRevision,
                targetRevision,
                roads,
                removedRoadIds,
                nodes,
                removedNodeIds,
                segments,
                removedSegmentIds,
                connectors,
                removedConnectorIds,
                Map.of(),
                List.of()
        );
    }

    public RoadGraphDelta {
        Objects.requireNonNull(dimension, "dimension");
        if (baseRevision < 0) {
            throw new IllegalArgumentException("baseRevision must be non-negative");
        }
        if (targetRevision <= baseRevision) {
            throw new IllegalArgumentException("targetRevision must be greater than baseRevision");
        }
        roads = immutableMap(roads, "roads");
        removedRoadIds = immutableDistinctList(removedRoadIds, "removedRoadIds");
        nodes = immutableMap(nodes, "nodes");
        removedNodeIds = immutableDistinctList(removedNodeIds, "removedNodeIds");
        segments = immutableMap(segments, "segments");
        removedSegmentIds = immutableDistinctList(removedSegmentIds, "removedSegmentIds");
        connectors = immutableMap(connectors, "connectors");
        removedConnectorIds = immutableDistinctList(
                removedConnectorIds,
                "removedConnectorIds"
        );
        junctionDefinitions = immutableMap(
                junctionDefinitions,
                "junctionDefinitions"
        );
        removedJunctionDefinitionNodeIds = immutableDistinctList(
                removedJunctionDefinitionNodeIds,
                "removedJunctionDefinitionNodeIds"
        );
        requireDisjoint(roads.keySet(), removedRoadIds, "road");
        requireDisjoint(nodes.keySet(), removedNodeIds, "node");
        requireDisjoint(segments.keySet(), removedSegmentIds, "segment");
        requireDisjoint(connectors.keySet(), removedConnectorIds, "connector");
        requireDisjoint(
                junctionDefinitions.keySet(),
                removedJunctionDefinitionNodeIds,
                "junction definition"
        );
        requireMatchingIds(roads, Road::id, "road");
        requireMatchingIds(nodes, RoadNode::id, "node");
        requireMatchingIds(segments, RoadSegment::id, "segment");
        requireMatchingIds(connectors, LaneConnector::id, "connector");
        requireMatchingIds(
                junctionDefinitions,
                JunctionDefinition::nodeId,
                "junction definition"
        );
    }

        public static RoadGraphDelta between(
            RoadGraphSnapshot before,
            RoadGraphSnapshot after
    ) {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        if (!before.dimension().equals(after.dimension())) {
            throw new IllegalArgumentException("graph delta dimensions do not match");
        }
        if (after.revision() <= before.revision()) {
            throw new IllegalArgumentException("graph delta must advance the revision");
        }

        return new RoadGraphDelta(
                before.dimension(),
                before.revision(),
                after.revision(),
                changed(before.roads(), after.roads()),
                removed(before.roads(), after.roads()),
                changed(before.nodes(), after.nodes()),
                removed(before.nodes(), after.nodes()),
                changed(before.segments(), after.segments()),
                removed(before.segments(), after.segments()),
                changed(before.connectors(), after.connectors()),
                removed(before.connectors(), after.connectors()),
                changed(
                        before.junctionDefinitions(),
                        after.junctionDefinitions()
                ),
                removed(
                        before.junctionDefinitions(),
                        after.junctionDefinitions()
                )
        );
    }

        public RoadGraphSnapshot applyTo(RoadGraphSnapshot base) {
        Objects.requireNonNull(base, "base");
        if (!dimension.equals(base.dimension())) {
            throw new IllegalArgumentException("graph delta dimension does not match its base");
        }
        if (baseRevision != base.revision()) {
            throw new IllegalArgumentException("graph delta base revision does not match");
        }

        Map<RoadId, Road> mergedRoads = new LinkedHashMap<>(base.roads());
        Map<RoadNodeId, RoadNode> mergedNodes = new LinkedHashMap<>(base.nodes());
        Map<RoadSegmentId, RoadSegment> mergedSegments = new LinkedHashMap<>(base.segments());
        Map<LaneConnectorId, LaneConnector> mergedConnectors =
                new LinkedHashMap<>(base.connectors());
        Map<RoadNodeId, JunctionDefinition> mergedJunctionDefinitions =
                new LinkedHashMap<>(base.junctionDefinitions());

        removedConnectorIds.forEach(mergedConnectors::remove);
        removedSegmentIds.forEach(mergedSegments::remove);
        removedNodeIds.forEach(mergedNodes::remove);
        removedRoadIds.forEach(mergedRoads::remove);
        removedJunctionDefinitionNodeIds.forEach(
                mergedJunctionDefinitions::remove
        );
        mergedRoads.putAll(roads);
        mergedNodes.putAll(nodes);
        mergedSegments.putAll(segments);
        mergedConnectors.putAll(connectors);
        mergedJunctionDefinitions.putAll(junctionDefinitions);

        return RoadGraph.restore(
                dimension,
                targetRevision,
                mergedRoads.values(),
                mergedNodes.values(),
                mergedSegments.values(),
                mergedConnectors.values(),
                mergedJunctionDefinitions.values(),
                RoadGraphLimits.defaults(),
                () -> {
                }
        ).snapshot();
    }

    private static <K, V> Map<K, V> changed(Map<K, V> before, Map<K, V> after) {
        Map<K, V> changed = new LinkedHashMap<>();
        after.forEach((id, value) -> {
            if (!value.equals(before.get(id))) {
                changed.put(id, value);
            }
        });
        return changed;
    }

    private static <K, V> List<K> removed(Map<K, V> before, Map<K, V> after) {
        List<K> removed = new ArrayList<>();
        before.keySet().forEach(id -> {
            if (!after.containsKey(id)) {
                removed.add(id);
            }
        });
        return List.copyOf(removed);
    }

    private static <K, V> Map<K, V> immutableMap(Map<K, V> values, String name) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(
                Objects.requireNonNull(values, name)
        ));
    }

    private static <T> List<T> immutableDistinctList(List<T> values, String name) {
        List<T> copy = List.copyOf(Objects.requireNonNull(values, name));
        if (new HashSet<>(copy).size() != copy.size()) {
            throw new IllegalArgumentException(name + " contains duplicate IDs");
        }
        return copy;
    }

    private static <T> void requireDisjoint(
            Set<T> upserted,
            List<T> removed,
            String name
    ) {
        for (T id : removed) {
            if (upserted.contains(id)) {
                throw new IllegalArgumentException(name + " ID is both upserted and removed: " + id);
            }
        }
    }

    private static <K, V> void requireMatchingIds(
            Map<K, V> values,
            java.util.function.Function<V, K> id,
            String name
    ) {
        values.forEach((key, value) -> {
            if (!key.equals(id.apply(value))) {
                throw new IllegalArgumentException(name + " map key does not match its record ID");
            }
        });
    }
}
