package com.cookiecraftmods.roadbuilder.networking;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

public record SegmentDirectionInvertIntentC2SPacket(
        int protocolVersion,
        UUID requestId,
        ResourceLocation dimensionId,
        UUID segmentId,
        long expectedGraphRevision
) implements FabricPacket {
    public static final int CURRENT_PROTOCOL_VERSION =
            PacketValidation.PROTOCOL_VERSION;
    public static final int MAX_PAYLOAD_BYTES = 192;
    public static final PacketType<SegmentDirectionInvertIntentC2SPacket> TYPE =
            PacketType.create(
                    new ResourceLocation(
                            "roadbuilder",
                            "segment_direction_invert_intent_v1"
                    ),
                    SegmentDirectionInvertIntentC2SPacket::new
            );

    public SegmentDirectionInvertIntentC2SPacket {
        PacketValidation.requireProtocolVersion(protocolVersion);
        Objects.requireNonNull(requestId, "requestId");
        PacketValidation.requireBoundedId(dimensionId, "dimensionId");
        Objects.requireNonNull(segmentId, "segmentId");
        if (expectedGraphRevision < 0) {
            throw new IllegalArgumentException(
                    "expectedGraphRevision must be non-negative"
            );
        }
    }

    public SegmentDirectionInvertIntentC2SPacket(FriendlyByteBuf buffer) {
        this(decode(buffer));
    }

    public static SegmentDirectionInvertIntentC2SPacket current(
            UUID requestId,
            ResourceLocation dimensionId,
            UUID segmentId,
            long expectedGraphRevision
    ) {
        return new SegmentDirectionInvertIntentC2SPacket(
                CURRENT_PROTOCOL_VERSION,
                requestId,
                dimensionId,
                segmentId,
                expectedGraphRevision
        );
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(protocolVersion);
        buffer.writeUUID(requestId);
        PacketValidation.writeBoundedId(buffer, dimensionId);
        buffer.writeUUID(segmentId);
        buffer.writeVarLong(expectedGraphRevision);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }

    private SegmentDirectionInvertIntentC2SPacket(WireFields fields) {
        this(
                fields.protocolVersion(),
                fields.requestId(),
                fields.dimensionId(),
                fields.segmentId(),
                fields.expectedGraphRevision()
        );
    }

    private static WireFields decode(FriendlyByteBuf buffer) {
        PacketValidation.requirePayloadSize(
                buffer,
                MAX_PAYLOAD_BYTES,
                "segment direction invert intent"
        );
        WireFields fields = new WireFields(
                buffer.readVarInt(),
                buffer.readUUID(),
                PacketValidation.readBoundedId(buffer, "dimensionId"),
                buffer.readUUID(),
                buffer.readVarLong()
        );
        PacketValidation.requireFullyRead(
                buffer,
                "segment direction invert intent"
        );
        return fields;
    }

    private record WireFields(
            int protocolVersion,
            UUID requestId,
            ResourceLocation dimensionId,
            UUID segmentId,
            long expectedGraphRevision
    ) {
    }
}
