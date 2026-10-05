package com.cookiecraftmods.roadbuilder.networking;

import com.cookiecraftmods.roadbuilder.RoadBuilder;
import com.cookiecraftmods.roadbuilder.network.RoadGraphDelta;
import com.cookiecraftmods.roadbuilder.network.RoadGraphSnapshot;
import com.cookiecraftmods.roadbuilder.persistence.RoadBuilderNetworkSavedData;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
        if (!ServerPlayNetworking.canSend(player, RoadGraphSnapshotS2CPacket.TYPE)) {
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
            RoadGraphSnapshotS2CPacket.current(after);
            if (before != null && before.dimension().equals(after.dimension())) {
                deltaPacket = RoadGraphDeltaS2CPacket.current(
                        RoadGraphDelta.between(before, after)
                );
            }
        } catch (IllegalArgumentException exception) {
            RoadBuilder.LOGGER.error(
                    "Road graph mutation in {} exceeds synchronization limits",
                    level.dimension().location(),
                    exception
            );
            return;
        }

        for (ServerPlayer player : level.players()) {
            if (deltaPacket != null
                    && ServerPlayNetworking.canSend(player, RoadGraphDeltaS2CPacket.TYPE)) {
                ServerPlayNetworking.send(player, deltaPacket);
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
                    && ServerPlayNetworking.canSend(
                            player,
                            RoadGraphSnapshotChunkS2CPacket.TYPE
                    )) {
                sendChunks(player, snapshot, encoded);
            } else if (ServerPlayNetworking.canSend(
                    player,
                    RoadGraphSnapshotS2CPacket.TYPE
            )) {
                ServerPlayNetworking.send(player, packet);
            }
        } catch (IllegalArgumentException exception) {
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
            ServerPlayNetworking.send(
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
