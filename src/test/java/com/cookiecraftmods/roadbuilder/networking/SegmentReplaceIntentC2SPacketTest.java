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
                        new ResourceLocation("minecraft", "overworld"),
                        new UUID(3, 4),
                        19,
                        new ResourceLocation("roadbuilder", "two_way"),
                        2
                );
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        packet.write(buffer);
        assertEquals(packet, new SegmentReplaceIntentC2SPacket(buffer));
        assertEquals(0, buffer.readableBytes());
    }
}
