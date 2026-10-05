package com.cookiecraftmods.roadbuilder.networking;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlacementResultS2CPacketTest {
    private static final UUID REQUEST_ID = UUID.fromString("9e495a8c-eb7d-40a9-b8f4-8894de7f0310");
    private static final UUID OPERATION_ID = UUID.fromString("7bfccca4-6907-4cbc-827d-719098af505f");

    @Test
    void acceptedVariantRoundTripsThroughItsTypedCodec() {
        PlacementResultS2CPacket packet = PlacementResultS2CPacket.accepted(REQUEST_ID, OPERATION_ID, 243);
        FriendlyByteBuf buffer = PacketByteBufs.create();

        packet.write(buffer);

        assertEquals(packet, PlacementResultS2CPacket.TYPE.read(buffer));
        assertEquals(0, buffer.readableBytes());
        assertSame(PlacementResultS2CPacket.TYPE, packet.getType());
        assertEquals(
                new ResourceLocation("roadbuilder", "placement_result_v1"),
                PlacementResultS2CPacket.TYPE.getId()
        );
    }

    @Test
    void queuedAcceptedVariantRoundTripsItsConstructionJobState() {
        PlacementResultS2CPacket packet = PlacementResultS2CPacket.queued(
                REQUEST_ID,
                OPERATION_ID,
                40_000
        );
        FriendlyByteBuf buffer = PacketByteBufs.create();

        packet.write(buffer);

        PlacementResultS2CPacket decoded =
                PlacementResultS2CPacket.TYPE.read(buffer);
        assertEquals(packet, decoded);
        assertEquals(true, decoded.acceptedPlacement().orElseThrow().queued());
    }

    @Test
    void rejectedVariantRoundTripsWithoutOperationMetadata() {
        PlacementResultS2CPacket packet = PlacementResultS2CPacket.rejected(
                REQUEST_ID,
                PlacementResultCode.NOT_AUTHORIZED
        );
        FriendlyByteBuf buffer = PacketByteBufs.create();

        packet.write(buffer);

        PlacementResultS2CPacket decoded = PlacementResultS2CPacket.TYPE.read(buffer);
        assertEquals(packet, decoded);
        assertEquals(Optional.empty(), decoded.acceptedPlacement());
    }

    @Test
    void enforcesTheAcceptedDiscriminatedVariant() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new PlacementResultS2CPacket(
                                PlacementResultS2CPacket.CURRENT_PROTOCOL_VERSION,
                                REQUEST_ID,
                                PlacementResultCode.ACCEPTED,
                                Optional.empty()
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new PlacementResultS2CPacket(
                                PlacementResultS2CPacket.CURRENT_PROTOCOL_VERSION,
                                REQUEST_ID,
                                PlacementResultCode.WORLD_CONFLICT,
                                Optional.of(new AcceptedPlacement(OPERATION_ID, 1))
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> PlacementResultS2CPacket.rejected(REQUEST_ID, PlacementResultCode.ACCEPTED)
                )
        );
    }

    @Test
    void affectedBlockCountHasAClosedPositiveRange() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new AcceptedPlacement(OPERATION_ID, 0)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new AcceptedPlacement(OPERATION_ID, AcceptedPlacement.MAX_AFFECTED_BLOCK_COUNT + 1)
                )
        );
        assertEquals(
                AcceptedPlacement.MAX_AFFECTED_BLOCK_COUNT,
                new AcceptedPlacement(OPERATION_ID, AcceptedPlacement.MAX_AFFECTED_BLOCK_COUNT)
                        .affectedBlockCount()
        );
    }

    @Test
    void stableResultCodesHaveExplicitWireIds() {
        assertAll(
                () -> assertEquals(0, PlacementResultCode.ACCEPTED.wireId()),
                () -> assertEquals(1, PlacementResultCode.UNSUPPORTED_PROTOCOL.wireId()),
                () -> assertEquals(2, PlacementResultCode.MALFORMED_REQUEST.wireId()),
                () -> assertEquals(3, PlacementResultCode.DUPLICATE_REQUEST.wireId()),
                () -> assertEquals(4, PlacementResultCode.DIMENSION_MISMATCH.wireId()),
                () -> assertEquals(5, PlacementResultCode.UNKNOWN_PRESET.wireId()),
                () -> assertEquals(6, PlacementResultCode.PRESET_REVISION_MISMATCH.wireId()),
                () -> assertEquals(7, PlacementResultCode.INVALID_GEOMETRY.wireId()),
                () -> assertEquals(8, PlacementResultCode.CONSTRUCTION_LIMIT_EXCEEDED.wireId()),
                () -> assertEquals(9, PlacementResultCode.NOT_AUTHORIZED.wireId()),
                () -> assertEquals(10, PlacementResultCode.WORLD_CONFLICT.wireId()),
                () -> assertEquals(11, PlacementResultCode.UNSUPPORTED_WORLD_STATE.wireId()),
                () -> assertEquals(12, PlacementResultCode.MUTATION_FAILED.wireId()),
                () -> assertEquals(13, PlacementResultCode.INTERNAL_ERROR.wireId()),
                () -> assertEquals(14, PlacementResultCode.PERSISTENCE_INCOMPATIBLE.wireId()),
                () -> assertEquals(15, PlacementResultCode.RATE_LIMITED.wireId()),
                () -> assertEquals(
                        16,
                        PlacementResultCode.GRAPH_REVISION_MISMATCH.wireId()
                ),
                () -> assertEquals(
                        17,
                        PlacementResultCode.CONSTRUCTION_BUSY.wireId()
                )
        );
    }

    @Test
    void decoderRejectsUnknownResultCode() {
        FriendlyByteBuf buffer = PacketByteBufs.create();
        buffer.writeVarInt(PlacementResultS2CPacket.CURRENT_PROTOCOL_VERSION);
        buffer.writeUUID(REQUEST_ID);
        buffer.writeVarInt(99);

        assertThrows(RuntimeException.class, () -> PlacementResultS2CPacket.TYPE.read(buffer));
    }

    @Test
    void decoderRejectsTrailingAndOversizedPayloads() {
        FriendlyByteBuf trailing = PacketByteBufs.create();
        PlacementResultS2CPacket.rejected(REQUEST_ID, PlacementResultCode.INTERNAL_ERROR).write(trailing);
        trailing.writeByte(0x5a);

        FriendlyByteBuf oversized = PacketByteBufs.create();
        oversized.writeZero(PlacementResultS2CPacket.MAX_PAYLOAD_BYTES + 1);

        assertAll(
                () -> assertThrows(RuntimeException.class, () -> PlacementResultS2CPacket.TYPE.read(trailing)),
                () -> assertThrows(RuntimeException.class, () -> PlacementResultS2CPacket.TYPE.read(oversized))
        );
    }
}
