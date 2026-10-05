package com.cookiecraftmods.roadbuilder.networking;

import com.cookiecraftmods.roadbuilder.network.RoadGraphSnapshot;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record RoadGraphSnapshotS2CPacket(
        int protocolVersion,
        RoadGraphSnapshot snapshot
) implements FabricPacket {
    public static final int CURRENT_PROTOCOL_VERSION = PacketValidation.PROTOCOL_VERSION;
    public static final int MAX_PAYLOAD_BYTES = 1_048_576;

    public static final PacketType<RoadGraphSnapshotS2CPacket> TYPE = PacketType.create(
            new ResourceLocation("roadbuilder", "road_graph_snapshot_v1"),
            RoadGraphSnapshotS2CPacket::new
    );

    public RoadGraphSnapshotS2CPacket {
        PacketValidation.requireProtocolVersion(protocolVersion);
        RoadGraphPacketCodecs.validateSnapshot(Objects.requireNonNull(snapshot, "snapshot"));
    }

    public RoadGraphSnapshotS2CPacket(FriendlyByteBuf buffer) {
        this(decode(buffer));
    }

    public static RoadGraphSnapshotS2CPacket current(RoadGraphSnapshot snapshot) {
        return new RoadGraphSnapshotS2CPacket(CURRENT_PROTOCOL_VERSION, snapshot);
    }

    public byte[] encode() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        write(buffer);
        byte[] encoded = new byte[buffer.readableBytes()];
        buffer.readBytes(encoded);
        return encoded;
    }

    public static RoadGraphSnapshotS2CPacket decode(byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded");
        if (encoded.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException(
                    "road graph snapshot payload exceeds limit"
            );
        }
        return TYPE.read(new FriendlyByteBuf(
                Unpooled.wrappedBuffer(encoded)
        ));
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        buffer.writeVarInt(protocolVersion);
        RoadGraphPacketCodecs.writeSnapshot(buffer, snapshot);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }

    private RoadGraphSnapshotS2CPacket(WireFields fields) {
        this(fields.protocolVersion(), fields.snapshot());
    }

    private static WireFields decode(FriendlyByteBuf buffer) {
        PacketValidation.requirePayloadSize(buffer, MAX_PAYLOAD_BYTES, "road graph snapshot");
        WireFields fields = new WireFields(
                buffer.readVarInt(),
                RoadGraphPacketCodecs.readSnapshot(buffer)
        );
        PacketValidation.requireFullyRead(buffer, "road graph snapshot");
        return fields;
    }

    private record WireFields(int protocolVersion, RoadGraphSnapshot snapshot) {
    }
}
