package com.cookiecraftmods.roadbuilder.networking;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

public record LaneConnectorEditC2SPacket(
        int protocolVersion,
        ResourceLocation dimensionId,
        long expectedGraphRevision,
        UUID fromLaneId,
        UUID toLaneId
) implements FabricPacket {
    public static final int CURRENT_PROTOCOL_VERSION =
            PacketValidation.PROTOCOL_VERSION;
    public static final int MAX_PAYLOAD_BYTES = 192;
    public static final PacketType<LaneConnectorEditC2SPacket> TYPE =
            PacketType.create(
                    new ResourceLocation(
                            "roadbuilder",
                            "lane_connector_edit_v1"
                    ),
                    LaneConnectorEditC2SPacket::new
            );

    public LaneConnectorEditC2SPacket {
        PacketValidation.requireProtocolVersion(protocolVersion);
        PacketValidation.requireBoundedId(dimensionId, "dimensionId");
        if (expectedGraphRevision < 0) {
            throw new IllegalArgumentException(
                    "expectedGraphRevision must be non-negative"
            );
        }
        Objects.requireNonNull(fromLaneId, "fromLaneId");
        Objects.requireNonNull(toLaneId, "toLaneId");
        if (fromLaneId.equals(toLaneId)) {
            throw new IllegalArgumentException(
                    "lane connector endpoints must be different"
            );
        }
    }

    public LaneConnectorEditC2SPacket(FriendlyByteBuf buffer) {
        this(decode(buffer));
    }

    public static LaneConnectorEditC2SPacket current(
            ResourceLocation dimensionId,
            long expectedGraphRevision,
            UUID fromLaneId,
            UUID toLaneId
    ) {
        return new LaneConnectorEditC2SPacket(
                CURRENT_PROTOCOL_VERSION,
                dimensionId,
                expectedGraphRevision,
                fromLaneId,
                toLaneId
        );
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(protocolVersion);
        PacketValidation.writeBoundedId(buffer, dimensionId);
        buffer.writeVarLong(expectedGraphRevision);
        buffer.writeUUID(fromLaneId);
        buffer.writeUUID(toLaneId);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }

    private LaneConnectorEditC2SPacket(WireFields fields) {
        this(
                fields.protocolVersion,
                fields.dimensionId,
                fields.expectedGraphRevision,
                fields.fromLaneId,
                fields.toLaneId
        );
    }

    private static WireFields decode(FriendlyByteBuf buffer) {
        PacketValidation.requirePayloadSize(
                buffer,
                MAX_PAYLOAD_BYTES,
                "lane connector edit"
        );
        WireFields fields = new WireFields(
                buffer.readVarInt(),
                PacketValidation.readBoundedId(buffer, "dimensionId"),
                buffer.readVarLong(),
                buffer.readUUID(),
                buffer.readUUID()
        );
        PacketValidation.requireFullyRead(buffer, "lane connector edit");
        return fields;
    }

    private record WireFields(
            int protocolVersion,
            ResourceLocation dimensionId,
            long expectedGraphRevision,
            UUID fromLaneId,
            UUID toLaneId
    ) {
    }
}
