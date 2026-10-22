package com.cookiecraftmods.roadbuilder.networking;

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
import com.cookiecraftmods.roadbuilder.network.RoadGraphDelta;
import com.cookiecraftmods.roadbuilder.network.RoadGraph;
import com.cookiecraftmods.roadbuilder.network.RoadGraphLimits;
import com.cookiecraftmods.roadbuilder.network.RoadGraphSnapshot;
import com.cookiecraftmods.roadbuilder.network.RoadId;
import com.cookiecraftmods.roadbuilder.network.RoadLaneId;
import com.cookiecraftmods.roadbuilder.network.RoadNode;
import com.cookiecraftmods.roadbuilder.network.RoadNodeId;
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
import com.cookiecraftmods.roadbuilder.preset.RoadMarkingPattern;
import com.cookiecraftmods.roadbuilder.preset.RoadSurfaceBand;
import com.cookiecraftmods.roadbuilder.preset.RoadSurfaceRole;
import com.cookiecraftmods.roadbuilder.preset.TrafficHandedness;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import io.netty.buffer.Unpooled;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class RoadGraphPacketCodecs {
    // Synchronization must accept the same graph sizes as persistence.
    static final int MAXIMUM_ROAD_COUNT = RoadGraphLimits.DEFAULT_MAXIMUM_ROAD_COUNT;
    static final int MAXIMUM_NODE_COUNT = RoadGraphLimits.DEFAULT_MAXIMUM_NODE_COUNT;
    static final int MAXIMUM_SEGMENT_COUNT = RoadGraphLimits.DEFAULT_MAXIMUM_SEGMENT_COUNT;
    static final int MAXIMUM_LANE_COUNT = RoadGraphLimits.DEFAULT_MAXIMUM_LANE_COUNT;
    static final int MAXIMUM_CONNECTOR_COUNT = RoadGraphLimits.DEFAULT_MAXIMUM_CONNECTOR_COUNT;
    static final int MAXIMUM_LANES_PER_SEGMENT = 64;
    private static final int FORMAT_V2_MARKER = 0x52424702;
    private static final int FORMAT_V3_MARKER = 0x52424703;

    private static final double MAX_ABSOLUTE_COORDINATE = 30_000_000.0;
    private static final RoadGraphLimits WIRE_LIMITS = new RoadGraphLimits(
            MAXIMUM_ROAD_COUNT,
            MAXIMUM_NODE_COUNT,
            MAXIMUM_SEGMENT_COUNT,
            MAXIMUM_LANE_COUNT,
            MAXIMUM_CONNECTOR_COUNT
    );

    private RoadGraphPacketCodecs() {
    }

        public static byte[] encodeDelta(RoadGraphDelta delta) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            writeDelta(buffer, delta);
            if (buffer.readableBytes() > RoadGraphDeltaS2CPacket.MAX_PAYLOAD_BYTES) {
                throw new IllegalArgumentException(
                        "encoded road graph delta exceeds its payload limit"
                );
            }
            byte[] encoded = new byte[buffer.readableBytes()];
            buffer.getBytes(buffer.readerIndex(), encoded);
            return encoded;
        } finally {
            buffer.release();
        }
    }

        public static RoadGraphDelta decodeDelta(byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded");
        if (encoded.length == 0
                || encoded.length > RoadGraphDeltaS2CPacket.MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException(
                    "persisted road graph delta has an invalid size"
            );
        }
        FriendlyByteBuf buffer = new FriendlyByteBuf(
                Unpooled.wrappedBuffer(encoded)
        );
        try {
            RoadGraphDelta delta = readDelta(buffer);
            PacketValidation.requireFullyRead(buffer, "persisted road graph delta");
            return delta;
        } finally {
            buffer.release();
        }
    }

    static void validateSnapshot(RoadGraphSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        PacketValidation.requireBoundedId(snapshot.dimension().location(), "dimension");
        requireCount(snapshot.roads().size(), MAXIMUM_ROAD_COUNT, "road");
        requireCount(snapshot.nodes().size(), MAXIMUM_NODE_COUNT, "node");
        requireCount(snapshot.segments().size(), MAXIMUM_SEGMENT_COUNT, "segment");
        requireCount(snapshot.lanes().size(), MAXIMUM_LANE_COUNT, "lane");
        requireCount(snapshot.connectors().size(), MAXIMUM_CONNECTOR_COUNT, "connector");
        requireCount(
                snapshot.junctionDefinitions().size(),
                MAXIMUM_NODE_COUNT,
                "junction definition"
        );
        for (RoadSegment segment : snapshot.segments().values()) {
            requireCount(
                    segment.lanes().size(),
                    MAXIMUM_LANES_PER_SEGMENT,
                    "lanes in segment " + segment.id()
            );
        }

        RoadGraphSnapshot canonical = RoadGraph.restore(
                snapshot.dimension(),
                snapshot.revision(),
                snapshot.roads().values(),
                snapshot.nodes().values(),
                snapshot.segments().values(),
                snapshot.connectors().values(),
                snapshot.junctionDefinitions().values(),
                WIRE_LIMITS,
                () -> {
                }
        ).snapshot();
        if (!canonical.equals(snapshot)) {
            throw new IllegalArgumentException("road graph snapshot is not canonical");
        }
    }

    static void validateDelta(RoadGraphDelta delta) {
        Objects.requireNonNull(delta, "delta");
        PacketValidation.requireBoundedId(delta.dimension().location(), "dimension");
        requireCount(delta.roads().size(), MAXIMUM_ROAD_COUNT, "upserted road");
        requireCount(delta.removedRoadIds().size(), MAXIMUM_ROAD_COUNT, "removed road");
        requireCount(delta.nodes().size(), MAXIMUM_NODE_COUNT, "upserted node");
        requireCount(delta.removedNodeIds().size(), MAXIMUM_NODE_COUNT, "removed node");
        requireCount(delta.segments().size(), MAXIMUM_SEGMENT_COUNT, "upserted segment");
        requireCount(
                delta.removedSegmentIds().size(),
                MAXIMUM_SEGMENT_COUNT,
                "removed segment"
        );
        requireCount(
                delta.connectors().size(),
                MAXIMUM_CONNECTOR_COUNT,
                "upserted connector"
        );
        requireCount(
                delta.removedConnectorIds().size(),
                MAXIMUM_CONNECTOR_COUNT,
                "removed connector"
        );
        requireCount(
                delta.junctionDefinitions().size(),
                MAXIMUM_NODE_COUNT,
                "upserted junction definition"
        );
        requireCount(
                delta.removedJunctionDefinitionNodeIds().size(),
                MAXIMUM_NODE_COUNT,
                "removed junction definition"
        );

        int laneCount = 0;
        for (RoadSegment segment : delta.segments().values()) {
            requireCount(
                    segment.lanes().size(),
                    MAXIMUM_LANES_PER_SEGMENT,
                    "lanes in segment " + segment.id()
            );
            laneCount += segment.lanes().size();
            requireCount(laneCount, MAXIMUM_LANE_COUNT, "upserted lane");
        }
    }

    static void writeSnapshot(FriendlyByteBuf buffer, RoadGraphSnapshot snapshot) {
        validateSnapshot(snapshot);
        buffer.writeVarInt(FORMAT_V3_MARKER);
        PacketValidation.writeBoundedId(buffer, snapshot.dimension().location());
        buffer.writeVarLong(snapshot.revision());

        buffer.writeVarInt(snapshot.roads().size());
        for (Road road : snapshot.roads().values()) {
            buffer.writeUUID(road.id().value());
            writeHandedness(buffer, road.trafficHandedness());
        }

        buffer.writeVarInt(snapshot.nodes().size());
        for (RoadNode node : snapshot.nodes().values()) {
            buffer.writeUUID(node.id().value());
            writePoint(buffer, node.position());
        }

        buffer.writeVarInt(snapshot.segments().size());
        for (RoadSegment segment : snapshot.segments().values()) {
            writeSegment(buffer, segment);
        }

        buffer.writeVarInt(snapshot.connectors().size());
        for (LaneConnector connector : snapshot.connectors().values()) {
            buffer.writeUUID(connector.id().value());
            buffer.writeUUID(connector.fromLaneId().value());
            buffer.writeUUID(connector.toLaneId().value());
            writeMovement(buffer, connector.movement());
        }
        buffer.writeVarInt(snapshot.junctionDefinitions().size());
        snapshot.junctionDefinitions().values().forEach(definition ->
                writeJunctionDefinition(buffer, definition));
    }

    static RoadGraphSnapshot readSnapshot(FriendlyByteBuf buffer) {
        int formatVersion = readFormatVersion(buffer);
        ResourceLocation dimensionId = PacketValidation.readBoundedId(buffer, "dimension");
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
        long revision = buffer.readVarLong();
        if (revision < 0) {
            throw new IllegalArgumentException("road graph revision must be non-negative");
        }

        int roadCount = readCount(buffer, MAXIMUM_ROAD_COUNT, "road");
        List<Road> roads = new ArrayList<>(roadCount);
        Map<RoadId, Road> roadsById = new HashMap<>(roadCount);
        for (int index = 0; index < roadCount; index++) {
            RoadId roadId = new RoadId(buffer.readUUID());
            Road road = new Road(roadId, readHandedness(buffer));
            if (roadsById.put(roadId, road) != null) {
                throw new IllegalArgumentException("duplicate road ID " + roadId);
            }
            roads.add(road);
        }

        int nodeCount = readCount(buffer, MAXIMUM_NODE_COUNT, "node");
        List<RoadNode> nodes = new ArrayList<>(nodeCount);
        for (int index = 0; index < nodeCount; index++) {
            RoadNodeId nodeId = new RoadNodeId(buffer.readUUID());
            nodes.add(new RoadNode(nodeId, readPoint(buffer)));
        }

        int segmentCount = readCount(buffer, MAXIMUM_SEGMENT_COUNT, "segment");
        List<RoadSegment> segments = new ArrayList<>(segmentCount);
        int totalLaneCount = 0;
        for (int index = 0; index < segmentCount; index++) {
            DecodedSegment decoded = readSegment(buffer, formatVersion);
            totalLaneCount += decoded.laneIds().size();
            requireCount(totalLaneCount, MAXIMUM_LANE_COUNT, "lane");
            Road road = roadsById.get(decoded.roadId());
            if (road == null) {
                throw new IllegalArgumentException(
                        "segment references unknown road " + decoded.roadId()
                );
            }
            segments.add(RoadSegment.path(
                    decoded.id(),
                    decoded.roadId(),
                    decoded.startNodeId(),
                    decoded.endNodeId(),
                    decoded.geometry(),
                    decoded.presetId(),
                    decoded.presetRevision(),
                    decoded.presetSnapshot(),
                    road.trafficHandedness(),
                    decoded.laneIds()
            ));
        }

        int connectorCount = readCount(buffer, MAXIMUM_CONNECTOR_COUNT, "connector");
        List<LaneConnector> connectors = new ArrayList<>(connectorCount);
        for (int index = 0; index < connectorCount; index++) {
            LaneConnectorId connectorId = new LaneConnectorId(buffer.readUUID());
            connectors.add(new LaneConnector(
                    connectorId,
                    new RoadLaneId(buffer.readUUID()),
                    new RoadLaneId(buffer.readUUID()),
                    readMovement(buffer)
            ));
        }
        int junctionCount = formatVersion >= 2
                ? readCount(
                        buffer,
                        MAXIMUM_NODE_COUNT,
                        "junction definition"
                )
                : 0;
        List<JunctionDefinition> junctionDefinitions =
                new ArrayList<>(junctionCount);
        for (int index = 0; index < junctionCount; index++) {
            junctionDefinitions.add(readJunctionDefinition(buffer));
        }

        return RoadGraph.restore(
                dimension,
                revision,
                roads,
                nodes,
                segments,
                connectors,
                junctionDefinitions,
                WIRE_LIMITS,
                () -> {
                }
        ).snapshot();
    }

    static void writeDelta(FriendlyByteBuf buffer, RoadGraphDelta delta) {
        validateDelta(delta);
        buffer.writeVarInt(FORMAT_V3_MARKER);
        PacketValidation.writeBoundedId(buffer, delta.dimension().location());
        buffer.writeVarLong(delta.baseRevision());
        buffer.writeVarLong(delta.targetRevision());

        buffer.writeVarInt(delta.roads().size());
        for (Road road : delta.roads().values()) {
            buffer.writeUUID(road.id().value());
            writeHandedness(buffer, road.trafficHandedness());
        }
        writeRoadIds(buffer, delta.removedRoadIds());

        buffer.writeVarInt(delta.nodes().size());
        for (RoadNode node : delta.nodes().values()) {
            buffer.writeUUID(node.id().value());
            writePoint(buffer, node.position());
        }
        writeNodeIds(buffer, delta.removedNodeIds());

        buffer.writeVarInt(delta.segments().size());
        for (RoadSegment segment : delta.segments().values()) {
            writeHandedness(buffer, segment.trafficHandedness());
            writeSegment(buffer, segment);
        }
        writeSegmentIds(buffer, delta.removedSegmentIds());

        buffer.writeVarInt(delta.connectors().size());
        for (LaneConnector connector : delta.connectors().values()) {
            buffer.writeUUID(connector.id().value());
            buffer.writeUUID(connector.fromLaneId().value());
            buffer.writeUUID(connector.toLaneId().value());
            writeMovement(buffer, connector.movement());
        }
        writeConnectorIds(buffer, delta.removedConnectorIds());
        buffer.writeVarInt(delta.junctionDefinitions().size());
        delta.junctionDefinitions().values().forEach(definition ->
                writeJunctionDefinition(buffer, definition));
        writeNodeIds(buffer, delta.removedJunctionDefinitionNodeIds());
    }

    static RoadGraphDelta readDelta(FriendlyByteBuf buffer) {
        int formatVersion = readFormatVersion(buffer);
        ResourceLocation dimensionId = PacketValidation.readBoundedId(buffer, "dimension");
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
        long baseRevision = buffer.readVarLong();
        long targetRevision = buffer.readVarLong();
        if (baseRevision < 0 || targetRevision <= baseRevision) {
            throw new IllegalArgumentException("road graph delta revisions are invalid");
        }

        int roadCount = readCount(buffer, MAXIMUM_ROAD_COUNT, "upserted road");
        Map<RoadId, Road> roads = new LinkedHashMap<>(roadCount);
        for (int index = 0; index < roadCount; index++) {
            RoadId id = new RoadId(buffer.readUUID());
            putUnique(roads, id, new Road(id, readHandedness(buffer)), "road");
        }
        List<RoadId> removedRoadIds = readIds(
                buffer,
                MAXIMUM_ROAD_COUNT,
                "removed road",
                () -> new RoadId(buffer.readUUID())
        );

        int nodeCount = readCount(buffer, MAXIMUM_NODE_COUNT, "upserted node");
        Map<RoadNodeId, RoadNode> nodes = new LinkedHashMap<>(nodeCount);
        for (int index = 0; index < nodeCount; index++) {
            RoadNodeId id = new RoadNodeId(buffer.readUUID());
            putUnique(nodes, id, new RoadNode(id, readPoint(buffer)), "node");
        }
        List<RoadNodeId> removedNodeIds = readIds(
                buffer,
                MAXIMUM_NODE_COUNT,
                "removed node",
                () -> new RoadNodeId(buffer.readUUID())
        );

        int segmentCount = readCount(buffer, MAXIMUM_SEGMENT_COUNT, "upserted segment");
        Map<RoadSegmentId, RoadSegment> segments = new LinkedHashMap<>(segmentCount);
        int laneCount = 0;
        for (int index = 0; index < segmentCount; index++) {
            TrafficHandedness handedness = readHandedness(buffer);
            DecodedSegment decoded = readSegment(buffer, formatVersion);
            laneCount += decoded.laneIds().size();
            requireCount(laneCount, MAXIMUM_LANE_COUNT, "upserted lane");
            RoadSegment segment = RoadSegment.path(
                    decoded.id(),
                    decoded.roadId(),
                    decoded.startNodeId(),
                    decoded.endNodeId(),
                    decoded.geometry(),
                    decoded.presetId(),
                    decoded.presetRevision(),
                    decoded.presetSnapshot(),
                    handedness,
                    decoded.laneIds()
            );
            putUnique(segments, segment.id(), segment, "segment");
        }
        List<RoadSegmentId> removedSegmentIds = readIds(
                buffer,
                MAXIMUM_SEGMENT_COUNT,
                "removed segment",
                () -> new RoadSegmentId(buffer.readUUID())
        );

        int connectorCount = readCount(
                buffer,
                MAXIMUM_CONNECTOR_COUNT,
                "upserted connector"
        );
        Map<LaneConnectorId, LaneConnector> connectors = new LinkedHashMap<>(connectorCount);
        for (int index = 0; index < connectorCount; index++) {
            LaneConnectorId id = new LaneConnectorId(buffer.readUUID());
            putUnique(
                    connectors,
                    id,
                    new LaneConnector(
                            id,
                            new RoadLaneId(buffer.readUUID()),
                            new RoadLaneId(buffer.readUUID()),
                            readMovement(buffer)
                    ),
                    "connector"
            );
        }
        List<LaneConnectorId> removedConnectorIds = readIds(
                buffer,
                MAXIMUM_CONNECTOR_COUNT,
                "removed connector",
                () -> new LaneConnectorId(buffer.readUUID())
        );
        int junctionCount = formatVersion >= 2
                ? readCount(
                        buffer,
                        MAXIMUM_NODE_COUNT,
                        "upserted junction definition"
                )
                : 0;
        Map<RoadNodeId, JunctionDefinition> junctionDefinitions =
                new LinkedHashMap<>(junctionCount);
        for (int index = 0; index < junctionCount; index++) {
            JunctionDefinition definition = readJunctionDefinition(buffer);
            putUnique(
                    junctionDefinitions,
                    definition.nodeId(),
                    definition,
                    "junction definition"
            );
        }
        List<RoadNodeId> removedJunctionDefinitionNodeIds = formatVersion >= 2
                ? readIds(
                        buffer,
                        MAXIMUM_NODE_COUNT,
                        "removed junction definition",
                        () -> new RoadNodeId(buffer.readUUID())
                )
                : List.of();

        return new RoadGraphDelta(
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
                junctionDefinitions,
                removedJunctionDefinitionNodeIds
        );
    }

    private static void writeSegment(FriendlyByteBuf buffer, RoadSegment segment) {
        buffer.writeUUID(segment.id().value());
        buffer.writeUUID(segment.roadId().value());
        buffer.writeUUID(segment.startNodeId().value());
        buffer.writeUUID(segment.endNodeId().value());
        writeGeometry(buffer, segment.referenceGeometry());
        PacketValidation.writeBoundedId(buffer, segment.presetId());
        buffer.writeVarInt(segment.presetRevision());

        RoadPresetSnapshot preset = segment.presetSnapshot();
        buffer.writeVarInt(preset.surfaceBlockWidth());
        PacketValidation.writeBoundedId(buffer, preset.surfaceBlock());
        buffer.writeVarInt(preset.surfaceBands().size());
        for (RoadSurfaceBand band : preset.surfaceBands()) {
            buffer.writeByte(switch (band.role()) {
                case PAVEMENT -> 0;
                case MARKING -> switch (band.pattern()) {
                    case CONTINUOUS -> 1;
                    case DASHED -> 3;
                    case DOUBLE_CONTINUOUS -> 4;
                };
                case SIDEWALK -> 2;
                case GRASS_MEDIAN -> 5;
                case CONCRETE_MEDIAN -> 6;
                case RAILING -> 7;
            });
            buffer.writeVarInt(band.width());
            PacketValidation.writeBoundedId(buffer, band.block());
            buffer.writeVarInt(band.blockSet().size());
            for (ResourceLocation block : band.blockSet()) {
                PacketValidation.writeBoundedId(buffer, block);
            }
        }
        writeClassification(buffer, preset.classification());
        writeIntersectionStyle(buffer, preset.intersectionStyle());
        buffer.writeVarInt(preset.lanes().size());
        for (int index = 0; index < preset.lanes().size(); index++) {
            buffer.writeUUID(segment.lanes().get(index).id().value());
            writeLaneComponent(buffer, preset.lanes().get(index));
        }
    }

    private static DecodedSegment readSegment(
            FriendlyByteBuf buffer,
            int formatVersion
    ) {
        RoadSegmentId id = new RoadSegmentId(buffer.readUUID());
        RoadId roadId = new RoadId(buffer.readUUID());
        RoadNodeId startNodeId = new RoadNodeId(buffer.readUUID());
        RoadNodeId endNodeId = new RoadNodeId(buffer.readUUID());
        ReferenceGeometry geometry = readGeometry(buffer);
        ResourceLocation presetId = PacketValidation.readBoundedId(buffer, "presetId");
        int presetRevision = buffer.readVarInt();
        if (presetRevision < 1) {
            throw new IllegalArgumentException("preset revision must be positive");
        }
        int surfaceWidth = buffer.readVarInt();
        ResourceLocation surfaceBlock = PacketValidation.readBoundedId(buffer, "surfaceBlock");
        int surfaceBandCount = readCount(
                buffer,
                RoadPreset.MAXIMUM_SURFACE_BLOCK_WIDTH,
                "surface bands in segment " + id
        );
        if (surfaceBandCount == 0) {
            throw new IllegalArgumentException("a road segment must contain a surface band");
        }
        List<RoadSurfaceBand> surfaceBands = new ArrayList<>(surfaceBandCount);
        for (int index = 0; index < surfaceBandCount; index++) {
            int encodedRole = buffer.readUnsignedByte();
            RoadSurfaceRole role = switch (encodedRole) {
                case 0 -> RoadSurfaceRole.PAVEMENT;
                case 1, 3, 4 -> RoadSurfaceRole.MARKING;
                case 2 -> RoadSurfaceRole.SIDEWALK;
                case 5 -> RoadSurfaceRole.GRASS_MEDIAN;
                case 6 -> RoadSurfaceRole.CONCRETE_MEDIAN;
                case 7 -> RoadSurfaceRole.RAILING;
                default -> throw new IllegalArgumentException("unknown road surface role");
            };
            RoadMarkingPattern pattern = switch (encodedRole) {
                case 3 -> RoadMarkingPattern.DASHED;
                case 4 -> RoadMarkingPattern.DOUBLE_CONTINUOUS;
                default -> RoadMarkingPattern.CONTINUOUS;
            };
            int width = buffer.readVarInt();
            ResourceLocation block = PacketValidation.readBoundedId(
                    buffer,
                    "surfaceBandBlock"
            );
            List<ResourceLocation> blockSet;
            if (formatVersion >= 3) {
                int blockCount = readCount(
                        buffer,
                        16,
                        "blocks in surface band"
                );
                if (blockCount == 0) {
                    throw new IllegalArgumentException(
                            "a road surface block set must not be empty"
                    );
                }
                blockSet = new ArrayList<>(blockCount);
                for (int blockIndex = 0;
                     blockIndex < blockCount;
                     blockIndex++) {
                    blockSet.add(PacketValidation.readBoundedId(
                            buffer,
                            "surfaceBandBlockSet"
                    ));
                }
            } else {
                blockSet = List.of(block);
            }
            surfaceBands.add(new RoadSurfaceBand(
                    role,
                    width,
                    block,
                    pattern,
                    blockSet
            ));
        }
        RoadClassification classification = formatVersion >= 2
                ? readClassification(buffer)
                : RoadClassification.DEFAULT;
        RoadIntersectionStyle intersectionStyle = formatVersion >= 2
                ? readIntersectionStyle(buffer)
                : RoadIntersectionStyle.defaults(surfaceBlock);
        int laneCount = readCount(
                buffer,
                MAXIMUM_LANES_PER_SEGMENT,
                "lanes in segment " + id
        );
        if (laneCount == 0) {
            throw new IllegalArgumentException("a road segment must contain at least one lane");
        }

        List<RoadLaneId> laneIds = new ArrayList<>(laneCount);
        List<LaneComponent> lanes = new ArrayList<>(laneCount);
        for (int index = 0; index < laneCount; index++) {
            laneIds.add(new RoadLaneId(buffer.readUUID()));
            lanes.add(readLaneComponent(buffer));
        }
        return new DecodedSegment(
                id,
                roadId,
                startNodeId,
                endNodeId,
                geometry,
                presetId,
                presetRevision,
                new RoadPresetSnapshot(
                        surfaceWidth,
                        surfaceBlock,
                        lanes,
                        surfaceBands,
                        classification,
                        intersectionStyle
                ),
                List.copyOf(laneIds)
        );
    }

    private static void writePoint(FriendlyByteBuf buffer, RoadPoint point) {
        requireCoordinate(point.x(), "x");
        requireCoordinate(point.y(), "y");
        requireCoordinate(point.z(), "z");
        buffer.writeDouble(point.x());
        buffer.writeDouble(point.y());
        buffer.writeDouble(point.z());
    }

    private static RoadPoint readPoint(FriendlyByteBuf buffer) {
        return new RoadPoint(
                readCoordinate(buffer, "x"),
                readCoordinate(buffer, "y"),
                readCoordinate(buffer, "z")
        );
    }

    private static void writeGeometry(
            FriendlyByteBuf buffer,
            ReferenceGeometry geometry
    ) {
        if (geometry instanceof StraightGeometry straight) {
            // Schema-v1 job journals persist encoded graph deltas. Keep the
            // original two-point straight wire representation byte-for-byte
            // so prepared jobs from earlier builds remain recoverable.
            writePoint(buffer, straight.start());
            writePoint(buffer, straight.end());
        } else if (geometry instanceof QuadraticBezierGeometry quadratic) {
            buffer.writeDouble(Double.NaN);
            buffer.writeByte(1);
            writePoints(buffer, quadratic.controlPoints());
        } else if (geometry instanceof CubicBezierGeometry cubic) {
            buffer.writeDouble(Double.NaN);
            buffer.writeByte(2);
            writePoints(buffer, cubic.controlPoints());
        } else if (geometry instanceof MultiCurveGeometry multi) {
            buffer.writeDouble(Double.NaN);
            buffer.writeByte(3);
            writePoints(buffer, multi.anchors());
        } else if (geometry instanceof FreehandGeometry freehand) {
            buffer.writeDouble(Double.NaN);
            buffer.writeByte(freehand.anchors().size() == 2 ? 4 : 3);
            writePoints(buffer, freehand.anchors());
        } else {
            throw new IllegalArgumentException(
                    "unsupported reference geometry " + geometry.getClass()
            );
        }
    }

    private static ReferenceGeometry readGeometry(FriendlyByteBuf buffer) {
        double firstX = buffer.readDouble();
        if (Double.isFinite(firstX)) {
            requireCoordinate(firstX, "start.x");
            RoadPoint start = new RoadPoint(
                    firstX,
                    readCoordinate(buffer, "start.y"),
                    readCoordinate(buffer, "start.z")
            );
            return new StraightGeometry(start, readPoint(buffer));
        }
        if (!Double.isNaN(firstX)) {
            throw new IllegalArgumentException(
                    "invalid reference geometry marker"
            );
        }
        int type = buffer.readUnsignedByte();
        List<RoadPoint> points = readPoints(buffer);
        return switch (type) {
            case 1 -> {
                requirePointCount(points, 3, "quadratic geometry");
                yield new QuadraticBezierGeometry(
                        points.get(0), points.get(1), points.get(2)
                );
            }
            case 2 -> {
                requirePointCount(points, 4, "cubic geometry");
                yield new CubicBezierGeometry(
                        points.get(0),
                        points.get(1),
                        points.get(2),
                        points.get(3)
                );
            }
            case 3 -> new MultiCurveGeometry(points);
            case 4 -> {
                requirePointCount(points, 2, "freehand line geometry");
                yield new FreehandGeometry(points, 0.0);
            }
            default -> throw new IllegalArgumentException(
                    "unknown reference geometry type " + type
            );
        };
    }

    private static void writePoints(
            FriendlyByteBuf buffer,
            List<RoadPoint> points
    ) {
        if (points.size() < 2 || points.size() > 64) {
            throw new IllegalArgumentException(
                    "reference geometry must contain between 2 and 64 points"
            );
        }
        buffer.writeVarInt(points.size());
        points.forEach(point -> writePoint(buffer, point));
    }

    private static List<RoadPoint> readPoints(FriendlyByteBuf buffer) {
        int count = readCount(buffer, 64, "reference geometry points");
        if (count < 2) {
            throw new IllegalArgumentException(
                    "reference geometry needs at least two points"
            );
        }
        List<RoadPoint> points = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            points.add(readPoint(buffer));
        }
        return List.copyOf(points);
    }

    private static void requirePointCount(
            List<RoadPoint> points,
            int expected,
            String name
    ) {
        if (points.size() != expected) {
            throw new IllegalArgumentException(
                    name + " needs exactly " + expected + " points"
            );
        }
    }

    private static void writeLaneComponent(FriendlyByteBuf buffer, LaneComponent lane) {
        buffer.writeByte(switch (lane.type()) {
            case VEHICLE -> 0;
            case BUS -> 1;
        });
        buffer.writeDouble(lane.width());
        buffer.writeDouble(lane.lateralOffset());
        buffer.writeByte(switch (lane.direction()) {
            case FORWARD -> 0;
            case BACKWARD -> 1;
        });
    }

    private static LaneComponent readLaneComponent(FriendlyByteBuf buffer) {
        LaneType type = switch (buffer.readUnsignedByte()) {
            case 0 -> LaneType.VEHICLE;
            case 1 -> LaneType.BUS;
            default -> throw new IllegalArgumentException("unknown lane type wire ID");
        };
        double width = buffer.readDouble();
        double offset = buffer.readDouble();
        LaneTravelDirection direction = switch (buffer.readUnsignedByte()) {
            case 0 -> LaneTravelDirection.FORWARD;
            case 1 -> LaneTravelDirection.BACKWARD;
            default -> throw new IllegalArgumentException("unknown lane direction wire ID");
        };
        return new LaneComponent(type, width, offset, direction);
    }

    private static void writeHandedness(
            FriendlyByteBuf buffer,
            TrafficHandedness handedness
    ) {
        buffer.writeByte(switch (handedness) {
            case RHT -> 0;
            case LHT -> 1;
        });
    }

    private static TrafficHandedness readHandedness(FriendlyByteBuf buffer) {
        return switch (buffer.readUnsignedByte()) {
            case 0 -> TrafficHandedness.RHT;
            case 1 -> TrafficHandedness.LHT;
            default -> throw new IllegalArgumentException("unknown traffic handedness wire ID");
        };
    }

    private static int readCount(FriendlyByteBuf buffer, int maximum, String name) {
        int count = buffer.readVarInt();
        requireCount(count, maximum, name);
        return count;
    }

    private static int readFormatVersion(FriendlyByteBuf buffer) {
        int start = buffer.readerIndex();
        int marker = buffer.readVarInt();
        if (marker == FORMAT_V3_MARKER) {
            return 3;
        }
        if (marker == FORMAT_V2_MARKER) {
            return 2;
        }
        buffer.readerIndex(start);
        return 1;
    }

    private static void writeRoadIds(FriendlyByteBuf buffer, List<RoadId> ids) {
        buffer.writeVarInt(ids.size());
        ids.forEach(id -> buffer.writeUUID(id.value()));
    }

    private static void writeNodeIds(FriendlyByteBuf buffer, List<RoadNodeId> ids) {
        buffer.writeVarInt(ids.size());
        ids.forEach(id -> buffer.writeUUID(id.value()));
    }

    private static void writeSegmentIds(FriendlyByteBuf buffer, List<RoadSegmentId> ids) {
        buffer.writeVarInt(ids.size());
        ids.forEach(id -> buffer.writeUUID(id.value()));
    }

    private static void writeMovement(
            FriendlyByteBuf buffer,
            LaneConnectorMovement movement
    ) {
        buffer.writeByte(movement.type().ordinal());
        buffer.writeBoolean(movement.allowed());
        buffer.writeBoolean(movement.manual());
        buffer.writeByte(movement.priority());
    }

    private static LaneConnectorMovement readMovement(
            FriendlyByteBuf buffer
    ) {
        int typeId = buffer.readUnsignedByte();
        LaneMovementType[] types = LaneMovementType.values();
        if (typeId >= types.length) {
            throw new IllegalArgumentException(
                    "unknown lane movement type " + typeId
            );
        }
        boolean allowed = buffer.readBoolean();
        boolean manual = buffer.readBoolean();
        int priority = buffer.readUnsignedByte();
        return new LaneConnectorMovement(
                types[typeId],
                allowed,
                manual,
                priority
        );
    }

    private static void writeClassification(
            FriendlyByteBuf buffer,
            RoadClassification classification
    ) {
        buffer.writeByte(classification.roadClass().ordinal());
        buffer.writeByte(classification.priorityRank());
        buffer.writeDouble(classification.nominalSpeed());
    }

    private static RoadClassification readClassification(
            FriendlyByteBuf buffer
    ) {
        int classId = buffer.readUnsignedByte();
        RoadClass[] classes = RoadClass.values();
        if (classId >= classes.length) {
            throw new IllegalArgumentException("unknown road class " + classId);
        }
        return new RoadClassification(
                classes[classId],
                buffer.readUnsignedByte(),
                buffer.readDouble()
        );
    }

    private static void writeIntersectionStyle(
            FriendlyByteBuf buffer,
            RoadIntersectionStyle style
    ) {
        PacketValidation.writeBoundedId(buffer, style.pavementBlock());
        writeOptionalId(buffer, style.markingBlock());
        writeOptionalId(buffer, style.crosswalkBlock());
        writeOptionalId(buffer, style.stopLineBlock());
        buffer.writeBoolean(style.defaultCrosswalks());
        buffer.writeByte(style.defaultControl().ordinal());
    }

    private static RoadIntersectionStyle readIntersectionStyle(
            FriendlyByteBuf buffer
    ) {
        ResourceLocation pavement = PacketValidation.readBoundedId(
                buffer,
                "junctionPavementBlock"
        );
        java.util.Optional<ResourceLocation> marking = readOptionalId(
                buffer,
                "junctionMarkingBlock"
        );
        java.util.Optional<ResourceLocation> crosswalk = readOptionalId(
                buffer,
                "junctionCrosswalkBlock"
        );
        java.util.Optional<ResourceLocation> stopLine = readOptionalId(
                buffer,
                "junctionStopLineBlock"
        );
        boolean defaultCrosswalks = buffer.readBoolean();
        int controlId = buffer.readUnsignedByte();
        IntersectionControl[] controls = IntersectionControl.values();
        if (controlId >= controls.length) {
            throw new IllegalArgumentException(
                    "unknown intersection control " + controlId
            );
        }
        return new RoadIntersectionStyle(
                pavement,
                marking,
                crosswalk,
                stopLine,
                defaultCrosswalks,
                controls[controlId]
        );
    }

    private static void writeOptionalId(
            FriendlyByteBuf buffer,
            java.util.Optional<ResourceLocation> value
    ) {
        buffer.writeBoolean(value.isPresent());
        value.ifPresent(id -> PacketValidation.writeBoundedId(buffer, id));
    }

    private static java.util.Optional<ResourceLocation> readOptionalId(
            FriendlyByteBuf buffer,
            String name
    ) {
        return buffer.readBoolean()
                ? java.util.Optional.of(
                        PacketValidation.readBoundedId(buffer, name)
                )
                : java.util.Optional.empty();
    }

    private static void writeJunctionDefinition(
            FriendlyByteBuf buffer,
            JunctionDefinition definition
    ) {
        buffer.writeUUID(definition.nodeId().value());
        buffer.writeByte(definition.strategy().ordinal());
        buffer.writeByte(definition.priorityPolicy().ordinal());
        buffer.writeVarInt(definition.primaryApproachKeys().size());
        definition.primaryApproachKeys().forEach(key ->
                writeApproachKey(buffer, key));
        buffer.writeByte(definition.crossingPolicy().ordinal());
        buffer.writeVarInt(definition.approachOverrides().size());
        definition.approachOverrides().forEach((key, override) -> {
            writeApproachKey(buffer, key);
            buffer.writeByte(override.control().ordinal());
            buffer.writeBoolean(override.crosswalk());
        });
        buffer.writeVarLong(definition.revision());
    }

    private static JunctionDefinition readJunctionDefinition(
            FriendlyByteBuf buffer
    ) {
        RoadNodeId nodeId = new RoadNodeId(buffer.readUUID());
        JunctionStrategy strategy = readEnum(
                buffer,
                JunctionStrategy.values(),
                "junction strategy"
        );
        JunctionPriorityPolicy priority = readEnum(
                buffer,
                JunctionPriorityPolicy.values(),
                "junction priority policy"
        );
        int primaryCount = readCount(
                buffer,
                JunctionDefinition.MAXIMUM_APPROACH_COUNT,
                "primary junction approach"
        );
        List<ApproachKey> primary = new ArrayList<>(primaryCount);
        for (int index = 0; index < primaryCount; index++) {
            primary.add(readApproachKey(buffer));
        }
        JunctionCrossingPolicy crossing = readEnum(
                buffer,
                JunctionCrossingPolicy.values(),
                "junction crossing policy"
        );
        int overrideCount = readCount(
                buffer,
                JunctionDefinition.MAXIMUM_APPROACH_COUNT,
                "junction approach override"
        );
        Map<ApproachKey, JunctionApproachOverride> overrides =
                new LinkedHashMap<>(overrideCount);
        for (int index = 0; index < overrideCount; index++) {
            ApproachKey key = readApproachKey(buffer);
            IntersectionControl control = readEnum(
                    buffer,
                    IntersectionControl.values(),
                    "intersection control"
            );
            if (overrides.put(
                    key,
                    new JunctionApproachOverride(
                            control,
                            buffer.readBoolean()
                    )
            ) != null) {
                throw new IllegalArgumentException(
                        "duplicate junction approach override"
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
                buffer.readVarLong()
        );
    }

    private static void writeApproachKey(
            FriendlyByteBuf buffer,
            ApproachKey key
    ) {
        buffer.writeUUID(key.segmentId().value());
        buffer.writeByte(key.end().ordinal());
    }

    private static ApproachKey readApproachKey(FriendlyByteBuf buffer) {
        return new ApproachKey(
                new RoadSegmentId(buffer.readUUID()),
                readEnum(buffer, ApproachEnd.values(), "approach end")
        );
    }

    private static <E extends Enum<E>> E readEnum(
            FriendlyByteBuf buffer,
            E[] values,
            String name
    ) {
        int id = buffer.readUnsignedByte();
        if (id >= values.length) {
            throw new IllegalArgumentException("unknown " + name + " " + id);
        }
        return values[id];
    }

    private static void writeConnectorIds(
            FriendlyByteBuf buffer,
            List<LaneConnectorId> ids
    ) {
        buffer.writeVarInt(ids.size());
        ids.forEach(id -> buffer.writeUUID(id.value()));
    }

    private static <T> List<T> readIds(
            FriendlyByteBuf buffer,
            int maximum,
            String name,
            java.util.function.Supplier<T> decoder
    ) {
        int count = readCount(buffer, maximum, name);
        List<T> ids = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            T id = decoder.get();
            if (ids.contains(id)) {
                throw new IllegalArgumentException("duplicate " + name + " ID " + id);
            }
            ids.add(id);
        }
        return List.copyOf(ids);
    }

    private static <K, V> void putUnique(
            Map<K, V> values,
            K id,
            V value,
            String name
    ) {
        if (values.put(id, value) != null) {
            throw new IllegalArgumentException("duplicate " + name + " ID " + id);
        }
    }

    private static void requireCount(int count, int maximum, String name) {
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException(
                    name + " count must be between 0 and " + maximum
            );
        }
    }

    private static double readCoordinate(FriendlyByteBuf buffer, String name) {
        double coordinate = buffer.readDouble();
        requireCoordinate(coordinate, name);
        return coordinate;
    }

    private static void requireCoordinate(double coordinate, String name) {
        if (!Double.isFinite(coordinate) || Math.abs(coordinate) > MAX_ABSOLUTE_COORDINATE) {
            throw new IllegalArgumentException(
                    name + " must be finite and within the Minecraft coordinate limit"
            );
        }
    }

    private record DecodedSegment(
            RoadSegmentId id,
            RoadId roadId,
            RoadNodeId startNodeId,
            RoadNodeId endNodeId,
            ReferenceGeometry geometry,
            ResourceLocation presetId,
            int presetRevision,
            RoadPresetSnapshot presetSnapshot,
            List<RoadLaneId> laneIds
    ) {
    }
}
