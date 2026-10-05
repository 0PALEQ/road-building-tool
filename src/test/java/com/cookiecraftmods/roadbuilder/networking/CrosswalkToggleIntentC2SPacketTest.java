package com.cookiecraftmods.roadbuilder.networking;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CrosswalkToggleIntentC2SPacketTest {
    @Test
    void roundTripsBoundedCrosswalkIntent() {
        CrosswalkToggleIntentC2SPacket packet =
                CrosswalkToggleIntentC2SPacket.current(
                        new UUID(1, 2),
                        new ResourceLocation("minecraft", "overworld"),
                        new UUID(3, 4),
                        new UUID(5, 6),
                        19
                );
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        packet.write(buffer);

        assertEquals(packet, new CrosswalkToggleIntentC2SPacket(buffer));
        assertEquals(0, buffer.readableBytes());
    }

    @Test
    void rejectsNegativeGraphRevision() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CrosswalkToggleIntentC2SPacket.current(
                        new UUID(1, 2),
                        new ResourceLocation("minecraft", "overworld"),
                        new UUID(3, 4),
                        new UUID(5, 6),
                        -1
                )
        );
    }
}
