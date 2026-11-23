package com.cookiecraftmods.roadbuilder.networking;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record RoadGraphResyncC2SPacket(
        int protocolVersion,
        ResourceLocation dimensionId,
        long knownRevision
) implements RoadPacket {
    public static final int CURRENT_PROTOCOL_VERSION = PacketValidation.PROTOCOL_VERSION;
    public static final int MAX_PAYLOAD_BYTES = 160;
    public static final long NO_KNOWN_REVISION = -1L;

    public static final RoadPacketType<RoadGraphResyncC2SPacket> TYPE = RoadPacketType.create(
            MinecraftIds.id("roadbuilder", "road_graph_resync_v2"),
            RoadGraphResyncC2SPacket::new
    );

    public RoadGraphResyncC2SPacket {
        PacketValidation.requireProtocolVersion(protocolVersion);
        PacketValidation.requireBoundedId(dimensionId, "dimensionId");
        if (knownRevision < NO_KNOWN_REVISION) {
            throw new IllegalArgumentException("knownRevision must be -1 or non-negative");
        }
    }

    public RoadGraphResyncC2SPacket(FriendlyByteBuf buffer) {
        this(decode(buffer));
    }

    public static RoadGraphResyncC2SPacket current(
            ResourceLocation dimensionId,
            long knownRevision
    ) {
        return new RoadGraphResyncC2SPacket(
                CURRENT_PROTOCOL_VERSION,
                Objects.requireNonNull(dimensionId, "dimensionId"),
                knownRevision
        );
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        buffer.writeVarInt(protocolVersion);
        PacketValidation.writeBoundedId(buffer, dimensionId);
        buffer.writeLong(knownRevision);
    }

    @Override
    public RoadPacketType<?> packetType() {
        return TYPE;
    }

    private RoadGraphResyncC2SPacket(WireFields fields) {
        this(fields.protocolVersion(), fields.dimensionId(), fields.knownRevision());
    }

    private static WireFields decode(FriendlyByteBuf buffer) {
        PacketValidation.requirePayloadSize(buffer, MAX_PAYLOAD_BYTES, "road graph resync");
        WireFields fields = new WireFields(
                buffer.readVarInt(),
                PacketValidation.readBoundedId(buffer, "dimensionId"),
                buffer.readLong()
        );
        PacketValidation.requireFullyRead(buffer, "road graph resync");
        return fields;
    }

    private record WireFields(
            int protocolVersion,
            ResourceLocation dimensionId,
            long knownRevision
    ) {
    }
}
