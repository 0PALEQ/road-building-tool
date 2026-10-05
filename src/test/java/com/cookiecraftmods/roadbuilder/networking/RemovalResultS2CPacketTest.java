package com.cookiecraftmods.roadbuilder.networking;

import com.cookiecraftmods.roadbuilder.placement.RemovalMode;
import com.cookiecraftmods.roadbuilder.placement.RemovalSummary;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemovalResultS2CPacketTest {
    private static final UUID REQUEST_ID = UUID.fromString("04a57d63-c497-4847-b289-e351545189b3");
    private static final UUID OPERATION_ID = UUID.fromString("19426729-631c-4dd3-b33f-68a94b6d764f");

    @Test
    void successfulPreviewRoundTripsWithBoundedConflictAccounting() {
        List<BlockPos> conflicts = IntStream.range(0, 20)
                .mapToObj(index -> new BlockPos(index, 64, -index))
                .toList();
        RemovalResultS2CPacket packet = RemovalResultS2CPacket.success(
                request(RemovalRequestAction.PREVIEW, RemovalMode.SAFE),
                new RemovalSummary(20, 0, 0, 0, 20, 0, conflicts)
        );
        FriendlyByteBuf buffer = PacketByteBufs.create();

        packet.write(buffer);

        RemovalResultS2CPacket decoded = RemovalResultS2CPacket.TYPE.read(buffer);
        RemovalResultSummary summary = decoded.summary().orElseThrow();
        assertAll(
                () -> assertEquals(packet, decoded),
                () -> assertEquals(20, summary.conflictCount()),
                () -> assertEquals(RemovalResultSummary.MAX_CONFLICT_SAMPLES, summary.conflictSamples().size()),
                () -> assertTrue(summary.conflictSamplesTruncated()),
                () -> assertEquals(conflicts.subList(0, 16), summary.conflictSamples()),
                () -> assertSame(RemovalResultS2CPacket.TYPE, packet.getType()),
                () -> assertEquals(
                        new ResourceLocation("roadbuilder", "removal_result_v1"),
                        RemovalResultS2CPacket.TYPE.getId()
                )
        );
    }

    @Test
    void successfulExecuteAndRejectedResultRoundTrip() {
        RemovalResultS2CPacket executed = RemovalResultS2CPacket.success(
                request(RemovalRequestAction.EXECUTE, RemovalMode.FORCE),
                new RemovalSummary(3, 2, 1, 0, 0, 2, List.of())
        );
        RemovalResultS2CPacket rejected = RemovalResultS2CPacket.rejected(
                request(RemovalRequestAction.EXECUTE, RemovalMode.FORCE),
                RemovalResultCode.FORCE_RESTORE_NOT_AUTHORIZED
        );
        RemovalResultS2CPacket queued = RemovalResultS2CPacket.queued(
                request(RemovalRequestAction.EXECUTE, RemovalMode.SAFE),
                new RemovalSummary(300, 300, 0, 0, 0, 0, List.of())
        );

        assertAll(
                () -> assertEquals(executed, roundTrip(executed)),
                () -> assertFalse(executed.summary().orElseThrow().conflictSamplesTruncated()),
                () -> assertEquals(queued, roundTrip(queued)),
                () -> assertEquals(
                        300,
                        queued.summary().orElseThrow().restoredCount()
                ),
                () -> assertEquals(rejected, roundTrip(rejected)),
                () -> assertEquals(Optional.empty(), rejected.summary())
        );
    }

    @Test
    void enforcesTheSuccessDiscriminatedVariantAndSummaryInvariants() {
        RemovalIntentC2SPacket request = request(RemovalRequestAction.PREVIEW, RemovalMode.SAFE);
        RemovalResultSummary validSummary = new RemovalResultSummary(1, 1, 0, 0, 0, 0, List.of());

        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new RemovalResultS2CPacket(
                                RemovalResultS2CPacket.CURRENT_PROTOCOL_VERSION,
                                REQUEST_ID,
                                OPERATION_ID,
                                RemovalRequestAction.PREVIEW,
                                RemovalMode.SAFE,
                                RemovalResultCode.SUCCESS,
                                Optional.empty()
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new RemovalResultS2CPacket(
                                RemovalResultS2CPacket.CURRENT_PROTOCOL_VERSION,
                                REQUEST_ID,
                                OPERATION_ID,
                                RemovalRequestAction.PREVIEW,
                                RemovalMode.SAFE,
                                RemovalResultCode.NOT_AUTHORIZED,
                                Optional.of(validSummary)
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> RemovalResultS2CPacket.rejected(request, RemovalResultCode.SUCCESS)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new RemovalResultSummary(2, 1, 0, 0, 0, 0, List.of())
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new RemovalResultSummary(
                                1,
                                Integer.MAX_VALUE,
                                Integer.MAX_VALUE,
                                3,
                                3,
                                0,
                                List.of()
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new RemovalResultSummary(
                                2,
                                0,
                                0,
                                0,
                                2,
                                0,
                                List.of(BlockPos.ZERO, BlockPos.ZERO)
                        )
                )
        );
    }

    @Test
    void stableResultCodesHaveExplicitWireIds() {
        assertAll(
                () -> assertEquals(0, RemovalResultCode.SUCCESS.wireId()),
                () -> assertEquals(1, RemovalResultCode.DIMENSION_MISMATCH.wireId()),
                () -> assertEquals(2, RemovalResultCode.UNKNOWN_OPERATION.wireId()),
                () -> assertEquals(3, RemovalResultCode.NOT_AUTHORIZED.wireId()),
                () -> assertEquals(4, RemovalResultCode.FORCE_RESTORE_NOT_AUTHORIZED.wireId()),
                () -> assertEquals(5, RemovalResultCode.REMOVAL_LIMIT_EXCEEDED.wireId()),
                () -> assertEquals(6, RemovalResultCode.OWNERSHIP_CORRUPT.wireId()),
                () -> assertEquals(7, RemovalResultCode.UNSUPPORTED_WORLD_STATE.wireId()),
                () -> assertEquals(8, RemovalResultCode.WORLD_CONFLICT.wireId()),
                () -> assertEquals(9, RemovalResultCode.MUTATION_FAILED.wireId()),
                () -> assertEquals(10, RemovalResultCode.PERSISTENCE_INCOMPATIBLE.wireId()),
                () -> assertEquals(11, RemovalResultCode.RATE_LIMITED.wireId()),
                () -> assertEquals(12, RemovalResultCode.INTERNAL_ERROR.wireId()),
                () -> assertEquals(13, RemovalResultCode.UNKNOWN_SEGMENT.wireId()),
                () -> assertEquals(14, RemovalResultCode.STALE_GRAPH.wireId()),
                () -> assertEquals(15, RemovalResultCode.QUEUED.wireId())
        );
    }

    @Test
    void decoderRejectsUnknownCodesUnboundedSamplesTrailingBytesAndOversizedPayloads() {
        FriendlyByteBuf unknownCode = resultPrefix();
        unknownCode.writeVarInt(99);

        FriendlyByteBuf tooManySamples = resultPrefix();
        tooManySamples.writeVarInt(RemovalResultCode.SUCCESS.wireId());
        tooManySamples.writeVarInt(17);
        tooManySamples.writeVarInt(0);
        tooManySamples.writeVarInt(0);
        tooManySamples.writeVarInt(0);
        tooManySamples.writeVarInt(17);
        tooManySamples.writeVarInt(0);
        tooManySamples.writeVarInt(RemovalResultSummary.MAX_CONFLICT_SAMPLES + 1);

        FriendlyByteBuf trailing = PacketByteBufs.create();
        RemovalResultS2CPacket.rejected(
                request(RemovalRequestAction.PREVIEW, RemovalMode.SAFE),
                RemovalResultCode.INTERNAL_ERROR
        ).write(trailing);
        trailing.writeByte(0x5a);

        FriendlyByteBuf oversized = PacketByteBufs.create();
        oversized.writeZero(RemovalResultS2CPacket.MAX_PAYLOAD_BYTES + 1);

        assertAll(
                () -> assertThrows(RuntimeException.class, () -> RemovalResultS2CPacket.TYPE.read(unknownCode)),
                () -> assertThrows(RuntimeException.class, () -> RemovalResultS2CPacket.TYPE.read(tooManySamples)),
                () -> assertThrows(RuntimeException.class, () -> RemovalResultS2CPacket.TYPE.read(trailing)),
                () -> assertThrows(RuntimeException.class, () -> RemovalResultS2CPacket.TYPE.read(oversized))
        );
    }

    private static RemovalResultS2CPacket roundTrip(RemovalResultS2CPacket packet) {
        FriendlyByteBuf buffer = PacketByteBufs.create();
        packet.write(buffer);
        RemovalResultS2CPacket decoded = RemovalResultS2CPacket.TYPE.read(buffer);
        assertEquals(0, buffer.readableBytes());
        return decoded;
    }

    private static FriendlyByteBuf resultPrefix() {
        FriendlyByteBuf buffer = PacketByteBufs.create();
        buffer.writeVarInt(RemovalResultS2CPacket.CURRENT_PROTOCOL_VERSION);
        buffer.writeUUID(REQUEST_ID);
        buffer.writeUUID(OPERATION_ID);
        buffer.writeVarInt(RemovalRequestAction.PREVIEW.wireId());
        buffer.writeVarInt(0);
        return buffer;
    }

    private static RemovalIntentC2SPacket request(
            RemovalRequestAction action,
            RemovalMode mode
    ) {
        return RemovalIntentC2SPacket.current(
                REQUEST_ID,
                new ResourceLocation("minecraft", "overworld"),
                OPERATION_ID,
                action,
                mode
        );
    }
}
