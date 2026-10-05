package com.cookiecraftmods.roadbuilder.network;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record RoadGraphSnapshot(
        ResourceKey<Level> dimension,
        long revision,
        Map<RoadId, Road> roads,
        Map<RoadNodeId, RoadNode> nodes,
        Map<RoadSegmentId, RoadSegment> segments,
        Map<RoadLaneId, RoadLane> lanes,
        Map<LaneConnectorId, LaneConnector> connectors,
        Map<RoadNodeId, JunctionDefinition> junctionDefinitions
) {
    public RoadGraphSnapshot(
            ResourceKey<Level> dimension,
            long revision,
            Map<RoadId, Road> roads,
            Map<RoadNodeId, RoadNode> nodes,
            Map<RoadSegmentId, RoadSegment> segments,
            Map<RoadLaneId, RoadLane> lanes,
            Map<LaneConnectorId, LaneConnector> connectors
    ) {
        this(
                dimension,
                revision,
                roads,
                nodes,
                segments,
                lanes,
                connectors,
                Map.of()
        );
    }

    public RoadGraphSnapshot {
        Objects.requireNonNull(dimension, "dimension");
        if (revision < 0) {
            throw new IllegalArgumentException("revision must be non-negative");
        }
        roads = immutableCopy(roads, "roads");
        nodes = immutableCopy(nodes, "nodes");
        segments = immutableCopy(segments, "segments");
        lanes = immutableCopy(lanes, "lanes");
        connectors = immutableCopy(connectors, "connectors");
        junctionDefinitions = immutableCopy(
                junctionDefinitions,
                "junctionDefinitions"
        );
        junctionDefinitions.forEach((nodeId, definition) -> {
            if (!nodeId.equals(definition.nodeId())) {
                throw new IllegalArgumentException(
                        "junction definition map key does not match its node ID"
                );
            }
        });
    }

    private static <K, V> Map<K, V> immutableCopy(Map<K, V> values, String name) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(
                Objects.requireNonNull(values, name)
        ));
    }
}
