package com.cookiecraftmods.roadbuilder.networking;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;
import com.cookiecraftmods.roadbuilder.preset.RoadPackArchive;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;
import java.util.UUID;

public record RoadPackImportResultS2CPacket(
        int protocolVersion,
        UUID requestId,
        Code code,
        int imported,
        int skipped
) implements RoadPacket {
    public static final RoadPacketType<RoadPackImportResultS2CPacket> TYPE = RoadPacketType.create(
            MinecraftIds.id("roadbuilder", "road_pack_import_result_v1"), RoadPackImportResultS2CPacket::new);

    public RoadPackImportResultS2CPacket {
        PacketValidation.requireProtocolVersion(protocolVersion);
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(code, "code");
        if (imported < 0 || skipped < 0 || imported > RoadPackArchive.MAXIMUM_ROADS
                || skipped > RoadPackArchive.MAXIMUM_ROADS - imported
                || (code != Code.IMPORTED && (imported != 0 || skipped != 0))) {
            throw new IllegalArgumentException("invalid road pack import counts");
        }
    }

    private RoadPackImportResultS2CPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readUUID(), buffer.readEnum(Code.class),
                buffer.readVarInt(), buffer.readVarInt());
        PacketValidation.requireFullyRead(buffer, "road pack import result");
    }

    static RoadPackImportResultS2CPacket status(UUID requestId, Code code) {
        return new RoadPackImportResultS2CPacket(PacketValidation.PROTOCOL_VERSION, requestId, code, 0, 0);
    }

    static RoadPackImportResultS2CPacket imported(UUID requestId, int imported, int skipped) {
        return new RoadPackImportResultS2CPacket(
                PacketValidation.PROTOCOL_VERSION, requestId, Code.IMPORTED, imported, skipped);
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(protocolVersion);
        buffer.writeUUID(requestId);
        buffer.writeEnum(code);
        buffer.writeVarInt(imported);
        buffer.writeVarInt(skipped);
    }

    @Override
    public RoadPacketType<?> packetType() {
        return TYPE;
    }

    public enum Code {
        AVAILABLE,
        IMPORTED,
        NOT_AUTHORIZED,
        INVALID_PACK,
        UNKNOWN_BLOCK,
        CATALOG_FULL
    }
}
