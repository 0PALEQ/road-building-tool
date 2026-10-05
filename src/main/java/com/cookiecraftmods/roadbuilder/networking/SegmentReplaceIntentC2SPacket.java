package com.cookiecraftmods.roadbuilder.networking;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

public record SegmentReplaceIntentC2SPacket(
        int protocolVersion,
        UUID requestId,
        ResourceLocation dimensionId,
        UUID segmentId,
        long expectedGraphRevision,
        ResourceLocation presetId,
        int expectedPresetRevision
) implements FabricPacket {
    public static final int CURRENT_PROTOCOL_VERSION = PacketValidation.PROTOCOL_VERSION;
    public static final int MAX_PAYLOAD_BYTES = 256;
    public static final PacketType<SegmentReplaceIntentC2SPacket> TYPE =
            PacketType.create(
                    new ResourceLocation("roadbuilder", "segment_replace_intent_v1"),
                    SegmentReplaceIntentC2SPacket::new
            );

    public SegmentReplaceIntentC2SPacket {
        PacketValidation.requireProtocolVersion(protocolVersion);
        Objects.requireNonNull(requestId, "requestId");
        PacketValidation.requireBoundedId(dimensionId, "dimensionId");
        Objects.requireNonNull(segmentId, "segmentId");
        if (expectedGraphRevision < 0) {
            throw new IllegalArgumentException("expectedGraphRevision must be non-negative");
        }
        PacketValidation.requireBoundedId(presetId, "presetId");
        if (expectedPresetRevision < 1) {
            throw new IllegalArgumentException("expectedPresetRevision must be at least 1");
        }
    }

    public SegmentReplaceIntentC2SPacket(FriendlyByteBuf buffer) {
        this(decode(buffer));
    }

    public static SegmentReplaceIntentC2SPacket current(
            UUID requestId,
            ResourceLocation dimensionId,
            UUID segmentId,
            long expectedGraphRevision,
            ResourceLocation presetId,
            int expectedPresetRevision
    ) {
        return new SegmentReplaceIntentC2SPacket(
                CURRENT_PROTOCOL_VERSION,
                requestId,
                dimensionId,
                segmentId,
                expectedGraphRevision,
                presetId,
                expectedPresetRevision
        );
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(protocolVersion);
        buffer.writeUUID(requestId);
        PacketValidation.writeBoundedId(buffer, dimensionId);
        buffer.writeUUID(segmentId);
        buffer.writeVarLong(expectedGraphRevision);
        PacketValidation.writeBoundedId(buffer, presetId);
        buffer.writeVarInt(expectedPresetRevision);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }

    private SegmentReplaceIntentC2SPacket(WireFields fields) {
        this(
                fields.protocolVersion,
                fields.requestId,
                fields.dimensionId,
                fields.segmentId,
                fields.expectedGraphRevision,
                fields.presetId,
                fields.expectedPresetRevision
        );
    }

    private static WireFields decode(FriendlyByteBuf buffer) {
        PacketValidation.requirePayloadSize(buffer, MAX_PAYLOAD_BYTES, "segment replace intent");
        WireFields result = new WireFields(
                buffer.readVarInt(),
                buffer.readUUID(),
                PacketValidation.readBoundedId(buffer, "dimensionId"),
                buffer.readUUID(),
                buffer.readVarLong(),
                PacketValidation.readBoundedId(buffer, "presetId"),
                buffer.readVarInt()
        );
        PacketValidation.requireFullyRead(buffer, "segment replace intent");
        return result;
    }

    private record WireFields(
            int protocolVersion,
            UUID requestId,
            ResourceLocation dimensionId,
            UUID segmentId,
            long expectedGraphRevision,
            ResourceLocation presetId,
            int expectedPresetRevision
    ) { }
}
