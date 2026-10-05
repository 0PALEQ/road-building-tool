package com.cookiecraftmods.roadbuilder.networking;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StraightRoadIntentC2SPacketTest {
    private static final UUID REQUEST_ID = UUID.fromString("87d25380-69ee-4c92-bbfa-4df8783aec50");

    @Test
    void roundTripsThroughItsTypedCodec() {
        StraightRoadIntentC2SPacket packet =
                StraightRoadIntentC2SPacket.current(
                        REQUEST_ID,
                        new ResourceLocation("minecraft", "overworld"),
                        new ResourceLocation("roadbuilder", "two_way"),
                        7,
                        42L,
                        -3.25, 64.0, 9.5,
                        42.75, 64.0, -11.0
                );
        FriendlyByteBuf buffer = PacketByteBufs.create();

        packet.write(buffer);

        assertEquals(packet, StraightRoadIntentC2SPacket.TYPE.read(buffer));
        assertEquals(0, buffer.readableBytes());
        assertSame(StraightRoadIntentC2SPacket.TYPE, packet.getType());
        assertEquals(42L, packet.expectedGraphRevision());
        assertEquals(
                new ResourceLocation("roadbuilder", "straight_road_intent_v1"),
                StraightRoadIntentC2SPacket.TYPE.getId()
        );
    }

    @Test
    void rejectsInvalidRevisionCoordinatesAndProtocolAtConstruction() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> packetWithRevision(0)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new StraightRoadIntentC2SPacket(
                                StraightRoadIntentC2SPacket.CURRENT_PROTOCOL_VERSION,
                                REQUEST_ID,
                                new ResourceLocation("minecraft", "overworld"),
                                new ResourceLocation("roadbuilder", "two_way"),
                                1,
                                -2L,
                                0.0, 64.0, 0.0,
                                10.0, 64.0, 10.0
                        )
                ),
                () -> assertThrows(IllegalArgumentException.class, () -> packetWithStartX(Double.NaN)),
                () -> assertThrows(IllegalArgumentException.class, () -> packetWithStartX(Double.POSITIVE_INFINITY)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> packetWithStartX(StraightRoadIntentC2SPacket.MAX_ABSOLUTE_COORDINATE + 1.0)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new StraightRoadIntentC2SPacket(
                                2,
                                REQUEST_ID,
                                new ResourceLocation("minecraft", "overworld"),
                                new ResourceLocation("roadbuilder", "two_way"),
                                1,
                                0.0, 64.0, 0.0,
                                10.0, 64.0, 10.0
                        )
                )
        );
    }

    @Test
    void acceptsCoordinateBoundaryAndPositiveRevisionBoundary() {
        StraightRoadIntentC2SPacket packet = new StraightRoadIntentC2SPacket(
                StraightRoadIntentC2SPacket.CURRENT_PROTOCOL_VERSION,
                REQUEST_ID,
                new ResourceLocation("minecraft", "overworld"),
                new ResourceLocation("roadbuilder", "two_way"),
                Integer.MAX_VALUE,
                -StraightRoadIntentC2SPacket.MAX_ABSOLUTE_COORDINATE,
                StraightRoadIntentC2SPacket.MAX_ABSOLUTE_COORDINATE,
                0.0,
                StraightRoadIntentC2SPacket.MAX_ABSOLUTE_COORDINATE,
                -StraightRoadIntentC2SPacket.MAX_ABSOLUTE_COORDINATE,
                -0.0
        );

        assertEquals(Integer.MAX_VALUE, packet.expectedPresetRevision());
    }

    @Test
    void rejectsResourceIdentifiersOverTheProtocolLimit() {
        ResourceLocation longPresetId = new ResourceLocation(
                "roadbuilder",
                "a".repeat(StraightRoadIntentC2SPacket.MAX_ID_CHARACTERS)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> StraightRoadIntentC2SPacket.current(
                        REQUEST_ID,
                        new ResourceLocation("minecraft", "overworld"),
                        longPresetId,
                        1,
                        0.0, 64.0, 0.0,
                        10.0, 64.0, 10.0
                )
        );
    }

    @Test
    void decoderRejectsTrailingBytes() {
        FriendlyByteBuf buffer = PacketByteBufs.create();
        validPacket().write(buffer);
        buffer.writeByte(0x5a);

        assertThrows(RuntimeException.class, () -> StraightRoadIntentC2SPacket.TYPE.read(buffer));
    }

    @Test
    void decoderRejectsOversizedPayloadBeforeReadingIt() {
        FriendlyByteBuf buffer = PacketByteBufs.create();
        buffer.writeZero(StraightRoadIntentC2SPacket.MAX_PAYLOAD_BYTES + 1);

        assertThrows(RuntimeException.class, () -> StraightRoadIntentC2SPacket.TYPE.read(buffer));
    }

    @Test
    void decoderRejectsAnInvalidResourceIdentifier() {
        FriendlyByteBuf buffer = PacketByteBufs.create();
        buffer.writeVarInt(StraightRoadIntentC2SPacket.CURRENT_PROTOCOL_VERSION);
        buffer.writeUUID(REQUEST_ID);
        buffer.writeUtf("NOT A VALID ID", StraightRoadIntentC2SPacket.MAX_ID_CHARACTERS);

        assertThrows(RuntimeException.class, () -> StraightRoadIntentC2SPacket.TYPE.read(buffer));
    }

    private static StraightRoadIntentC2SPacket validPacket() {
        return StraightRoadIntentC2SPacket.current(
                REQUEST_ID,
                new ResourceLocation("minecraft", "overworld"),
                new ResourceLocation("roadbuilder", "two_way"),
                7,
                -3.25, 64.0, 9.5,
                42.75, 64.0, -11.0
        );
    }

    private static StraightRoadIntentC2SPacket packetWithRevision(int revision) {
        StraightRoadIntentC2SPacket packet = validPacket();
        return new StraightRoadIntentC2SPacket(
                packet.protocolVersion(), packet.requestId(), packet.dimensionId(), packet.presetId(), revision,
                packet.startX(), packet.startY(), packet.startZ(), packet.endX(), packet.endY(), packet.endZ()
        );
    }

    private static StraightRoadIntentC2SPacket packetWithStartX(double startX) {
        StraightRoadIntentC2SPacket packet = validPacket();
        return new StraightRoadIntentC2SPacket(
                packet.protocolVersion(), packet.requestId(), packet.dimensionId(), packet.presetId(),
                packet.expectedPresetRevision(),
                startX, packet.startY(), packet.startZ(), packet.endX(), packet.endY(), packet.endZ()
        );
    }
}
