package com.cookiecraftmods.roadbuilder.networking;

import com.cookiecraftmods.roadbuilder.network.RoadGraphDelta;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record RoadGraphDeltaS2CPacket(
        int protocolVersion,
        RoadGraphDelta delta
) implements FabricPacket {
    public static final int CURRENT_PROTOCOL_VERSION = PacketValidation.PROTOCOL_VERSION;
    public static final int MAX_PAYLOAD_BYTES = 262_144;

    public static final PacketType<RoadGraphDeltaS2CPacket> TYPE = PacketType.create(
            new ResourceLocation("roadbuilder", "road_graph_delta_v2"),
            RoadGraphDeltaS2CPacket::new
    );

    public RoadGraphDeltaS2CPacket {
        PacketValidation.requireProtocolVersion(protocolVersion);
        RoadGraphPacketCodecs.validateDelta(Objects.requireNonNull(delta, "delta"));
    }

    public RoadGraphDeltaS2CPacket(FriendlyByteBuf buffer) {
        this(decode(buffer));
    }

    public static RoadGraphDeltaS2CPacket current(RoadGraphDelta delta) {
        return new RoadGraphDeltaS2CPacket(CURRENT_PROTOCOL_VERSION, delta);
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        buffer.writeVarInt(protocolVersion);
        RoadGraphPacketCodecs.writeDelta(buffer, delta);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }

    private RoadGraphDeltaS2CPacket(WireFields fields) {
        this(fields.protocolVersion(), fields.delta());
    }

    private static WireFields decode(FriendlyByteBuf buffer) {
        PacketValidation.requirePayloadSize(buffer, MAX_PAYLOAD_BYTES, "road graph delta");
        WireFields fields = new WireFields(
                buffer.readVarInt(),
                RoadGraphPacketCodecs.readDelta(buffer)
        );
        PacketValidation.requireFullyRead(buffer, "road graph delta");
        return fields;
    }

    private record WireFields(int protocolVersion, RoadGraphDelta delta) {
    }
}
