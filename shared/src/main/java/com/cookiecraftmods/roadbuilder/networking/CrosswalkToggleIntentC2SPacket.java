package com.cookiecraftmods.roadbuilder.networking;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

public record CrosswalkToggleIntentC2SPacket(
        int protocolVersion,
        UUID requestId,
        ResourceLocation dimensionId,
        UUID segmentId,
        UUID nodeId,
        long expectedGraphRevision
) implements RoadPacket {
    public static final int CURRENT_PROTOCOL_VERSION =
            PacketValidation.PROTOCOL_VERSION;
    public static final int MAX_PAYLOAD_BYTES = 224;
    public static final RoadPacketType<CrosswalkToggleIntentC2SPacket> TYPE =
            RoadPacketType.create(
                    MinecraftIds.id(
                            "roadbuilder",
                            "crosswalk_toggle_intent_v1"
                    ),
                    CrosswalkToggleIntentC2SPacket::new
            );

    public CrosswalkToggleIntentC2SPacket {
        PacketValidation.requireProtocolVersion(protocolVersion);
        Objects.requireNonNull(requestId, "requestId");
        PacketValidation.requireBoundedId(dimensionId, "dimensionId");
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(nodeId, "nodeId");
        if (expectedGraphRevision < 0) {
            throw new IllegalArgumentException(
                    "expectedGraphRevision must be non-negative"
            );
        }
    }

    public CrosswalkToggleIntentC2SPacket(FriendlyByteBuf buffer) {
        this(decode(buffer));
    }

    public static CrosswalkToggleIntentC2SPacket current(
            UUID requestId,
            ResourceLocation dimensionId,
            UUID segmentId,
            UUID nodeId,
            long expectedGraphRevision
    ) {
        return new CrosswalkToggleIntentC2SPacket(
                CURRENT_PROTOCOL_VERSION,
                requestId,
                dimensionId,
                segmentId,
                nodeId,
                expectedGraphRevision
        );
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(protocolVersion);
        buffer.writeUUID(requestId);
        PacketValidation.writeBoundedId(buffer, dimensionId);
        buffer.writeUUID(segmentId);
        buffer.writeUUID(nodeId);
        buffer.writeVarLong(expectedGraphRevision);
    }

    @Override
    public RoadPacketType<?> packetType() {
        return TYPE;
    }

    private CrosswalkToggleIntentC2SPacket(WireFields fields) {
        this(
                fields.protocolVersion(),
                fields.requestId(),
                fields.dimensionId(),
                fields.segmentId(),
                fields.nodeId(),
                fields.expectedGraphRevision()
        );
    }

    private static WireFields decode(FriendlyByteBuf buffer) {
        PacketValidation.requirePayloadSize(
                buffer,
                MAX_PAYLOAD_BYTES,
                "crosswalk toggle intent"
        );
        WireFields result = new WireFields(
                buffer.readVarInt(),
                buffer.readUUID(),
                PacketValidation.readBoundedId(buffer, "dimensionId"),
                buffer.readUUID(),
                buffer.readUUID(),
                buffer.readVarLong()
        );
        PacketValidation.requireFullyRead(buffer, "crosswalk toggle intent");
        return result;
    }

    private record WireFields(
            int protocolVersion,
            UUID requestId,
            ResourceLocation dimensionId,
            UUID segmentId,
            UUID nodeId,
            long expectedGraphRevision
    ) {
    }
}
