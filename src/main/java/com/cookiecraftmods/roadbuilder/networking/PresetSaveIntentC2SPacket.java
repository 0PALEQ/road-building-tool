package com.cookiecraftmods.roadbuilder.networking;

import com.cookiecraftmods.roadbuilder.preset.RoadPreset;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

public record PresetSaveIntentC2SPacket(
        int protocolVersion,
        UUID requestId,
        ResourceLocation presetId,
        int expectedRevision,
        RoadPreset candidate
) implements FabricPacket {
    public static final PacketType<PresetSaveIntentC2SPacket> TYPE =
            PacketType.create(
                    new ResourceLocation("roadbuilder", "preset_save_v1"),
                    PresetSaveIntentC2SPacket::new
            );

    public PresetSaveIntentC2SPacket {
        PacketValidation.requireProtocolVersion(protocolVersion);
        Objects.requireNonNull(requestId, "requestId");
        PacketValidation.requireBoundedId(presetId, "presetId");
        if (expectedRevision < 0) {
            throw new IllegalArgumentException(
                    "expectedRevision must be non-negative"
            );
        }
        Objects.requireNonNull(candidate, "candidate");
    }

    public PresetSaveIntentC2SPacket(FriendlyByteBuf buffer) {
        this(
                buffer.readVarInt(),
                buffer.readUUID(),
                PacketValidation.readBoundedId(buffer, "presetId"),
                buffer.readVarInt(),
                RoadPresetPacketCodecs.readPreset(buffer)
        );
        PacketValidation.requireFullyRead(buffer, "preset save intent");
    }

    public static PresetSaveIntentC2SPacket current(
            UUID requestId,
            ResourceLocation presetId,
            int expectedRevision,
            RoadPreset candidate
    ) {
        return new PresetSaveIntentC2SPacket(
                PacketValidation.PROTOCOL_VERSION,
                requestId,
                presetId,
                expectedRevision,
                candidate
        );
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(protocolVersion);
        buffer.writeUUID(requestId);
        PacketValidation.writeBoundedId(buffer, presetId);
        buffer.writeVarInt(expectedRevision);
        RoadPresetPacketCodecs.writePreset(buffer, candidate);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
}
