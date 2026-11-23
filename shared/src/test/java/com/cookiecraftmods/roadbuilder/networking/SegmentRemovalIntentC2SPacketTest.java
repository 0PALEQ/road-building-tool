package com.cookiecraftmods.roadbuilder.networking;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import com.cookiecraftmods.roadbuilder.placement.RemovalMode;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SegmentRemovalIntentC2SPacketTest {
    private static final UUID REQUEST_ID =
            UUID.fromString("7d3641c3-fc3f-448a-aa87-89b81350c36b");
    private static final UUID SEGMENT_ID =
            UUID.fromString("bf5e41ea-5cda-421c-8365-290bab46c587");
    private static final ResourceLocation OVERWORLD =
            MinecraftIds.id("minecraft", "overworld");

    @Test
    void stableSegmentAndExpectedRevisionRoundTrip() {
        SegmentRemovalIntentC2SPacket packet = SegmentRemovalIntentC2SPacket.current(
                REQUEST_ID,
                OVERWORLD,
                SEGMENT_ID,
                42,
                RemovalRequestAction.PREVIEW,
                RemovalMode.SAFE
        );
        FriendlyByteBuf buffer = PacketByteBufs.create();

        packet.write(buffer);

        assertEquals(packet, SegmentRemovalIntentC2SPacket.TYPE.read(buffer));
        assertEquals(0, buffer.readableBytes());
        assertSame(SegmentRemovalIntentC2SPacket.TYPE, packet.packetType());
        assertEquals(
                MinecraftIds.id("roadbuilder", "segment_removal_intent_v1"),
                SegmentRemovalIntentC2SPacket.TYPE.id()
        );
    }

    @Test
    void rejectsNegativeRevisionAndTrailingPayload() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SegmentRemovalIntentC2SPacket.current(
                        REQUEST_ID,
                        OVERWORLD,
                        SEGMENT_ID,
                        -1,
                        RemovalRequestAction.EXECUTE,
                        RemovalMode.SAFE
                )
        );

        FriendlyByteBuf trailing = PacketByteBufs.create();
        SegmentRemovalIntentC2SPacket.current(
                REQUEST_ID,
                OVERWORLD,
                SEGMENT_ID,
                1,
                RemovalRequestAction.EXECUTE,
                RemovalMode.SAFE
        ).write(trailing);
        trailing.writeByte(0x5a);
        assertThrows(
                RuntimeException.class,
                () -> SegmentRemovalIntentC2SPacket.TYPE.read(trailing)
        );
    }
}
