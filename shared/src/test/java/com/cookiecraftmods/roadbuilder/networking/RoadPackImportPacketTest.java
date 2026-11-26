package com.cookiecraftmods.roadbuilder.networking;

import com.cookiecraftmods.roadbuilder.preset.RoadPackArchive;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadPackImportPacketTest {
    private static final UUID REQUEST = new UUID(0, 1);

    @Test
    void permissionProbeAndLargestArchiveFitTheLegacyPayload() {
        var probe = PacketByteBufs.create();
        var upload = PacketByteBufs.create();
        try {
            RoadPackImportC2SPacket.current(REQUEST, new byte[0]).write(probe);
            byte[] archive = new byte[RoadPackArchive.MAXIMUM_ARCHIVE_BYTES];
            RoadPackImportC2SPacket.current(REQUEST, archive).write(upload);

            assertTrue(RoadPackImportC2SPacket.TYPE.read(probe).checksPermission());
            assertTrue(upload.readableBytes() < 32_767);
            var decoded = RoadPackImportC2SPacket.TYPE.read(upload);
            assertEquals(REQUEST, decoded.requestId());
            assertArrayEquals(archive, decoded.archive());
        } finally {
            probe.release();
            upload.release();
        }
    }

    @Test
    void decoderRejectsArchivesOverTheLimitAndResultCountsRoundTrip() {
        var oversized = PacketByteBufs.create();
        var resultBuffer = PacketByteBufs.create();
        try {
            oversized.writeVarInt(PacketValidation.PROTOCOL_VERSION);
            oversized.writeUUID(REQUEST);
            oversized.writeByteArray(new byte[RoadPackArchive.MAXIMUM_ARCHIVE_BYTES + 1]);
            assertThrows(RuntimeException.class, () -> RoadPackImportC2SPacket.TYPE.read(oversized));

            var result = RoadPackImportResultS2CPacket.imported(REQUEST, 2, 1);
            result.write(resultBuffer);
            assertEquals(result, RoadPackImportResultS2CPacket.TYPE.read(resultBuffer));
        } finally {
            oversized.release();
            resultBuffer.release();
        }
    }
}
