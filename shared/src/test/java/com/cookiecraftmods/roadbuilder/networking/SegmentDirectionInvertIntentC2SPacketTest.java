package com.cookiecraftmods.roadbuilder.networking;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SegmentDirectionInvertIntentC2SPacketTest {
    @Test
    void roundTripsBoundedDirectionInvertIntent() {
        SegmentDirectionInvertIntentC2SPacket packet =
                SegmentDirectionInvertIntentC2SPacket.current(
                        new UUID(1, 2),
                        MinecraftIds.id("minecraft", "overworld"),
                        new UUID(3, 4),
                        19
                );
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        packet.write(buffer);

        assertEquals(
                packet,
                new SegmentDirectionInvertIntentC2SPacket(buffer)
        );
        assertEquals(0, buffer.readableBytes());
    }

    @Test
    void rejectsNegativeGraphRevision() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SegmentDirectionInvertIntentC2SPacket.current(
                        new UUID(1, 2),
                        MinecraftIds.id("minecraft", "overworld"),
                        new UUID(3, 4),
                        -1
                )
        );
    }
}
