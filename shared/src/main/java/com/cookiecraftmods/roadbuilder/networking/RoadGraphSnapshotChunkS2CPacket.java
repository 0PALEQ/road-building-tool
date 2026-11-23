package com.cookiecraftmods.roadbuilder.networking;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public record RoadGraphSnapshotChunkS2CPacket(
        int protocolVersion,
        UUID transferId,
        ResourceLocation dimensionId,
        long graphRevision,
        int chunkIndex,
        int chunkCount,
        int totalBytes,
        long checksum,
        byte[] payload
) implements RoadPacket {
    public static final int MAXIMUM_CHUNK_BYTES = 64 * 1024;
    public static final int MAXIMUM_CHUNK_COUNT =
            RoadGraphSnapshotS2CPacket.MAX_TRANSFER_BYTES / MAXIMUM_CHUNK_BYTES;
    public static final RoadPacketType<RoadGraphSnapshotChunkS2CPacket> TYPE =
            RoadPacketType.create(
                    MinecraftIds.id(
                            "roadbuilder",
                            "road_graph_snapshot_chunk_v2"
                    ),
                    RoadGraphSnapshotChunkS2CPacket::new
            );

    public RoadGraphSnapshotChunkS2CPacket {
        PacketValidation.requireProtocolVersion(protocolVersion);
        Objects.requireNonNull(transferId, "transferId");
        PacketValidation.requireBoundedId(dimensionId, "dimensionId");
        if (graphRevision < 0) {
            throw new IllegalArgumentException(
                    "graphRevision must be non-negative"
            );
        }
        if (chunkCount < 1 || chunkCount > MAXIMUM_CHUNK_COUNT
                || chunkIndex < 0 || chunkIndex >= chunkCount) {
            throw new IllegalArgumentException("invalid snapshot chunk index/count");
        }
        if (totalBytes < 1
                || totalBytes > RoadGraphSnapshotS2CPacket.MAX_TRANSFER_BYTES) {
            throw new IllegalArgumentException(
                    "invalid snapshot transfer size"
            );
        }
        payload = Arrays.copyOf(
                Objects.requireNonNull(payload, "payload"),
                payload.length
        );
        if (payload.length < 1 || payload.length > MAXIMUM_CHUNK_BYTES) {
            throw new IllegalArgumentException(
                    "invalid snapshot chunk payload size"
            );
        }
    }

    public RoadGraphSnapshotChunkS2CPacket(FriendlyByteBuf buffer) {
        this(
                buffer.readVarInt(),
                buffer.readUUID(),
                PacketValidation.readBoundedId(buffer, "dimension"),
                buffer.readVarLong(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readLong(),
                buffer.readByteArray(MAXIMUM_CHUNK_BYTES)
        );
        PacketValidation.requireFullyRead(buffer, "road graph snapshot chunk");
    }

    public static RoadGraphSnapshotChunkS2CPacket current(
            UUID transferId,
            ResourceLocation dimensionId,
            long graphRevision,
            int chunkIndex,
            int chunkCount,
            int totalBytes,
            long checksum,
            byte[] payload
    ) {
        return new RoadGraphSnapshotChunkS2CPacket(
                PacketValidation.PROTOCOL_VERSION,
                transferId,
                dimensionId,
                graphRevision,
                chunkIndex,
                chunkCount,
                totalBytes,
                checksum,
                payload
        );
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(protocolVersion);
        buffer.writeUUID(transferId);
        PacketValidation.writeBoundedId(buffer, dimensionId);
        buffer.writeVarLong(graphRevision);
        buffer.writeVarInt(chunkIndex);
        buffer.writeVarInt(chunkCount);
        buffer.writeVarInt(totalBytes);
        buffer.writeLong(checksum);
        buffer.writeByteArray(payload);
    }

    @Override
    public RoadPacketType<?> packetType() {
        return TYPE;
    }

    @Override
    public byte[] payload() {
        return Arrays.copyOf(payload, payload.length);
    }
}
