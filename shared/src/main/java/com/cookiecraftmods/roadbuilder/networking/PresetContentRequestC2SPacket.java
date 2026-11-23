package com.cookiecraftmods.roadbuilder.networking;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

public record PresetContentRequestC2SPacket(
        int protocolVersion,
        UUID requestId,
        ResourceLocation presetId
) implements RoadPacket {
    public static final RoadPacketType<PresetContentRequestC2SPacket> TYPE =
            RoadPacketType.create(
                    MinecraftIds.id("roadbuilder", "preset_content_request_v1"),
                    PresetContentRequestC2SPacket::new
            );

    public PresetContentRequestC2SPacket {
        PacketValidation.requireProtocolVersion(protocolVersion);
        Objects.requireNonNull(requestId, "requestId");
        PacketValidation.requireBoundedId(presetId, "presetId");
    }

    public PresetContentRequestC2SPacket(FriendlyByteBuf buffer) {
        this(
                buffer.readVarInt(),
                buffer.readUUID(),
                PacketValidation.readBoundedId(buffer, "presetId")
        );
        PacketValidation.requireFullyRead(buffer, "preset content request");
    }

    public static PresetContentRequestC2SPacket current(
            UUID requestId,
            ResourceLocation presetId
    ) {
        return new PresetContentRequestC2SPacket(
                PacketValidation.PROTOCOL_VERSION,
                requestId,
                presetId
        );
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(protocolVersion);
        buffer.writeUUID(requestId);
        PacketValidation.writeBoundedId(buffer, presetId);
    }

    @Override
    public RoadPacketType<?> packetType() {
        return TYPE;
    }
}
