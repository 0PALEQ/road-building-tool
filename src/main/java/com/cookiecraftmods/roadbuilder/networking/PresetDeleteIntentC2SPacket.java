package com.cookiecraftmods.roadbuilder.networking;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

public record PresetDeleteIntentC2SPacket(
        int protocolVersion,
        UUID requestId,
        ResourceLocation presetId,
        int expectedRevision,
        boolean confirmed
) implements FabricPacket {
    public static final PacketType<PresetDeleteIntentC2SPacket> TYPE =
            PacketType.create(
                    new ResourceLocation("roadbuilder", "preset_delete_v1"),
                    PresetDeleteIntentC2SPacket::new
            );

    public PresetDeleteIntentC2SPacket {
        PacketValidation.requireProtocolVersion(protocolVersion);
        Objects.requireNonNull(requestId, "requestId");
        PacketValidation.requireBoundedId(presetId, "presetId");
        if (expectedRevision < 1 || !confirmed) {
            throw new IllegalArgumentException(
                    "preset deletion must be revisioned and confirmed"
            );
        }
    }

    public PresetDeleteIntentC2SPacket(FriendlyByteBuf buffer) {
        this(
                buffer.readVarInt(),
                buffer.readUUID(),
                PacketValidation.readBoundedId(buffer, "presetId"),
                buffer.readVarInt(),
                buffer.readBoolean()
        );
        PacketValidation.requireFullyRead(buffer, "preset delete intent");
    }

    public static PresetDeleteIntentC2SPacket current(
            UUID requestId,
            ResourceLocation presetId,
            int expectedRevision
    ) {
        return new PresetDeleteIntentC2SPacket(
                PacketValidation.PROTOCOL_VERSION,
                requestId,
                presetId,
                expectedRevision,
                true
        );
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(protocolVersion);
        buffer.writeUUID(requestId);
        PacketValidation.writeBoundedId(buffer, presetId);
        buffer.writeVarInt(expectedRevision);
        buffer.writeBoolean(confirmed);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
}
