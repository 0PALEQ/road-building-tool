package com.cookiecraftmods.roadbuilder.networking;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import com.cookiecraftmods.roadbuilder.placement.ConstructionJobAction;
import com.cookiecraftmods.roadbuilder.placement.ConstructionJobPhase;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConstructionJobProgressS2CPacketTest {
    private static final UUID JOB_ID =
            UUID.fromString("61429c58-3a32-42c0-b3ee-879867ddf476");
    private static final ResourceLocation DIMENSION =
            MinecraftIds.id("minecraft", "overworld");

    @Test
    void applyingAndQuarantinedVariantsRoundTrip() {
        ConstructionJobProgressS2CPacket applying = packet(
                ConstructionJobPhase.APPLYING,
                300,
                1_200,
                Optional.empty()
        );
        ConstructionJobProgressS2CPacket quarantined = packet(
                ConstructionJobPhase.QUARANTINED,
                301,
                1_200,
                Optional.of(new BlockPos(-5, 70, 9))
        );

        assertEquals(applying, roundTrip(applying));
        assertEquals(quarantined, roundTrip(quarantined));
        assertEquals(
                MinecraftIds.id("roadbuilder", "construction_job_progress_v1"),
                applying.packetType().id()
        );
    }

    @Test
    void enforcesCountsAndConflictDiscriminant() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> packet(
                                ConstructionJobPhase.APPLYING,
                                -1,
                                1,
                                Optional.empty()
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> packet(
                                ConstructionJobPhase.APPLYING,
                                2,
                                1,
                                Optional.empty()
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> packet(
                                ConstructionJobPhase.APPLYING,
                                0,
                                1,
                                Optional.of(BlockPos.ZERO)
                        )
                )
        );
    }

    @Test
    void decoderRejectsUnknownEnumsTrailingAndOversizedPayloads() {
        FriendlyByteBuf unknownAction = PacketByteBufs.create();
        unknownAction.writeVarInt(1);
        PacketValidation.writeBoundedId(unknownAction, DIMENSION);
        unknownAction.writeUUID(JOB_ID);
        unknownAction.writeVarInt(99);

        FriendlyByteBuf trailing = PacketByteBufs.create();
        packet(
                ConstructionJobPhase.APPLYING,
                0,
                1,
                Optional.empty()
        ).write(trailing);
        trailing.writeByte(1);

        FriendlyByteBuf oversized = PacketByteBufs.create();
        oversized.writeZero(ConstructionJobProgressS2CPacket.MAX_PAYLOAD_BYTES + 1);

        assertAll(
                () -> assertThrows(
                        RuntimeException.class,
                        () -> ConstructionJobProgressS2CPacket.TYPE.read(unknownAction)
                ),
                () -> assertThrows(
                        RuntimeException.class,
                        () -> ConstructionJobProgressS2CPacket.TYPE.read(trailing)
                ),
                () -> assertThrows(
                        RuntimeException.class,
                        () -> ConstructionJobProgressS2CPacket.TYPE.read(oversized)
                )
        );
    }

    private static ConstructionJobProgressS2CPacket packet(
            ConstructionJobPhase phase,
            int completed,
            int total,
            Optional<BlockPos> conflict
    ) {
        return new ConstructionJobProgressS2CPacket(
                ConstructionJobProgressS2CPacket.CURRENT_PROTOCOL_VERSION,
                DIMENSION,
                JOB_ID,
                ConstructionJobAction.PLACE,
                phase,
                completed,
                total,
                conflict
        );
    }

    private static ConstructionJobProgressS2CPacket roundTrip(
            ConstructionJobProgressS2CPacket packet
    ) {
        FriendlyByteBuf buffer = PacketByteBufs.create();
        packet.write(buffer);
        ConstructionJobProgressS2CPacket decoded =
                ConstructionJobProgressS2CPacket.TYPE.read(buffer);
        assertEquals(0, buffer.readableBytes());
        return decoded;
    }
}
