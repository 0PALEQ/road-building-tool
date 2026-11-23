package com.cookiecraftmods.roadbuilder.networking;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

public record StraightRoadIntentC2SPacket(
        int protocolVersion,
        UUID requestId,
        ResourceLocation dimensionId,
        ResourceLocation presetId,
        int expectedPresetRevision,
        long expectedGraphRevision,
        double startX,
        double startY,
        double startZ,
        double endX,
        double endY,
        double endZ
) implements RoadPacket {
    public static final int CURRENT_PROTOCOL_VERSION = PacketValidation.PROTOCOL_VERSION;
    public static final int MAX_ID_CHARACTERS = PacketValidation.MAX_RESOURCE_ID_CHARACTERS;
    public static final double MAX_ABSOLUTE_COORDINATE = 30_000_000.0;
    public static final int MAX_PAYLOAD_BYTES = 384;

    public static final RoadPacketType<StraightRoadIntentC2SPacket> TYPE = RoadPacketType.create(
            MinecraftIds.id("roadbuilder", "straight_road_intent_v1"),
            StraightRoadIntentC2SPacket::new
    );

    public StraightRoadIntentC2SPacket {
        PacketValidation.requireProtocolVersion(protocolVersion);
        Objects.requireNonNull(requestId, "requestId");
        PacketValidation.requireBoundedId(dimensionId, "dimensionId");
        PacketValidation.requireBoundedId(presetId, "presetId");
        if (expectedPresetRevision <= 0) {
            throw new IllegalArgumentException("expectedPresetRevision must be greater than zero");
        }
        if (expectedGraphRevision < -1L) {
            throw new IllegalArgumentException(
                    "expectedGraphRevision must be non-negative or -1"
            );
        }
        requireCoordinate(startX, "startX");
        requireCoordinate(startY, "startY");
        requireCoordinate(startZ, "startZ");
        requireCoordinate(endX, "endX");
        requireCoordinate(endY, "endY");
        requireCoordinate(endZ, "endZ");
    }

    public StraightRoadIntentC2SPacket(FriendlyByteBuf buffer) {
        this(decode(buffer));
    }

        public StraightRoadIntentC2SPacket(
            int protocolVersion,
            UUID requestId,
            ResourceLocation dimensionId,
            ResourceLocation presetId,
            int expectedPresetRevision,
            double startX,
            double startY,
            double startZ,
            double endX,
            double endY,
            double endZ
    ) {
        this(
                protocolVersion,
                requestId,
                dimensionId,
                presetId,
                expectedPresetRevision,
                -1L,
                startX,
                startY,
                startZ,
                endX,
                endY,
                endZ
        );
    }

    public static StraightRoadIntentC2SPacket current(
            UUID requestId,
            ResourceLocation dimensionId,
            ResourceLocation presetId,
            int expectedPresetRevision,
            long expectedGraphRevision,
            double startX,
            double startY,
            double startZ,
            double endX,
            double endY,
            double endZ
    ) {
        return new StraightRoadIntentC2SPacket(
                CURRENT_PROTOCOL_VERSION,
                requestId,
                dimensionId,
                presetId,
                expectedPresetRevision,
                expectedGraphRevision,
                startX,
                startY,
                startZ,
                endX,
                endY,
                endZ
        );
    }

    public static StraightRoadIntentC2SPacket current(
            UUID requestId,
            ResourceLocation dimensionId,
            ResourceLocation presetId,
            int expectedPresetRevision,
            double startX,
            double startY,
            double startZ,
            double endX,
            double endY,
            double endZ
    ) {
        return current(
                requestId,
                dimensionId,
                presetId,
                expectedPresetRevision,
                -1L,
                startX,
                startY,
                startZ,
                endX,
                endY,
                endZ
        );
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        buffer.writeVarInt(protocolVersion);
        buffer.writeUUID(requestId);
        PacketValidation.writeBoundedId(buffer, dimensionId);
        PacketValidation.writeBoundedId(buffer, presetId);
        buffer.writeVarInt(expectedPresetRevision);
        buffer.writeVarLong(expectedGraphRevision);
        buffer.writeDouble(startX);
        buffer.writeDouble(startY);
        buffer.writeDouble(startZ);
        buffer.writeDouble(endX);
        buffer.writeDouble(endY);
        buffer.writeDouble(endZ);
    }

    @Override
    public RoadPacketType<?> packetType() {
        return TYPE;
    }

    private StraightRoadIntentC2SPacket(WireFields fields) {
        this(
                fields.protocolVersion,
                fields.requestId,
                fields.dimensionId,
                fields.presetId,
                fields.expectedPresetRevision,
                fields.expectedGraphRevision,
                fields.startX,
                fields.startY,
                fields.startZ,
                fields.endX,
                fields.endY,
                fields.endZ
        );
    }

    private static WireFields decode(FriendlyByteBuf buffer) {
        PacketValidation.requirePayloadSize(buffer, MAX_PAYLOAD_BYTES, "straight road intent");
        WireFields fields = new WireFields(
                buffer.readVarInt(),
                buffer.readUUID(),
                PacketValidation.readBoundedId(buffer, "dimensionId"),
                PacketValidation.readBoundedId(buffer, "presetId"),
                buffer.readVarInt(),
                buffer.readVarLong(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble()
        );
        PacketValidation.requireFullyRead(buffer, "straight road intent");
        return fields;
    }

    private static void requireCoordinate(double coordinate, String fieldName) {
        if (!Double.isFinite(coordinate) || Math.abs(coordinate) > MAX_ABSOLUTE_COORDINATE) {
            throw new IllegalArgumentException(
                    fieldName + " must be finite and between -" + MAX_ABSOLUTE_COORDINATE
                            + " and " + MAX_ABSOLUTE_COORDINATE
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
            double startX,
            double startY,
            double startZ,
            double endX,
            double endY,
            double endZ
    ) {
    }
}
