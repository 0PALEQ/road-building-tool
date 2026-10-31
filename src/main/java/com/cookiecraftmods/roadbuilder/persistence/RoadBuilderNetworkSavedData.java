package com.cookiecraftmods.roadbuilder.persistence;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.CubicBezierGeometry;
import com.cookiecraftmods.roadbuilder.geometry.FreehandGeometry;
import com.cookiecraftmods.roadbuilder.geometry.MultiCurveGeometry;
import com.cookiecraftmods.roadbuilder.geometry.QuadraticBezierGeometry;
import com.cookiecraftmods.roadbuilder.geometry.ReferenceGeometry;
import com.cookiecraftmods.roadbuilder.geometry.StraightGeometry;
import com.cookiecraftmods.roadbuilder.network.LaneConnector;
import com.cookiecraftmods.roadbuilder.network.LaneConnectorId;
import com.cookiecraftmods.roadbuilder.network.LaneConnectorMovement;
import com.cookiecraftmods.roadbuilder.network.LaneMovementType;
import com.cookiecraftmods.roadbuilder.network.ApproachEnd;
import com.cookiecraftmods.roadbuilder.network.ApproachKey;
import com.cookiecraftmods.roadbuilder.network.JunctionApproachOverride;
import com.cookiecraftmods.roadbuilder.network.JunctionCrossingPolicy;
import com.cookiecraftmods.roadbuilder.network.JunctionDefinition;
import com.cookiecraftmods.roadbuilder.network.JunctionPriorityPolicy;
import com.cookiecraftmods.roadbuilder.network.JunctionStrategy;
import com.cookiecraftmods.roadbuilder.network.Road;
import com.cookiecraftmods.roadbuilder.network.RoadGraph;
import com.cookiecraftmods.roadbuilder.network.RoadGraphLimits;
import com.cookiecraftmods.roadbuilder.network.RoadGraphSnapshot;
import com.cookiecraftmods.roadbuilder.network.RoadId;
import com.cookiecraftmods.roadbuilder.network.RoadLane;
import com.cookiecraftmods.roadbuilder.network.RoadLaneId;
import com.cookiecraftmods.roadbuilder.network.RoadNode;
import com.cookiecraftmods.roadbuilder.network.RoadNodeId;
import com.cookiecraftmods.roadbuilder.network.RoadNodePortResolver;
import com.cookiecraftmods.roadbuilder.network.RoadPresetSnapshot;
import com.cookiecraftmods.roadbuilder.network.RoadSegment;
import com.cookiecraftmods.roadbuilder.network.RoadSegmentId;
import com.cookiecraftmods.roadbuilder.preset.LaneComponent;
import com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection;
import com.cookiecraftmods.roadbuilder.preset.LaneType;
import com.cookiecraftmods.roadbuilder.preset.RoadPreset;
import com.cookiecraftmods.roadbuilder.preset.IntersectionControl;
import com.cookiecraftmods.roadbuilder.preset.RoadClass;
import com.cookiecraftmods.roadbuilder.preset.RoadClassification;
import com.cookiecraftmods.roadbuilder.preset.RoadIntersectionStyle;
import com.cookiecraftmods.roadbuilder.preset.RoadSurfaceBand;
import com.cookiecraftmods.roadbuilder.preset.RoadMarkingPattern;
import com.cookiecraftmods.roadbuilder.preset.RoadSurfaceRole;
import com.cookiecraftmods.roadbuilder.preset.TrafficHandedness;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class RoadBuilderNetworkSavedData extends SavedData {
    public static final int CURRENT_SCHEMA_VERSION = 4;
    public static final String DATA_NAME = "roadbuilder_road_graph";

    private static final int MAXIMUM_RESOURCE_ID_CHARACTERS = 128;
    private static final String SCHEMA_VERSION_KEY = "schema_version";
    private static final String REVISION_KEY = "revision";
    private static final String ROADS_KEY = "roads";
    private static final String NODES_KEY = "nodes";
    private static final String SEGMENTS_KEY = "segments";
    private static final String CONNECTORS_KEY = "connectors";
    private static final String JUNCTIONS_KEY = "junctions";
    private static final String ID_KEY = "id";
    private static final String HANDEDNESS_KEY = "handedness";
    private static final String X_KEY = "x";
    private static final String Y_KEY = "y";
    private static final String Z_KEY = "z";
    private static final String ROAD_KEY = "road";
    private static final String START_NODE_KEY = "start_node";
    private static final String END_NODE_KEY = "end_node";
    private static final String GEOMETRY_KEY = "geometry";
    private static final String PRESET_KEY = "preset";
    private static final String LANE_IDS_KEY = "lane_ids";
    private static final String TYPE_KEY = "type";
    private static final String START_X_KEY = "start_x";
    private static final String START_Y_KEY = "start_y";
    private static final String START_Z_KEY = "start_z";
    private static final String END_X_KEY = "end_x";
    private static final String END_Y_KEY = "end_y";
    private static final String END_Z_KEY = "end_z";
    private static final String CONTROL_POINTS_KEY = "control_points";
    private static final String SOURCE_ID_KEY = "source_id";
    private static final String SOURCE_REVISION_KEY = "source_revision";
    private static final String SURFACE_WIDTH_KEY = "surface_width";
    private static final String SURFACE_BLOCK_KEY = "surface_block";
    private static final String SURFACE_BANDS_KEY = "surface_bands";
    private static final String BLOCK_KEY = "block";
    private static final String BLOCK_SET_KEY = "block_set";
    private static final String ROLE_KEY = "role";
    private static final String LANES_KEY = "lanes";
    private static final String WIDTH_KEY = "width";
    private static final String OFFSET_KEY = "offset";
    private static final String DIRECTION_KEY = "direction";
    private static final String FROM_LANE_KEY = "from_lane";
    private static final String TO_LANE_KEY = "to_lane";
    private static final String MOVEMENT_TYPE_KEY = "movement_type";
    private static final String MOVEMENT_ALLOWED_KEY = "movement_allowed";
    private static final String MOVEMENT_MANUAL_KEY = "movement_manual";
    private static final String MOVEMENT_PRIORITY_KEY = "movement_priority";
    private static final String CLASSIFICATION_KEY = "classification";
    private static final String INTERSECTION_STYLE_KEY = "intersection_style";
    private static final String ROAD_CLASS_KEY = "road_class";
    private static final String PRIORITY_RANK_KEY = "priority_rank";
    private static final String NOMINAL_SPEED_KEY = "nominal_speed";
    private static final String PAVEMENT_BLOCK_KEY = "pavement_block";
    private static final String MARKING_BLOCK_KEY = "marking_block";
    private static final String CROSSWALK_BLOCK_KEY = "crosswalk_block";
    private static final String STOP_LINE_BLOCK_KEY = "stop_line_block";
    private static final String DEFAULT_CROSSWALKS_KEY = "default_crosswalks";
    private static final String DEFAULT_CONTROL_KEY = "default_control";
    private static final String NODE_KEY = "node";
    private static final String STRATEGY_KEY = "strategy";
    private static final String PRIORITY_POLICY_KEY = "priority_policy";
    private static final String PRIMARY_APPROACHES_KEY = "primary_approaches";
    private static final String CROSSING_POLICY_KEY = "crossing_policy";
    private static final String APPROACH_OVERRIDES_KEY = "approach_overrides";
    private static final String DEFINITION_REVISION_KEY = "definition_revision";
    private static final String SEGMENT_KEY = "segment";
    private static final String END_KEY = "end";
    private static final String CONTROL_KEY = "control";
    private static final String CROSSWALK_KEY = "crosswalk";

    private static final Set<String> LEGACY_ROOT_KEYS = Set.of(
            SCHEMA_VERSION_KEY,
            REVISION_KEY,
            ROADS_KEY,
            NODES_KEY,
            SEGMENTS_KEY,
            CONNECTORS_KEY
    );
    private static final Set<String> ROOT_KEYS = Set.of(
            SCHEMA_VERSION_KEY,
            REVISION_KEY,
            ROADS_KEY,
            NODES_KEY,
            SEGMENTS_KEY,
            CONNECTORS_KEY,
            JUNCTIONS_KEY
    );
    private static final Set<String> ROAD_KEYS = Set.of(ID_KEY, HANDEDNESS_KEY);
    private static final Set<String> NODE_KEYS = Set.of(ID_KEY, X_KEY, Y_KEY, Z_KEY);
    private static final Set<String> SEGMENT_KEYS = Set.of(
            ID_KEY,
            ROAD_KEY,
            START_NODE_KEY,
            END_NODE_KEY,
            GEOMETRY_KEY,
            PRESET_KEY,
            LANE_IDS_KEY
    );
    private static final Set<String> GEOMETRY_KEYS = Set.of(
            TYPE_KEY,
            START_X_KEY,
            START_Y_KEY,
            START_Z_KEY,
            END_X_KEY,
            END_Y_KEY,
            END_Z_KEY
    );
    private static final Set<String> CURVE_GEOMETRY_KEYS = Set.of(
            TYPE_KEY,
            CONTROL_POINTS_KEY
    );
    private static final Set<String> LEGACY_PRESET_KEYS = Set.of(
            SOURCE_ID_KEY,
            SOURCE_REVISION_KEY,
            SURFACE_WIDTH_KEY,
            SURFACE_BLOCK_KEY,
            LANES_KEY
    );
    private static final Set<String> PRESET_KEYS = Set.of(
            SOURCE_ID_KEY,
            SOURCE_REVISION_KEY,
            SURFACE_WIDTH_KEY,
            SURFACE_BLOCK_KEY,
            SURFACE_BANDS_KEY,
            LANES_KEY
    );
    private static final Set<String> PRESET_V2_KEYS = Set.of(
            SOURCE_ID_KEY,
            SOURCE_REVISION_KEY,
            SURFACE_WIDTH_KEY,
            SURFACE_BLOCK_KEY,
            SURFACE_BANDS_KEY,
            LANES_KEY,
            CLASSIFICATION_KEY,
            INTERSECTION_STYLE_KEY
    );
    private static final Set<String> CLASSIFICATION_KEYS = Set.of(
            ROAD_CLASS_KEY,
            PRIORITY_RANK_KEY,
            NOMINAL_SPEED_KEY
    );
    private static final Set<String> INTERSECTION_STYLE_KEYS = Set.of(
            PAVEMENT_BLOCK_KEY,
            MARKING_BLOCK_KEY,
            CROSSWALK_BLOCK_KEY,
            STOP_LINE_BLOCK_KEY,
            DEFAULT_CROSSWALKS_KEY,
            DEFAULT_CONTROL_KEY
    );
    private static final Set<String> LEGACY_SURFACE_BAND_KEYS =
            Set.of(ROLE_KEY, WIDTH_KEY, BLOCK_KEY);
    private static final Set<String> SURFACE_BAND_KEYS =
            Set.of(ROLE_KEY, WIDTH_KEY, BLOCK_KEY, BLOCK_SET_KEY);
    private static final Set<String> LANE_COMPONENT_KEYS = Set.of(
            TYPE_KEY,
            WIDTH_KEY,
            OFFSET_KEY,
            DIRECTION_KEY
    );
    private static final Set<String> ID_ONLY_KEYS = Set.of(ID_KEY);
    private static final Set<String> LEGACY_CONNECTOR_KEYS = Set.of(
            ID_KEY,
            FROM_LANE_KEY,
            TO_LANE_KEY
    );
    private static final Set<String> CONNECTOR_KEYS = Set.of(
            ID_KEY,
            FROM_LANE_KEY,
            TO_LANE_KEY,
            MOVEMENT_TYPE_KEY,
            MOVEMENT_ALLOWED_KEY,
            MOVEMENT_MANUAL_KEY,
            MOVEMENT_PRIORITY_KEY
    );
    private static final Set<String> JUNCTION_KEYS = Set.of(
            NODE_KEY,
            STRATEGY_KEY,
            PRIORITY_POLICY_KEY,
            PRIMARY_APPROACHES_KEY,
            CROSSING_POLICY_KEY,
            APPROACH_OVERRIDES_KEY,
            DEFINITION_REVISION_KEY
    );
    private static final Set<String> APPROACH_KEYS = Set.of(
            SEGMENT_KEY,
            END_KEY
    );
    private static final Set<String> APPROACH_OVERRIDE_KEYS = Set.of(
            SEGMENT_KEY,
            END_KEY,
            CONTROL_KEY,
            CROSSWALK_KEY
    );
    private static final RoadGraphLimits DEFAULT_LIMITS = RoadGraphLimits.defaults();

    private final ResourceKey<Level> dimension;
    private final RoadGraph compatibleGraph;
    private final CompoundTag incompatibleRawTag;
    private final String incompatibilityReason;

    private RoadBuilderNetworkSavedData(ResourceKey<Level> dimension) {
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.compatibleGraph = new RoadGraph(dimension, this::setDirty);
        this.incompatibleRawTag = null;
        this.incompatibilityReason = null;
    }

    private RoadBuilderNetworkSavedData(
            ResourceKey<Level> dimension,
            DecodedData decoded,
            RoadGraphLimits limits,
            boolean recovered
    ) {
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.compatibleGraph = RoadGraph.restore(
                dimension,
                decoded.revision(),
                decoded.roads(),
                decoded.nodes(),
                decoded.segments(),
                decoded.connectors(),
                decoded.junctionDefinitions(),
                limits,
                this::setDirty
        );
        if (decoded.schemaVersion() < CURRENT_SCHEMA_VERSION) {
            this.compatibleGraph.rebuildAutomaticConnectors();
        }
        if (recovered) {
            this.setDirty();
        }
        this.incompatibleRawTag = null;
        this.incompatibilityReason = null;
    }

    private RoadBuilderNetworkSavedData(
            ResourceKey<Level> dimension,
            CompoundTag incompatibleRawTag,
            String incompatibilityReason
    ) {
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.compatibleGraph = null;
        this.incompatibleRawTag = Objects.requireNonNull(
                incompatibleRawTag,
                "incompatibleRawTag"
        ).copy();
        this.incompatibilityReason = Objects.requireNonNull(
                incompatibilityReason,
                "incompatibilityReason"
        );
    }

    public static RoadBuilderNetworkSavedData get(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        ResourceKey<Level> dimension = level.dimension();
        return level.getDataStorage().computeIfAbsent(
                tag -> load(dimension, tag),
                () -> create(dimension),
                DATA_NAME
        );
    }

    public static RoadBuilderNetworkSavedData create(ResourceKey<Level> dimension) {
        return new RoadBuilderNetworkSavedData(dimension);
    }

    public static RoadBuilderNetworkSavedData load(
            ResourceKey<Level> dimension,
            CompoundTag tag
    ) {
        return load(dimension, tag, DEFAULT_LIMITS);
    }

    static RoadBuilderNetworkSavedData load(
            ResourceKey<Level> dimension,
            CompoundTag tag,
            RoadGraphLimits limits
    ) {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(limits, "limits");
        CompoundTag retained = tag.copy();
        try {
            DecodedData decoded = decode(tag, limits);
            RecoveredData recovered = recoverLostAnchors(decoded);
            return new RoadBuilderNetworkSavedData(
                    dimension,
                    recovered.decodedData(),
                    limits,
                    recovered.recovered()
            );
        } catch (RuntimeException exception) {
            String message = exception.getMessage();
            String reason = message == null || message.isBlank()
                    ? exception.getClass().getSimpleName()
                    : message;
            return new RoadBuilderNetworkSavedData(dimension, retained, reason);
        }
    }

    /**
     * Narrowly repairs the legacy bounded lost-anchor case: a node whose
     * canonical anchor segment was removed, leaving its position outside the
     * end caps of the surviving incident segments but still within the
     * original bounded radius. Only existing incident endpoints within the
     * original node's radius and vertical limits are considered, so
     * malformed, distant, or disconnected graphs still fail closed and keep
     * their raw NBT. Segments, curves, lanes, and roads are never moved.
     */
    private static RecoveredData recoverLostAnchors(DecodedData decoded) {
        Map<RoadNodeId, RoadNode> nodes = new LinkedHashMap<>();
        for (RoadNode node : decoded.nodes()) {
            nodes.put(node.id(), node);
        }
        Map<RoadSegmentId, RoadSegment> segments = new LinkedHashMap<>();
        for (RoadSegment segment : decoded.segments()) {
            segments.put(segment.id(), segment);
        }
        Map<LaneConnectorId, LaneConnector> connectors = new LinkedHashMap<>();
        decoded.connectors().forEach(connector -> connectors.put(connector.id(), connector));
        Map<RoadNodeId, JunctionDefinition> definitions = new LinkedHashMap<>();
        decoded.junctionDefinitions().forEach(definition ->
                definitions.put(definition.nodeId(), definition));
        boolean recovered = com.cookiecraftmods.roadbuilder.network.RoadBoundaryRecovery.recover(
                nodes.keySet(), nodes, segments, connectors, definitions
        );
        if (!recovered) {
            return new RecoveredData(decoded, false);
        }
        return new RecoveredData(new DecodedData(
                decoded.schemaVersion(),
                decoded.revision(),
                decoded.roads(),
                List.copyOf(nodes.values()),
                List.copyOf(segments.values()),
                List.copyOf(connectors.values()),
                List.copyOf(definitions.values())
        ), true);
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public boolean isCompatible() {
        return compatibleGraph != null;
    }

    public RoadGraph graph() {
        if (compatibleGraph == null) {
            throw new IllegalStateException(
                    "Road Builder graph data is incompatible: " + incompatibilityReason
            );
        }
        return compatibleGraph;
    }

    public Optional<String> incompatibilityReason() {
        return Optional.ofNullable(incompatibilityReason);
    }

    public Optional<CompoundTag> incompatibleRawTag() {
        return Optional.ofNullable(incompatibleRawTag).map(CompoundTag::copy);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        Objects.requireNonNull(tag, "tag");
        if (compatibleGraph == null) {
            return incompatibleRawTag.copy();
        }
        for (String key : List.copyOf(tag.getAllKeys())) {
            tag.remove(key);
        }

        RoadGraphSnapshot snapshot = compatibleGraph.snapshot();
        tag.putInt(SCHEMA_VERSION_KEY, CURRENT_SCHEMA_VERSION);
        tag.putLong(REVISION_KEY, snapshot.revision());

        ListTag roads = new ListTag();
        for (Road road : snapshot.roads().values()) {
            CompoundTag roadTag = new CompoundTag();
            roadTag.putUUID(ID_KEY, road.id().value());
            roadTag.putString(HANDEDNESS_KEY, road.trafficHandedness().serializedName());
            roads.add(roadTag);
        }
        tag.put(ROADS_KEY, roads);

        ListTag nodes = new ListTag();
        for (RoadNode node : snapshot.nodes().values()) {
            CompoundTag nodeTag = new CompoundTag();
            nodeTag.putUUID(ID_KEY, node.id().value());
            nodeTag.putDouble(X_KEY, node.position().x());
            nodeTag.putDouble(Y_KEY, node.position().y());
            nodeTag.putDouble(Z_KEY, node.position().z());
            nodes.add(nodeTag);
        }
        tag.put(NODES_KEY, nodes);

        ListTag segments = new ListTag();
        for (RoadSegment segment : snapshot.segments().values()) {
            CompoundTag segmentTag = new CompoundTag();
            segmentTag.putUUID(ID_KEY, segment.id().value());
            segmentTag.putUUID(ROAD_KEY, segment.roadId().value());
            segmentTag.putUUID(START_NODE_KEY, segment.startNodeId().value());
            segmentTag.putUUID(END_NODE_KEY, segment.endNodeId().value());
            segmentTag.put(GEOMETRY_KEY, writeGeometry(segment.referenceGeometry()));
            segmentTag.put(PRESET_KEY, writePreset(segment));

            ListTag laneIds = new ListTag();
            for (RoadLane lane : segment.lanes()) {
                CompoundTag laneIdTag = new CompoundTag();
                laneIdTag.putUUID(ID_KEY, lane.id().value());
                laneIds.add(laneIdTag);
            }
            segmentTag.put(LANE_IDS_KEY, laneIds);
            segments.add(segmentTag);
        }
        tag.put(SEGMENTS_KEY, segments);

        ListTag connectors = new ListTag();
        for (LaneConnector connector : snapshot.connectors().values()) {
            CompoundTag connectorTag = new CompoundTag();
            connectorTag.putUUID(ID_KEY, connector.id().value());
            connectorTag.putUUID(FROM_LANE_KEY, connector.fromLaneId().value());
            connectorTag.putUUID(TO_LANE_KEY, connector.toLaneId().value());
            connectorTag.putString(
                    MOVEMENT_TYPE_KEY,
                    connector.movement().type().name()
            );
            connectorTag.putBoolean(
                    MOVEMENT_ALLOWED_KEY,
                    connector.movement().allowed()
            );
            connectorTag.putBoolean(
                    MOVEMENT_MANUAL_KEY,
                    connector.movement().manual()
            );
            connectorTag.putInt(
                    MOVEMENT_PRIORITY_KEY,
                    connector.movement().priority()
            );
            connectors.add(connectorTag);
        }
        tag.put(CONNECTORS_KEY, connectors);

        ListTag junctions = new ListTag();
        for (JunctionDefinition definition
                : snapshot.junctionDefinitions().values()) {
            junctions.add(writeJunctionDefinition(definition));
        }
        tag.put(JUNCTIONS_KEY, junctions);
        return tag;
    }

    private static CompoundTag writeGeometry(ReferenceGeometry geometry) {
        CompoundTag tag = new CompoundTag();
        if (geometry instanceof StraightGeometry straight) {
            tag.putString(TYPE_KEY, "straight");
            tag.putDouble(START_X_KEY, straight.start().x());
            tag.putDouble(START_Y_KEY, straight.start().y());
            tag.putDouble(START_Z_KEY, straight.start().z());
            tag.putDouble(END_X_KEY, straight.end().x());
            tag.putDouble(END_Y_KEY, straight.end().y());
            tag.putDouble(END_Z_KEY, straight.end().z());
            return tag;
        }

        List<RoadPoint> points;
        if (geometry instanceof QuadraticBezierGeometry quadratic) {
            tag.putString(TYPE_KEY, "quadratic");
            points = quadratic.controlPoints();
        } else if (geometry instanceof CubicBezierGeometry cubic) {
            tag.putString(TYPE_KEY, "cubic");
            points = cubic.controlPoints();
        } else if (geometry instanceof MultiCurveGeometry multi) {
            tag.putString(TYPE_KEY, multi.hasExplicitSpans() ? "joined_cubics" : "multi");
            points = multi.hasExplicitSpans() ? multi.controlPoints() : multi.anchors();
        } else if (geometry instanceof FreehandGeometry freehand) {
            tag.putString(TYPE_KEY, freehand.anchors().size() == 2
                    ? "freehand_line"
                    : "multi");
            points = freehand.anchors();
        } else {
            throw new IllegalArgumentException(
                    "unsupported reference geometry " + geometry.getClass()
            );
        }
        ListTag controls = new ListTag();
        for (RoadPoint point : points) {
            CompoundTag pointTag = new CompoundTag();
            pointTag.putDouble(X_KEY, point.x());
            pointTag.putDouble(Y_KEY, point.y());
            pointTag.putDouble(Z_KEY, point.z());
            controls.add(pointTag);
        }
        tag.put(CONTROL_POINTS_KEY, controls);
        return tag;
    }

    private static CompoundTag writePreset(RoadSegment segment) {
        RoadPresetSnapshot snapshot = segment.presetSnapshot();
        CompoundTag tag = new CompoundTag();
        tag.putString(SOURCE_ID_KEY, segment.presetId().toString());
        tag.putInt(SOURCE_REVISION_KEY, segment.presetRevision());
        tag.putInt(SURFACE_WIDTH_KEY, snapshot.surfaceBlockWidth());
        tag.putString(SURFACE_BLOCK_KEY, snapshot.surfaceBlock().toString());

        ListTag surfaceBands = new ListTag();
        for (RoadSurfaceBand band : snapshot.surfaceBands()) {
            CompoundTag bandTag = new CompoundTag();
            bandTag.putString(ROLE_KEY, serializedBandRole(band));
            bandTag.putInt(WIDTH_KEY, band.width());
            bandTag.putString(BLOCK_KEY, band.block().toString());
            ListTag blockSet = new ListTag();
            band.blockSet().forEach(block -> blockSet.add(
                    StringTag.valueOf(block.toString())
            ));
            bandTag.put(BLOCK_SET_KEY, blockSet);
            surfaceBands.add(bandTag);
        }
        tag.put(SURFACE_BANDS_KEY, surfaceBands);

        CompoundTag classification = new CompoundTag();
        classification.putString(
                ROAD_CLASS_KEY,
                snapshot.classification().roadClass().serializedName()
        );
        classification.putInt(
                PRIORITY_RANK_KEY,
                snapshot.classification().priorityRank()
        );
        classification.putDouble(
                NOMINAL_SPEED_KEY,
                snapshot.classification().nominalSpeed()
        );
        tag.put(CLASSIFICATION_KEY, classification);

        RoadIntersectionStyle style = snapshot.intersectionStyle();
        CompoundTag intersectionStyle = new CompoundTag();
        intersectionStyle.putString(
                PAVEMENT_BLOCK_KEY,
                style.pavementBlock().toString()
        );
        intersectionStyle.putString(
                MARKING_BLOCK_KEY,
                style.markingBlock().map(Object::toString).orElse("")
        );
        intersectionStyle.putString(
                CROSSWALK_BLOCK_KEY,
                style.crosswalkBlock().map(Object::toString).orElse("")
        );
        intersectionStyle.putString(
                STOP_LINE_BLOCK_KEY,
                style.stopLineBlock().map(Object::toString).orElse("")
        );
        intersectionStyle.putBoolean(
                DEFAULT_CROSSWALKS_KEY,
                style.defaultCrosswalks()
        );
        intersectionStyle.putString(
                DEFAULT_CONTROL_KEY,
                style.defaultControl().serializedName()
        );
        tag.put(INTERSECTION_STYLE_KEY, intersectionStyle);

        ListTag lanes = new ListTag();
        for (LaneComponent lane : snapshot.lanes()) {
            CompoundTag laneTag = new CompoundTag();
            laneTag.putString(TYPE_KEY, lane.type().serializedName());
            laneTag.putDouble(WIDTH_KEY, lane.width());
            laneTag.putDouble(OFFSET_KEY, lane.lateralOffset());
            laneTag.putString(DIRECTION_KEY, lane.direction().serializedName());
            lanes.add(laneTag);
        }
        tag.put(LANES_KEY, lanes);
        return tag;
    }

    private static CompoundTag writeJunctionDefinition(
            JunctionDefinition definition
    ) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(NODE_KEY, definition.nodeId().value());
        tag.putString(STRATEGY_KEY, definition.strategy().name());
        tag.putString(
                PRIORITY_POLICY_KEY,
                definition.priorityPolicy().name()
        );
        ListTag primary = new ListTag();
        definition.primaryApproachKeys().forEach(key ->
                primary.add(writeApproach(key, null)));
        tag.put(PRIMARY_APPROACHES_KEY, primary);
        tag.putString(
                CROSSING_POLICY_KEY,
                definition.crossingPolicy().name()
        );
        ListTag overrides = new ListTag();
        definition.approachOverrides().forEach((key, override) ->
                overrides.add(writeApproach(key, override)));
        tag.put(APPROACH_OVERRIDES_KEY, overrides);
        tag.putLong(DEFINITION_REVISION_KEY, definition.revision());
        return tag;
    }

    private static CompoundTag writeApproach(
            ApproachKey key,
            JunctionApproachOverride override
    ) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(SEGMENT_KEY, key.segmentId().value());
        tag.putString(END_KEY, key.end().name());
        if (override != null) {
            tag.putString(
                    CONTROL_KEY,
                    override.control().serializedName()
            );
            tag.putBoolean(CROSSWALK_KEY, override.crosswalk());
        }
        return tag;
    }

    private static DecodedData decode(CompoundTag root, RoadGraphLimits limits) {
        int schemaVersion = readInt(root.get(SCHEMA_VERSION_KEY), "schema_version");
        if (schemaVersion < 1 || schemaVersion > CURRENT_SCHEMA_VERSION) {
            throw malformed(
                    "unsupported road graph schema version " + schemaVersion
                            + "; expected 1.." + CURRENT_SCHEMA_VERSION
            );
        }
        requireExactKeys(
                root,
                schemaVersion == 1 ? LEGACY_ROOT_KEYS : ROOT_KEYS,
                "root"
        );
        long revision = readLong(root.get(REVISION_KEY), "revision");
        if (revision < 0) {
            throw malformed("revision must be non-negative");
        }

        ListTag roadTags = readCompoundList(root.get(ROADS_KEY), "roads");
        ListTag nodeTags = readCompoundList(root.get(NODES_KEY), "nodes");
        ListTag segmentTags = readCompoundList(root.get(SEGMENTS_KEY), "segments");
        ListTag connectorTags = readCompoundList(root.get(CONNECTORS_KEY), "connectors");
        ListTag junctionTags = schemaVersion == 1
                ? new ListTag()
                : readCompoundList(root.get(JUNCTIONS_KEY), "junctions");
        requireCount(roadTags.size(), limits.maximumRoadCount(), "road");
        requireCount(nodeTags.size(), limits.maximumNodeCount(), "node");
        requireCount(segmentTags.size(), limits.maximumSegmentCount(), "segment");
        requireCount(connectorTags.size(), limits.maximumConnectorCount(), "connector");
        requireCount(junctionTags.size(), limits.maximumNodeCount(), "junction");

        Map<RoadId, Road> roads = new LinkedHashMap<>();
        for (int index = 0; index < roadTags.size(); index++) {
            CompoundTag roadTag = roadTags.getCompound(index);
            String path = "roads[" + index + "]";
            requireExactKeys(roadTag, ROAD_KEYS, path);
            RoadId id = new RoadId(readUuid(roadTag.get(ID_KEY), path + ".id"));
            TrafficHandedness handedness = TrafficHandedness
                    .fromSerializedName(readString(
                            roadTag.get(HANDEDNESS_KEY),
                            path + ".handedness"
                    ))
                    .orElseThrow(() -> malformed(path + ".handedness is unknown"));
            requireAbsent(roads.put(id, new Road(id, handedness)), "road", id);
        }

        Map<RoadNodeId, RoadNode> nodes = new LinkedHashMap<>();
        for (int index = 0; index < nodeTags.size(); index++) {
            CompoundTag nodeTag = nodeTags.getCompound(index);
            String path = "nodes[" + index + "]";
            requireExactKeys(nodeTag, NODE_KEYS, path);
            RoadNodeId id = new RoadNodeId(readUuid(nodeTag.get(ID_KEY), path + ".id"));
            RoadPoint position = new RoadPoint(
                    readDouble(nodeTag.get(X_KEY), path + ".x"),
                    readDouble(nodeTag.get(Y_KEY), path + ".y"),
                    readDouble(nodeTag.get(Z_KEY), path + ".z")
            );
            requireAbsent(nodes.put(id, new RoadNode(id, position)), "node", id);
        }

        Map<RoadSegmentId, RoadSegment> segments = new LinkedHashMap<>();
        long totalLaneCount = 0;
        for (int index = 0; index < segmentTags.size(); index++) {
            CompoundTag segmentTag = segmentTags.getCompound(index);
            String path = "segments[" + index + "]";
            requireExactKeys(segmentTag, SEGMENT_KEYS, path);

            RoadSegmentId id = new RoadSegmentId(readUuid(segmentTag.get(ID_KEY), path + ".id"));
            RoadId roadId = new RoadId(readUuid(segmentTag.get(ROAD_KEY), path + ".road"));
            Road road = roads.get(roadId);
            if (road == null) {
                throw malformed(path + " references unknown road " + roadId);
            }
            RoadNodeId startNodeId = new RoadNodeId(readUuid(
                    segmentTag.get(START_NODE_KEY),
                    path + ".start_node"
            ));
            RoadNodeId endNodeId = new RoadNodeId(readUuid(
                    segmentTag.get(END_NODE_KEY),
                    path + ".end_node"
            ));
            ReferenceGeometry geometry = readGeometry(
                    segmentTag.get(GEOMETRY_KEY),
                    path + ".geometry"
            );
            int remainingLaneCount = limits.maximumLaneCount() - (int) totalLaneCount;
            DecodedPreset preset = readPreset(
                    segmentTag.get(PRESET_KEY),
                    path + ".preset",
                    remainingLaneCount,
                    schemaVersion
            );
            List<RoadLaneId> laneIds = readLaneIds(
                    segmentTag.get(LANE_IDS_KEY),
                    path + ".lane_ids",
                    remainingLaneCount
            );
            totalLaneCount += laneIds.size();
            if (totalLaneCount > limits.maximumLaneCount()) {
                throw malformed("lane count exceeds " + limits.maximumLaneCount());
            }

            RoadSegment segment = RoadSegment.path(
                    id,
                    roadId,
                    startNodeId,
                    endNodeId,
                    geometry,
                    preset.sourceId(),
                    preset.sourceRevision(),
                    preset.snapshot(),
                    road.trafficHandedness(),
                    laneIds
            );
            requireAbsent(segments.put(id, segment), "segment", id);
        }

        Map<LaneConnectorId, LaneConnector> connectors = new LinkedHashMap<>();
        for (int index = 0; index < connectorTags.size(); index++) {
            CompoundTag connectorTag = connectorTags.getCompound(index);
            String path = "connectors[" + index + "]";
            boolean legacyConnector =
                    connectorTag.getAllKeys().equals(LEGACY_CONNECTOR_KEYS);
            if (!legacyConnector) {
                requireExactKeys(connectorTag, CONNECTOR_KEYS, path);
            }
            LaneConnectorId id = new LaneConnectorId(readUuid(
                    connectorTag.get(ID_KEY),
                    path + ".id"
            ));
            LaneConnector connector = new LaneConnector(
                    id,
                    new RoadLaneId(readUuid(
                            connectorTag.get(FROM_LANE_KEY),
                            path + ".from_lane"
                    )),
                    new RoadLaneId(readUuid(
                            connectorTag.get(TO_LANE_KEY),
                            path + ".to_lane"
                    )),
                    legacyConnector
                            ? LaneConnectorMovement.AUTOMATIC
                            : new LaneConnectorMovement(
                                    readMovementType(
                                            connectorTag,
                                            path
                                    ),
                                    connectorTag.getBoolean(
                                            MOVEMENT_ALLOWED_KEY
                                    ),
                                    connectorTag.getBoolean(
                                            MOVEMENT_MANUAL_KEY
                                    ),
                                    connectorTag.getInt(
                                            MOVEMENT_PRIORITY_KEY
                                    )
                            )
            );
            requireAbsent(connectors.put(id, connector), "connector", id);
        }

        List<JunctionDefinition> junctionDefinitions =
                new ArrayList<>(junctionTags.size());
        Set<RoadNodeId> junctionNodeIds = new HashSet<>();
        for (int index = 0; index < junctionTags.size(); index++) {
            JunctionDefinition definition = readJunctionDefinition(
                    junctionTags.getCompound(index),
                    "junctions[" + index + "]"
            );
            if (!junctionNodeIds.add(definition.nodeId())) {
                throw malformed(
                        "duplicate junction definition for "
                                + definition.nodeId()
                );
            }
            junctionDefinitions.add(definition);
        }

        return new DecodedData(
                schemaVersion,
                revision,
                List.copyOf(roads.values()),
                List.copyOf(nodes.values()),
                List.copyOf(segments.values()),
                List.copyOf(connectors.values()),
                List.copyOf(junctionDefinitions)
        );
    }

    private static ReferenceGeometry readGeometry(Tag rawTag, String path) {
        if (!(rawTag instanceof CompoundTag tag)) {
            throw malformed(path + " must be a compound");
        }
        String type = readString(tag.get(TYPE_KEY), path + ".type");
        if (type.equals("straight")) {
            requireExactKeys(tag, GEOMETRY_KEYS, path);
            return new StraightGeometry(
                    new RoadPoint(
                            readDouble(tag.get(START_X_KEY), path + ".start_x"),
                            readDouble(tag.get(START_Y_KEY), path + ".start_y"),
                            readDouble(tag.get(START_Z_KEY), path + ".start_z")
                    ),
                    new RoadPoint(
                            readDouble(tag.get(END_X_KEY), path + ".end_x"),
                            readDouble(tag.get(END_Y_KEY), path + ".end_y"),
                            readDouble(tag.get(END_Z_KEY), path + ".end_z")
                    )
            );
        }
        requireExactKeys(tag, CURVE_GEOMETRY_KEYS, path);
        List<RoadPoint> points = readGeometryPoints(
                tag.get(CONTROL_POINTS_KEY),
                path + ".control_points"
        );
        return switch (type) {
            case "quadratic" -> {
                requirePointCount(points, 3, path);
                yield new QuadraticBezierGeometry(
                        points.get(0), points.get(1), points.get(2)
                );
            }
            case "cubic" -> {
                requirePointCount(points, 4, path);
                yield new CubicBezierGeometry(
                        points.get(0),
                        points.get(1),
                        points.get(2),
                        points.get(3)
                );
            }
            case "multi" -> new MultiCurveGeometry(points);
            case "joined_cubics" -> MultiCurveGeometry.fromControlPoints(points);
            case "freehand_line" -> {
                requirePointCount(points, 2, path);
                yield new FreehandGeometry(points, 0.0);
            }
            default -> throw malformed(
                    path + ".type is unsupported: " + type
            );
        };
    }

    private static List<RoadPoint> readGeometryPoints(
            Tag rawTag,
            String path
    ) {
        if (!(rawTag instanceof ListTag tags)
                || tags.getElementType() != Tag.TAG_COMPOUND) {
            throw malformed(path + " must be a compound list");
        }
        if (tags.size() < 2 || tags.size() > 64) {
            throw malformed(path + " must contain between 2 and 64 points");
        }
        List<RoadPoint> points = new ArrayList<>(tags.size());
        for (int index = 0; index < tags.size(); index++) {
            CompoundTag pointTag = tags.getCompound(index);
            String pointPath = path + "[" + index + "]";
            requireExactKeys(pointTag, Set.of(X_KEY, Y_KEY, Z_KEY), pointPath);
            points.add(new RoadPoint(
                    readDouble(pointTag.get(X_KEY), pointPath + ".x"),
                    readDouble(pointTag.get(Y_KEY), pointPath + ".y"),
                    readDouble(pointTag.get(Z_KEY), pointPath + ".z")
            ));
        }
        return List.copyOf(points);
    }

    private static void requirePointCount(
            List<RoadPoint> points,
            int expected,
            String path
    ) {
        if (points.size() != expected) {
            throw malformed(
                    path + " must contain exactly " + expected + " points"
            );
        }
    }

    private static DecodedPreset readPreset(
            Tag rawTag,
            String path,
            int maximumLaneCount,
            int graphSchemaVersion
    ) {
        if (!(rawTag instanceof CompoundTag tag)) {
            throw malformed(path + " must be a compound");
        }
        requireExactKeys(
                tag,
                graphSchemaVersion >= 2
                        ? PRESET_V2_KEYS
                        : tag.contains(SURFACE_BANDS_KEY)
                                ? PRESET_KEYS
                                : LEGACY_PRESET_KEYS,
                path
        );
        ResourceLocation sourceId = readResourceId(tag.get(SOURCE_ID_KEY), path + ".source_id");
        int sourceRevision = readInt(tag.get(SOURCE_REVISION_KEY), path + ".source_revision");
        if (sourceRevision < 1) {
            throw malformed(path + ".source_revision must be positive");
        }
        int surfaceWidth = readInt(tag.get(SURFACE_WIDTH_KEY), path + ".surface_width");
        ResourceLocation surfaceBlock = readResourceId(
                tag.get(SURFACE_BLOCK_KEY),
                path + ".surface_block"
        );
        List<RoadSurfaceBand> surfaceBands = new ArrayList<>();
        if (tag.contains(SURFACE_BANDS_KEY)) {
            ListTag bandTags = readCompoundList(
                    tag.get(SURFACE_BANDS_KEY),
                    path + ".surface_bands"
            );
            if (bandTags.isEmpty()
                    || bandTags.size() > RoadPreset.MAXIMUM_SURFACE_BLOCK_WIDTH) {
                throw malformed(path + ".surface_bands has an invalid count");
            }
            for (int index = 0; index < bandTags.size(); index++) {
                CompoundTag bandTag = bandTags.getCompound(index);
                String bandPath = path + ".surface_bands[" + index + "]";
                requireExactKeys(
                        bandTag,
                        graphSchemaVersion >= 3
                                ? SURFACE_BAND_KEYS
                                : LEGACY_SURFACE_BAND_KEYS,
                        bandPath
                );
                String serializedRole = readString(
                        bandTag.get(ROLE_KEY),
                        bandPath + ".role"
                );
                RoadSurfaceRole role;
                RoadMarkingPattern pattern;
                if (serializedRole.equals("marking_dashed")) {
                    role = RoadSurfaceRole.MARKING;
                    pattern = RoadMarkingPattern.DASHED;
                } else if (serializedRole.equals(
                        "marking_double_continuous"
                )) {
                    role = RoadSurfaceRole.MARKING;
                    pattern = RoadMarkingPattern.DOUBLE_CONTINUOUS;
                } else {
                    role = RoadSurfaceRole.fromSerializedName(serializedRole)
                            .orElseThrow(() -> malformed(
                                    bandPath + ".role is unknown"
                            ));
                    pattern = RoadMarkingPattern.CONTINUOUS;
                }
                ResourceLocation primaryBlock = readResourceId(
                        bandTag.get(BLOCK_KEY),
                        bandPath + ".block"
                );
                List<ResourceLocation> blockSet = graphSchemaVersion >= 3
                        ? readBlockSet(
                                bandTag.get(BLOCK_SET_KEY),
                                bandPath + ".block_set"
                        )
                        : List.of(primaryBlock);
                surfaceBands.add(new RoadSurfaceBand(
                        role,
                        readInt(bandTag.get(WIDTH_KEY), bandPath + ".width"),
                        primaryBlock,
                        pattern,
                        blockSet
                ));
            }
        }
        ListTag laneTags = readCompoundList(tag.get(LANES_KEY), path + ".lanes");
        if (laneTags.isEmpty()) {
            throw malformed(path + ".lanes must not be empty");
        }
        if (laneTags.size() > maximumLaneCount) {
            throw malformed(path + ".lanes exceeds the remaining lane limit");
        }

        List<LaneComponent> lanes = new ArrayList<>(laneTags.size());
        for (int index = 0; index < laneTags.size(); index++) {
            CompoundTag laneTag = laneTags.getCompound(index);
            String lanePath = path + ".lanes[" + index + "]";
            requireExactKeys(laneTag, LANE_COMPONENT_KEYS, lanePath);
            LaneType type = LaneType.fromSerializedName(readString(
                    laneTag.get(TYPE_KEY),
                    lanePath + ".type"
            )).orElseThrow(() -> malformed(lanePath + ".type is unknown"));
            LaneTravelDirection direction = LaneTravelDirection.fromSerializedName(readString(
                    laneTag.get(DIRECTION_KEY),
                    lanePath + ".direction"
            )).orElseThrow(() -> malformed(lanePath + ".direction is unknown"));
            lanes.add(new LaneComponent(
                    type,
                    readDouble(laneTag.get(WIDTH_KEY), lanePath + ".width"),
                    readDouble(laneTag.get(OFFSET_KEY), lanePath + ".offset"),
                    direction
            ));
        }
        RoadClassification classification = graphSchemaVersion >= 2
                ? readClassification(
                        tag.getCompound(CLASSIFICATION_KEY),
                        path + ".classification"
                )
                : RoadClassification.DEFAULT;
        RoadIntersectionStyle intersectionStyle = graphSchemaVersion >= 2
                ? readIntersectionStyle(
                        tag.getCompound(INTERSECTION_STYLE_KEY),
                        path + ".intersection_style"
                )
                : RoadIntersectionStyle.defaults(surfaceBlock);
        return new DecodedPreset(
                sourceId,
                sourceRevision,
                new RoadPresetSnapshot(
                        surfaceWidth,
                        surfaceBlock,
                        lanes,
                        surfaceBands,
                        classification,
                        intersectionStyle
                )
        );
    }

    private static String serializedBandRole(RoadSurfaceBand band) {
        if (band.role() != RoadSurfaceRole.MARKING) {
            return band.role().serializedName();
        }
        return switch (band.pattern()) {
            case CONTINUOUS -> RoadSurfaceRole.MARKING.serializedName();
            case DASHED -> "marking_dashed";
            case DOUBLE_CONTINUOUS -> "marking_double_continuous";
        };
    }

    private static List<RoadLaneId> readLaneIds(
            Tag rawTag,
            String path,
            int maximumLaneCount
    ) {
        ListTag tags = readCompoundList(rawTag, path);
        if (tags.size() > maximumLaneCount) {
            throw malformed(path + " exceeds the remaining lane limit");
        }
        List<RoadLaneId> laneIds = new ArrayList<>(tags.size());
        Set<RoadLaneId> unique = new HashSet<>();
        for (int index = 0; index < tags.size(); index++) {
            CompoundTag laneIdTag = tags.getCompound(index);
            String lanePath = path + "[" + index + "]";
            requireExactKeys(laneIdTag, ID_ONLY_KEYS, lanePath);
            RoadLaneId laneId = new RoadLaneId(readUuid(laneIdTag.get(ID_KEY), lanePath + ".id"));
            if (!unique.add(laneId)) {
                throw malformed(path + " contains duplicate lane ID " + laneId);
            }
            laneIds.add(laneId);
        }
        return List.copyOf(laneIds);
    }

    private static RoadClassification readClassification(
            CompoundTag tag,
            String path
    ) {
        requireExactKeys(tag, CLASSIFICATION_KEYS, path);
        RoadClass roadClass;
        try {
            roadClass = java.util.Arrays.stream(RoadClass.values())
                    .filter(value -> value.serializedName().equals(
                            readString(
                                    tag.get(ROAD_CLASS_KEY),
                                    path + ".road_class"
                            )
                    ))
                    .findFirst()
                    .orElseThrow();
        } catch (RuntimeException invalid) {
            throw malformed(path + ".road_class is unknown");
        }
        return new RoadClassification(
                roadClass,
                readInt(
                        tag.get(PRIORITY_RANK_KEY),
                        path + ".priority_rank"
                ),
                readDouble(
                        tag.get(NOMINAL_SPEED_KEY),
                        path + ".nominal_speed"
                )
        );
    }

    private static RoadIntersectionStyle readIntersectionStyle(
            CompoundTag tag,
            String path
    ) {
        requireExactKeys(tag, INTERSECTION_STYLE_KEYS, path);
        IntersectionControl control;
        String serializedControl = readString(
                tag.get(DEFAULT_CONTROL_KEY),
                path + ".default_control"
        );
        try {
            control = java.util.Arrays.stream(IntersectionControl.values())
                    .filter(value -> value.serializedName()
                            .equals(serializedControl))
                    .findFirst()
                    .orElseThrow();
        } catch (RuntimeException invalid) {
            throw malformed(path + ".default_control is unknown");
        }
        return new RoadIntersectionStyle(
                readResourceId(
                        tag.get(PAVEMENT_BLOCK_KEY),
                        path + ".pavement_block"
                ),
                readOptionalResourceId(
                        tag.get(MARKING_BLOCK_KEY),
                        path + ".marking_block"
                ),
                readOptionalResourceId(
                        tag.get(CROSSWALK_BLOCK_KEY),
                        path + ".crosswalk_block"
                ),
                readOptionalResourceId(
                        tag.get(STOP_LINE_BLOCK_KEY),
                        path + ".stop_line_block"
                ),
                tag.getBoolean(DEFAULT_CROSSWALKS_KEY),
                control
        );
    }

    private static Optional<ResourceLocation> readOptionalResourceId(
            Tag tag,
            String path
    ) {
        String value = readString(tag, path);
        return value.isEmpty()
                ? Optional.empty()
                : Optional.of(readResourceId(tag, path));
    }

    private static JunctionDefinition readJunctionDefinition(
            CompoundTag tag,
            String path
    ) {
        requireExactKeys(tag, JUNCTION_KEYS, path);
        RoadNodeId nodeId = new RoadNodeId(readUuid(
                tag.get(NODE_KEY),
                path + ".node"
        ));
        JunctionStrategy strategy = readEnum(
                JunctionStrategy.class,
                tag.get(STRATEGY_KEY),
                path + ".strategy"
        );
        JunctionPriorityPolicy priority = readEnum(
                JunctionPriorityPolicy.class,
                tag.get(PRIORITY_POLICY_KEY),
                path + ".priority_policy"
        );
        ListTag primaryTags = readCompoundList(
                tag.get(PRIMARY_APPROACHES_KEY),
                path + ".primary_approaches"
        );
        if (primaryTags.size()
                > JunctionDefinition.MAXIMUM_APPROACH_COUNT) {
            throw malformed(path + ".primary_approaches exceeds "
                    + JunctionDefinition.MAXIMUM_APPROACH_COUNT);
        }
        List<ApproachKey> primary = new ArrayList<>(primaryTags.size());
        for (int index = 0; index < primaryTags.size(); index++) {
            primary.add(readApproachKey(
                    primaryTags.getCompound(index),
                    path + ".primary_approaches[" + index + "]",
                    false
            ));
        }
        JunctionCrossingPolicy crossing = readEnum(
                JunctionCrossingPolicy.class,
                tag.get(CROSSING_POLICY_KEY),
                path + ".crossing_policy"
        );
        ListTag overrideTags = readCompoundList(
                tag.get(APPROACH_OVERRIDES_KEY),
                path + ".approach_overrides"
        );
        if (overrideTags.size()
                > JunctionDefinition.MAXIMUM_APPROACH_COUNT) {
            throw malformed(path + ".approach_overrides exceeds "
                    + JunctionDefinition.MAXIMUM_APPROACH_COUNT);
        }
        Map<ApproachKey, JunctionApproachOverride> overrides =
                new LinkedHashMap<>();
        for (int index = 0; index < overrideTags.size(); index++) {
            CompoundTag overrideTag = overrideTags.getCompound(index);
            String overridePath = path + ".approach_overrides[" + index + "]";
            ApproachKey key = readApproachKey(
                    overrideTag,
                    overridePath,
                    true
            );
            JunctionApproachOverride override =
                    new JunctionApproachOverride(
                            readIntersectionControl(
                                    overrideTag.get(CONTROL_KEY),
                                    overridePath + ".control"
                            ),
                            overrideTag.getBoolean(CROSSWALK_KEY)
                    );
            if (overrides.put(key, override) != null) {
                throw malformed(
                        path + ".approach_overrides contains duplicate keys"
                );
            }
        }
        return new JunctionDefinition(
                nodeId,
                strategy,
                priority,
                primary,
                crossing,
                overrides,
                readLong(
                        tag.get(DEFINITION_REVISION_KEY),
                        path + ".definition_revision"
                )
        );
    }

    private static ApproachKey readApproachKey(
            CompoundTag tag,
            String path,
            boolean override
    ) {
        requireExactKeys(
                tag,
                override ? APPROACH_OVERRIDE_KEYS : APPROACH_KEYS,
                path
        );
        return new ApproachKey(
                new RoadSegmentId(readUuid(
                        tag.get(SEGMENT_KEY),
                        path + ".segment"
                )),
                readEnum(
                        ApproachEnd.class,
                        tag.get(END_KEY),
                        path + ".end"
                )
        );
    }

    private static IntersectionControl readIntersectionControl(
            Tag tag,
            String path
    ) {
        String value = readString(tag, path);
        return java.util.Arrays.stream(IntersectionControl.values())
                .filter(control -> control.serializedName().equals(value))
                .findFirst()
                .orElseThrow(() -> malformed(path + " is unknown"));
    }

    private static <E extends Enum<E>> E readEnum(
            Class<E> type,
            Tag tag,
            String path
    ) {
        try {
            return Enum.valueOf(type, readString(tag, path));
        } catch (IllegalArgumentException invalid) {
            throw malformed(path + " is unknown");
        }
    }

    private static ListTag readCompoundList(Tag rawTag, String path) {
        if (!(rawTag instanceof ListTag list)) {
            throw malformed(path + " must be a list");
        }
        if (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) {
            throw malformed(path + " entries must be compounds");
        }
        return list;
    }

    private static List<ResourceLocation> readBlockSet(
            Tag rawTag,
            String path
    ) {
        if (!(rawTag instanceof ListTag list)) {
            throw malformed(path + " must be a list");
        }
        if (list.isEmpty() || list.size() > 16) {
            throw malformed(path + " must contain between 1 and 16 blocks");
        }
        if (list.getElementType() != Tag.TAG_STRING) {
            throw malformed(path + " entries must be strings");
        }
        List<ResourceLocation> blocks = new ArrayList<>(list.size());
        for (int index = 0; index < list.size(); index++) {
            blocks.add(readResourceId(
                    list.get(index),
                    path + "[" + index + "]"
            ));
        }
        return List.copyOf(blocks);
    }

    private static int readInt(Tag tag, String path) {
        if (!(tag instanceof IntTag intTag)) {
            throw malformed(path + " must be an int");
        }
        return intTag.getAsInt();
    }

    private static long readLong(Tag tag, String path) {
        if (!(tag instanceof LongTag longTag)) {
            throw malformed(path + " must be a long");
        }
        return longTag.getAsLong();
    }

    private static double readDouble(Tag tag, String path) {
        if (!(tag instanceof DoubleTag doubleTag)) {
            throw malformed(path + " must be a double");
        }
        double value = doubleTag.getAsDouble();
        if (!Double.isFinite(value)) {
            throw malformed(path + " must be finite");
        }
        return value;
    }

    private static String readString(Tag tag, String path) {
        if (!(tag instanceof StringTag stringTag)) {
            throw malformed(path + " must be a string");
        }
        return stringTag.getAsString();
    }

    private static ResourceLocation readResourceId(Tag tag, String path) {
        String value = readString(tag, path);
        if (value.length() > MAXIMUM_RESOURCE_ID_CHARACTERS) {
            throw malformed(path + " exceeds " + MAXIMUM_RESOURCE_ID_CHARACTERS + " characters");
        }
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            throw malformed(path + " is not a valid resource identifier");
        }
        return id;
    }

    private static UUID readUuid(Tag tag, String path) {
        if (!(tag instanceof IntArrayTag uuidTag) || uuidTag.size() != 4) {
            throw malformed(path + " must be a four-int UUID");
        }
        try {
            return NbtUtils.loadUUID(uuidTag);
        } catch (RuntimeException exception) {
            throw malformed(path + " is not a valid UUID");
        }
    }

    private static void requireCount(int actual, int maximum, String type) {
        if (actual > maximum) {
            throw malformed(type + " count exceeds " + maximum);
        }
    }

    private static void requireExactKeys(CompoundTag tag, Set<String> keys, String path) {
        if (!tag.getAllKeys().equals(keys)) {
            throw malformed(path + " must contain exactly " + keys);
        }
    }

    private static LaneMovementType readMovementType(
            CompoundTag tag,
            String path
    ) {
        if (!tag.contains(MOVEMENT_TYPE_KEY, Tag.TAG_STRING)
                || !tag.contains(MOVEMENT_ALLOWED_KEY, Tag.TAG_BYTE)
                || !tag.contains(MOVEMENT_MANUAL_KEY, Tag.TAG_BYTE)
                || !tag.contains(MOVEMENT_PRIORITY_KEY, Tag.TAG_INT)) {
            throw new IllegalArgumentException(
                    path + " has invalid connector movement fields"
            );
        }
        try {
            return LaneMovementType.valueOf(
                    tag.getString(MOVEMENT_TYPE_KEY)
            );
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException(
                    path + ".movement_type is unknown"
            );
        }
    }

    private static void requireAbsent(Object previous, String type, Object id) {
        if (previous != null) {
            throw malformed("duplicate " + type + " ID " + id);
        }
    }

    private static IllegalArgumentException malformed(String message) {
        return new IllegalArgumentException(message);
    }

    private record DecodedPreset(
            ResourceLocation sourceId,
            int sourceRevision,
            RoadPresetSnapshot snapshot
    ) {
    }

    private record DecodedData(
            int schemaVersion,
            long revision,
            List<Road> roads,
            List<RoadNode> nodes,
            List<RoadSegment> segments,
            List<LaneConnector> connectors,
            List<JunctionDefinition> junctionDefinitions
    ) {
    }

    private record RecoveredData(DecodedData decodedData, boolean recovered) {
    }
}
