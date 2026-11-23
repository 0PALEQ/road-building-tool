package com.cookiecraftmods.roadbuilder.networking;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import com.cookiecraftmods.roadbuilder.preset.RoadPreset;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

public record PresetContentS2CPacket(
        int protocolVersion,
        UUID requestId,
        ResourceLocation presetId,
        RoadPreset preset
) implements RoadPacket {
    public static final RoadPacketType<PresetContentS2CPacket> TYPE =
            RoadPacketType.create(
                    MinecraftIds.id("roadbuilder", "preset_content_v1"),
                    PresetContentS2CPacket::new
            );

    public PresetContentS2CPacket {
        PacketValidation.requireProtocolVersion(protocolVersion);
        Objects.requireNonNull(requestId, "requestId");
        PacketValidation.requireBoundedId(presetId, "presetId");
        Objects.requireNonNull(preset, "preset");
    }

    public PresetContentS2CPacket(FriendlyByteBuf buffer) {
        this(
                buffer.readVarInt(),
                buffer.readUUID(),
                PacketValidation.readBoundedId(buffer, "presetId"),
                RoadPresetPacketCodecs.readPreset(buffer)
        );
        PacketValidation.requireFullyRead(buffer, "preset content");
    }

    public static PresetContentS2CPacket current(
            UUID requestId,
            ResourceLocation presetId,
            RoadPreset preset
    ) {
        return new PresetContentS2CPacket(
                PacketValidation.PROTOCOL_VERSION,
                requestId,
                presetId,
                preset
        );
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(protocolVersion);
        buffer.writeUUID(requestId);
        PacketValidation.writeBoundedId(buffer, presetId);
        RoadPresetPacketCodecs.writePreset(buffer, preset);
    }

    @Override
    public RoadPacketType<?> packetType() {
        return TYPE;
    }
}
