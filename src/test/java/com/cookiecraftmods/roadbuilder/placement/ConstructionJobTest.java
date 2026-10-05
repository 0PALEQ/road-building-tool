package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.test.MinecraftBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstructionJobTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftBootstrap.bootstrap();
    }

    @Test
    void advancesThroughPreparedApplyingAndCompleting() {
        ConstructionJob prepared = job(3);

        ConstructionJob firstBatch = prepared.start().advance(2);
        ConstructionJob complete = firstBatch.advance(1);

        assertEquals(ConstructionJobPhase.APPLYING, firstBatch.phase());
        assertEquals(2, firstBatch.cursor());
        assertEquals(ConstructionJobPhase.COMPLETING, complete.phase());
        assertEquals(3, complete.cursor());
    }

    @Test
    void cancellationRollsTheAppliedPrefixBackToZero() {
        ConstructionJob applying = job(4).start().advance(3);

        ConstructionJob cancelling = applying.requestCancellation();
        ConstructionJob partlyRolledBack = cancelling.rollback(2);
        ConstructionJob cancelled = partlyRolledBack.rollback(1);

        assertEquals(ConstructionJobPhase.CANCELLING, cancelling.phase());
        assertEquals(3, cancelling.cursor());
        assertEquals(1, partlyRolledBack.cursor());
        assertEquals(ConstructionJobPhase.CANCELLED, cancelled.phase());
        assertEquals(0, cancelled.cursor());
    }

    @Test
    void quarantineRetainsCursorConflictAndBoundedDiagnostic() {
        BlockPos conflict = new BlockPos(2, 64, 0);
        ConstructionJob quarantined = job(3)
                .start()
                .advance(2)
                .quarantine(conflict, "external block state");

        assertEquals(ConstructionJobPhase.QUARANTINED, quarantined.phase());
        assertEquals(2, quarantined.cursor());
        assertEquals(conflict, quarantined.conflictPosition().orElseThrow());
        assertEquals("external block state", quarantined.diagnostic().orElseThrow());
        assertThrows(
                IllegalArgumentException.class,
                () -> job(1).quarantine(null, "x".repeat(257))
        );
    }

    @Test
    void restartDiscardsAdvisoryCursorForRollForwardAndRollback() {
        ConstructionJob applying = job(4).start().advance(3);
        ConstructionJob cancelled = applying
                .requestCancellation()
                .rollback(3);

        ConstructionJob rollForward =
                applying.restartForWorldReconciliation();
        ConstructionJob rollBack =
                cancelled.restartForWorldReconciliation();

        assertEquals(ConstructionJobPhase.APPLYING, rollForward.phase());
        assertEquals(0, rollForward.cursor());
        assertEquals(ConstructionJobPhase.CANCELLING, rollBack.phase());
        assertEquals(4, rollBack.cursor());
    }

    @Test
    void restartRollsBackOnlyKnownUpdateDrivenVegetationQuarantine() {
        BlockPos conflict = new BlockPos(0, 64, 0);
        ConstructionJobBlockTransition vegetation =
                new ConstructionJobBlockTransition(
                        conflict,
                        Blocks.TALL_GRASS.defaultBlockState().setValue(
                                DoublePlantBlock.HALF,
                                DoubleBlockHalf.LOWER
                        ),
                        Blocks.GRAY_CONCRETE.defaultBlockState()
                );
        ConstructionJob retryable = ConstructionJob.prepared(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ConstructionJobAction.PLACE,
                List.of(vegetation)
        ).quarantine(
                conflict,
                "world state matches neither before nor after"
        );
        ConstructionJob solidConflict = job(1).quarantine(
                conflict,
                "world state matches neither before nor after"
        );

        ConstructionJob restarted =
                retryable.restartForWorldReconciliation();

        assertEquals(ConstructionJobPhase.CANCELLING, restarted.phase());
        assertEquals(1, restarted.cursor());
        assertEquals(
                ConstructionJobPhase.QUARANTINED,
                solidConflict.restartForWorldReconciliation().phase()
        );
    }

    @Test
    void rejectsInvalidLifecycleMovementAndDuplicateTransitions() {
        ConstructionJob prepared = job(2);
        ConstructionJobBlockTransition duplicate = prepared.transitions().get(0);

        assertThrows(IllegalStateException.class, () -> prepared.advance(1));
        assertThrows(IllegalArgumentException.class, () -> prepared.start().advance(3));
        assertThrows(
                IllegalArgumentException.class,
                () -> ConstructionJob.prepared(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        ConstructionJobAction.PLACE,
                        List.of(duplicate, duplicate)
                )
        );
    }

    @Test
    void onlyAdvancingPhasesBlockOtherGraphMutations() {
        ConstructionJob prepared = job(2);
        ConstructionJob applying = prepared.start();
        ConstructionJob completing = applying.advance(2);
        ConstructionJob cancelling = job(2).start().advance(1)
                .requestCancellation();
        ConstructionJob cancelled = cancelling.rollback(1);
        ConstructionJob quarantined = applying.quarantine(
                null,
                "operator diagnosis required"
        );

        assertTrue(prepared.phase().blocksGraphMutation());
        assertTrue(applying.phase().blocksGraphMutation());
        assertTrue(completing.phase().blocksGraphMutation());
        assertTrue(cancelling.phase().blocksGraphMutation());
        assertFalse(cancelled.phase().blocksGraphMutation());
        assertFalse(quarantined.phase().blocksGraphMutation());
    }

    private static ConstructionJob job(int transitions) {
        List<ConstructionJobBlockTransition> values = java.util.stream.IntStream
                .range(0, transitions)
                .mapToObj(index -> new ConstructionJobBlockTransition(
                        new BlockPos(index, 64, 0),
                        Blocks.DIRT.defaultBlockState(),
                        Blocks.GRAY_CONCRETE.defaultBlockState()
                ))
                .toList();
        return ConstructionJob.prepared(
                UUID.fromString("ac0f87ad-74ed-4a3e-bae0-279702dfaa01"),
                UUID.fromString("48bf96ba-ac65-4b68-8798-d85ce47ddb13"),
                ConstructionJobAction.PLACE,
                values
        );
    }
}
