package com.cookiecraftmods.roadbuilder.networking;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

public record PresetSaveResultS2CPacket(
        int protocolVersion,
        UUID requestId,
        PresetSaveResultCode code,
        long catalogRevision,
        int presetRevision,
        String fingerprint
) implements FabricPacket {
    public static final PacketType<PresetSaveResultS2CPacket> TYPE =
            PacketType.create(
                    new ResourceLocation("roadbuilder", "preset_save_result_v1"),
                    PresetSaveResultS2CPacket::new
            );

    public PresetSaveResultS2CPacket {
        PacketValidation.requireProtocolVersion(protocolVersion);
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(code, "code");
        if (catalogRevision < 0 || presetRevision < 0) {
            throw new IllegalArgumentException("result revisions are invalid");
        }
        Objects.requireNonNull(fingerprint, "fingerprint");
        if (!fingerprint.isEmpty()
                && !fingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid preset fingerprint");
        }
    }

    public PresetSaveResultS2CPacket(FriendlyByteBuf buffer) {
        this(
                buffer.readVarInt(),
                buffer.readUUID(),
                readCode(buffer),
                buffer.readVarLong(),
                buffer.readVarInt(),
                buffer.readUtf(64)
        );
        PacketValidation.requireFullyRead(buffer, "preset save result");
    }

    public static PresetSaveResultS2CPacket accepted(
            UUID requestId,
            long catalogRevision,
            int presetRevision,
            String fingerprint
    ) {
        return new PresetSaveResultS2CPacket(
                PacketValidation.PROTOCOL_VERSION,
                requestId,
                PresetSaveResultCode.ACCEPTED,
                catalogRevision,
                presetRevision,
                fingerprint
        );
    }

    public static PresetSaveResultS2CPacket rejected(
            UUID requestId,
            PresetSaveResultCode code
    ) {
        return new PresetSaveResultS2CPacket(
                PacketValidation.PROTOCOL_VERSION,
                requestId,
                code,
                0,
                0,
                ""
        );
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(protocolVersion);
        buffer.writeUUID(requestId);
        buffer.writeByte(code.ordinal());
        buffer.writeVarLong(catalogRevision);
        buffer.writeVarInt(presetRevision);
        buffer.writeUtf(fingerprint, 64);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }

    private static PresetSaveResultCode readCode(FriendlyByteBuf buffer) {
        int id = buffer.readUnsignedByte();
        PresetSaveResultCode[] values = PresetSaveResultCode.values();
        if (id >= values.length) {
            throw new IllegalArgumentException("unknown preset result code");
        }
        return values[id];
    }
}
