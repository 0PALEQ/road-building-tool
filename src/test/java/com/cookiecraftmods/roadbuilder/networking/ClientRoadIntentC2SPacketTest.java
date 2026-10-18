package com.cookiecraftmods.roadbuilder.networking;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.preset.RoadPreset;
import com.cookiecraftmods.roadbuilder.preset.RoadPresetTemplates;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ClientRoadIntentC2SPacketTest {
    private static final ResourceLocation DIMENSION = new ResourceLocation("minecraft", "overworld");
    private static final ResourceLocation PRESET = new ResourceLocation("player_pack", "avenue");

    @Test
    void roundTripsLayoutWithEachConstructionAction() {
        RoadPreset preset = RoadPresetTemplates.blank();
        UUID request = UUID.randomUUID();
        List<FabricPacket> intents = List.of(
                StraightRoadIntentC2SPacket.current(request, DIMENSION, PRESET, preset.revision(),
                        5L, 0, 64, 0, 20, 64, 0),
                RoadPathIntentC2SPacket.current(request, DIMENSION, PRESET, preset.revision(), 5L,
                        RoadPathIntentC2SPacket.PathType.QUADRATIC,
                        List.of(new RoadPoint(0, 64, 0), new RoadPoint(10, 64, 5), new RoadPoint(20, 64, 0))),
                SegmentReplaceIntentC2SPacket.current(request, DIMENSION, UUID.randomUUID(), 5L,
                        PRESET, preset.revision()));
        for (FabricPacket intent : intents) {
            var packet = new ClientRoadIntentC2SPacket(intent, preset);
            var buffer = PacketByteBufs.create();
            try {
                packet.write(buffer);
                var decoded = ClientRoadIntentC2SPacket.TYPE.read(buffer);
                assertEquals(packet, decoded);
                assertEquals(request, decoded.requestId());
                assertEquals(PRESET, decoded.presetId());
                assertEquals(0, buffer.readableBytes());
            } finally { buffer.release(); }
        }
    }

    @Test
    void rejectsMismatchedRevision() {
        var intent = StraightRoadIntentC2SPacket.current(UUID.randomUUID(), DIMENSION, PRESET,
                99, 0, 64, 0, 20, 64, 0);
        assertThrows(IllegalArgumentException.class,
                () -> new ClientRoadIntentC2SPacket(intent, RoadPresetTemplates.blank()));
    }

    @Test
    void rejectsOversizedAndTrailingPayloads() {
        var oversized = PacketByteBufs.create();
        try {
            oversized.writeZero(ClientRoadIntentC2SPacket.MAX_PAYLOAD_BYTES + 1);
            assertThrows(IllegalArgumentException.class, () -> new ClientRoadIntentC2SPacket(oversized));
        } finally { oversized.release(); }
        var buffer = PacketByteBufs.create();
        try {
            var preset = RoadPresetTemplates.blank();
            new ClientRoadIntentC2SPacket(StraightRoadIntentC2SPacket.current(UUID.randomUUID(), DIMENSION,
                    PRESET, preset.revision(), 0, 64, 0, 20, 64, 0), preset).write(buffer);
            buffer.writeByte(0);
            assertThrows(IllegalArgumentException.class, () -> new ClientRoadIntentC2SPacket(buffer));
        } finally { buffer.release(); }
    }
}
