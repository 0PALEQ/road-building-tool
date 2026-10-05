package com.cookiecraftmods.roadbuilder.networking;

import com.cookiecraftmods.roadbuilder.persistence.RoadBuilderConstructionJobSavedData;
import com.cookiecraftmods.roadbuilder.placement.ConstructionJob;
import com.cookiecraftmods.roadbuilder.placement.ConstructionJobAction;
import com.cookiecraftmods.roadbuilder.placement.ConstructionJobBlockTransition;
import com.cookiecraftmods.roadbuilder.placement.ConstructionJobPhase;
import com.cookiecraftmods.roadbuilder.test.MinecraftBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstructionJobCancellationServiceTest {
    private static final UUID PLAYER_ID =
            UUID.fromString("14590e08-dc61-4d4f-ad27-0ee46b6c0f67");
    private static final UUID JOB_ID =
            UUID.fromString("a1f88ff1-461b-42b8-8852-e5192aa2aade");
    private static final ConstructionJobCancelC2SPacket REQUEST =
            ConstructionJobCancelC2SPacket.current(
                    UUID.fromString("d2ab0d9a-1086-44b9-bb6b-41cfe626231c"),
                    new ResourceLocation("minecraft", "overworld"),
                    JOB_ID
            );
    private final ConstructionJobCancellationService service =
            new ConstructionJobCancellationService();

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftBootstrap.bootstrap();
    }

    @Test
    void ownerCanCancelPreparedAndApplyingJobs() {
        RoadBuilderConstructionJobSavedData preparedData = data(job());
        ConstructionJobCancellationService.Outcome prepared =
                service.cancel(REQUEST, PLAYER_ID, preparedData);
        RoadBuilderConstructionJobSavedData applyingData =
                data(job().start().advance(1));
        ConstructionJobCancellationService.Outcome applying =
                service.cancel(REQUEST, PLAYER_ID, applyingData);

        assertEquals(
                ConstructionJobCancelResultCode.ACCEPTED,
                prepared.result().resultCode()
        );
        assertEquals(
                ConstructionJobPhase.CANCELLED,
                prepared.updatedJob().orElseThrow().phase()
        );
        assertEquals(
                ConstructionJobPhase.CANCELLING,
                applying.updatedJob().orElseThrow().phase()
        );
        assertEquals(
                applying.updatedJob().orElseThrow(),
                applyingData.job(JOB_ID).orElseThrow()
        );
    }

    @Test
    void cancellationRejectsUnknownForeignAndTerminalJobsWithoutMutation() {
        RoadBuilderConstructionJobSavedData unknown =
                RoadBuilderConstructionJobSavedData.create(Level.OVERWORLD);
        assertEquals(
                ConstructionJobCancelResultCode.UNKNOWN_JOB,
                service.cancel(REQUEST, PLAYER_ID, unknown).result().resultCode()
        );

        RoadBuilderConstructionJobSavedData foreign = data(job());
        assertEquals(
                ConstructionJobCancelResultCode.NOT_AUTHORIZED,
                service.cancel(REQUEST, UUID.randomUUID(), foreign)
                        .result().resultCode()
        );

        RoadBuilderConstructionJobSavedData completing =
                data(job().start().advance(2));
        assertEquals(
                ConstructionJobCancelResultCode.INVALID_PHASE,
                service.cancel(REQUEST, PLAYER_ID, completing)
                        .result().resultCode()
        );
        assertEquals(
                ConstructionJobPhase.COMPLETING,
                completing.job(JOB_ID).orElseThrow().phase()
        );
    }

    @Test
    void incompatiblePersistenceFailsClosed() {
        CompoundTag raw = new CompoundTag();
        raw.putInt("schema_version", 99);
        raw.put("jobs", new ListTag());
        RoadBuilderConstructionJobSavedData incompatible =
                RoadBuilderConstructionJobSavedData.load(Level.OVERWORLD, raw);

        ConstructionJobCancellationService.Outcome outcome =
                service.cancel(REQUEST, PLAYER_ID, incompatible);

        assertEquals(
                ConstructionJobCancelResultCode.PERSISTENCE_INCOMPATIBLE,
                outcome.result().resultCode()
        );
        assertTrue(outcome.updatedJob().isEmpty());
    }

    private static RoadBuilderConstructionJobSavedData data(ConstructionJob job) {
        RoadBuilderConstructionJobSavedData data =
                RoadBuilderConstructionJobSavedData.create(Level.OVERWORLD);
        data.add(job);
        return data;
    }

    private static ConstructionJob job() {
        return ConstructionJob.prepared(
                JOB_ID,
                PLAYER_ID,
                ConstructionJobAction.PLACE,
                List.of(
                        transition(0),
                        transition(1)
                )
        );
    }

    private static ConstructionJobBlockTransition transition(int x) {
        return new ConstructionJobBlockTransition(
                new BlockPos(x, 64, 0),
                Blocks.DIRT.defaultBlockState(),
                Blocks.GRAY_CONCRETE.defaultBlockState()
        );
    }
}
