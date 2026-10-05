package com.cookiecraftmods.roadbuilder.persistence;

import com.cookiecraftmods.roadbuilder.placement.ConstructionJob;
import com.cookiecraftmods.roadbuilder.placement.ConstructionJobAction;
import com.cookiecraftmods.roadbuilder.placement.ConstructionJobBlockTransition;
import com.cookiecraftmods.roadbuilder.test.MinecraftBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadBuilderConstructionJobSavedDataTest {
    private static final UUID JOB_ID =
            UUID.fromString("5265432a-a01a-4976-8960-a348a3dd70e8");
    private static final UUID PLAYER_ID =
            UUID.fromString("22465a83-d494-4884-bb75-46eaefbb21ae");

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftBootstrap.bootstrap();
    }

    @Test
    void roundTripsCanonicalLifecycleAndTransitions() {
        ConstructionJob applying = job().start().advance(1);
        RoadBuilderConstructionJobSavedData data =
                RoadBuilderConstructionJobSavedData.create(Level.OVERWORLD);
        data.add(applying);

        CompoundTag saved = data.save(new CompoundTag());
        RoadBuilderConstructionJobSavedData restored =
                RoadBuilderConstructionJobSavedData.load(Level.OVERWORLD, saved);

        assertTrue(restored.isCompatible());
        assertEquals(List.of(applying), restored.jobs());
        assertEquals(applying, restored.job(JOB_ID).orElseThrow());
        assertEquals(saved, restored.save(new CompoundTag()));
    }

    @Test
    void replacementOnlyAdvancesLifecycleStateAndMarksDirty() {
        ConstructionJob prepared = job();
        RoadBuilderConstructionJobSavedData data =
                RoadBuilderConstructionJobSavedData.create(Level.OVERWORLD);
        data.add(prepared);
        data.save(new CompoundTag());
        data.setDirty(false);

        ConstructionJob applying = prepared.start();
        data.replace(applying);

        assertTrue(data.isDirty());
        assertEquals(applying, data.job(JOB_ID).orElseThrow());
        ConstructionJob changedPayload = ConstructionJob.prepared(
                JOB_ID,
                PLAYER_ID,
                ConstructionJobAction.REMOVE,
                prepared.transitions()
        );
        assertThrows(IllegalArgumentException.class, () -> data.replace(changedPayload));
    }

    @Test
    void loadedJobsEnterCursorIndependentWorldReconciliationOnce() {
        ConstructionJob applying = job().start().advance(1);
        RoadBuilderConstructionJobSavedData original =
                RoadBuilderConstructionJobSavedData.create(Level.OVERWORLD);
        original.add(applying);
        RoadBuilderConstructionJobSavedData restored =
                RoadBuilderConstructionJobSavedData.load(
                        Level.OVERWORLD,
                        original.save(new CompoundTag())
                );
        restored.setDirty(false);

        assertEquals(1, restored.prepareLoadedJobsForWorldReconciliation());
        assertEquals(0, restored.job(JOB_ID).orElseThrow().cursor());
        assertEquals(
                com.cookiecraftmods.roadbuilder.placement.ConstructionJobPhase.APPLYING,
                restored.job(JOB_ID).orElseThrow().phase()
        );
        assertTrue(restored.isDirty());
        assertEquals(0, restored.prepareLoadedJobsForWorldReconciliation());
    }

    @Test
    void incompatibleInputIsFailClosedAndRawPreserving() {
        CompoundTag unsupported = new CompoundTag();
        unsupported.putInt("schema_version", 99);
        unsupported.put("jobs", new net.minecraft.nbt.ListTag());

        RoadBuilderConstructionJobSavedData data =
                RoadBuilderConstructionJobSavedData.load(
                        Level.OVERWORLD,
                        unsupported
                );

        assertFalse(data.isCompatible());
        assertTrue(data.incompatibilityReason().orElseThrow().contains("unsupported"));
        assertEquals(unsupported, data.incompatibleRawTag().orElseThrow());
        assertEquals(unsupported, data.save(new CompoundTag()));
        assertThrows(IllegalStateException.class, data::jobs);
    }

    @Test
    void removalDeletesExactlyOneKnownJob() {
        RoadBuilderConstructionJobSavedData data =
                RoadBuilderConstructionJobSavedData.create(Level.OVERWORLD);
        ConstructionJob job = job();
        data.add(job);

        assertEquals(job, data.remove(JOB_ID));
        assertTrue(data.jobs().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> data.remove(JOB_ID));
    }

    private static ConstructionJob job() {
        return ConstructionJob.prepared(
                JOB_ID,
                PLAYER_ID,
                ConstructionJobAction.PLACE,
                List.of(
                        new ConstructionJobBlockTransition(
                                new BlockPos(4, 70, 1),
                                Blocks.DIRT.defaultBlockState(),
                                Blocks.GRAY_CONCRETE.defaultBlockState()
                        ),
                        new ConstructionJobBlockTransition(
                                new BlockPos(5, 70, 1),
                                Blocks.GRASS_BLOCK.defaultBlockState(),
                                Blocks.GRAY_CONCRETE.defaultBlockState()
                        )
                )
        );
    }
}
