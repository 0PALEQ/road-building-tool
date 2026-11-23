package com.cookiecraftmods.roadbuilder.networking;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import com.cookiecraftmods.roadbuilder.placement.RemovalMode;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RemovalIntentC2SPacketTest {
    private static final UUID REQUEST_ID = UUID.fromString("7d3641c3-fc3f-448a-aa87-89b81350c36b");
    private static final UUID OPERATION_ID = UUID.fromString("bf5e41ea-5cda-421c-8365-290bab46c587");
    private static final ResourceLocation OVERWORLD = MinecraftIds.id("minecraft", "overworld");

    @Test
    void previewAndExecuteRoundTripThroughTheirTypedCodec() {
        RemovalIntentC2SPacket preview = packet(RemovalRequestAction.PREVIEW, RemovalMode.SAFE);
        RemovalIntentC2SPacket execute = packet(RemovalRequestAction.EXECUTE, RemovalMode.FORCE);

        assertAll(
                () -> assertRoundTrip(preview),
                () -> assertRoundTrip(execute),
                () -> assertSame(RemovalIntentC2SPacket.TYPE, preview.packetType()),
                () -> assertEquals(
                        MinecraftIds.id("roadbuilder", "removal_intent_v1"),
                        RemovalIntentC2SPacket.TYPE.id()
                )
        );
    }

    @Test
    void stableActionsHaveExplicitWireIds() {
        assertAll(
                () -> assertEquals(0, RemovalRequestAction.PREVIEW.wireId()),
                () -> assertEquals(1, RemovalRequestAction.EXECUTE.wireId()),
                () -> assertEquals(RemovalRequestAction.PREVIEW, RemovalRequestAction.fromWireId(0).orElseThrow()),
                () -> assertEquals(RemovalRequestAction.EXECUTE, RemovalRequestAction.fromWireId(1).orElseThrow())
        );
    }

    @Test
    void constructorRejectsUnsupportedProtocolAndNullVariantFields() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new RemovalIntentC2SPacket(
                                2,
                                REQUEST_ID,
                                OVERWORLD,
                                OPERATION_ID,
                                RemovalRequestAction.PREVIEW,
                                RemovalMode.SAFE
                        )
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new RemovalIntentC2SPacket(
                                RemovalIntentC2SPacket.CURRENT_PROTOCOL_VERSION,
                                REQUEST_ID,
                                OVERWORLD,
                                OPERATION_ID,
                                null,
                                RemovalMode.SAFE
                        )
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new RemovalIntentC2SPacket(
                                RemovalIntentC2SPacket.CURRENT_PROTOCOL_VERSION,
                                REQUEST_ID,
                                OVERWORLD,
                                OPERATION_ID,
                                RemovalRequestAction.EXECUTE,
                                null
                        )
                )
        );
    }

    @Test
    void decoderRejectsUnknownVariantsTrailingBytesAndOversizedPayloads() {
        FriendlyByteBuf unknownAction = prefix();
        unknownAction.writeVarInt(99);
        unknownAction.writeVarInt(0);

        FriendlyByteBuf unknownMode = prefix();
        unknownMode.writeVarInt(RemovalRequestAction.EXECUTE.wireId());
        unknownMode.writeVarInt(99);

        FriendlyByteBuf trailing = PacketByteBufs.create();
        packet(RemovalRequestAction.PREVIEW, RemovalMode.SAFE).write(trailing);
        trailing.writeByte(0x5a);

        FriendlyByteBuf oversized = PacketByteBufs.create();
        oversized.writeZero(RemovalIntentC2SPacket.MAX_PAYLOAD_BYTES + 1);

        assertAll(
                () -> assertThrows(RuntimeException.class, () -> RemovalIntentC2SPacket.TYPE.read(unknownAction)),
                () -> assertThrows(RuntimeException.class, () -> RemovalIntentC2SPacket.TYPE.read(unknownMode)),
                () -> assertThrows(RuntimeException.class, () -> RemovalIntentC2SPacket.TYPE.read(trailing)),
                () -> assertThrows(RuntimeException.class, () -> RemovalIntentC2SPacket.TYPE.read(oversized))
        );
    }

    private static void assertRoundTrip(RemovalIntentC2SPacket packet) {
        FriendlyByteBuf buffer = PacketByteBufs.create();
        packet.write(buffer);
        assertEquals(packet, RemovalIntentC2SPacket.TYPE.read(buffer));
        assertEquals(0, buffer.readableBytes());
    }

    private static FriendlyByteBuf prefix() {
        FriendlyByteBuf buffer = PacketByteBufs.create();
        buffer.writeVarInt(RemovalIntentC2SPacket.CURRENT_PROTOCOL_VERSION);
        buffer.writeUUID(REQUEST_ID);
        buffer.writeUtf(OVERWORLD.toString(), RemovalIntentC2SPacket.MAX_ID_CHARACTERS);
        buffer.writeUUID(OPERATION_ID);
        return buffer;
    }

    private static RemovalIntentC2SPacket packet(
            RemovalRequestAction action,
            RemovalMode mode
    ) {
        return RemovalIntentC2SPacket.current(REQUEST_ID, OVERWORLD, OPERATION_ID, action, mode);
    }
}
