package com.cookiecraftmods.roadbuilder.networking;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

public record PresetContentRequestC2SPacket(
        int protocolVersion,
        UUID requestId,
        ResourceLocation presetId
) implements FabricPacket {
    public static final PacketType<PresetContentRequestC2SPacket> TYPE =
            PacketType.create(
                    new ResourceLocation("roadbuilder", "preset_content_request_v1"),
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
    public PacketType<?> getType() {
        return TYPE;
    }
}
