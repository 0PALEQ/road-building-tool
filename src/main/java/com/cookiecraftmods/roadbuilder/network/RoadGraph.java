package com.cookiecraftmods.roadbuilder.network;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.RoadVector;
import com.cookiecraftmods.roadbuilder.geometry.ReferenceGeometry;
import com.cookiecraftmods.roadbuilder.geometry.ReferenceGeometryReverser;
import com.cookiecraftmods.roadbuilder.geometry.ReferenceGeometryEditor;
import com.cookiecraftmods.roadbuilder.geometry.RoundaboutGeometry;
import com.cookiecraftmods.roadbuilder.geometry.StraightGeometry;
import com.cookiecraftmods.roadbuilder.preset.LaneComponent;
import com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection;
import com.cookiecraftmods.roadbuilder.preset.RoadPreset;
import com.cookiecraftmods.roadbuilder.preset.TrafficHandedness;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public final class RoadGraph {
    private static final Comparator<UUID> UUID_ORDER = Comparator.naturalOrder();
    public static final double JUNCTION_MAXIMUM_VERTICAL_GAP =
            RoadPathCrossingAnalyzer.DEFAULT_MAXIMUM_VERTICAL_GAP;

    private final ResourceKey<Level> dimension;
    private final RoadGraphLimits limits;
    private final Runnable changeListener;
    private final Supplier<UUID> idGenerator;
    private final Map<RoadId, Road> roads = new HashMap<>();
    private final Map<RoadNodeId, RoadNode> nodes = new HashMap<>();
    private final Map<RoadSegmentId, RoadSegment> segments = new HashMap<>();
    private final Map<RoadLaneId, RoadLane> lanes = new HashMap<>();
    private final Map<LaneConnectorId, LaneConnector> connectors = new HashMap<>();
    private final Map<RoadNodeId, JunctionDefinition> junctionDefinitions =
            new HashMap<>();
    private long revision;

    public RoadGraph(ResourceKey<Level> dimension) {
        this(dimension, RoadGraphLimits.defaults(), UUID::randomUUID, () -> { });
    }

    public RoadGraph(ResourceKey<Level> dimension, Runnable changeListener) {
        this(dimension, RoadGraphLimits.defaults(), UUID::randomUUID, changeListener);
    }

    public RoadGraph(
            ResourceKey<Level> dimension,
            RoadGraphLimits limits,
            Supplier<UUID> idGenerator,
            Runnable changeListener
    ) {
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
        this.changeListener = Objects.requireNonNull(changeListener, "changeListener");
    }

    public static RoadGraph restore(
            ResourceKey<Level> dimension,
            long revision,
            Collection<Road> suppliedRoads,
            Collection<RoadNode> suppliedNodes,
            Collection<RoadSegment> suppliedSegments,
            Collection<LaneConnector> suppliedConnectors,
            RoadGraphLimits limits,
            Runnable changeListener
    ) {
        return restore(
                dimension,
                revision,
                suppliedRoads,
                suppliedNodes,
                suppliedSegments,
                suppliedConnectors,
                List.of(),
                limits,
                changeListener
        );
    }

    public static RoadGraph restore(
            ResourceKey<Level> dimension,
            long revision,
            Collection<Road> suppliedRoads,
            Collection<RoadNode> suppliedNodes,
            Collection<RoadSegment> suppliedSegments,
            Collection<LaneConnector> suppliedConnectors,
            Collection<JunctionDefinition> suppliedJunctionDefinitions,
            RoadGraphLimits limits,
            Runnable changeListener
    ) {
        if (revision < 0) {
            throw new IllegalArgumentException("restored graph revision must be non-negative");
        }
        RoadGraph graph = new RoadGraph(
                dimension,
                limits,
                UUID::randomUUID,
                changeListener
        );

        for (Road road : Objects.requireNonNull(suppliedRoads, "suppliedRoads")) {
            Road nonNullRoad = Objects.requireNonNull(road, "suppliedRoads contains null");
            requireAbsent(graph.roads.put(nonNullRoad.id(), nonNullRoad), "road", nonNullRoad.id());
        }
        for (RoadNode node : Objects.requireNonNull(suppliedNodes, "suppliedNodes")) {
            RoadNode nonNullNode = Objects.requireNonNull(node, "suppliedNodes contains null");
            requireAbsent(graph.nodes.put(nonNullNode.id(), nonNullNode), "node", nonNullNode.id());
        }
        for (RoadSegment segment : Objects.requireNonNull(suppliedSegments, "suppliedSegments")) {
            RoadSegment nonNullSegment = Objects.requireNonNull(
                    segment,
                    "suppliedSegments contains null"
            );
            graph.validateSegmentReferences(nonNullSegment);
            requireAbsent(
                    graph.segments.put(nonNullSegment.id(), nonNullSegment),
                    "segment",
                    nonNullSegment.id()
            );
            for (RoadLane lane : nonNullSegment.lanes()) {
                requireAbsent(graph.lanes.put(lane.id(), lane), "lane", lane.id());
            }
        }
        for (LaneConnector connector : Objects.requireNonNull(
                suppliedConnectors,
                "suppliedConnectors"
        )) {
            LaneConnector nonNullConnector = Objects.requireNonNull(
                    connector,
                    "suppliedConnectors contains null"
            );
            graph.validateConnectorReferences(nonNullConnector);
            requireAbsent(
                    graph.connectors.put(nonNullConnector.id(), nonNullConnector),
                    "connector",
                    nonNullConnector.id()
            );
        }
        for (JunctionDefinition definition : Objects.requireNonNull(
                suppliedJunctionDefinitions,
                "suppliedJunctionDefinitions"
        )) {
            JunctionDefinition nonNull = Objects.requireNonNull(
                    definition,
                    "suppliedJunctionDefinitions contains null"
            );
            graph.validateJunctionDefinition(nonNull);
            requireAbsent(
                    graph.junctionDefinitions.put(nonNull.nodeId(), nonNull),
                    "junction definition",
                    nonNull.nodeId()
            );
        }

        graph.requireWithinLimits();
        graph.validateCompleteTopology();
        graph.validateGloballyUniqueUuids();
        graph.revision = revision;
        return graph;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public long revision() {
        return revision;
    }

    public Optional<Road> road(RoadId id) {
        return Optional.ofNullable(roads.get(Objects.requireNonNull(id, "id")));
    }

    public Optional<RoadNode> node(RoadNodeId id) {
        return Optional.ofNullable(nodes.get(Objects.requireNonNull(id, "id")));
    }

    public Optional<RoadSegment> segment(RoadSegmentId id) {
        return Optional.ofNullable(segments.get(Objects.requireNonNull(id, "id")));
    }

    public Optional<RoadLane> lane(RoadLaneId id) {
        return Optional.ofNullable(lanes.get(Objects.requireNonNull(id, "id")));
    }

    public Optional<JunctionDefinition> junctionDefinition(RoadNodeId nodeId) {
        return Optional.ofNullable(junctionDefinitions.get(
                Objects.requireNonNull(nodeId, "nodeId")
        ));
    }

    public void upsertJunctionDefinition(
            JunctionDefinition definition,
            long expectedGraphRevision
    ) {
        Objects.requireNonNull(definition, "definition");
        if (revision != expectedGraphRevision) {
            throw new IllegalStateException(
                    "road graph changed before junction edit"
            );
        }
        validateJunctionDefinition(definition);
        JunctionDefinition existing =
                junctionDefinitions.get(definition.nodeId());
        long expectedDefinitionRevision = existing == null
                ? 1
                : existing.revision() + 1;
        if (definition.revision() != expectedDefinitionRevision) {
            throw new IllegalArgumentException(
                    "stale junction definition revision"
            );
        }
        changeListener.run();
        junctionDefinitions.put(definition.nodeId(), definition);
        revision++;
    }

        public PreparedSegmentEdit prepareSegmentEdit(
            RoadSegmentId segmentId,
            ReferenceGeometry replacementGeometry
    ) {
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(replacementGeometry, "replacementGeometry");
        RoadSegment original = segments.get(segmentId);
        if (original == null) {
            throw new IllegalArgumentException(
                    "unknown road segment " + segmentId
            );
        }
        if (!original.referenceGeometry().start()
                .equals(replacementGeometry.start())
                || !original.referenceGeometry().end()
                .equals(replacementGeometry.end())) {
            throw new IllegalArgumentException(
                    "curve-handle edits cannot move boundary nodes"
            );
        }
        RoadSegment replacement = recreateSegment(
                original,
                original.id(),
                original.startNodeId(),
                original.endNodeId(),
                replacementGeometry,
                original.lanes().stream().map(RoadLane::id).toList()
        );
        Set<RoadLaneId> editedLaneIds = original.lanes().stream()
                .map(RoadLane::id)
                .collect(java.util.stream.Collectors.toSet());
        List<LaneConnectorId> removedConnectorIds = connectors.values().stream()
                .filter(connector ->
                        editedLaneIds.contains(connector.fromLaneId())
                                || editedLaneIds.contains(connector.toLaneId()))
                .map(LaneConnector::id)
                .sorted()
                .toList();
        Map<RoadSegmentId, RoadSegment> candidate = new HashMap<>(segments);
        candidate.put(replacement.id(), replacement);
        Set<UUID> generated = new HashSet<>();
        List<LaneConnector> addedConnectors = rebuildStraightConnectors(
                candidate.values(),
                Set.of(replacement.startNodeId(), replacement.endNodeId()),
                removedConnectorIds,
                generated
        );
        return new PreparedSegmentEdit(
                revision,
                original,
                replacement,
                removedConnectorIds,
                addedConnectors
        );
    }

    public void applySegmentEdit(PreparedSegmentEdit edit) {
        Objects.requireNonNull(edit, "edit");
        if (revision != edit.expectedRevision()) {
            throw new IllegalStateException(
                    "road graph changed after segment-edit preflight"
            );
        }
        if (!edit.original().equals(segments.get(edit.original().id()))) {
            throw new IllegalStateException(
                    "edited segment changed after preflight"
            );
        }
        RoadGraphSnapshot before = snapshot();
        Map<RoadSegmentId, RoadSegment> candidateSegments =
                new HashMap<>(before.segments());
        candidateSegments.put(edit.replacement().id(), edit.replacement());
        Map<LaneConnectorId, LaneConnector> candidateConnectors =
                new HashMap<>(before.connectors());
        edit.removedConnectorIds().forEach(candidateConnectors::remove);
        edit.addedConnectors().forEach(connector ->
                candidateConnectors.put(connector.id(), connector));
        RoadGraph.restore(
                dimension,
                revision + 1,
                roads.values(),
                nodes.values(),
                candidateSegments.values(),
                candidateConnectors.values(),
                junctionDefinitions.values(),
                limits,
                () -> { }
        );

        changeListener.run();
        RoadSegment removed = segments.put(
                edit.replacement().id(),
                edit.replacement()
        );
        removed.lanes().forEach(lane -> lanes.remove(lane.id()));
        edit.replacement().lanes().forEach(lane -> lanes.put(lane.id(), lane));
        edit.removedConnectorIds().forEach(connectors::remove);
        edit.addedConnectors().forEach(connector ->
                connectors.put(connector.id(), connector));
        revision++;
    }

    public PreparedSegmentReplacement prepareSegmentReplacement(
            RoadSegmentId segmentId,
            ResourceLocation presetId,
            RoadPreset preset
    ) {
        return prepareSegmentReplacement(segmentId, presetId, preset, false);
    }

    public PreparedSegmentReplacement prepareSegmentReplacement(
            RoadSegmentId segmentId,
            ResourceLocation presetId,
            RoadPreset preset,
            boolean sunkenRoad
    ) {
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(presetId, "presetId");
        Objects.requireNonNull(preset, "preset");
        RoadSegment original = segments.get(segmentId);
        if (original == null) {
            throw new IllegalArgumentException("unknown road segment " + segmentId);
        }
        Set<UUID> generated = new HashSet<>();
        lanes.keySet().forEach(id -> generated.add(id.value()));
        List<RoadLaneId> replacementLaneIds = new ArrayList<>();
        int laneCount = preset.lanes().size();
        for (int index = 0; index < laneCount; index++) {
            replacementLaneIds.add(index < original.lanes().size()
                    ? original.lanes().get(index).id()
                    : new RoadLaneId(nextGeneratedId(generated)));
        }
        RoadPresetSnapshot presetSnapshot = original.lanes().stream().anyMatch(lane ->
                lane.component().type() == com.cookiecraftmods.roadbuilder.preset.LaneType.ROUNDABOUT)
                ? roundaboutSnapshot(preset) : RoadPresetSnapshot.from(preset);
        RoadSegment replacement = RoadSegment.path(
                original.id(),
                original.roadId(),
                original.startNodeId(),
                original.endNodeId(),
                original.referenceGeometry(),
                presetId,
                preset.revision(),
                presetSnapshot.withSunkenRoad(sunkenRoad),
                original.trafficHandedness(),
                replacementLaneIds
        );
        Set<RoadLaneId> oldLanes = original.lanes().stream()
                .map(RoadLane::id)
                .collect(java.util.stream.Collectors.toSet());
        List<LaneConnectorId> removed = connectors.values().stream()
                .filter(connector -> oldLanes.contains(connector.fromLaneId())
                        || oldLanes.contains(connector.toLaneId()))
                .map(LaneConnector::id)
                .sorted()
                .toList();
        Map<RoadSegmentId, RoadSegment> candidate = new HashMap<>(segments);
        candidate.put(segmentId, replacement);
        List<LaneConnector> added = rebuildStraightConnectors(
                candidate.values(),
                Set.of(replacement.startNodeId(), replacement.endNodeId()),
                removed,
                generated
        );
        return new PreparedSegmentReplacement(
                revision, original, replacement, removed, added
        );
    }

    public void applySegmentReplacement(PreparedSegmentReplacement edit) {
        Objects.requireNonNull(edit, "edit");
        if (revision != edit.expectedRevision()
                || !edit.original().equals(segments.get(edit.original().id()))) {
            throw new IllegalStateException("road graph changed after replacement preflight");
        }
        RoadGraphSnapshot before = snapshot();
        Map<RoadSegmentId, RoadSegment> candidateSegments =
                new HashMap<>(before.segments());
        candidateSegments.put(edit.replacement().id(), edit.replacement());
        Map<LaneConnectorId, LaneConnector> candidateConnectors =
                new HashMap<>(before.connectors());
        edit.removedConnectorIds().forEach(candidateConnectors::remove);
        edit.addedConnectors().forEach(connector ->
                candidateConnectors.put(connector.id(), connector));
        RoadGraph.restore(
                dimension,
                revision + 1,
                roads.values(),
                nodes.values(),
                candidateSegments.values(),
                candidateConnectors.values(),
                junctionDefinitions.values(),
                limits,
                () -> { }
        );
        changeListener.run();
        RoadSegment removed = segments.put(edit.replacement().id(), edit.replacement());
        removed.lanes().forEach(lane -> lanes.remove(lane.id()));
        edit.replacement().lanes().forEach(lane -> lanes.put(lane.id(), lane));
        edit.removedConnectorIds().forEach(connectors::remove);
        edit.addedConnectors().forEach(connector -> connectors.put(connector.id(), connector));
        revision++;
    }

    public PreparedSegmentDirectionInversion
    prepareSegmentDirectionInversion(RoadSegmentId segmentId) {
        Objects.requireNonNull(segmentId, "segmentId");
        RoadSegment original = segments.get(segmentId);
        if (original == null) {
            throw new IllegalArgumentException(
                    "unknown road segment " + segmentId
            );
        }
        RoadSegment replacement = RoadSegment.path(
                original.id(),
                original.roadId(),
                original.endNodeId(),
                original.startNodeId(),
                ReferenceGeometryReverser.reverse(
                        original.referenceGeometry()
                ),
                original.presetId(),
                original.presetRevision(),
                original.presetSnapshot(),
                original.trafficHandedness(),
                original.lanes().stream().map(RoadLane::id).toList()
        );
        Set<RoadLaneId> oldLanes = original.lanes().stream()
                .map(RoadLane::id)
                .collect(java.util.stream.Collectors.toSet());
        List<LaneConnectorId> removed = connectors.values().stream()
                .filter(connector -> oldLanes.contains(connector.fromLaneId())
                        || oldLanes.contains(connector.toLaneId()))
                .map(LaneConnector::id)
                .sorted()
                .toList();
        Map<RoadSegmentId, RoadSegment> candidate = new HashMap<>(segments);
        candidate.put(segmentId, replacement);
        Set<UUID> generated = new HashSet<>();
        lanes.keySet().forEach(id -> generated.add(id.value()));
        List<LaneConnector> added = rebuildStraightConnectors(
                candidate.values(),
                Set.of(replacement.startNodeId(), replacement.endNodeId()),
                removed,
                generated
        );
        Map<RoadNodeId, JunctionDefinition> definitions =
                new HashMap<>(junctionDefinitions);
        migrateInvertedApproach(
                definitions,
                original.startNodeId(),
                ApproachKey.at(original, original.startNodeId()),
                ApproachKey.at(replacement, original.startNodeId())
        );
        migrateInvertedApproach(
                definitions,
                original.endNodeId(),
                ApproachKey.at(original, original.endNodeId()),
                ApproachKey.at(replacement, original.endNodeId())
        );
        return new PreparedSegmentDirectionInversion(
                revision,
                original,
                replacement,
                removed,
                added,
                definitions
        );
    }

    public void applySegmentDirectionInversion(
            PreparedSegmentDirectionInversion inversion
    ) {
        Objects.requireNonNull(inversion, "inversion");
        if (revision != inversion.expectedRevision()
                || !inversion.original().equals(
                        segments.get(inversion.original().id())
                )) {
            throw new IllegalStateException(
                    "road graph changed after direction-inversion preflight"
            );
        }
        RoadGraphSnapshot before = snapshot();
        Map<RoadSegmentId, RoadSegment> candidateSegments =
                new HashMap<>(before.segments());
        candidateSegments.put(
                inversion.replacement().id(),
                inversion.replacement()
        );
        Map<LaneConnectorId, LaneConnector> candidateConnectors =
                new HashMap<>(before.connectors());
        inversion.removedConnectorIds().forEach(candidateConnectors::remove);
        inversion.addedConnectors().forEach(connector ->
                candidateConnectors.put(connector.id(), connector));
        RoadGraph.restore(
                dimension,
                revision + 1,
                roads.values(),
                nodes.values(),
                candidateSegments.values(),
                candidateConnectors.values(),
                inversion.junctionDefinitions().values(),
                limits,
                () -> { }
        );

        changeListener.run();
        RoadSegment removed = segments.put(
                inversion.replacement().id(),
                inversion.replacement()
        );
        removed.lanes().forEach(lane -> lanes.remove(lane.id()));
        inversion.replacement().lanes().forEach(lane ->
                lanes.put(lane.id(), lane));
        inversion.removedConnectorIds().forEach(connectors::remove);
        inversion.addedConnectors().forEach(connector ->
                connectors.put(connector.id(), connector));
        junctionDefinitions.clear();
        junctionDefinitions.putAll(inversion.junctionDefinitions());
        revision++;
    }

    private static void migrateInvertedApproach(
            Map<RoadNodeId, JunctionDefinition> definitions,
            RoadNodeId nodeId,
            ApproachKey original,
            ApproachKey replacement
    ) {
        JunctionDefinition definition = definitions.get(nodeId);
        if (definition != null
                && (definition.primaryApproachKeys().contains(original)
                || definition.approachOverrides().containsKey(original))) {
            definitions.put(
                    nodeId,
                    definition.migrateApproach(original, replacement)
            );
        }
    }

    public LaneConnector updateConnectorMovement(
            LaneConnectorId connectorId,
            long expectedRevision,
            LaneConnectorMovement movement
    ) {
        Objects.requireNonNull(connectorId, "connectorId");
        Objects.requireNonNull(movement, "movement");
        if (revision != expectedRevision) {
            throw new IllegalStateException(
                    "road graph changed after connector-edit preview"
            );
        }
        LaneConnector existing = connectors.get(connectorId);
        if (existing == null) {
            throw new IllegalArgumentException(
                    "unknown lane connector " + connectorId
            );
        }
        if (movement.allowed()) {
            RoadLane from = Objects.requireNonNull(
                    lanes.get(existing.fromLaneId()),
                    "connector source lane is missing"
            );
            RoadLane to = Objects.requireNonNull(
                    lanes.get(existing.toLaneId()),
                    "connector target lane is missing"
            );
            RoadSegment fromSegment = segments.get(from.segmentId());
            RoadSegment toSegment = segments.get(to.segmentId());
            RoadNodeId nodeId = sharedConnectorNode(fromSegment, toSegment, from);
            if (MedianCrossingValidator.crossesMedian(
                    fromSegment,
                    from,
                    toSegment,
                    to,
                    nodeId
            )) {
                throw new IllegalArgumentException(
                        "connector would cross a road median"
                );
            }
        }
        LaneConnector replacement = new LaneConnector(
                existing.id(),
                existing.fromLaneId(),
                existing.toLaneId(),
                new LaneConnectorMovement(
                        movement.type(),
                        movement.allowed(),
                        true,
                        movement.priority()
                )
        );
        changeListener.run();
        connectors.put(connectorId, replacement);
        revision++;
        return replacement;
    }

    public LaneConnector putManualConnector(
            RoadLaneId fromLaneId,
            RoadLaneId toLaneId,
            long expectedRevision,
            LaneConnectorMovement movement
    ) {
        Objects.requireNonNull(fromLaneId, "fromLaneId");
        Objects.requireNonNull(toLaneId, "toLaneId");
        Objects.requireNonNull(movement, "movement");
        if (revision != expectedRevision) {
            throw new IllegalStateException(
                    "road graph changed after connector-edit preview"
            );
        }
        Optional<LaneConnector> existing = connectors.values().stream()
                .filter(connector -> connector.fromLaneId().equals(fromLaneId)
                        && connector.toLaneId().equals(toLaneId))
                .findFirst();
        if (existing.isPresent()) {
            return updateConnectorMovement(
                    existing.orElseThrow().id(),
                    expectedRevision,
                    movement
            );
        }
        if (connectors.size() >= limits.maximumConnectorCount()) {
            throw new IllegalStateException("road graph connector limit reached");
        }
        RoadLane from = Objects.requireNonNull(
                lanes.get(fromLaneId),
                "manual connector source lane is missing"
        );
        RoadLane to = Objects.requireNonNull(
                lanes.get(toLaneId),
                "manual connector target lane is missing"
        );
        if (!LaneConnectionCompatibility.canTransition(
                from.component().type(),
                to.component().type()
        )) {
            throw new IllegalArgumentException(
                    "manual connector lanes have incompatible access types"
            );
        }
        RoadSegment fromSegment = segments.get(from.segmentId());
        RoadSegment toSegment = segments.get(to.segmentId());
        RoadNodeId nodeId = sharedConnectorNode(fromSegment, toSegment, from);
        if (leavesConnectorNode(fromSegment, from, nodeId)
                || !leavesConnectorNode(toSegment, to, nodeId)) {
            throw new IllegalArgumentException(
                    "manual connector must run from an incoming lane to an outgoing lane"
            );
        }
        if (MedianCrossingValidator.crossesMedian(
                fromSegment,
                from,
                toSegment,
                to,
                nodeId
        )) {
            throw new IllegalArgumentException(
                    "manual connector would cross a road median"
            );
        }
        LaneConnector connector = new LaneConnector(
                new LaneConnectorId(nextGeneratedId(new HashSet<>())),
                fromLaneId,
                toLaneId,
                new LaneConnectorMovement(
                        movement.type(),
                        movement.allowed(),
                        true,
                        movement.priority()
                )
        );
        changeListener.run();
        connectors.put(connector.id(), connector);
        revision++;
        return connector;
    }

    public void removeManualConnector(
            LaneConnectorId connectorId,
            long expectedRevision
    ) {
        Objects.requireNonNull(connectorId, "connectorId");
        if (revision != expectedRevision) {
            throw new IllegalStateException(
                    "road graph changed after connector-edit preview"
            );
        }
        LaneConnector connector = connectors.get(connectorId);
        if (connector == null) {
            throw new IllegalArgumentException(
                    "unknown lane connector " + connectorId
            );
        }
        if (!connector.movement().manual()) {
            throw new IllegalArgumentException(
                    "automatic connectors must be disabled instead of removed"
            );
        }
        changeListener.run();
        connectors.remove(connectorId);
        revision++;
    }

    private static RoadNodeId sharedConnectorNode(
            RoadSegment first,
            RoadSegment second,
            RoadLane from
    ) {
        Objects.requireNonNull(first, "manual connector source segment is missing");
        Objects.requireNonNull(second, "manual connector target segment is missing");
        RoadNodeId nodeId = from.component().direction().followsReferenceDirection()
                ? first.endNodeId() : first.startNodeId();
        if (nodeId.equals(second.startNodeId())
                || nodeId.equals(second.endNodeId())) {
            return nodeId;
        }
        throw new IllegalArgumentException(
                "manual connector lanes must share a junction node"
        );
    }

    private static boolean leavesConnectorNode(
            RoadSegment segment,
            RoadLane lane,
            RoadNodeId nodeId
    ) {
        boolean nodeIsStart = segment.startNodeId().equals(nodeId);
        return nodeIsStart
                == lane.component().direction().followsReferenceDirection();
    }

    public List<RoadCrossingCandidate> analyzeStraightRoadCrossings(
            StraightGeometry geometry
    ) {
        return new StraightRoadCrossingAnalyzer().analyze(geometry, segments.values());
    }

    public PreparedRoadAddition prepareStraightRoad(
            ReferenceGeometry geometry,
            ResourceLocation presetId,
            RoadPreset preset,
            TrafficHandedness handedness
    ) {
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(presetId, "presetId");
        Objects.requireNonNull(preset, "preset");
        Objects.requireNonNull(handedness, "handedness");

        Set<UUID> generated = new HashSet<>();
        Optional<RoadNode> joinedStart = findNodeAt(geometry.start());
        Optional<RoadNode> joinedEnd = findNodeAt(geometry.end());
        List<RoadNode> preparedNodes = new ArrayList<>(2);
        RoadNode startNode = joinedStart.orElseGet(() -> {
            RoadNode node = new RoadNode(
                    new RoadNodeId(nextGeneratedId(generated)),
                    geometry.start()
            );
            preparedNodes.add(node);
            return node;
        });
        RoadNode endNode = joinedEnd.orElseGet(() -> {
            RoadNode node = new RoadNode(
                    new RoadNodeId(nextGeneratedId(generated)),
                    geometry.end()
            );
            preparedNodes.add(node);
            return node;
        });
        if (startNode.id().equals(endNode.id())) {
            throw new IllegalArgumentException(
                    "straight-road endpoints resolve to the same graph node"
            );
        }

        Optional<Road> joinedRoad = selectContinuationRoad(
                joinedStart,
                joinedEnd,
                geometry,
                handedness
        );
        boolean createsRoad = joinedRoad.isEmpty();
        Road road = joinedRoad.orElseGet(() -> new Road(
                new RoadId(nextGeneratedId(generated)),
                handedness
        ));
        RoadSegmentId segmentId = new RoadSegmentId(nextGeneratedId(generated));
        List<RoadLaneId> laneIds = new ArrayList<>(preset.lanes().size());
        for (int index = 0; index < preset.lanes().size(); index++) {
            laneIds.add(new RoadLaneId(nextGeneratedId(generated)));
        }

        RoadSegment segment = RoadSegment.path(
                segmentId,
                road.id(),
                startNode.id(),
                endNode.id(),
                geometry,
                presetId,
                preset.revision(),
                RoadPresetSnapshot.from(preset),
                handedness,
                laneIds
        );
        List<LaneConnector> preparedConnectors = new ArrayList<>();
        joinedStart.ifPresent(node -> preparedConnectors.addAll(
                connectorsForAddedSegment(node.id(), segment, generated)
        ));
        joinedEnd.ifPresent(node -> preparedConnectors.addAll(
                connectorsForAddedSegment(node.id(), segment, generated)
        ));
        PreparedRoadAddition addition = new PreparedRoadAddition(
                revision,
                road,
                createsRoad,
                preparedNodes,
                segment,
                preparedConnectors
        );
        validateAddition(addition);
        return addition;
    }

    private List<LaneConnector> connectorsForAddedSegment(
            RoadNodeId nodeId,
            RoadSegment added,
            Set<UUID> generated
    ) {
        List<RoadSegment> incident = new ArrayList<>(incidentSegments(nodeId));
        incident.add(added);
        Set<RoadLaneId> addedLaneIds = added.lanes().stream()
                .map(RoadLane::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new JunctionConnectorPlanner().plan(
                nodeId,
                incident,
                connectors.values(),
                () -> new LaneConnectorId(nextGeneratedId(generated))
        ).stream().filter(connector ->
                addedLaneIds.contains(connector.fromLaneId())
                        || addedLaneIds.contains(connector.toLaneId())
        ).toList();
    }

    public PreparedRoadNetworkMutation prepareRoadNetwork(
            ReferenceGeometry geometry,
            ResourceLocation presetId,
            RoadPreset preset,
            TrafficHandedness handedness
    ) {
        if (geometry instanceof RoundaboutGeometry roundabout) {
            return prepareRoundaboutNetwork(
                    roundabout,
                    presetId,
                    preset,
                    handedness
            );
        }
        return prepareIntersectingRoadNetwork(
                geometry,
                presetId,
                preset,
                handedness
        );
    }

    /** Preflight both paths on an isolated graph, then publish one atomic mutation. */
    public PreparedRoadNetworkMutation prepareParallelRoadNetwork(
            ReferenceGeometry primary, ReferenceGeometry parallel, ResourceLocation presetId,
            RoadPreset preset, TrafficHandedness handedness) {
        RoadGraph staged = restore(dimension, revision, roads.values(), nodes.values(),
                segments.values(), connectors.values(), junctionDefinitions.values(), limits, () -> { });
        PreparedRoadNetworkMutation first = staged.prepareRoadNetwork(primary, presetId, preset, handedness);
        staged.applyNetworkMutation(first);
        PreparedRoadNetworkMutation second = parallel instanceof RoundaboutGeometry ring
                ? staged.prepareRoundaboutNetwork(ring, presetId, preset, handedness, true)
                : staged.prepareRoadNetwork(
                        com.cookiecraftmods.roadbuilder.geometry.ReferenceGeometryReverser.reverse(parallel),
                        presetId, preset, handedness);
        if (second.createsRoad()) {
            // The pair is one road operation; keep its disconnected segments under one road ID.
            List<RoadSegment> pairedSegments = second.placedSegments().stream().map(segment ->
                    RoadSegment.path(segment.id(), first.road().id(), segment.startNodeId(),
                            segment.endNodeId(), segment.referenceGeometry(), segment.presetId(),
                            segment.presetRevision(), segment.presetSnapshot(), segment.trafficHandedness(),
                            segment.lanes().stream().map(RoadLane::id).toList())).toList();
            second = new PreparedRoadNetworkMutation(second.expectedRevision(), first.road(), false,
                    second.addedNodes(), second.segmentSplits(), pairedSegments,
                    second.removedConnectorIds(), second.addedConnectors());
        }
        staged.applyNetworkMutation(second);
        Map<RoadSegmentId, List<RoadSegment>> laterSplits = new HashMap<>();
        second.segmentSplits().forEach(split -> laterSplits.put(split.original().id(), split.replacements()));
        List<PreparedSegmentSplit> combinedSplits = new ArrayList<>();
        Set<RoadSegmentId> intermediateIds = new HashSet<>();
        first.replacementSegments().forEach(segment -> intermediateIds.add(segment.id()));
        first.placedSegments().forEach(segment -> intermediateIds.add(segment.id()));
        for (PreparedSegmentSplit split : first.segmentSplits()) {
            combinedSplits.add(new PreparedSegmentSplit(split.original(), expandSplits(split.replacements(), laterSplits)));
        }
        second.segmentSplits().stream().filter(split -> !intermediateIds.contains(split.original().id()))
                .forEach(combinedSplits::add);
        List<RoadSegment> placed = new ArrayList<>(expandSplits(first.placedSegments(), laterSplits));
        placed.addAll(second.placedSegments());
        List<RoadNode> addedNodes = new ArrayList<>(first.addedNodes());
        addedNodes.addAll(second.addedNodes());
        PreparedRoadNetworkMutation combined = new PreparedRoadNetworkMutation(revision, first.road(),
                first.createsRoad(), addedNodes, combinedSplits, placed,
                connectors.keySet().stream().filter(id -> !staged.connectors.containsKey(id)).toList(),
                staged.connectors.values().stream().filter(connector -> !connectors.containsKey(connector.id())).toList());
        validateNetworkMutation(combined);
        return combined;
    }

    private static List<RoadSegment> expandSplits(List<RoadSegment> originals,
                                                 Map<RoadSegmentId, List<RoadSegment>> splits) {
        return originals.stream().flatMap(segment -> splits.getOrDefault(segment.id(), List.of(segment)).stream()).toList();
    }

    private PreparedRoadNetworkMutation prepareRoundaboutNetwork(
            RoundaboutGeometry geometry,
            ResourceLocation presetId,
            RoadPreset preset,
            TrafficHandedness handedness
    ) {
        return prepareRoundaboutNetwork(geometry, presetId, preset, handedness, false);
    }

    private PreparedRoadNetworkMutation prepareRoundaboutNetwork(
            RoundaboutGeometry geometry,
            ResourceLocation presetId,
            RoadPreset preset,
            TrafficHandedness handedness,
            boolean reverseTraversal
    ) {
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(presetId, "presetId");
        Objects.requireNonNull(preset, "preset");
        Objects.requireNonNull(handedness, "handedness");
        double minimumRadius = RoundaboutGeometry.minimumRadius(
                preset.surfaceBlockWidth()
        );
        if (geometry.radius() < minimumRadius) {
            throw new IllegalArgumentException(
                    "roundabout radius must be at least " + minimumRadius
                            + " blocks for this road width"
            );
        }

        // RHT traffic circulates counter-clockwise; LHT traffic circulates
        // clockwise. A roundabout is one-way even when made from a two-way
        // road preset, so all selected lanes follow the ring traversal.
        var traversalSpans = geometry.spans(
                (handedness == TrafficHandedness.LHT) != reverseTraversal
        );
        RoadNode center = nodes.values().stream()
                .filter(node -> node.position().distanceTo(geometry.center()) < 0.1)
                .filter(node -> incidentSegments(node.id()).size() >= 3)
                .findFirst().orElse(null);
        RoundaboutConversion.Plan conversion = center == null ? null : RoundaboutConversion.plan(
                geometry, center, incidentSegments(center.id()),
                (handedness == TrafficHandedness.LHT) != reverseTraversal);
        if (conversion != null) traversalSpans = conversion.spans();
        RoadPresetSnapshot roundaboutSnapshot = roundaboutSnapshot(preset);

        Set<UUID> generated = new HashSet<>();
        List<RoadNode> addedNodes = new ArrayList<>(4);
        List<RoadNode> boundaryNodes = new ArrayList<>(4);
        Set<RoadNodeId> distinctNodeIds = new HashSet<>();
        for (var span : traversalSpans) {
            RoadPoint boundary = span.start();
            RoadNode node = findNodeAt(boundary).orElseGet(() -> {
                RoadNode created = new RoadNode(
                        new RoadNodeId(nextGeneratedId(generated)),
                        boundary
                );
                addedNodes.add(created);
                return created;
            });
            if (!distinctNodeIds.add(node.id())) {
                throw new IllegalArgumentException(
                        "roundabout ports resolve to the same graph node"
                );
            }
            boundaryNodes.add(node);
        }

        Road road = new Road(
                new RoadId(nextGeneratedId(generated)),
                handedness
        );
        List<RoadSegment> placedSegments = new ArrayList<>(4);
        for (int index = 0; index < traversalSpans.size(); index++) {
            RoadNode start = boundaryNodes.get(index);
            RoadNode end = boundaryNodes.get(
                    (index + 1) % boundaryNodes.size()
            );
            placedSegments.add(RoadSegment.path(
                    new RoadSegmentId(nextGeneratedId(generated)),
                    road.id(),
                    start.id(),
                    end.id(),
                    traversalSpans.get(index),
                    presetId,
                    preset.revision(),
                    roundaboutSnapshot,
                    handedness,
                    generatedLaneIds(preset.lanes().size(), generated)
            ));
        }

        List<RoadSegment> resultingSegments = new ArrayList<>(segments.values());
        List<PreparedSegmentSplit> replacements = new ArrayList<>();
        Set<RoadNodeId> affectedNodes = new HashSet<>(distinctNodeIds);
        if (conversion != null) {
            affectedNodes.add(center.id());
            for (var approach : conversion.approaches()) {
                RoadSegment source = approach.source();
                RoadNode port = boundaryNodes.stream().min(Comparator.comparingDouble(
                        node -> node.position().distanceTo(approach.port()))).orElseThrow();
                RoadSegment trimmed = recreateSegment(source, source.id(),
                        approach.atStart() ? port.id() : source.startNodeId(),
                        approach.atStart() ? source.endNodeId() : port.id(),
                        approach.trimmed(), source.lanes().stream().map(RoadLane::id).toList());
                replacements.add(new PreparedSegmentSplit(source, List.of(trimmed)));
                resultingSegments.remove(source);
                resultingSegments.add(trimmed);
                affectedNodes.add(source.startNodeId());
                affectedNodes.add(source.endNodeId());
            }
        }
        resultingSegments.addAll(placedSegments);
        List<LaneConnectorId> removedConnectors = connectors.values().stream()
                .filter(connector -> {
                    RoadLane lane = lanes.get(connector.fromLaneId());
                    RoadSegment segment = lane == null ? null : segments.get(lane.segmentId());
                    return segment != null && affectedNodes.contains(lane.component().direction().followsReferenceDirection()
                            ? segment.endNodeId() : segment.startNodeId());
                })
                .map(LaneConnector::id).toList();
        List<LaneConnector> addedConnectors = rebuildStraightConnectors(
                resultingSegments,
                affectedNodes,
                removedConnectors,
                generated
        );
        PreparedRoadNetworkMutation mutation = new PreparedRoadNetworkMutation(
                revision,
                road,
                true,
                addedNodes,
                replacements,
                placedSegments,
                removedConnectors,
                addedConnectors
        );
        validateNetworkMutation(mutation);
        return mutation;
    }

    private static RoadPresetSnapshot roundaboutSnapshot(RoadPreset preset) {
        List<LaneComponent> circulatingLanes = preset.lanes().stream()
                .map(lane -> new LaneComponent(
                        com.cookiecraftmods.roadbuilder.preset.LaneType.ROUNDABOUT,
                        lane.width(),
                        lane.lateralOffset(),
                        LaneTravelDirection.FORWARD
                ))
                .toList();
        return new RoadPresetSnapshot(
                preset.surfaceBlockWidth(),
                preset.surfaceBlock(),
                circulatingLanes,
                preset.surfaceBands(),
                preset.classification(),
                preset.intersectionStyle()
        );
    }

    public PreparedRoadNetworkMutation prepareStraightRoadNetwork(
            StraightGeometry geometry,
            ResourceLocation presetId,
            RoadPreset preset,
            TrafficHandedness handedness
    ) {
        return prepareIntersectingRoadNetwork(
                geometry,
                presetId,
                preset,
                handedness
        );
    }

    public PreparedRoadNetworkMutation withTunnelPortals(
            PreparedRoadNetworkMutation mutation,
            com.cookiecraftmods.roadbuilder.placement.PlacementBlockWorld world
    ) {
        return withTunnelPortals(mutation, world, position -> false);
    }

    public PreparedRoadNetworkMutation withTunnelPortals(
            PreparedRoadNetworkMutation mutation,
            com.cookiecraftmods.roadbuilder.placement.PlacementBlockWorld world,
            java.util.function.Predicate<net.minecraft.core.BlockPos> existingRoadPosition
    ) {
        List<RoadNode> nodes = new ArrayList<>(mutation.addedNodes());
        List<RoadSegment> placed = new ArrayList<>();
        Set<RoadNodeId> affected = new HashSet<>();
        Set<UUID> generated = new HashSet<>();
        for (RoadSegment segment : mutation.placedSegments()) {
            if (segment.presetSnapshot().sunkenRoad()) { placed.add(segment); continue; }
            var geometry = segment.referenceGeometry();
            List<Double> cuts = com.cookiecraftmods.roadbuilder.placement.RoadTunnelPlanner.portalFractions(
                    geometry, segment.presetSnapshot().surfaceBlockWidth(), world, existingRoadPosition);
            if (cuts.isEmpty()) { placed.add(segment); continue; }
            List<RoadNodeId> boundaries = new ArrayList<>();
            boundaries.add(segment.startNodeId());
            List<RoadPoint> points = new ArrayList<>();
            for (double fraction : cuts) {
                RoadPoint point = geometry.sample(fraction * geometry.length()).position();
                RoadNode node = new RoadNode(new RoadNodeId(nextGeneratedId(generated)), point);
                nodes.add(node);
                points.add(point);
                boundaries.add(node.id());
            }
            boundaries.add(segment.endNodeId());
            affected.addAll(boundaries);
            var parts = ReferenceGeometryEditor.split(geometry, cuts, points);
            for (int index = 0; index < parts.size(); index++) {
                placed.add(RoadSegment.path(new RoadSegmentId(nextGeneratedId(generated)), segment.roadId(),
                        boundaries.get(index), boundaries.get(index + 1), parts.get(index), segment.presetId(),
                        segment.presetRevision(), segment.presetSnapshot(), segment.trafficHandedness(),
                        generatedLaneIds(segment.presetSnapshot().lanes(segment.trafficHandedness()).size(), generated)));
            }
        }
        if (affected.isEmpty()) return mutation;
        Map<RoadSegmentId, RoadSegment> resulting = new HashMap<>(segments);
        mutation.segmentSplits().forEach(split -> {
            resulting.remove(split.original().id());
            split.replacements().forEach(segment -> resulting.put(segment.id(), segment));
        });
        placed.forEach(segment -> resulting.put(segment.id(), segment));
        Set<RoadLaneId> affectedLanes = new HashSet<>();
        mutation.placedSegments().stream()
                .filter(segment -> placed.stream().noneMatch(candidate -> candidate.id().equals(segment.id())))
                .flatMap(segment -> segment.lanes().stream()).map(RoadLane::id).forEach(affectedLanes::add);
        // Connector rebuilding uses graph node incidence, including newly created portals.
        List<LaneConnectorId> removed = new ArrayList<>(mutation.removedConnectorIds());
        connectors.values().stream().filter(connector -> (affectedLanes.contains(connector.fromLaneId()) || affectedLanes.contains(connector.toLaneId())))
                .map(LaneConnector::id).filter(id -> !removed.contains(id)).forEach(removed::add);
        List<LaneConnector> added = new ArrayList<>(mutation.addedConnectors().stream()
                .filter(connector -> !(affectedLanes.contains(connector.fromLaneId()) || affectedLanes.contains(connector.toLaneId()))).toList());
        for (LaneConnector connector : rebuildStraightConnectors(resulting.values(), affected, removed, generated)) {
            if (added.stream().noneMatch(existing -> existing.fromLaneId().equals(connector.fromLaneId())
                    && existing.toLaneId().equals(connector.toLaneId()))) added.add(connector);
        }
        var result = new PreparedRoadNetworkMutation(mutation.expectedRevision(), mutation.road(), mutation.createsRoad(),
                nodes, mutation.segmentSplits(), placed, removed, added);
        validateNetworkMutation(result);
        return result;
    }

    private PreparedRoadNetworkMutation prepareIntersectingRoadNetwork(
            ReferenceGeometry geometry,
            ResourceLocation presetId,
            RoadPreset preset,
            TrafficHandedness handedness
    ) {
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(presetId, "presetId");
        Objects.requireNonNull(preset, "preset");
        Objects.requireNonNull(handedness, "handedness");

        Optional<RoadNode> startPort = findNodeAt(geometry.start());
        Optional<RoadNode> endPort = findNodeAt(geometry.end());
        List<RoadCrossingCandidate> crossings = new RoadPathCrossingAnalyzer().analyze(
                geometry,
                segments.values(),
                JUNCTION_MAXIMUM_VERTICAL_GAP,
                RoadPathCrossingAnalyzer.DEFAULT_MAXIMUM_CHORD_LENGTH
        ).stream().filter(crossing -> {
            Optional<RoadNode> port = crossing.proposedPathFraction() == 0.0
                    ? startPort : crossing.proposedPathFraction() == 1.0
                    ? endPort : Optional.empty();
            RoadSegment crossed = segments.get(crossing.crossedSegmentId());
            // End-cap snapping wins over a projection slightly inside the same
            // arm. Splitting that arm would create a tiny, redundant junction.
            return port.isEmpty() || !(crossed.startNodeId().equals(port.get().id())
                    || crossed.endNodeId().equals(port.get().id()));
        }).toList();
        if (crossings.isEmpty()) {
            PreparedRoadAddition addition = prepareStraightRoad(
                    geometry,
                    presetId,
                    preset,
                    handedness
            );
            return withJunctionElevations(new PreparedRoadNetworkMutation(
                    addition.expectedRevision(),
                    addition.road(),
                    addition.createsRoad(),
                    addition.nodes(),
                    List.of(),
                    List.of(addition.segment()),
                    List.of(),
                    addition.connectors()
            ));
        }

        Set<UUID> generated = new HashSet<>();
        List<RoadNode> addedNodes = new ArrayList<>();
        List<RoadNode> crossingNodes = new ArrayList<>(crossings.size());
        RoadNode startJunction = null;
        RoadNode endJunction = null;
        for (RoadCrossingCandidate crossing : crossings) {
            RoadNode node;
            if (crossing.proposedPathFraction() == 0.0
                    && startJunction != null) {
                node = startJunction;
            } else if (crossing.proposedPathFraction() == 1.0
                    && endJunction != null) {
                node = endJunction;
            } else {
                node = new RoadNode(
                        new RoadNodeId(nextGeneratedId(generated)),
                        crossing.nodePosition()
                );
                addedNodes.add(node);
                if (crossing.proposedPathFraction() == 0.0) {
                    startJunction = node;
                } else if (crossing.proposedPathFraction() == 1.0) {
                    endJunction = node;
                }
            }
            crossingNodes.add(node);
        }

        Map<RoadSegmentId, List<IndexedCrossing>> crossingsBySegment =
                new LinkedHashMap<>();
        for (int index = 0; index < crossings.size(); index++) {
            RoadCrossingCandidate crossing = crossings.get(index);
            crossingsBySegment.computeIfAbsent(
                    crossing.crossedSegmentId(),
                    ignored -> new ArrayList<>()
            ).add(new IndexedCrossing(crossing, crossingNodes.get(index)));
        }

        List<PreparedSegmentSplit> splits = new ArrayList<>(
                crossingsBySegment.size()
        );
        Set<RoadLaneId> replacedLaneIds = new HashSet<>();
        Set<RoadNodeId> affectedNodeIds = new HashSet<>();
        for (Map.Entry<RoadSegmentId, List<IndexedCrossing>> entry
                : crossingsBySegment.entrySet()) {
            RoadSegment original = segments.get(entry.getKey());
            List<IndexedCrossing> segmentCrossings = entry.getValue().stream()
                    .sorted(Comparator.comparingDouble(value ->
                            value.candidate().crossedPathFraction()))
                    .toList();
            original.lanes().stream().map(RoadLane::id).forEach(replacedLaneIds::add);
            affectedNodeIds.add(original.startNodeId());
            affectedNodeIds.add(original.endNodeId());
            segmentCrossings.forEach(value ->
                    affectedNodeIds.add(value.node().id()));

            List<Double> splitFractions = segmentCrossings.stream()
                    .map(value -> value.candidate().crossedPathFraction())
                    .toList();
            List<RoadPoint> splitPoints = segmentCrossings.stream()
                    .map(value -> value.node().position())
                    .toList();
            List<ReferenceGeometry> geometries = ReferenceGeometryEditor.split(
                    original.referenceGeometry(),
                    splitFractions,
                    splitPoints
            );
            List<RoadNodeId> boundaries = new ArrayList<>(
                    segmentCrossings.size() + 2
            );
            boundaries.add(original.startNodeId());
            segmentCrossings.forEach(value -> boundaries.add(value.node().id()));
            boundaries.add(original.endNodeId());
            List<RoadSegment> replacements = new ArrayList<>(geometries.size());
            for (int childIndex = 0;
                    childIndex < geometries.size();
                    childIndex++) {
                replacements.add(recreateSegment(
                        original,
                        childIndex == 0
                                ? original.id()
                                : new RoadSegmentId(nextGeneratedId(generated)),
                        boundaries.get(childIndex),
                        boundaries.get(childIndex + 1),
                        geometries.get(childIndex),
                        childIndex == 0
                                ? original.lanes().stream()
                                        .map(RoadLane::id)
                                        .toList()
                                : generatedLaneIds(
                                        original.presetSnapshot().lanes().size(),
                                        generated
                                )
                ));
            }
            splits.add(new PreparedSegmentSplit(original, replacements));
        }

        Optional<RoadNode> joinedStart = startJunction == null
                ? findNodeAt(geometry.start())
                : Optional.of(startJunction);
        Optional<RoadNode> joinedEnd = endJunction == null
                ? findNodeAt(geometry.end())
                : Optional.of(endJunction);
        RoadNode startNode = joinedStart.orElseGet(() -> {
            RoadNode node = new RoadNode(
                    new RoadNodeId(nextGeneratedId(generated)),
                    geometry.start()
            );
            addedNodes.add(node);
            return node;
        });
        RoadNode endNode = joinedEnd.orElseGet(() -> {
            RoadNode node = new RoadNode(
                    new RoadNodeId(nextGeneratedId(generated)),
                    geometry.end()
            );
            addedNodes.add(node);
            return node;
        });
        if (startNode.id().equals(endNode.id())) {
            throw new IllegalArgumentException(
                    "straight-road endpoints resolve to the same graph node"
            );
        }
        affectedNodeIds.add(startNode.id());
        affectedNodeIds.add(endNode.id());

        Optional<Road> joinedRoad = selectContinuationRoad(
                joinedStart,
                joinedEnd,
                geometry,
                handedness
        );
        boolean createsRoad = joinedRoad.isEmpty();
        Road road = joinedRoad.orElseGet(() -> new Road(
                new RoadId(nextGeneratedId(generated)),
                handedness
        ));

        List<RoadNode> pathNodes = new ArrayList<>(crossings.size() + 2);
        pathNodes.add(startNode);
        for (int index = 0; index < crossings.size(); index++) {
            double fraction = crossings.get(index).proposedPathFraction();
            if (fraction > 0.0 && fraction < 1.0) {
                pathNodes.add(crossingNodes.get(index));
            }
        }
        pathNodes.add(endNode);

        List<RoadCrossingCandidate> interiorCrossings = crossings.stream()
                .filter(crossing -> crossing.proposedPathFraction() > 0.0
                        && crossing.proposedPathFraction() < 1.0)
                .toList();
        ReferenceGeometry nodeBoundGeometry = geometry;
        List<ReferenceGeometry> placedGeometries = ReferenceGeometryEditor.split(
                nodeBoundGeometry,
                interiorCrossings.stream()
                        .map(RoadCrossingCandidate::proposedPathFraction)
                        .toList(),
                pathNodes.subList(1, pathNodes.size() - 1).stream()
                        .map(RoadNode::position)
                        .toList()
        );
        List<RoadSegment> placedSegments = new ArrayList<>(pathNodes.size() - 1);
        for (int index = 0; index < pathNodes.size() - 1; index++) {
            RoadNode childStart = pathNodes.get(index);
            RoadNode childEnd = pathNodes.get(index + 1);
            placedSegments.add(RoadSegment.path(
                    new RoadSegmentId(nextGeneratedId(generated)),
                    road.id(),
                    childStart.id(),
                    childEnd.id(),
                    placedGeometries.get(index),
                    presetId,
                    preset.revision(),
                    RoadPresetSnapshot.from(preset),
                    handedness,
                    generatedLaneIds(preset.lanes().size(), generated)
            ));
        }

        List<LaneConnectorId> removedConnectorIds = connectors.values().stream()
                .filter(connector -> replacedLaneIds.contains(connector.fromLaneId())
                        || replacedLaneIds.contains(connector.toLaneId()))
                .map(LaneConnector::id)
                .sorted()
                .toList();

        Map<RoadSegmentId, RoadSegment> resultingSegments = new HashMap<>(segments);
        splits.forEach(split -> {
            resultingSegments.remove(split.original().id());
            split.replacements().forEach(segment ->
                    resultingSegments.put(segment.id(), segment));
        });
        placedSegments.forEach(segment -> resultingSegments.put(segment.id(), segment));

        List<LaneConnector> addedConnectors = rebuildStraightConnectors(
                resultingSegments.values(),
                affectedNodeIds,
                removedConnectorIds,
                generated
        );
        PreparedRoadNetworkMutation mutation = new PreparedRoadNetworkMutation(
                revision,
                road,
                createsRoad,
                addedNodes,
                splits,
                placedSegments,
                removedConnectorIds,
                addedConnectors
        );
        validateNetworkMutation(mutation);
        return withJunctionElevations(mutation);
    }

    private PreparedRoadNetworkMutation withJunctionElevations(PreparedRoadNetworkMutation mutation) {
        Map<RoadSegmentId,RoadSegment> resulting = new HashMap<>(segments);
        mutation.segmentSplits().forEach(split -> resulting.remove(split.original().id()));
        mutation.allUpsertedSegments().forEach(segment -> resulting.put(segment.id(),segment));
        Map<RoadNodeId,RoadNode> allNodes = new HashMap<>(nodes);
        mutation.addedNodes().forEach(node -> allNodes.put(node.id(),node));
        Set<RoadNodeId> touched = new HashSet<>();
        mutation.allUpsertedSegments().forEach(segment -> {
            touched.add(segment.startNodeId()); touched.add(segment.endNodeId());
        });
        Map<RoadNodeId,Double> holds = new HashMap<>();
        for (var id : touched) {
            var incident = resulting.values().stream().filter(s -> s.startNodeId().equals(id)||s.endNodeId().equals(id)).toList();
            var node = allNodes.get(id);
            if (incident.size()>=3 && node!=null && !RoadForkJunction.isFork(node,incident)
                    && AuxiliaryLanePorts.attachments(id,incident).isEmpty()) {
                holds.put(id,incident.stream().mapToDouble(s -> s.presetSnapshot().surfaceBlockWidth()).max().orElse(1)/2+2);
            }
        }
        if (holds.isEmpty()) return mutation;
        Map<RoadSegmentId,RoadSegment> adjusted = new HashMap<>();
        Set<RoadNodeId> affected = new HashSet<>();
        for (var segment : resulting.values()) {
            double start = holds.getOrDefault(segment.startNodeId(),0.0);
            double end = holds.getOrDefault(segment.endNodeId(),0.0);
            if (start==0 && end==0) continue;
            var geometry = com.cookiecraftmods.roadbuilder.geometry.JunctionElevationProfile.fit(segment.referenceGeometry(),start,end);
            if (geometry==segment.referenceGeometry() || geometry.equals(segment.referenceGeometry())) continue;
            var replacement = recreateSegment(segment,segment.id(),segment.startNodeId(),segment.endNodeId(),geometry,
                    segment.lanes().stream().map(RoadLane::id).toList());
            adjusted.put(segment.id(),replacement);
            affected.add(segment.startNodeId()); affected.add(segment.endNodeId());
        }
        if (adjusted.isEmpty()) return mutation;
        resulting.putAll(adjusted);
        List<PreparedSegmentSplit> splits = new ArrayList<>();
        Set<RoadSegmentId> accounted = new HashSet<>();
        for (var split : mutation.segmentSplits()) {
            splits.add(new PreparedSegmentSplit(split.original(),split.replacements().stream()
                    .map(s -> adjusted.getOrDefault(s.id(),s)).toList()));
            split.replacements().forEach(s -> accounted.add(s.id()));
        }
        var placed = mutation.placedSegments().stream().map(s -> adjusted.getOrDefault(s.id(),s)).toList();
        placed.forEach(s -> accounted.add(s.id()));
        for (var entry : adjusted.entrySet()) if (!accounted.contains(entry.getKey()))
            splits.add(new PreparedSegmentSplit(segments.get(entry.getKey()),List.of(entry.getValue())));
        Set<RoadLaneId> changedLanes = new HashSet<>();
        adjusted.values().forEach(s -> s.lanes().forEach(l -> changedLanes.add(l.id())));
        List<LaneConnectorId> removed = new ArrayList<>(mutation.removedConnectorIds());
        connectors.values().stream().filter(c -> changedLanes.contains(c.fromLaneId())||changedLanes.contains(c.toLaneId()))
                .map(LaneConnector::id).filter(id -> !removed.contains(id)).forEach(removed::add);
        List<LaneConnector> added = new ArrayList<>(mutation.addedConnectors().stream()
                .filter(c -> !changedLanes.contains(c.fromLaneId())&&!changedLanes.contains(c.toLaneId())).toList());
        Set<UUID> generated = new HashSet<>();
        for (var connector : rebuildStraightConnectors(resulting.values(),affected,removed,generated))
            if (added.stream().noneMatch(c -> c.fromLaneId().equals(connector.fromLaneId())&&c.toLaneId().equals(connector.toLaneId())))
                added.add(connector);
        var result = new PreparedRoadNetworkMutation(mutation.expectedRevision(),mutation.road(),mutation.createsRoad(),
                mutation.addedNodes(),splits,placed,removed,added);
        validateNetworkMutation(result);
        return result;
    }

    private record IndexedCrossing(
            RoadCrossingCandidate candidate,
            RoadNode node
    ) {
    }

    public void applyNetworkMutation(PreparedRoadNetworkMutation mutation) {
        Objects.requireNonNull(mutation, "mutation");
        if (revision != mutation.expectedRevision()) {
            throw new IllegalStateException("road graph changed after placement preflight");
        }
        validateNetworkMutation(mutation);

        changeListener.run();
        Map<RoadNodeId, JunctionDefinition> migratedDefinitions =
                migrateDefinitionsForSplits(
                        junctionDefinitions,
                        mutation.segmentSplits()
                );
        junctionDefinitions.clear();
        junctionDefinitions.putAll(migratedDefinitions);
        if (mutation.createsRoad()) {
            roads.put(mutation.road().id(), mutation.road());
        }
        mutation.addedNodes().forEach(node -> nodes.put(node.id(), node));
        mutation.removedConnectorIds().forEach(connectors::remove);
        for (PreparedSegmentSplit split : mutation.segmentSplits()) {
            RoadSegment removed = segments.remove(split.original().id());
            if (removed != null) {
                removed.lanes().forEach(lane -> lanes.remove(lane.id()));
            }
        }
        for (RoadSegment segment : mutation.allUpsertedSegments()) {
            segments.put(segment.id(), segment);
            segment.lanes().forEach(lane -> lanes.put(lane.id(), lane));
        }
        mutation.addedConnectors().forEach(connector ->
                connectors.put(connector.id(), connector));
        removeDetachedReplacementNodes(nodes, segments.values(), mutation);
        revision++;
    }

        public void applyDelta(RoadGraphDelta delta) {
        Objects.requireNonNull(delta, "delta");
        if (!dimension.equals(delta.dimension())) {
            throw new IllegalArgumentException(
                    "road graph delta dimension does not match"
            );
        }
        if (revision != delta.baseRevision()) {
            throw new IllegalStateException(
                    "road graph changed after placement job preparation"
            );
        }
        RoadGraphSnapshot candidate = delta.applyTo(snapshot());
        RoadGraph validated = RoadGraph.restore(
                dimension,
                candidate.revision(),
                candidate.roads().values(),
                candidate.nodes().values(),
                candidate.segments().values(),
                candidate.connectors().values(),
                candidate.junctionDefinitions().values(),
                limits,
                () -> {
                }
        );

        changeListener.run();
        roads.clear();
        roads.putAll(validated.roads);
        nodes.clear();
        nodes.putAll(validated.nodes);
        segments.clear();
        segments.putAll(validated.segments);
        lanes.clear();
        lanes.putAll(validated.lanes);
        connectors.clear();
        connectors.putAll(validated.connectors);
        junctionDefinitions.clear();
        junctionDefinitions.putAll(validated.junctionDefinitions);
        revision = validated.revision;
    }

    public void applyAddition(PreparedRoadAddition addition) {
        Objects.requireNonNull(addition, "addition");
        if (revision != addition.expectedRevision()) {
            throw new IllegalStateException("road graph changed after placement preflight");
        }
        validateAddition(addition);

        changeListener.run();
        if (addition.createsRoad()) {
            roads.put(addition.road().id(), addition.road());
        }
        for (RoadNode node : addition.nodes()) {
            nodes.put(node.id(), node);
        }
        segments.put(addition.segment().id(), addition.segment());
        for (RoadLane lane : addition.segment().lanes()) {
            lanes.put(lane.id(), lane);
        }
        for (LaneConnector connector : addition.connectors()) {
            connectors.put(connector.id(), connector);
        }
        revision++;
    }

    public PreparedSegmentRemoval prepareSegmentRemoval(RoadSegmentId segmentId) {
        RoadSegment segment = segments.get(Objects.requireNonNull(segmentId, "segmentId"));
        if (segment == null) {
            throw new IllegalArgumentException("unknown road segment " + segmentId);
        }

        Set<RoadLaneId> removedLaneIds = new HashSet<>();
        for (RoadLane lane : segment.lanes()) {
            removedLaneIds.add(lane.id());
        }
        List<LaneConnectorId> removedConnectors = connectors.values().stream()
                .filter(connector -> removedLaneIds.contains(connector.fromLaneId())
                        || removedLaneIds.contains(connector.toLaneId()))
                .map(LaneConnector::id)
                .sorted()
                .toList();

        List<RoadNodeId> orphanedNodes = nodes.keySet().stream()
                .filter(nodeId -> segments.values().stream()
                        .filter(candidate -> !candidate.id().equals(segmentId))
                        .noneMatch(candidate -> candidate.startNodeId().equals(nodeId)
                                || candidate.endNodeId().equals(nodeId)))
                .sorted()
                .toList();
        boolean removeRoad = segments.values().stream()
                .filter(candidate -> !candidate.id().equals(segmentId))
                .noneMatch(candidate -> candidate.roadId().equals(segment.roadId()));
        preflightSurvivingNodeAnchors(segment, orphanedNodes);
        return new PreparedSegmentRemoval(
                revision,
                segmentId,
                orphanedNodes,
                removedConnectors,
                removeRoad
        );
    }

    private void preflightSurvivingNodeAnchors(
            RoadSegment removed,
            List<RoadNodeId> orphanedNodeIds
    ) {
        Map<RoadNodeId, RoadNode> remainingNodes = new HashMap<>(nodes);
        orphanedNodeIds.forEach(remainingNodes::remove);
        Map<RoadSegmentId, RoadSegment> remainingSegments = new HashMap<>(segments);
        remainingSegments.remove(removed.id());
        RoadBoundaryRecovery.recover(
                List.of(removed.startNodeId(), removed.endNodeId()),
                remainingNodes, remainingSegments,
                new HashMap<>(connectors), new HashMap<>(junctionDefinitions)
        );
        if (remainingNodes.size() > limits.maximumNodeCount()) {
            throw new IllegalArgumentException("road graph node limit reached");
        }
    }

    public void applyRemoval(PreparedSegmentRemoval removal) {
        Objects.requireNonNull(removal, "removal");
        if (revision != removal.expectedRevision()) {
            throw new IllegalStateException("road graph changed after removal preflight");
        }
        RoadSegment segment = segments.get(removal.segmentId());
        if (segment == null) {
            throw new IllegalStateException("prepared segment no longer exists");
        }

        PreparedSegmentRemoval current = prepareSegmentRemoval(removal.segmentId());
        if (!current.equals(removal)) {
            throw new IllegalStateException("prepared segment removal no longer matches graph topology");
        }

        changeListener.run();
        removeDefinitionApproach(
                segment.startNodeId(),
                new ApproachKey(segment.id(), ApproachEnd.START)
        );
        removeDefinitionApproach(
                segment.endNodeId(),
                new ApproachKey(segment.id(), ApproachEnd.END)
        );
        for (LaneConnectorId connectorId : removal.connectorIds()) {
            connectors.remove(connectorId);
        }
        for (RoadLane lane : segment.lanes()) {
            lanes.remove(lane.id());
        }
        segments.remove(segment.id());
        for (RoadNodeId nodeId : removal.orphanedNodeIds()) {
            nodes.remove(nodeId);
            junctionDefinitions.remove(nodeId);
        }
        reapplySurvivingNodeAnchors(segment, removal.orphanedNodeIds());
        if (removal.removeRoad()) {
            roads.remove(segment.roadId());
        }
        revision++;
    }

    private void reapplySurvivingNodeAnchors(
            RoadSegment removed,
            List<RoadNodeId> orphanedNodeIds
    ) {
        RoadBoundaryRecovery.recover(
                List.of(removed.startNodeId(), removed.endNodeId()),
                nodes, segments, connectors, junctionDefinitions
        );
    }

    public RoadGraphSnapshot snapshot() {
        return new RoadGraphSnapshot(
                dimension,
                revision,
                sortedCopy(roads, id -> id.value()),
                sortedCopy(nodes, id -> id.value()),
                sortedCopy(segments, id -> id.value()),
                sortedCopy(lanes, id -> id.value()),
                sortedCopy(connectors, id -> id.value()),
                sortedCopy(junctionDefinitions, id -> id.value())
        );
    }

    public void rebuildAutomaticConnectors() {
        List<LaneConnector> manual = connectors.values().stream()
                .filter(connector -> connector.movement().manual())
                .sorted(Comparator.comparing(LaneConnector::id))
                .toList();
        List<LaneConnector> replacement = new ArrayList<>();
        Set<UUID> generated = new HashSet<>();
        JunctionConnectorPlanner planner = new JunctionConnectorPlanner();
        for (RoadNode node : nodes.values().stream()
                .sorted(Comparator.comparing(RoadNode::id))
                .toList()) {
            List<RoadSegment> incident = segments.values().stream()
                    .filter(segment -> segment.startNodeId().equals(node.id())
                            || segment.endNodeId().equals(node.id()))
                    .sorted(Comparator.comparing(RoadSegment::id))
                    .toList();
            if (incident.size() < 2) {
                continue;
            }
            replacement.addAll(planner.plan(
                    node.id(),
                    incident,
                    manual,
                    () -> new LaneConnectorId(nextGeneratedId(generated))
            ));
        }
        if (replacement.size() > limits.maximumConnectorCount()) {
            throw new IllegalStateException(
                    "rebuilt road graph connector limit reached"
            );
        }
        RoadGraph.restore(
                dimension,
                revision + 1,
                roads.values(),
                nodes.values(),
                segments.values(),
                replacement,
                junctionDefinitions.values(),
                limits,
                () -> { }
        );
        changeListener.run();
        connectors.clear();
        replacement.forEach(connector ->
                connectors.put(connector.id(), connector));
        revision++;
    }

    private UUID nextGeneratedId(Set<UUID> generated) {
        UUID id = Objects.requireNonNull(idGenerator.get(), "idGenerator returned null");
        if (!generated.add(id) || containsUuid(id)) {
            throw new IllegalStateException("road graph ID collision: " + id);
        }
        return id;
    }

    private boolean containsUuid(UUID id) {
        return roads.keySet().stream().anyMatch(value -> value.value().equals(id))
                || nodes.keySet().stream().anyMatch(value -> value.value().equals(id))
                || segments.keySet().stream().anyMatch(value -> value.value().equals(id))
                || lanes.keySet().stream().anyMatch(value -> value.value().equals(id))
                || connectors.keySet().stream().anyMatch(value -> value.value().equals(id));
    }

    private void validateAddition(PreparedRoadAddition addition) {
        RoadSegment segment = addition.segment();
        if (!segment.roadId().equals(addition.road().id())) {
            throw new IllegalArgumentException("segment road ID does not match prepared road");
        }
        if (segment.trafficHandedness() != addition.road().trafficHandedness()) {
            throw new IllegalArgumentException("segment handedness does not match prepared road");
        }
        Map<RoadNodeId, RoadNode> preparedNodes = new HashMap<>();
        for (RoadNode node : addition.nodes()) {
            if (preparedNodes.put(node.id(), node) != null) {
                throw new IllegalArgumentException("duplicate prepared node " + node.id());
            }
        }
        RoadNode start = preparedNodes.getOrDefault(
                segment.startNodeId(),
                nodes.get(segment.startNodeId())
        );
        RoadNode end = preparedNodes.getOrDefault(
                segment.endNodeId(),
                nodes.get(segment.endNodeId())
        );
        if (start == null || end == null) {
            throw new IllegalArgumentException("prepared nodes do not match segment boundaries");
        }
        List<RoadSegment> startIncident = new ArrayList<>(
                incidentSegments(start.id())
        );
        startIncident.add(segment);
        List<RoadSegment> endIncident = new ArrayList<>(
                incidentSegments(end.id())
        );
        endIncident.add(segment);
        if (!RoadNodePortResolver.isValidTopology(start, startIncident)
                || !RoadNodePortResolver.isValidTopology(end, endIncident)) {
            throw new IllegalArgumentException(
                    "segment geometry does not meet its boundary node end caps"
            );
        }
        Road existingRoad = roads.get(addition.road().id());
        if ((addition.createsRoad() && existingRoad != null)
                || (!addition.createsRoad() && !addition.road().equals(existingRoad))
                || preparedNodes.keySet().stream().anyMatch(nodes::containsKey)
                || segments.containsKey(segment.id())
                || segment.lanes().stream().map(RoadLane::id).anyMatch(lanes::containsKey)) {
            throw new IllegalStateException("prepared graph records collide with existing IDs");
        }
        Set<RoadLaneId> availableLaneIds = new HashSet<>(lanes.keySet());
        segment.lanes().stream().map(RoadLane::id).forEach(availableLaneIds::add);
        Set<LaneConnectorId> preparedConnectorIds = new HashSet<>();
        Set<String> preparedMovements = new HashSet<>();
        for (LaneConnector connector : addition.connectors()) {
            if (!preparedConnectorIds.add(connector.id())
                    || connectors.containsKey(connector.id())) {
                throw new IllegalStateException(
                        "prepared connector ID collides with another graph record"
                );
            }
            if (!availableLaneIds.contains(connector.fromLaneId())
                    || !availableLaneIds.contains(connector.toLaneId())) {
                throw new IllegalArgumentException(
                        "prepared connector references an unknown lane"
                );
            }
            if (segment.lanes().stream()
                    .map(RoadLane::id)
                    .noneMatch(id -> id.equals(connector.fromLaneId())
                            || id.equals(connector.toLaneId()))) {
                throw new IllegalArgumentException(
                        "prepared connector does not involve the added segment"
                );
            }
            String movement = connector.fromLaneId() + "->" + connector.toLaneId();
            if (!preparedMovements.add(movement)
                    || connectors.values().stream().anyMatch(existing ->
                    existing.fromLaneId().equals(connector.fromLaneId())
                            && existing.toLaneId().equals(connector.toLaneId()))) {
                throw new IllegalArgumentException("duplicate prepared lane movement");
            }
        }
        if ((long) roads.size() + (addition.createsRoad() ? 1 : 0)
                > limits.maximumRoadCount()
                || (long) nodes.size() + addition.nodes().size() > limits.maximumNodeCount()
                || (long) segments.size() + 1 > limits.maximumSegmentCount()
                || (long) lanes.size() + segment.lanes().size() > limits.maximumLaneCount()
                || (long) connectors.size() + addition.connectors().size()
                > limits.maximumConnectorCount()) {
            throw new IllegalStateException("prepared road would exceed graph limits");
        }
    }

    private void validateNetworkMutation(PreparedRoadNetworkMutation mutation) {
        Map<RoadId, Road> candidateRoads = new HashMap<>(roads);
        Map<RoadNodeId, RoadNode> candidateNodes = new HashMap<>(nodes);
        Map<RoadSegmentId, RoadSegment> candidateSegments = new HashMap<>(segments);
        Map<LaneConnectorId, LaneConnector> candidateConnectors =
                new HashMap<>(connectors);

        if (mutation.createsRoad()) {
            if (candidateRoads.put(mutation.road().id(), mutation.road()) != null) {
                throw new IllegalStateException("prepared road ID already exists");
            }
        } else if (!mutation.road().equals(candidateRoads.get(mutation.road().id()))) {
            throw new IllegalStateException("prepared continuation road changed");
        }
        for (RoadNode node : mutation.addedNodes()) {
            if (candidateNodes.put(node.id(), node) != null) {
                throw new IllegalStateException("prepared node ID already exists");
            }
        }
        mutation.segmentSplits().forEach(split -> {
            if (!split.original().equals(candidateSegments.remove(split.original().id()))) {
                throw new IllegalStateException("crossed segment changed after preflight");
            }
        });
        for (RoadSegment segment : mutation.allUpsertedSegments()) {
            if (candidateSegments.put(segment.id(), segment) != null) {
                throw new IllegalStateException("prepared segment ID collides");
            }
        }
        mutation.removedConnectorIds().forEach(candidateConnectors::remove);
        for (LaneConnector connector : mutation.addedConnectors()) {
            if (candidateConnectors.put(connector.id(), connector) != null) {
                throw new IllegalStateException("prepared connector ID collides");
            }
        }

        Map<RoadNodeId, JunctionDefinition> candidateDefinitions =
                migrateDefinitionsForSplits(
                        junctionDefinitions,
                        mutation.segmentSplits()
                );
        removeDetachedReplacementNodes(candidateNodes, candidateSegments.values(), mutation);
        RoadGraph.restore(
                dimension,
                revision + 1,
                candidateRoads.values(),
                candidateNodes.values(),
                candidateSegments.values(),
                candidateConnectors.values(),
                candidateDefinitions.values(),
                limits,
                () -> { }
        );
    }

    private static void removeDetachedReplacementNodes(Map<RoadNodeId, RoadNode> nodes,
            Collection<RoadSegment> segments, PreparedRoadNetworkMutation mutation) {
        Set<RoadNodeId> used = new HashSet<>();
        segments.forEach(segment -> {
            used.add(segment.startNodeId());
            used.add(segment.endNodeId());
        });
        for (PreparedSegmentSplit split : mutation.segmentSplits()) {
            if (!used.contains(split.original().startNodeId())) nodes.remove(split.original().startNodeId());
            if (!used.contains(split.original().endNodeId())) nodes.remove(split.original().endNodeId());
        }
    }

    private void validateJunctionDefinition(JunctionDefinition definition) {
        RoadNode node = nodes.get(definition.nodeId());
        if (node == null) {
            throw new IllegalArgumentException(
                    "junction definition references an unknown node"
            );
        }
        List<RoadSegment> incident = segments.values().stream()
                .filter(segment ->
                        segment.startNodeId().equals(definition.nodeId())
                                || segment.endNodeId().equals(
                                        definition.nodeId()
                                ))
                .toList();
        JunctionSolveResult result = new JunctionSolver().solve(
                node,
                incident,
                definition
        );
        if (result instanceof JunctionSolveResult.Failure failure) {
            throw new IllegalArgumentException(
                    "invalid junction definition: " + failure.diagnostic()
            );
        }
    }

    private static Map<RoadNodeId, JunctionDefinition>
    migrateDefinitionsForSplits(
            Map<RoadNodeId, JunctionDefinition> source,
            List<PreparedSegmentSplit> splits
    ) {
        Map<RoadNodeId, JunctionDefinition> migrated =
                new HashMap<>(source);
        for (PreparedSegmentSplit split : splits) {
            RoadSegment original = split.original();
            if (!split.replacements().get(0).startNodeId().equals(original.startNodeId())) {
                migrated.remove(original.startNodeId());
            }
            if (!split.replacements().get(split.replacements().size() - 1).endNodeId().equals(original.endNodeId())) {
                migrated.remove(original.endNodeId());
            }
            RoadSegment last = split.replacements().get(
                    split.replacements().size() - 1
            );
            JunctionDefinition atEnd = migrated.get(original.endNodeId());
            if (atEnd != null) {
                ApproachKey oldKey = new ApproachKey(
                        original.id(),
                        ApproachEnd.END
                );
                ApproachKey newKey = new ApproachKey(
                        last.id(),
                        ApproachEnd.END
                );
                if (!oldKey.equals(newKey)
                        && (atEnd.primaryApproachKeys().contains(oldKey)
                                || atEnd.approachOverrides().containsKey(oldKey))) {
                    migrated.put(
                            original.endNodeId(),
                            atEnd.migrateApproach(oldKey, newKey)
                    );
                }
            }
        }
        return migrated;
    }

    private void removeDefinitionApproach(
            RoadNodeId nodeId,
            ApproachKey removed
    ) {
        JunctionDefinition definition = junctionDefinitions.get(nodeId);
        if (definition == null) {
            return;
        }
        long remainingDegree = segments.values().stream()
                .filter(candidate -> !candidate.id()
                        .equals(removed.segmentId()))
                .filter(candidate -> candidate.startNodeId().equals(nodeId)
                        || candidate.endNodeId().equals(nodeId))
                .count();
        if (remainingDegree < 2) {
            junctionDefinitions.remove(nodeId);
        } else {
            junctionDefinitions.put(
                    nodeId,
                    definition.removeApproach(removed)
            );
        }
    }

    private RoadSegment recreateSegment(
            RoadSegment source,
            RoadSegmentId id,
            RoadNodeId startNodeId,
            RoadNodeId endNodeId,
            ReferenceGeometry geometry,
            List<RoadLaneId> laneIds
    ) {
        return RoadSegment.path(
                id,
                source.roadId(),
                startNodeId,
                endNodeId,
                geometry,
                source.presetId(),
                source.presetRevision(),
                source.presetSnapshot(),
                source.trafficHandedness(),
                laneIds
        );
    }

    private List<RoadLaneId> generatedLaneIds(int count, Set<UUID> generated) {
        List<RoadLaneId> ids = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            ids.add(new RoadLaneId(nextGeneratedId(generated)));
        }
        return ids;
    }

    private List<LaneConnector> rebuildStraightConnectors(
            Collection<RoadSegment> resultingSegments,
            Set<RoadNodeId> affectedNodeIds,
            List<LaneConnectorId> removedConnectorIds,
            Set<UUID> generated
    ) {
        Set<LaneConnectorId> removed = Set.copyOf(removedConnectorIds);
        Set<String> movements = new HashSet<>();
        connectors.values().stream()
                .filter(connector -> !removed.contains(connector.id()))
                .forEach(connector -> movements.add(
                        connector.fromLaneId() + "->" + connector.toLaneId()
                ));

        JunctionConnectorPlanner planner = new JunctionConnectorPlanner();
        List<LaneConnector> rebuilt = new ArrayList<>();
        for (RoadNodeId nodeId : affectedNodeIds.stream().sorted().toList()) {
            List<RoadSegment> incident = resultingSegments.stream()
                    .filter(segment -> segment.startNodeId().equals(nodeId)
                            || segment.endNodeId().equals(nodeId))
                    .sorted(Comparator.comparing(RoadSegment::id))
                    .toList();
            for (LaneConnector connector : planner.plan(
                    nodeId,
                    incident,
                    connectors.values(),
                    () -> new LaneConnectorId(nextGeneratedId(generated))
            )) {
                String movement = connector.fromLaneId() + "->"
                        + connector.toLaneId();
                if (movements.add(movement)) {
                    rebuilt.add(connector);
                }
            }
        }
        return List.copyOf(rebuilt);
    }

    private Optional<RoadNode> findNodeAt(RoadPoint point) {
        return RoadNodePortResolver.resolve(
                point,
                nodes.values(),
                segments.values()
        );
    }

    private Optional<Road> selectContinuationRoad(
            Optional<RoadNode> joinedStart,
            Optional<RoadNode> joinedEnd,
            ReferenceGeometry geometry,
            TrafficHandedness handedness
    ) {
        List<RoadId> startCandidates = joinedStart
                .map(node -> continuationRoadIds(
                        node,
                        geometry.sample(0.0).tangent(),
                        handedness
                ))
                .orElseGet(List::of);
        List<RoadId> endCandidates = joinedEnd
                .map(node -> continuationRoadIds(
                        node,
                        geometry.sample(geometry.length())
                                .tangent()
                                .scale(-1.0),
                        handedness
                ))
                .orElseGet(List::of);

        List<RoadId> candidates;
        if (joinedStart.isPresent() && joinedEnd.isPresent()) {
            candidates = startCandidates.stream()
                    .filter(endCandidates::contains)
                    .toList();
        } else if (joinedStart.isPresent()) {
            candidates = startCandidates;
        } else if (joinedEnd.isPresent()) {
            candidates = endCandidates;
        } else {
            candidates = List.of();
        }
        return candidates.size() == 1
                ? Optional.ofNullable(roads.get(candidates.get(0)))
                : Optional.empty();
    }

    private List<RoadId> continuationRoadIds(
            RoadNode node,
            RoadVector addedOutward,
            TrafficHandedness handedness
    ) {
        return incidentSegments(node.id()).stream()
                .filter(segment -> segment.trafficHandedness() == handedness)
                .filter(segment -> {
                    RoadVector existingOutward =
                            segment.startNodeId().equals(node.id())
                                    ? segment.referenceGeometry()
                                    .sample(0.0).tangent()
                                    : segment.referenceGeometry()
                                    .sample(
                                            segment.referenceGeometry().length()
                                    )
                                    .tangent()
                                    .scale(-1.0);
                    return StraightThroughConnectorPlanner.areStraightOpposite(
                            addedOutward,
                            existingOutward
                    );
                })
                .map(RoadSegment::roadId)
                .distinct()
                .sorted()
                .toList();
    }

    private List<RoadSegment> incidentSegments(RoadNodeId nodeId) {
        return segments.values().stream()
                .filter(segment -> segment.startNodeId().equals(nodeId)
                        || segment.endNodeId().equals(nodeId))
                .sorted(Comparator.comparing(RoadSegment::id))
                .toList();
    }

    private void validateSegmentReferences(RoadSegment segment) {
        Road road = roads.get(segment.roadId());
        RoadNode start = nodes.get(segment.startNodeId());
        RoadNode end = nodes.get(segment.endNodeId());
        if (road == null) {
            throw new IllegalArgumentException("segment references unknown road " + segment.roadId());
        }
        if (start == null || end == null) {
            throw new IllegalArgumentException("segment references an unknown boundary node");
        }
        if (road.trafficHandedness() != segment.trafficHandedness()) {
            throw new IllegalArgumentException("segment handedness disagrees with its road");
        }
    }

    private void validateConnectorReferences(LaneConnector connector) {
        if (!lanes.containsKey(connector.fromLaneId()) || !lanes.containsKey(connector.toLaneId())) {
            throw new IllegalArgumentException("connector references an unknown lane");
        }
    }

    private void validateCompleteTopology() {
        for (Road road : roads.values()) {
            boolean hasSegment = segments.values().stream()
                    .anyMatch(segment -> segment.roadId().equals(road.id()));
            if (!hasSegment) {
                throw new IllegalArgumentException("road has no segments: " + road.id());
            }
        }
        for (RoadNode node : nodes.values()) {
            List<RoadSegment> incident = segments.values().stream()
                    .filter(segment -> segment.startNodeId().equals(node.id())
                            || segment.endNodeId().equals(node.id()))
                    .toList();
            if (incident.isEmpty()) {
                throw new IllegalArgumentException("node has no incident segments: " + node.id());
            }
            if (!RoadNodePortResolver.isValidTopology(node, incident)) {
                throw new IllegalArgumentException(
                        "segment geometry disagrees with its boundary node "
                                + "end caps: "
                                + node.id()
                );
            }
        }
    }

    private void validateGloballyUniqueUuids() {
        Set<UUID> ids = new HashSet<>();
        roads.keySet().forEach(id -> requireUniqueUuid(ids, id.value()));
        nodes.keySet().forEach(id -> requireUniqueUuid(ids, id.value()));
        segments.keySet().forEach(id -> requireUniqueUuid(ids, id.value()));
        lanes.keySet().forEach(id -> requireUniqueUuid(ids, id.value()));
        connectors.keySet().forEach(id -> requireUniqueUuid(ids, id.value()));
    }

    private void requireWithinLimits() {
        if (roads.size() > limits.maximumRoadCount()
                || nodes.size() > limits.maximumNodeCount()
                || segments.size() > limits.maximumSegmentCount()
                || lanes.size() > limits.maximumLaneCount()
                || connectors.size() > limits.maximumConnectorCount()) {
            throw new IllegalArgumentException("restored road graph exceeds configured limits");
        }
    }

    private static void requireUniqueUuid(Set<UUID> ids, UUID id) {
        if (!ids.add(id)) {
            throw new IllegalArgumentException("UUID is reused by multiple graph records: " + id);
        }
    }

    private static void requireAbsent(Object previous, String type, Object id) {
        if (previous != null) {
            throw new IllegalArgumentException("duplicate " + type + " ID " + id);
        }
    }

    private static <K, V> Map<K, V> sortedCopy(
            Map<K, V> values,
            java.util.function.Function<K, UUID> uuid
    ) {
        List<Map.Entry<K, V>> entries = new ArrayList<>(values.entrySet());
        entries.sort(Comparator.comparing(entry -> uuid.apply(entry.getKey()), UUID_ORDER));
        Map<K, V> ordered = new LinkedHashMap<>(entries.size());
        for (Map.Entry<K, V> entry : entries) {
            ordered.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(ordered);
    }
}
