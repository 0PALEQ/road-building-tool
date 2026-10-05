package com.cookiecraftmods.roadbuilder.client.networking;

import com.cookiecraftmods.roadbuilder.network.RoadGraphSnapshot;
import com.cookiecraftmods.roadbuilder.networking.RoadGraphSnapshotChunkS2CPacket;
import com.cookiecraftmods.roadbuilder.networking.RoadGraphSnapshotS2CPacket;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.zip.CRC32;

public final class RoadGraphSnapshotChunkAssembler {
    private static final int MAXIMUM_ACTIVE_TRANSFERS = 2;
    private final Map<UUID, Transfer> transfers = new LinkedHashMap<>();
    private long generation;

    public synchronized Optional<RoadGraphSnapshot> accept(
            RoadGraphSnapshotChunkS2CPacket chunk
    ) {
        CompletedTransfer transfer = acceptTransfer(chunk);
        return transfer == null ? Optional.empty() : transfer.decode();
    }

        public void acceptAsync(
            RoadGraphSnapshotChunkS2CPacket chunk,
            Consumer<RoadGraphSnapshot> completed
    ) {
        java.util.Objects.requireNonNull(completed, "completed");
        CompletedTransfer transfer;
        long acceptedGeneration;
        synchronized (this) {
            transfer = acceptTransfer(chunk);
            acceptedGeneration = generation;
        }
        if (transfer == null) {
            return;
        }
        CompletableFuture.supplyAsync(transfer::decodeRequired)
                .thenAccept(snapshot -> {
                    synchronized (this) {
                        if (generation != acceptedGeneration) {
                            return;
                        }
                    }
                    completed.accept(snapshot);
                });
    }

    public synchronized void clear() {
        transfers.clear();
        generation++;
    }

    private CompletedTransfer acceptTransfer(
            RoadGraphSnapshotChunkS2CPacket chunk
    ) {
        Transfer transfer = transfers.get(chunk.transferId());
        if (transfer == null) {
            if (transfers.size() >= MAXIMUM_ACTIVE_TRANSFERS) {
                UUID oldest = transfers.keySet().iterator().next();
                transfers.remove(oldest);
            }
            transfer = new Transfer(chunk);
            transfers.put(chunk.transferId(), transfer);
        }
        if (!transfer.matches(chunk)) {
            transfers.remove(chunk.transferId());
            return null;
        }
        transfer.add(chunk);
        if (!transfer.complete()) {
            return null;
        }
        transfers.remove(chunk.transferId());
        byte[] encoded = transfer.join();
        return new CompletedTransfer(
                encoded,
                chunk.dimensionId(),
                chunk.graphRevision(),
                chunk.checksum()
        );
    }

    private static final class Transfer {
        private final RoadGraphSnapshotChunkS2CPacket first;
        private final byte[][] chunks;
        private int received;

        private Transfer(RoadGraphSnapshotChunkS2CPacket first) {
            this.first = first;
            this.chunks = new byte[first.chunkCount()][];
        }

        private boolean matches(RoadGraphSnapshotChunkS2CPacket chunk) {
            return first.dimensionId().equals(chunk.dimensionId())
                    && first.graphRevision() == chunk.graphRevision()
                    && first.chunkCount() == chunk.chunkCount()
                    && first.totalBytes() == chunk.totalBytes()
                    && first.checksum() == chunk.checksum();
        }

        private void add(RoadGraphSnapshotChunkS2CPacket chunk) {
            if (chunks[chunk.chunkIndex()] == null) {
                chunks[chunk.chunkIndex()] = chunk.payload();
                received++;
            } else if (!Arrays.equals(
                    chunks[chunk.chunkIndex()],
                    chunk.payload()
            )) {
                throw new IllegalArgumentException(
                        "conflicting duplicate snapshot chunk"
                );
            }
        }

        private boolean complete() {
            return received == chunks.length;
        }

        private byte[] join() {
            ByteArrayOutputStream output =
                    new ByteArrayOutputStream(first.totalBytes());
            for (byte[] chunk : chunks) {
                output.writeBytes(chunk);
            }
            byte[] result = output.toByteArray();
            if (result.length != first.totalBytes()) {
                throw new IllegalArgumentException(
                        "snapshot chunks do not match declared size"
                );
            }
            return result;
        }
    }

    private record CompletedTransfer(
            byte[] encoded,
            net.minecraft.resources.ResourceLocation dimensionId,
            long revision,
            long checksum
    ) {
        private Optional<RoadGraphSnapshot> decode() {
            CRC32 crc = new CRC32();
            crc.update(encoded);
            if (crc.getValue() != checksum) {
                return Optional.empty();
            }
            RoadGraphSnapshot snapshot =
                    RoadGraphSnapshotS2CPacket.decode(encoded).snapshot();
            if (!snapshot.dimension().location().equals(dimensionId)
                    || snapshot.revision() != revision) {
                return Optional.empty();
            }
            return Optional.of(snapshot);
        }

        private RoadGraphSnapshot decodeRequired() {
            return decode().orElseThrow(() -> new IllegalArgumentException(
                    "snapshot chunk checksum or identity mismatch"
            ));
        }
    }
}
