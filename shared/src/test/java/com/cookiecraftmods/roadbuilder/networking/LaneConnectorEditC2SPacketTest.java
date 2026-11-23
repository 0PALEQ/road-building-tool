package com.cookiecraftmods.roadbuilder.networking;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LaneConnectorEditC2SPacketTest {
    @Test
    void roundTripsBoundedLaneConnectorEdit() {
        LaneConnectorEditC2SPacket packet =
                LaneConnectorEditC2SPacket.current(
                        MinecraftIds.id("minecraft", "overworld"),
                        19,
                        new UUID(1, 2),
                        new UUID(3, 4)
                );
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        packet.write(buffer);

        assertEquals(packet, new LaneConnectorEditC2SPacket(buffer));
        assertEquals(0, buffer.readableBytes());
    }

    @Test
    void rejectsTheSameLaneAtBothEnds() {
        UUID laneId = new UUID(1, 2);

        assertThrows(IllegalArgumentException.class, () ->
                LaneConnectorEditC2SPacket.current(
                        MinecraftIds.id("minecraft", "overworld"),
                        0,
                        laneId,
                        laneId
                ));
    }
}
