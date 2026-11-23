package com.cookiecraftmods.roadbuilder.networking;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import com.cookiecraftmods.roadbuilder.geometry.CubicBezierGeometry;
import com.cookiecraftmods.roadbuilder.geometry.FreehandGeometry;
import com.cookiecraftmods.roadbuilder.geometry.MultiCurveGeometry;
import com.cookiecraftmods.roadbuilder.geometry.QuadraticBezierGeometry;
import com.cookiecraftmods.roadbuilder.geometry.ReferenceGeometry;
import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.RoundaboutGeometry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record RoadPathIntentC2SPacket(
        int protocolVersion,
        UUID requestId,
        ResourceLocation dimensionId,
        ResourceLocation presetId,
        int expectedPresetRevision,
        long expectedGraphRevision,
        PathType pathType,
        List<RoadPoint> points,
        double parallelOffset,
        boolean sunkenRoad
) implements RoadPacket {
    public static final int MAXIMUM_POINTS = 64;
    public static final double MAXIMUM_CONTROL_POLYGON_LENGTH = 8_192.0;
    public static final int MAX_PAYLOAD_BYTES = 2_048;
    public static final RoadPacketType<RoadPathIntentC2SPacket> TYPE =
            RoadPacketType.create(
                    MinecraftIds.id(
                            "roadbuilder",
                            "road_path_intent_v3"
                    ),
                    RoadPathIntentC2SPacket::new
            );

    public RoadPathIntentC2SPacket {
        PacketValidation.requireProtocolVersion(protocolVersion);
        Objects.requireNonNull(requestId, "requestId");
        PacketValidation.requireBoundedId(dimensionId, "dimensionId");
        PacketValidation.requireBoundedId(presetId, "presetId");
        if (expectedPresetRevision < 1) {
            throw new IllegalArgumentException(
                    "expectedPresetRevision must be positive"
            );
        }
        if (expectedGraphRevision < -1L) {
            throw new IllegalArgumentException(
                    "expectedGraphRevision must be non-negative or -1"
            );
        }
        Objects.requireNonNull(pathType, "pathType");
        points = List.copyOf(Objects.requireNonNull(points, "points"));
        pathType.validateCount(points.size());
        for (RoadPoint point : points) {
            requirePoint(Objects.requireNonNull(
                    point,
                    "points contains null"
            ));
        }
        requireBoundedControlPolygon(points);
        // Construct once at the packet boundary to reject cusps, vertical
        // paths, and other invalid control arrangements before sending.
        var primary = geometry(pathType, points);
        if (parallelOffset != 0) {
            var parallel = com.cookiecraftmods.roadbuilder.geometry.ParallelRoadGeometry.create(primary, parallelOffset);
            if (parallel instanceof MultiCurveGeometry multi) {
                multi.anchors().forEach(RoadPathIntentC2SPacket::requirePoint);
                requireBoundedControlPolygon(multi.anchors());
            }
            requirePoint(parallel.start());
            requirePoint(parallel.end());
        }
    }

    public RoadPathIntentC2SPacket(int protocolVersion, UUID requestId, ResourceLocation dimensionId,
            ResourceLocation presetId, int expectedPresetRevision, long expectedGraphRevision,
            PathType pathType, List<RoadPoint> points) {
        this(protocolVersion, requestId, dimensionId, presetId, expectedPresetRevision,
                expectedGraphRevision, pathType, points, 0);
    }

    public RoadPathIntentC2SPacket(int protocolVersion, UUID requestId, ResourceLocation dimensionId,
            ResourceLocation presetId, int expectedPresetRevision, long expectedGraphRevision,
            PathType pathType, List<RoadPoint> points, double parallelOffset) {
        this(protocolVersion, requestId, dimensionId, presetId, expectedPresetRevision,
                expectedGraphRevision, pathType, points, parallelOffset, false);
    }

    public RoadPathIntentC2SPacket withSunkenRoad(boolean enabled) {
        return new RoadPathIntentC2SPacket(protocolVersion, requestId, dimensionId, presetId,
                expectedPresetRevision, expectedGraphRevision, pathType, points, parallelOffset, enabled);
    }

    public RoadPathIntentC2SPacket withParallelOffset(double offset) {
        return new RoadPathIntentC2SPacket(protocolVersion, requestId, dimensionId, presetId,
                expectedPresetRevision, expectedGraphRevision, pathType, points, offset, sunkenRoad);
    }

    public RoadPathIntentC2SPacket(FriendlyByteBuf buffer) {
        this(decode(buffer));
    }

    public RoadPathIntentC2SPacket(
            int protocolVersion,
            UUID requestId,
            ResourceLocation dimensionId,
            ResourceLocation presetId,
            int expectedPresetRevision,
            PathType pathType,
            List<RoadPoint> points
    ) {
        this(
                protocolVersion,
                requestId,
                dimensionId,
                presetId,
                expectedPresetRevision,
                -1L,
                pathType,
                points
        );
    }

    public static RoadPathIntentC2SPacket current(
            UUID requestId,
            ResourceLocation dimensionId,
            ResourceLocation presetId,
            int expectedPresetRevision,
            long expectedGraphRevision,
            PathType pathType,
            List<RoadPoint> points
    ) {
        return new RoadPathIntentC2SPacket(
                PacketValidation.PROTOCOL_VERSION,
                requestId,
                dimensionId,
                presetId,
                expectedPresetRevision,
                expectedGraphRevision,
                pathType,
                points
        );
    }

    public static RoadPathIntentC2SPacket current(
            UUID requestId,
            ResourceLocation dimensionId,
            ResourceLocation presetId,
            int expectedPresetRevision,
            PathType pathType,
            List<RoadPoint> points
    ) {
        return current(
                requestId,
                dimensionId,
                presetId,
                expectedPresetRevision,
                -1L,
                pathType,
                points
        );
    }

    public ReferenceGeometry geometry() {
        return geometry(pathType, points);
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(protocolVersion);
        buffer.writeUUID(requestId);
        PacketValidation.writeBoundedId(buffer, dimensionId);
        PacketValidation.writeBoundedId(buffer, presetId);
        buffer.writeVarInt(expectedPresetRevision);
        buffer.writeVarLong(expectedGraphRevision);
        buffer.writeByte(pathType.wireId);
        buffer.writeVarInt(points.size());
        for (RoadPoint point : points) {
            buffer.writeDouble(point.x());
            buffer.writeDouble(point.y());
            buffer.writeDouble(point.z());
        }
        buffer.writeDouble(parallelOffset);
        buffer.writeBoolean(sunkenRoad);
    }

    @Override
    public RoadPacketType<?> packetType() {
        return TYPE;
    }

    private RoadPathIntentC2SPacket(WireFields fields) {
        this(
                fields.protocolVersion,
                fields.requestId,
                fields.dimensionId,
                fields.presetId,
                fields.expectedPresetRevision,
                fields.expectedGraphRevision,
                fields.pathType,
                fields.points,
                fields.parallelOffset,
                fields.sunkenRoad
        );
    }

    private static WireFields decode(FriendlyByteBuf buffer) {
        PacketValidation.requirePayloadSize(
                buffer,
                MAX_PAYLOAD_BYTES,
                "road path intent"
        );
        int protocol = buffer.readVarInt();
        UUID requestId = buffer.readUUID();
        ResourceLocation dimension =
                PacketValidation.readBoundedId(buffer, "dimensionId");
        ResourceLocation preset =
                PacketValidation.readBoundedId(buffer, "presetId");
        int revision = buffer.readVarInt();
        long graphRevision = buffer.readVarLong();
        PathType type = PathType.fromWire(buffer.readUnsignedByte());
        int count = buffer.readVarInt();
        type.validateCount(count);
        List<RoadPoint> points = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            RoadPoint point = new RoadPoint(
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble()
            );
            requirePoint(point);
            points.add(point);
        }
        double offset = buffer.readDouble();
        boolean sunkenRoad = buffer.readBoolean();
        PacketValidation.requireFullyRead(buffer, "road path intent");
        return new WireFields(
                protocol,
                requestId,
                dimension,
                preset,
                revision,
                graphRevision,
                type,
                List.copyOf(points),
                offset,
                sunkenRoad
        );
    }

    private static ReferenceGeometry geometry(
            PathType type,
            List<RoadPoint> points
    ) {
        return switch (type) {
            case STRAIGHT -> new com.cookiecraftmods.roadbuilder.geometry.StraightGeometry(points.get(0), points.get(1));
            case QUADRATIC -> new QuadraticBezierGeometry(
                    points.get(0), points.get(1), points.get(2)
            );
            case CUBIC -> new CubicBezierGeometry(
                    points.get(0),
                    points.get(1),
                    points.get(2),
                    points.get(3)
            );
            case MULTI -> new MultiCurveGeometry(points);
            case FREEHAND -> new FreehandGeometry(points);
            case ROUNDABOUT -> new RoundaboutGeometry(
                    points.get(0),
                    points.get(1)
            );
        };
    }

    private static void requirePoint(RoadPoint point) {
        if (Math.abs(point.x()) > StraightRoadIntentC2SPacket.MAX_ABSOLUTE_COORDINATE
                || Math.abs(point.y()) > StraightRoadIntentC2SPacket.MAX_ABSOLUTE_COORDINATE
                || Math.abs(point.z()) > StraightRoadIntentC2SPacket.MAX_ABSOLUTE_COORDINATE) {
            throw new IllegalArgumentException(
                    "path point exceeds the coordinate limit"
            );
        }
    }

    private static void requireBoundedControlPolygon(List<RoadPoint> points) {
        double length = 0.0;
        for (int index = 1; index < points.size(); index++) {
            length += points.get(index - 1).distanceTo(points.get(index));
            if (!Double.isFinite(length)
                    || length > MAXIMUM_CONTROL_POLYGON_LENGTH) {
                throw new IllegalArgumentException(
                        "path control polygon exceeds "
                                + MAXIMUM_CONTROL_POLYGON_LENGTH
                                + " blocks"
                );
            }
        }
    }

    public enum PathType {
        QUADRATIC(0, 3, 3),
        CUBIC(1, 4, 4),
        MULTI(2, 3, MAXIMUM_POINTS),
        FREEHAND(3, 2, MAXIMUM_POINTS),
        ROUNDABOUT(4, 2, 2),
        STRAIGHT(5, 2, 2);

        private final int wireId;
        private final int minimumPoints;
        private final int maximumPoints;

        PathType(int wireId, int minimumPoints, int maximumPoints) {
            this.wireId = wireId;
            this.minimumPoints = minimumPoints;
            this.maximumPoints = maximumPoints;
        }

        private void validateCount(int count) {
            if (count < minimumPoints || count > maximumPoints) {
                throw new IllegalArgumentException(
                        name() + " requires between " + minimumPoints
                                + " and " + maximumPoints + " points"
                );
            }
        }

        private static PathType fromWire(int wireId) {
            for (PathType type : values()) {
                if (type.wireId == wireId) {
                    return type;
                }
            }
            throw new IllegalArgumentException(
                    "unknown road path type " + wireId
            );
        }
    }

    private record WireFields(
            int protocolVersion,
            UUID requestId,
            ResourceLocation dimensionId,
            ResourceLocation presetId,
            int expectedPresetRevision,
            long expectedGraphRevision,
            PathType pathType,
            List<RoadPoint> points,
            double parallelOffset,
            boolean sunkenRoad
    ) {
    }
}
