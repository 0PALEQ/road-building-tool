package com.cookiecraftmods.roadbuilder.networking;

import com.cookiecraftmods.roadbuilder.RoadBuilder;
import com.cookiecraftmods.roadbuilder.network.RoadGraphDelta;
import com.cookiecraftmods.roadbuilder.network.RoadGraphSnapshot;
import com.cookiecraftmods.roadbuilder.persistence.RoadBuilderNetworkSavedData;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.UUID;
import java.util.zip.CRC32;

final class RoadGraphSynchronizer {
    private static final int CHUNKING_THRESHOLD_BYTES = 128 * 1024;

    private RoadGraphSynchronizer() {
    }

    static RoadGraphSnapshot currentSnapshot(ServerLevel level) {
        RoadBuilderNetworkSavedData savedData = RoadBuilderNetworkSavedData.get(level);
        return savedData.isCompatible() ? savedData.graph().snapshot() : null;
    }

    static void sendCurrentSnapshot(ServerPlayer player) {
        if (!RoadServerTransport.canSend(player, RoadGraphSnapshotS2CPacket.TYPE)) {
            return;
        }
        RoadGraphSnapshot snapshot = currentSnapshot(player.serverLevel());
        if (snapshot != null) {
            sendSnapshot(player, snapshot);
        }
    }

    static void broadcastMutation(ServerLevel level, RoadGraphSnapshot before) {
        RoadGraphSnapshot after = currentSnapshot(level);
        if (after == null || unchanged(before, after)) {
            return;
        }

        RoadGraphDeltaS2CPacket deltaPacket = null;
        try {
            if (before != null && before.dimension().equals(after.dimension())) {
                deltaPacket = RoadGraphDeltaS2CPacket.current(
                        RoadGraphDelta.between(before, after)
                );
                // A large mutation uses the bounded, chunked snapshot path instead.
                FriendlyByteBuf buffer = new FriendlyByteBuf(
                        Unpooled.buffer(256, RoadGraphDeltaS2CPacket.MAX_PAYLOAD_BYTES));
                try {
                    deltaPacket.write(buffer);
                } finally {
                    buffer.release();
                }
            }
        } catch (IllegalArgumentException | IndexOutOfBoundsException exception) {
            deltaPacket = null;
        }

        for (ServerPlayer player : level.players()) {
            if (deltaPacket != null
                    && RoadServerTransport.canSend(player, RoadGraphDeltaS2CPacket.TYPE)) {
                RoadServerTransport.send(player, deltaPacket);
            } else {
                sendSnapshot(player, after);
            }
        }
    }

    static void sendSnapshot(ServerPlayer player, RoadGraphSnapshot snapshot) {
        try {
            RoadGraphSnapshotS2CPacket packet =
                    RoadGraphSnapshotS2CPacket.current(snapshot);
            byte[] encoded = packet.encode();
            if (encoded.length > CHUNKING_THRESHOLD_BYTES
                    && RoadServerTransport.canSend(
                            player,
                            RoadGraphSnapshotChunkS2CPacket.TYPE
                    )) {
                sendChunks(player, snapshot, encoded);
            } else if (RoadServerTransport.canSend(
                    player,
                    RoadGraphSnapshotS2CPacket.TYPE
            )) {
                if (encoded.length > RoadGraphSnapshotS2CPacket.MAX_PAYLOAD_BYTES) {
                    throw new IllegalArgumentException("client needs chunked graph synchronization");
                }
                RoadServerTransport.send(player, packet);
            }
        } catch (IllegalArgumentException | IndexOutOfBoundsException exception) {
            RoadBuilder.LOGGER.error(
                    "Road graph in {} exceeds synchronization limits for player {}",
                    snapshot.dimension().location(),
                    player.getUUID(),
                    exception
            );
        }
    }

    private static boolean unchanged(
            RoadGraphSnapshot before,
            RoadGraphSnapshot after
    ) {
        return before != null
                && before.dimension().equals(after.dimension())
                && before.revision() == after.revision();
    }

    private static void sendChunks(
            ServerPlayer player,
            RoadGraphSnapshot snapshot,
            byte[] encoded
    ) {
        int chunkSize = RoadGraphSnapshotChunkS2CPacket.MAXIMUM_CHUNK_BYTES;
        int chunkCount = (encoded.length + chunkSize - 1) / chunkSize;
        UUID transferId = UUID.randomUUID();
        CRC32 crc = new CRC32();
        crc.update(encoded);
        for (int index = 0; index < chunkCount; index++) {
            int start = index * chunkSize;
            int end = Math.min(encoded.length, start + chunkSize);
            RoadServerTransport.send(
                    player,
                    RoadGraphSnapshotChunkS2CPacket.current(
                            transferId,
                            snapshot.dimension().location(),
                            snapshot.revision(),
                            index,
                            chunkCount,
                            encoded.length,
                            crc.getValue(),
                            Arrays.copyOfRange(encoded, start, end)
                    )
            );
        }
    }
}
