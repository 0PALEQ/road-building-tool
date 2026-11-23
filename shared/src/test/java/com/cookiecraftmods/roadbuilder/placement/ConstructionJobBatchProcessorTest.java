package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.test.MinecraftBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConstructionJobBatchProcessorTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftBootstrap.bootstrap();
    }

    @Test
    void appliesAndCancelsInBoundedIdempotentBatches() {
        MapWorld world = world(5);
        ConstructionJob job = job(5);
        ConstructionJobBatchProcessor processor = new ConstructionJobBatchProcessor(2);

        ConstructionJobBatchResult first = processor.process(job, world);
        ConstructionJobBatchResult second = processor.process(first.job(), world);
        ConstructionJob cancelling = second.job().requestCancellation();
        ConstructionJobBatchResult rollbackOne = processor.process(cancelling, world);
        ConstructionJobBatchResult rollbackTwo = processor.process(rollbackOne.job(), world);

        assertEquals(2, first.examinedTransitions());
        assertEquals(2, first.changedBlocks());
        assertEquals(4, second.job().cursor());
        assertEquals(ConstructionJobPhase.CANCELLING, rollbackOne.job().phase());
        assertEquals(2, rollbackOne.job().cursor());
        assertEquals(ConstructionJobPhase.CANCELLED, rollbackTwo.job().phase());
        assertEquals(0, rollbackTwo.job().cursor());
        for (int index = 0; index < 5; index++) {
            assertEquals(
                    Blocks.DIRT.defaultBlockState(),
                    world.blockState(new BlockPos(index, 64, 0))
            );
        }
    }

    @Test
    void resumeTreatsRecordedAfterStatesAsAlreadyApplied() {
        MapWorld world = world(3);
        world.states.put(
                new BlockPos(0, 64, 0),
                Blocks.GRAY_CONCRETE.defaultBlockState()
        );

        ConstructionJobBatchResult result =
                new ConstructionJobBatchProcessor(3).process(job(3), world);

        assertEquals(3, result.examinedTransitions());
        assertEquals(2, result.changedBlocks());
        assertEquals(ConstructionJobPhase.COMPLETING, result.job().phase());
    }

    @Test
    void appliesAndRestoresStructureBlocksInWater() {
        BlockPos position = new BlockPos(0, 48, 0);
        MapWorld placementWorld = new MapWorld();
        placementWorld.states.put(
                position,
                Blocks.WATER.defaultBlockState()
        );
        ConstructionJob placement = ConstructionJob.prepared(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ConstructionJobAction.PLACE,
                List.of(new ConstructionJobBlockTransition(
                        position,
                        Blocks.WATER.defaultBlockState(),
                        Blocks.STONE_BRICKS.defaultBlockState()
                ))
        );

        ConstructionJobBatchResult placed =
                new ConstructionJobBatchProcessor(1).process(
                        placement,
                        placementWorld
                );

        assertEquals(ConstructionJobPhase.COMPLETING, placed.job().phase());
        assertEquals(
                Blocks.STONE_BRICKS.defaultBlockState(),
                placementWorld.blockState(position)
        );

        ConstructionJob removal = ConstructionJob.prepared(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ConstructionJobAction.REMOVE,
                List.of(new ConstructionJobBlockTransition(
                        position,
                        Blocks.STONE_BRICKS.defaultBlockState(),
                        Blocks.WATER.defaultBlockState()
                ))
        );
        ConstructionJobBatchResult restored =
                new ConstructionJobBatchProcessor(1).process(
                        removal,
                        placementWorld
                );

        assertEquals(ConstructionJobPhase.COMPLETING, restored.job().phase());
        assertEquals(
                Blocks.WATER.defaultBlockState(),
                placementWorld.blockState(position)
        );
    }

    @Test
    void resumeAcceptsUpdateAdjustedPropertiesOnAPlacedTarget() {
        BlockPos position = new BlockPos(0, 64, 0);
        var plannedLeaves = Blocks.OAK_LEAVES.defaultBlockState()
                .setValue(BlockStateProperties.DISTANCE, 1);
        var updatedLeaves = plannedLeaves.setValue(
                BlockStateProperties.DISTANCE,
                2
        );
        MapWorld world = new MapWorld();
        world.states.put(position, updatedLeaves);
        ConstructionJob job = ConstructionJob.prepared(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ConstructionJobAction.PLACE,
                List.of(new ConstructionJobBlockTransition(
                        position,
                        Blocks.DIRT.defaultBlockState(),
                        plannedLeaves
                ))
        );

        ConstructionJobBatchResult result =
                new ConstructionJobBatchProcessor(1).process(job, world);

        assertEquals(ConstructionJobPhase.COMPLETING, result.job().phase());
        assertEquals(0, result.changedBlocks());
        assertEquals(updatedLeaves, world.blockState(position));
    }

    @Test
    void removalJobDoesNotMistakeClearedAirForRestoredLeaves() {
        BlockPos position = new BlockPos(0, 64, 0);
        var originalLeaves = Blocks.OAK_LEAVES.defaultBlockState()
                .setValue(BlockStateProperties.DISTANCE, 1);
        MapWorld world = new MapWorld();
        ConstructionJob job = ConstructionJob.prepared(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ConstructionJobAction.REMOVE,
                List.of(new ConstructionJobBlockTransition(
                        position,
                        Blocks.AIR.defaultBlockState(),
                        originalLeaves
                ))
        );

        ConstructionJobBatchResult result =
                new ConstructionJobBatchProcessor(1).process(job, world);

        assertEquals(ConstructionJobPhase.COMPLETING, result.job().phase());
        assertEquals(1, result.changedBlocks());
        assertEquals(originalLeaves, world.blockState(position));
    }

    @Test
    void removalAcceptsATallPlantThatBreaksDuringItsRestoreWrite() {
        BlockPos position = new BlockPos(0, 64, 0);
        BlockState upperTallGrass = Blocks.TALL_GRASS.defaultBlockState()
                .setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER);
        MapWorld world = new MapWorld();
        world.states.put(position, Blocks.AIR.defaultBlockState());
        world.stateAfterWrite.put(position, Blocks.AIR.defaultBlockState());
        ConstructionJob job = ConstructionJob.prepared(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ConstructionJobAction.REMOVE,
                List.of(new ConstructionJobBlockTransition(
                        position,
                        Blocks.AIR.defaultBlockState(),
                        upperTallGrass
                ))
        );

        ConstructionJobBatchResult result =
                new ConstructionJobBatchProcessor(1).process(job, world);

        assertEquals(ConstructionJobPhase.COMPLETING, result.job().phase());
        assertEquals(1, result.changedBlocks());
        assertEquals(Blocks.AIR.defaultBlockState(), world.blockState(position));
    }

    @Test
    void failedPlacementCleanupRestoresLeavesInsteadOfAcceptingTargetAir() {
        BlockPos position = new BlockPos(0, 64, 0);
        var originalLeaves = Blocks.OAK_LEAVES.defaultBlockState()
                .setValue(BlockStateProperties.DISTANCE, 1);
        MapWorld world = new MapWorld();
        BlockPos pending = position.east();
        world.states.put(pending, Blocks.DIRT.defaultBlockState());
        ConstructionJob cancelling = ConstructionJob.prepared(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ConstructionJobAction.PLACE,
                List.of(
                        new ConstructionJobBlockTransition(
                                position,
                                originalLeaves,
                                Blocks.AIR.defaultBlockState()
                        ),
                        new ConstructionJobBlockTransition(
                                pending,
                                Blocks.DIRT.defaultBlockState(),
                                Blocks.STONE.defaultBlockState()
                        )
                )
        ).start().advance(1).requestCancellation();

        ConstructionJobBatchResult result =
                new ConstructionJobBatchProcessor(1).process(cancelling, world);

        assertEquals(ConstructionJobPhase.CANCELLED, result.job().phase());
        assertEquals(1, result.changedBlocks());
        assertEquals(originalLeaves, world.blockState(position));
    }

    @Test
    void restartReconcilesNonPrefixChunkPersistenceFromActualStates() {
        MapWorld world = world(4);
        world.states.put(
                new BlockPos(0, 64, 0),
                Blocks.GRAY_CONCRETE.defaultBlockState()
        );
        world.states.put(
                new BlockPos(3, 64, 0),
                Blocks.GRAY_CONCRETE.defaultBlockState()
        );
        ConstructionJob persistedWithAdvisoryCursor =
                job(4).start().advance(2);

        ConstructionJobBatchResult result =
                new ConstructionJobBatchProcessor(4).process(
                        persistedWithAdvisoryCursor
                                .restartForWorldReconciliation(),
                        world
                );

        assertEquals(4, result.examinedTransitions());
        assertEquals(2, result.changedBlocks());
        assertEquals(ConstructionJobPhase.COMPLETING, result.job().phase());
        for (int index = 0; index < 4; index++) {
            assertEquals(
                    Blocks.GRAY_CONCRETE.defaultBlockState(),
                    world.blockState(new BlockPos(index, 64, 0))
            );
        }
    }

    @Test
    void externalStateQuarantinesWithoutOverwritingIt() {
        MapWorld world = world(3);
        BlockPos conflict = new BlockPos(1, 64, 0);
        world.states.put(conflict, Blocks.GOLD_BLOCK.defaultBlockState());

        ConstructionJobBatchResult result =
                new ConstructionJobBatchProcessor(3).process(job(3), world);

        assertEquals(ConstructionJobPhase.QUARANTINED, result.job().phase());
        assertEquals(1, result.job().cursor());
        assertEquals(conflict, result.job().conflictPosition().orElseThrow());
        assertEquals(Blocks.GOLD_BLOCK.defaultBlockState(), world.blockState(conflict));
    }

    @Test
    void failedPlacementCleanupPreservesExternalEditAndRemovesEveryTarget() {
        MapWorld world = world(3);
        BlockPos external = new BlockPos(1, 64, 0);
        world.states.put(
                new BlockPos(0, 64, 0),
                Blocks.GRAY_CONCRETE.defaultBlockState()
        );
        world.states.put(external, Blocks.GOLD_BLOCK.defaultBlockState());
        world.states.put(
                new BlockPos(2, 64, 0),
                Blocks.GRAY_CONCRETE.defaultBlockState()
        );
        ConstructionJob failed = job(3).start().advance(1).quarantine(
                external,
                "world state matches neither before nor after"
        ).rollbackFailedPlacement();

        ConstructionJobBatchResult result =
                new ConstructionJobBatchProcessor(3).process(failed, world);

        assertEquals(ConstructionJobPhase.CANCELLED, result.job().phase());
        assertEquals(
                Blocks.DIRT.defaultBlockState(),
                world.blockState(new BlockPos(0, 64, 0))
        );
        assertEquals(
                Blocks.GOLD_BLOCK.defaultBlockState(),
                world.blockState(external)
        );
        assertEquals(
                Blocks.DIRT.defaultBlockState(),
                world.blockState(new BlockPos(2, 64, 0))
        );
    }

    @Test
    void rejectedWriteQuarantinesAtTheFailedTransition() {
        MapWorld world = world(2);
        world.rejectedPosition = new BlockPos(1, 64, 0);

        ConstructionJobBatchResult result =
                new ConstructionJobBatchProcessor(2).process(job(2), world);

        assertEquals(ConstructionJobPhase.QUARANTINED, result.job().phase());
        assertEquals(1, result.job().cursor());
        assertEquals(world.rejectedPosition, result.job().conflictPosition().orElseThrow());
    }

    @Test
    void restartCleansUpdateDrivenPartialPlacementWithoutRewritingMissingPlant() {
        BlockPos position = new BlockPos(0, 64, 0);
        ConstructionJob job = ConstructionJob.prepared(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ConstructionJobAction.PLACE,
                List.of(new ConstructionJobBlockTransition(
                        position,
                        Blocks.TALL_GRASS.defaultBlockState().setValue(
                                DoublePlantBlock.HALF,
                                DoubleBlockHalf.LOWER
                        ),
                        Blocks.GRAY_CONCRETE.defaultBlockState()
                ))
        ).quarantine(
                position,
                "world state matches neither before nor after"
        ).restartForWorldReconciliation();
        MapWorld world = new MapWorld();

        ConstructionJobBatchResult result =
                new ConstructionJobBatchProcessor(1).process(job, world);

        assertEquals(ConstructionJobPhase.CANCELLED, result.job().phase());
        assertEquals(Blocks.AIR.defaultBlockState(), world.blockState(position));
    }

    private static ConstructionJob job(int count) {
        return ConstructionJob.prepared(
                UUID.fromString("77a49319-08aa-4479-a324-c85738832f56"),
                UUID.fromString("119881b0-2530-4931-b3f2-156c23043215"),
                ConstructionJobAction.PLACE,
                java.util.stream.IntStream.range(0, count)
                        .mapToObj(index -> new ConstructionJobBlockTransition(
                                new BlockPos(index, 64, 0),
                                Blocks.DIRT.defaultBlockState(),
                                Blocks.GRAY_CONCRETE.defaultBlockState()
                        ))
                        .toList()
        );
    }

    private static MapWorld world(int count) {
        MapWorld world = new MapWorld();
        for (int index = 0; index < count; index++) {
            world.states.put(
                    new BlockPos(index, 64, 0),
                    Blocks.DIRT.defaultBlockState()
            );
        }
        return world;
    }

    private static final class MapWorld implements PlacementBlockWorld {
        private final Map<BlockPos, BlockState> states = new HashMap<>();
        private final Map<BlockPos, BlockState> stateAfterWrite = new HashMap<>();
        private BlockPos rejectedPosition;

        @Override
        public ResourceKey<Level> dimension() {
            return Level.OVERWORLD;
        }

        @Override
        public BlockState blockState(BlockPos position) {
            return states.getOrDefault(position, Blocks.AIR.defaultBlockState());
        }

        @Override
        public boolean hasBlockEntity(BlockPos position) {
            return false;
        }

        @Override
        public boolean setBlockState(BlockPos position, BlockState state) {
            if (position.equals(rejectedPosition)) {
                return false;
            }
            states.put(
                    position.immutable(),
                    stateAfterWrite.getOrDefault(position, state)
            );
            return true;
        }
    }
}
