package com.cookiecraftmods.roadbuilder.networking;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;
import com.cookiecraftmods.roadbuilder.preset.RoadPackArchive;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;
import java.util.UUID;

public record RoadPackImportC2SPacket(
        int protocolVersion,
        UUID requestId,
        byte[] archive
) implements RoadPacket {
    public static final RoadPacketType<RoadPackImportC2SPacket> TYPE = RoadPacketType.create(
            MinecraftIds.id("roadbuilder", "road_pack_import_v1"), RoadPackImportC2SPacket::new);

    public RoadPackImportC2SPacket {
        PacketValidation.requireProtocolVersion(protocolVersion);
        Objects.requireNonNull(requestId, "requestId");
        if (archive.length > RoadPackArchive.MAXIMUM_ARCHIVE_BYTES) {
            throw new IllegalArgumentException("road pack exceeds the transfer limit");
        }
        archive = archive.clone();
    }

    private RoadPackImportC2SPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readUUID(),
                buffer.readByteArray(RoadPackArchive.MAXIMUM_ARCHIVE_BYTES));
        PacketValidation.requireFullyRead(buffer, "road pack import");
    }

    public static RoadPackImportC2SPacket current(UUID requestId, byte[] archive) {
        return new RoadPackImportC2SPacket(PacketValidation.PROTOCOL_VERSION, requestId, archive);
    }

    public boolean checksPermission() {
        return archive.length == 0;
    }

    @Override
    public byte[] archive() {
        return archive.clone();
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(protocolVersion);
        buffer.writeUUID(requestId);
        buffer.writeByteArray(archive);
    }

    @Override
    public RoadPacketType<?> packetType() {
        return TYPE;
    }
}
