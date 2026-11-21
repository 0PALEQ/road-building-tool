package com.cookiecraftmods.roadbuilder.networking;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SegmentReplaceIntentC2SPacketTest {
    @Test
    void roundTripsBoundedReplacementIntent() {
        SegmentReplaceIntentC2SPacket packet =
                SegmentReplaceIntentC2SPacket.current(
                        new UUID(1, 2),
                        new ResourceLocation("test", "d".repeat(105)),
                        new UUID(3, 4),
                        19,
                        new ResourceLocation("test", "p".repeat(104)),
                        2
                );
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.write(buffer);
            assertEquals(packet, new SegmentReplaceIntentC2SPacket(buffer));
            assertEquals(0, buffer.readableBytes());

            var sunken = packet.withSunkenRoad(true);
            sunken.write(buffer);
            assertEquals(SegmentReplaceIntentC2SPacket.MAX_PAYLOAD_BYTES, buffer.readableBytes());
            assertEquals(sunken, new SegmentReplaceIntentC2SPacket(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }
}
