package com.cookiecraftmods.roadbuilder.networking;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConstructionJobCancelPacketTest {
    private static final UUID REQUEST_ID =
            UUID.fromString("d7b8e97d-83f9-4c60-84fd-3271c26e77f9");
    private static final UUID JOB_ID =
            UUID.fromString("6969e685-b9f7-4a4b-971d-b93af5bf32b5");
    private static final ResourceLocation DIMENSION =
            new ResourceLocation("minecraft", "overworld");

    @Test
    void requestAndEveryStableResultCodeRoundTrip() {
        ConstructionJobCancelC2SPacket request =
                ConstructionJobCancelC2SPacket.current(
                        REQUEST_ID,
                        DIMENSION,
                        JOB_ID
                );
        assertEquals(request, roundTrip(request));
        assertEquals(
                new ResourceLocation("roadbuilder", "construction_job_cancel_v1"),
                request.getType().getId()
        );

        for (ConstructionJobCancelResultCode code
                : ConstructionJobCancelResultCode.values()) {
            ConstructionJobCancelResultS2CPacket result =
                    ConstructionJobCancelResultS2CPacket.current(request, code);
            assertEquals(result, roundTrip(result));
            assertEquals(code.ordinal(), code.wireId());
        }
    }

    @Test
    void decodersRejectUnknownResultTrailingAndOversizedPayloads() {
        FriendlyByteBuf unknownResult = PacketByteBufs.create();
        unknownResult.writeVarInt(1);
        unknownResult.writeUUID(REQUEST_ID);
        unknownResult.writeUUID(JOB_ID);
        unknownResult.writeVarInt(99);

        FriendlyByteBuf trailing = PacketByteBufs.create();
        ConstructionJobCancelC2SPacket.current(
                REQUEST_ID,
                DIMENSION,
                JOB_ID
        ).write(trailing);
        trailing.writeByte(1);

        FriendlyByteBuf oversized = PacketByteBufs.create();
        oversized.writeZero(ConstructionJobCancelC2SPacket.MAX_PAYLOAD_BYTES + 1);

        assertAll(
                () -> assertThrows(
                        RuntimeException.class,
                        () -> ConstructionJobCancelResultS2CPacket.TYPE.read(unknownResult)
                ),
                () -> assertThrows(
                        RuntimeException.class,
                        () -> ConstructionJobCancelC2SPacket.TYPE.read(trailing)
                ),
                () -> assertThrows(
                        RuntimeException.class,
                        () -> ConstructionJobCancelC2SPacket.TYPE.read(oversized)
                )
        );
    }

    private static ConstructionJobCancelC2SPacket roundTrip(
            ConstructionJobCancelC2SPacket packet
    ) {
        FriendlyByteBuf buffer = PacketByteBufs.create();
        packet.write(buffer);
        ConstructionJobCancelC2SPacket decoded =
                ConstructionJobCancelC2SPacket.TYPE.read(buffer);
        assertEquals(0, buffer.readableBytes());
        return decoded;
    }

    private static ConstructionJobCancelResultS2CPacket roundTrip(
            ConstructionJobCancelResultS2CPacket packet
    ) {
        FriendlyByteBuf buffer = PacketByteBufs.create();
        packet.write(buffer);
        ConstructionJobCancelResultS2CPacket decoded =
                ConstructionJobCancelResultS2CPacket.TYPE.read(buffer);
        assertEquals(0, buffer.readableBytes());
        return decoded;
    }
}
