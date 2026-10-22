package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.test.MinecraftBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacementTransactionTest {
    private static final BlockPos ORIGIN = new BlockPos(4, 70, -3);

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftBootstrap.bootstrap();
    }

    @Test
    void positionSnapshotIsOrderedReadOnlyAndDetachedFromLaterChanges() {
        BlockPos east = ORIGIN.offset(1, 0, 0);
        MapBackedPlacementBlockWorld world = worldWith(ORIGIN, Blocks.DIRT.defaultBlockState());
        PlacementOwnershipLedger ledger = new PlacementOwnershipLedger(Level.OVERWORLD);
        assertInstanceOf(PlacementCommitResult.Success.class,
                commit(operationId(1), world, ledger, east, Blocks.STONE.defaultBlockState()));
        assertInstanceOf(PlacementCommitResult.Success.class,
                commit(operationId(2), world, ledger, ORIGIN, Blocks.STONE.defaultBlockState()));

        Map<BlockPos, PositionChangeStack> snapshot = ledger.positionStacks();
        assertEquals(List.of(ORIGIN, east), List.copyOf(snapshot.keySet()));
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.entrySet().iterator().next().setValue(snapshot.get(east)));

        assertInstanceOf(PlacementRemovalResult.Success.class,
                new PlacementRemovalService().remove(operationId(2), RemovalMode.SAFE, world, ledger));
        assertEquals(1, ledger.positionCount());
        assertEquals(2, snapshot.size());
        assertEquals(operationId(2), snapshot.get(ORIGIN).top().operationId());
    }

    @Test
    void commitsAndSafelyRemovesOneOperation() {
        MapBackedPlacementBlockWorld world = worldWith(ORIGIN, Blocks.DIRT.defaultBlockState());
        PlacementOwnershipLedger ledger = new PlacementOwnershipLedger(Level.OVERWORLD);
        PlacementOperationId operationId = operationId(1);

        PlacementCommitResult.Success commit = assertInstanceOf(
                PlacementCommitResult.Success.class,
                commit(operationId, world, ledger, ORIGIN, Blocks.STONE.defaultBlockState())
        );

        assertEquals(operationId, commit.operation().id());
        assertEquals(1, commit.changedBlockCount());
        assertEquals(Blocks.STONE.defaultBlockState(), world.blockState(ORIGIN));
        assertEquals(Blocks.STONE.defaultBlockState(), ledger.expectedVisibleState(ORIGIN).orElseThrow());

        PlacementRemovalResult.Success removal = assertInstanceOf(
                PlacementRemovalResult.Success.class,
                new PlacementRemovalService().remove(operationId, RemovalMode.SAFE, world, ledger)
        );

        assertEquals(1, removal.summary().restoredCount());
        assertEquals(0, removal.summary().conflictCount());
        assertEquals(Blocks.DIRT.defaultBlockState(), world.blockState(ORIGIN));
        assertTrue(ledger.operation(operationId).isEmpty());
        assertTrue(ledger.stack(ORIGIN).isEmpty());
    }

    @Test
    void commitsThroughWaterAndRestoresTheSourceFluid() {
        MapBackedPlacementBlockWorld world = worldWith(
                ORIGIN,
                Blocks.WATER.defaultBlockState()
        );
        PlacementOwnershipLedger ledger =
                new PlacementOwnershipLedger(Level.OVERWORLD);
        PlacementOperationId operationId = operationId(101);

        assertInstanceOf(
                PlacementCommitResult.Success.class,
                commit(
                        operationId,
                        world,
                        ledger,
                        ORIGIN,
                        Blocks.STONE_BRICKS.defaultBlockState()
                )
        );
        assertEquals(
                Blocks.STONE_BRICKS.defaultBlockState(),
                world.blockState(ORIGIN)
        );

        assertInstanceOf(
                PlacementRemovalResult.Success.class,
                new PlacementRemovalService().remove(
                        operationId,
                        RemovalMode.SAFE,
                        world,
                        ledger
                )
        );
        assertEquals(
                Blocks.WATER.defaultBlockState(),
                world.blockState(ORIGIN)
        );
    }

    @Test
    void unchangedCommitStillOwnsThePositionAndRemovalDoesNotWrite() {
        MapBackedPlacementBlockWorld world = worldWith(ORIGIN, Blocks.STONE.defaultBlockState());
        PlacementOwnershipLedger ledger = new PlacementOwnershipLedger(Level.OVERWORLD);
        PlacementOperationId operationId = operationId(2);

        PlacementCommitResult.Success commit = assertInstanceOf(
                PlacementCommitResult.Success.class,
                commit(operationId, world, ledger, ORIGIN, Blocks.STONE.defaultBlockState())
        );

        assertEquals(0, commit.changedBlockCount());
        assertTrue(ledger.operation(operationId).isPresent());
        assertEquals(0, world.writeAttempts());

        PlacementRemovalResult.Success removal = assertInstanceOf(
                PlacementRemovalResult.Success.class,
                new PlacementRemovalService().remove(operationId, RemovalMode.SAFE, world, ledger)
        );

        assertEquals(1, removal.summary().unchangedCount());
        assertEquals(0, removal.summary().restoredCount());
        assertEquals(0, world.writeAttempts());
        assertTrue(ledger.operation(operationId).isEmpty());
    }

    @Test
    void removingNewestOverlapFirstRestoresTheOlderLayerThenTheBase() {
        OverlapFixture fixture = twoLayers();

        PlacementRemovalResult.Success removeB = fixture.remove(fixture.operationB());
        assertEquals(1, removeB.summary().restoredCount());
        assertEquals(Blocks.STONE.defaultBlockState(), fixture.world().blockState(ORIGIN));
        assertTrue(fixture.ledger().operation(fixture.operationA()).isPresent());

        PlacementRemovalResult.Success removeA = fixture.remove(fixture.operationA());
        assertEquals(Blocks.DIRT.defaultBlockState(), fixture.world().blockState(ORIGIN));
        assertTrue(fixture.ledger().stack(ORIGIN).isEmpty());
    }

    @Test
    void removingOlderOverlapFirstLeavesNewestVisibleAndRebasesItsRestoration() {
        OverlapFixture fixture = twoLayers();
        fixture.world().resetWriteAttempts();

        PlacementRemovalResult.Success removeA = fixture.remove(fixture.operationA());

        assertEquals(1, removeA.summary().shadowedCount());
        assertEquals(0, fixture.world().writeAttempts());
        assertEquals(Blocks.GRASS_BLOCK.defaultBlockState(), fixture.world().blockState(ORIGIN));
        assertTrue(fixture.ledger().operation(fixture.operationA()).isEmpty());

        fixture.remove(fixture.operationB());
        assertEquals(Blocks.DIRT.defaultBlockState(), fixture.world().blockState(ORIGIN));
        assertTrue(fixture.ledger().stack(ORIGIN).isEmpty());
    }

    @Test
    void allThreeLayerRemovalOrdersUltimatelyRestoreTheBase() {
        List<List<Integer>> removalOrders = List.of(
                List.of(0, 1, 2),
                List.of(0, 2, 1),
                List.of(1, 0, 2),
                List.of(1, 2, 0),
                List.of(2, 0, 1),
                List.of(2, 1, 0)
        );

        for (List<Integer> removalOrder : removalOrders) {
            MapBackedPlacementBlockWorld world = worldWith(ORIGIN, Blocks.DIRT.defaultBlockState());
            PlacementOwnershipLedger ledger = new PlacementOwnershipLedger(Level.OVERWORLD);
            List<PlacementOperationId> operationIds = List.of(operationId(10), operationId(11), operationId(12));
            List<BlockState> targets = List.of(
                    Blocks.STONE.defaultBlockState(),
                    Blocks.GRASS_BLOCK.defaultBlockState(),
                    Blocks.SAND.defaultBlockState()
            );
            for (int index = 0; index < operationIds.size(); index++) {
                assertInstanceOf(
                        PlacementCommitResult.Success.class,
                        commit(operationIds.get(index), world, ledger, ORIGIN, targets.get(index))
                );
            }

            for (int index : removalOrder) {
                assertInstanceOf(
                        PlacementRemovalResult.Success.class,
                        new PlacementRemovalService().remove(
                                operationIds.get(index),
                                RemovalMode.SAFE,
                                world,
                                ledger
                        ),
                        "removal order " + removalOrder
                );
            }

            assertEquals(
                    Blocks.DIRT.defaultBlockState(),
                    world.blockState(ORIGIN),
                    "removal order " + removalOrder
            );
            assertTrue(ledger.operations().isEmpty(), "removal order " + removalOrder);
            assertTrue(ledger.stack(ORIGIN).isEmpty(), "removal order " + removalOrder);
        }
    }

    @Test
    void safeRemovalPreservesExternalEditButReleasesOwnership() {
        MapBackedPlacementBlockWorld world = worldWith(ORIGIN, Blocks.DIRT.defaultBlockState());
        PlacementOwnershipLedger ledger = new PlacementOwnershipLedger(Level.OVERWORLD);
        PlacementOperationId operationId = operationId(20);
        commit(operationId, world, ledger, ORIGIN, Blocks.STONE.defaultBlockState());
        world.externalSet(ORIGIN, Blocks.GRASS_BLOCK.defaultBlockState());
        world.resetWriteAttempts();

        PlacementRemovalResult.Success removal = assertInstanceOf(
                PlacementRemovalResult.Success.class,
                new PlacementRemovalService().remove(operationId, RemovalMode.SAFE, world, ledger)
        );

        assertEquals(1, removal.summary().conflictCount());
        assertEquals(List.of(ORIGIN), removal.summary().conflictPositions());
        assertEquals(0, world.writeAttempts());
        assertEquals(Blocks.GRASS_BLOCK.defaultBlockState(), world.blockState(ORIGIN));
        assertTrue(ledger.operation(operationId).isEmpty());
        assertTrue(ledger.stack(ORIGIN).isEmpty());
    }

    @Test
    void forceRemovalOverwritesSupportedExternalEdit() {
        MapBackedPlacementBlockWorld world = worldWith(ORIGIN, Blocks.DIRT.defaultBlockState());
        PlacementOwnershipLedger ledger = new PlacementOwnershipLedger(Level.OVERWORLD);
        PlacementOperationId operationId = operationId(21);
        commit(operationId, world, ledger, ORIGIN, Blocks.STONE.defaultBlockState());
        world.externalSet(ORIGIN, Blocks.GRASS_BLOCK.defaultBlockState());

        PlacementRemovalResult.Success removal = assertInstanceOf(
                PlacementRemovalResult.Success.class,
                new PlacementRemovalService().remove(operationId, RemovalMode.FORCE, world, ledger)
        );

        assertEquals(1, removal.summary().forcedConflictCount());
        assertEquals(1, removal.summary().restoredCount());
        assertEquals(Blocks.DIRT.defaultBlockState(), world.blockState(ORIGIN));
        assertTrue(ledger.operation(operationId).isEmpty());
    }

    @Test
    void forceRemovalRejectsFluidAndRetainsOwnership() {
        MapBackedPlacementBlockWorld world = worldWith(ORIGIN, Blocks.DIRT.defaultBlockState());
        PlacementOwnershipLedger ledger = new PlacementOwnershipLedger(Level.OVERWORLD);
        PlacementOperationId operationId = operationId(22);
        commit(operationId, world, ledger, ORIGIN, Blocks.STONE.defaultBlockState());
        world.externalSet(ORIGIN, Blocks.WATER.defaultBlockState());

        PlacementRemovalResult.Failure failure = assertInstanceOf(
                PlacementRemovalResult.Failure.class,
                new PlacementRemovalService().remove(operationId, RemovalMode.FORCE, world, ledger)
        );

        assertEquals(PlacementRemovalFailureCode.FORCE_CURRENT_FLUID_UNSUPPORTED, failure.code());
        assertEquals(List.of(ORIGIN), failure.positions());
        assertEquals(Blocks.WATER.defaultBlockState(), world.blockState(ORIGIN));
        assertTrue(ledger.operation(operationId).isPresent());
    }

    @Test
    void forceRemovalRejectsBlockEntityAndRetainsOwnership() {
        MapBackedPlacementBlockWorld world = worldWith(ORIGIN, Blocks.DIRT.defaultBlockState());
        PlacementOwnershipLedger ledger = new PlacementOwnershipLedger(Level.OVERWORLD);
        PlacementOperationId operationId = operationId(23);
        commit(operationId, world, ledger, ORIGIN, Blocks.STONE.defaultBlockState());
        world.externalSet(ORIGIN, Blocks.CHEST.defaultBlockState());

        PlacementRemovalResult.Failure failure = assertInstanceOf(
                PlacementRemovalResult.Failure.class,
                new PlacementRemovalService().remove(operationId, RemovalMode.FORCE, world, ledger)
        );

        assertEquals(PlacementRemovalFailureCode.FORCE_CURRENT_BLOCK_ENTITY_UNSUPPORTED, failure.code());
        assertEquals(List.of(ORIGIN), failure.positions());
        assertEquals(Blocks.CHEST.defaultBlockState(), world.blockState(ORIGIN));
        assertTrue(ledger.operation(operationId).isPresent());
    }

    @Test
    void commitWriteFailureRollsBackWorldAndLeavesLedgerUnchanged() {
        BlockPos second = ORIGIN.east();
        MapBackedPlacementBlockWorld world = new MapBackedPlacementBlockWorld(Level.OVERWORLD);
        world.externalSet(ORIGIN, Blocks.DIRT.defaultBlockState());
        world.externalSet(second, Blocks.DIRT.defaultBlockState());
        world.rejectWritesAt(second);
        PlacementOwnershipLedger ledger = new PlacementOwnershipLedger(Level.OVERWORLD);
        PlacementOperationId operationId = operationId(30);
        BlockPlacementPlan plan = BlockPlacementPlan.create(Level.OVERWORLD, List.of(
                new PlannedBlock(ORIGIN, Blocks.STONE.defaultBlockState()),
                new PlannedBlock(second, Blocks.STONE.defaultBlockState())
        ));

        PlacementCommitResult.Failure failure = assertInstanceOf(
                PlacementCommitResult.Failure.class,
                new BlockPlanCommitter().commit(operationId, plan, world, ledger)
        );

        assertEquals(PlacementCommitFailureCode.WORLD_WRITE_REJECTED, failure.code());
        assertEquals(List.of(second), failure.positions());
        assertEquals(Blocks.DIRT.defaultBlockState(), world.blockState(ORIGIN));
        assertEquals(Blocks.DIRT.defaultBlockState(), world.blockState(second));
        assertEquals(0, ledger.revision());
        assertTrue(ledger.operation(operationId).isEmpty());
        assertTrue(ledger.positionStacks().isEmpty());
    }

    @Test
    void commitClearsTopDownAndAdoptsAPlannedTargetProducedByItsOwnUpdate() {
        BlockPos surface = ORIGIN;
        BlockPos vegetation = surface.above();
        BlockPos canopy = vegetation.above();
        MapBackedPlacementBlockWorld world = new MapBackedPlacementBlockWorld(
                Level.OVERWORLD
        );
        world.externalSet(surface, Blocks.DIRT.defaultBlockState());
        world.externalSet(vegetation, Blocks.GRASS.defaultBlockState());
        world.externalSet(canopy, Blocks.OAK_LEAVES.defaultBlockState());
        world.changeAfterNextSuccessfulWrite(
                canopy,
                vegetation,
                Blocks.AIR.defaultBlockState()
        );
        PlacementOwnershipLedger ledger = new PlacementOwnershipLedger(
                Level.OVERWORLD
        );
        PlacementOperationId operationId = operationId(45);
        BlockPlacementPlan plan = BlockPlacementPlan.create(
                Level.OVERWORLD,
                List.of(
                        new PlannedBlock(
                                surface,
                                Blocks.GRAY_CONCRETE.defaultBlockState()
                        ),
                        new PlannedBlock(
                                vegetation,
                                Blocks.AIR.defaultBlockState()
                        ),
                        new PlannedBlock(
                                canopy,
                                Blocks.AIR.defaultBlockState()
                        )
                )
        );

        PlacementCommitResult.Success result = assertInstanceOf(
                PlacementCommitResult.Success.class,
                new BlockPlanCommitter().commit(
                        operationId,
                        plan,
                        world,
                        ledger
                )
        );

        assertEquals(3, result.changedBlockCount());
        assertEquals(Blocks.GRAY_CONCRETE.defaultBlockState(), world.blockState(surface));
        assertEquals(Blocks.AIR.defaultBlockState(), world.blockState(vegetation));
        assertEquals(Blocks.AIR.defaultBlockState(), world.blockState(canopy));
        BlockChangeLayer vegetationLayer = ledger.stack(vegetation)
                .orElseThrow()
                .layer(operationId)
                .orElseThrow();
        assertEquals(Blocks.GRASS.defaultBlockState(), vegetationLayer.beforeState());
        assertEquals(Blocks.AIR.defaultBlockState(), vegetationLayer.afterState());
    }

    @Test
    void commitRecordsUpdateAdjustedTargetAndRemovesItSafely() {
        MapBackedPlacementBlockWorld world = worldWith(
                ORIGIN,
                Blocks.DIRT.defaultBlockState()
        );
        var plannedLeaves = Blocks.OAK_LEAVES.defaultBlockState()
                .setValue(BlockStateProperties.DISTANCE, 1);
        var updatedLeaves = plannedLeaves.setValue(
                BlockStateProperties.DISTANCE,
                2
        );
        world.changeAfterNextSuccessfulWrite(
                ORIGIN,
                ORIGIN,
                updatedLeaves
        );
        PlacementOwnershipLedger ledger = new PlacementOwnershipLedger(
                Level.OVERWORLD
        );
        PlacementOperationId operationId = operationId(46);

        assertInstanceOf(
                PlacementCommitResult.Success.class,
                commit(operationId, world, ledger, ORIGIN, plannedLeaves)
        );

        assertEquals(updatedLeaves, world.blockState(ORIGIN));
        assertEquals(
                updatedLeaves,
                ledger.stack(ORIGIN).orElseThrow().top().afterState()
        );
        assertInstanceOf(
                PlacementRemovalResult.Success.class,
                new PlacementRemovalService().remove(
                        operationId,
                        RemovalMode.SAFE,
                        world,
                        ledger
                )
        );
        assertEquals(Blocks.DIRT.defaultBlockState(), world.blockState(ORIGIN));
    }

    @Test
    void removalAdoptsRestorationProducedByItsOwnNeighborUpdate() {
        BlockPos second = ORIGIN.east();
        MapBackedPlacementBlockWorld world = new MapBackedPlacementBlockWorld(
                Level.OVERWORLD
        );
        world.externalSet(ORIGIN, Blocks.DIRT.defaultBlockState());
        world.externalSet(second, Blocks.DIRT.defaultBlockState());
        PlacementOwnershipLedger ledger = new PlacementOwnershipLedger(
                Level.OVERWORLD
        );
        PlacementOperationId operationId = operationId(47);
        BlockPlacementPlan plan = BlockPlacementPlan.create(
                Level.OVERWORLD,
                List.of(
                        new PlannedBlock(ORIGIN, Blocks.STONE.defaultBlockState()),
                        new PlannedBlock(second, Blocks.STONE.defaultBlockState())
                )
        );
        assertInstanceOf(
                PlacementCommitResult.Success.class,
                new BlockPlanCommitter().commit(operationId, plan, world, ledger)
        );
        world.changeAfterNextSuccessfulWrite(
                ORIGIN,
                second,
                Blocks.DIRT.defaultBlockState()
        );

        assertInstanceOf(
                PlacementRemovalResult.Success.class,
                new PlacementRemovalService().remove(
                        operationId,
                        RemovalMode.SAFE,
                        world,
                        ledger
                )
        );

        assertEquals(Blocks.DIRT.defaultBlockState(), world.blockState(ORIGIN));
        assertEquals(Blocks.DIRT.defaultBlockState(), world.blockState(second));
        assertTrue(ledger.operation(operationId).isEmpty());
    }

    @Test
    void removalRestoresVegetationOwnedAsClearedAir() {
        var originalLeaves = Blocks.OAK_LEAVES.defaultBlockState()
                .setValue(BlockStateProperties.DISTANCE, 1);
        MapBackedPlacementBlockWorld world = worldWith(ORIGIN, originalLeaves);
        PlacementOwnershipLedger ledger = new PlacementOwnershipLedger(
                Level.OVERWORLD
        );
        PlacementOperationId operationId = operationId(48);
        assertInstanceOf(
                PlacementCommitResult.Success.class,
                commit(
                        operationId,
                        world,
                        ledger,
                        ORIGIN,
                        Blocks.AIR.defaultBlockState()
                )
        );
        assertTrue(world.blockState(ORIGIN).isAir());

        assertInstanceOf(
                PlacementRemovalResult.Success.class,
                new PlacementRemovalService().remove(
                        operationId,
                        RemovalMode.SAFE,
                        world,
                        ledger
                )
        );

        assertEquals(originalLeaves, world.blockState(ORIGIN));
        assertTrue(ledger.operation(operationId).isEmpty());
    }

    @Test
    void removalWriteFailureRollsBackWorldAndLeavesLedgerUnchanged() {
        BlockPos second = ORIGIN.east();
        MapBackedPlacementBlockWorld world = new MapBackedPlacementBlockWorld(Level.OVERWORLD);
        world.externalSet(ORIGIN, Blocks.DIRT.defaultBlockState());
        world.externalSet(second, Blocks.DIRT.defaultBlockState());
        PlacementOwnershipLedger ledger = new PlacementOwnershipLedger(Level.OVERWORLD);
        PlacementOperationId operationId = operationId(31);
        BlockPlacementPlan plan = BlockPlacementPlan.create(Level.OVERWORLD, List.of(
                new PlannedBlock(ORIGIN, Blocks.STONE.defaultBlockState()),
                new PlannedBlock(second, Blocks.STONE.defaultBlockState())
        ));
        assertInstanceOf(
                PlacementCommitResult.Success.class,
                new BlockPlanCommitter().commit(operationId, plan, world, ledger)
        );
        long revisionBeforeRemoval = ledger.revision();
        world.rejectWritesAt(second);

        PlacementRemovalResult.Failure failure = assertInstanceOf(
                PlacementRemovalResult.Failure.class,
                new PlacementRemovalService().remove(operationId, RemovalMode.SAFE, world, ledger)
        );

        assertEquals(PlacementRemovalFailureCode.WORLD_WRITE_REJECTED, failure.code());
        assertEquals(List.of(second), failure.positions());
        assertEquals(Blocks.STONE.defaultBlockState(), world.blockState(ORIGIN));
        assertEquals(Blocks.STONE.defaultBlockState(), world.blockState(second));
        assertEquals(revisionBeforeRemoval, ledger.revision());
        assertTrue(ledger.operation(operationId).isPresent());
        assertEquals(2, ledger.positionStacks().size());
    }

    @Test
    void recoveryRemovalDiscardsOwnershipWhenAWorldWriteIsRejected() {
        MapBackedPlacementBlockWorld world = worldWith(
                ORIGIN,
                Blocks.DIRT.defaultBlockState()
        );
        PlacementOwnershipLedger ledger = new PlacementOwnershipLedger(
                Level.OVERWORLD
        );
        PlacementOperationId operationId = operationId(49);
        assertInstanceOf(
                PlacementCommitResult.Success.class,
                commit(
                        operationId,
                        world,
                        ledger,
                        ORIGIN,
                        Blocks.STONE.defaultBlockState()
                )
        );
        world.rejectWritesAt(ORIGIN);

        PlacementRemovalResult.Success result = assertInstanceOf(
                PlacementRemovalResult.Success.class,
                new PlacementRemovalService().removeBestEffort(
                        operationId,
                        world,
                        ledger
                )
        );

        assertEquals(0, result.summary().restoredCount());
        assertEquals(1, result.summary().unchangedCount());
        assertTrue(ledger.operation(operationId).isEmpty());
        assertTrue(ledger.stack(ORIGIN).isEmpty());
    }

    @Test
    void ownershipLimitRejectsBeforeAnyWorldWrite() {
        BlockPos second = ORIGIN.east();
        MapBackedPlacementBlockWorld world = new MapBackedPlacementBlockWorld(Level.OVERWORLD);
        world.externalSet(ORIGIN, Blocks.DIRT.defaultBlockState());
        world.externalSet(second, Blocks.DIRT.defaultBlockState());
        PlacementOwnershipLedger ledger = new PlacementOwnershipLedger(
                Level.OVERWORLD,
                new PlacementOwnershipLimits(1, 2, 2),
                () -> { }
        );
        BlockPlacementPlan plan = BlockPlacementPlan.create(Level.OVERWORLD, List.of(
                new PlannedBlock(ORIGIN, Blocks.STONE.defaultBlockState()),
                new PlannedBlock(second, Blocks.STONE.defaultBlockState())
        ));

        PlacementCommitResult.Failure failure = assertInstanceOf(
                PlacementCommitResult.Failure.class,
                new BlockPlanCommitter().commit(operationId(40), plan, world, ledger)
        );

        assertEquals(PlacementCommitFailureCode.OWNERSHIP_LIMIT_EXCEEDED, failure.code());
        assertEquals(0, world.writeAttempts());
        assertEquals(0, ledger.positionCount());
        assertEquals(0, ledger.totalLayerCount());
    }

    @Test
    void stackDepthLimitRejectsAnOverlapBeforeAnyAdditionalWrite() {
        MapBackedPlacementBlockWorld world = worldWith(ORIGIN, Blocks.DIRT.defaultBlockState());
        PlacementOwnershipLedger ledger = new PlacementOwnershipLedger(
                Level.OVERWORLD,
                new PlacementOwnershipLimits(1, 2, 1),
                () -> { }
        );
        assertInstanceOf(
                PlacementCommitResult.Success.class,
                commit(operationId(41), world, ledger, ORIGIN, Blocks.STONE.defaultBlockState())
        );
        world.resetWriteAttempts();

        PlacementCommitResult.Failure failure = assertInstanceOf(
                PlacementCommitResult.Failure.class,
                commit(operationId(42), world, ledger, ORIGIN, Blocks.GRASS_BLOCK.defaultBlockState())
        );

        assertEquals(PlacementCommitFailureCode.OWNERSHIP_LIMIT_EXCEEDED, failure.code());
        assertEquals(0, world.writeAttempts());
        assertEquals(Blocks.STONE.defaultBlockState(), world.blockState(ORIGIN));
        assertEquals(1, ledger.totalLayerCount());
    }

    @Test
    void finalCommitVerificationDetectsANeighborCallbackChangingAnEarlierTarget() {
        BlockPos second = ORIGIN.east();
        MapBackedPlacementBlockWorld world = new MapBackedPlacementBlockWorld(Level.OVERWORLD);
        world.externalSet(ORIGIN, Blocks.DIRT.defaultBlockState());
        world.externalSet(second, Blocks.DIRT.defaultBlockState());
        world.changeAfterNextSuccessfulWrite(second, ORIGIN, Blocks.SAND.defaultBlockState());
        PlacementOwnershipLedger ledger = new PlacementOwnershipLedger(Level.OVERWORLD);
        BlockPlacementPlan plan = BlockPlacementPlan.create(Level.OVERWORLD, List.of(
                new PlannedBlock(ORIGIN, Blocks.STONE.defaultBlockState()),
                new PlannedBlock(second, Blocks.STONE.defaultBlockState())
        ));

        PlacementCommitResult.Failure failure = assertInstanceOf(
                PlacementCommitResult.Failure.class,
                new BlockPlanCommitter().commit(operationId(43), plan, world, ledger)
        );

        assertEquals(PlacementCommitFailureCode.ROLLBACK_INCOMPLETE, failure.code());
        assertEquals(List.of(ORIGIN), failure.positions());
        assertEquals(Blocks.SAND.defaultBlockState(), world.blockState(ORIGIN));
        assertEquals(Blocks.DIRT.defaultBlockState(), world.blockState(second));
        assertTrue(ledger.operations().isEmpty());
    }

    @Test
    void restorationOverlayRecordsAnEarlierTargetAfterItEvolves() {
        BlockPos second = ORIGIN.east();
        MapBackedPlacementBlockWorld world =
                new MapBackedPlacementBlockWorld(Level.OVERWORLD);
        world.externalSet(ORIGIN, Blocks.STONE.defaultBlockState());
        world.externalSet(second, Blocks.STONE.defaultBlockState());
        world.changeAfterNextSuccessfulWrite(
                second,
                ORIGIN,
                Blocks.SAND.defaultBlockState()
        );
        PlacementOwnershipLedger ledger =
                new PlacementOwnershipLedger(Level.OVERWORLD);
        PlacementOperationId operationId = operationId(45);
        BlockPlacementPlan plan = BlockPlacementPlan.create(
                Level.OVERWORLD,
                List.of(
                        new PlannedBlock(ORIGIN, Blocks.DIRT.defaultBlockState()),
                        new PlannedBlock(second, Blocks.DIRT.defaultBlockState())
                )
        );

        assertInstanceOf(
                PlacementCommitResult.Success.class,
                new BlockPlanCommitter().commitRestorationOverlay(
                        operationId,
                        plan,
                        world,
                        ledger
                )
        );

        assertEquals(
                Blocks.SAND.defaultBlockState(),
                ledger.stack(ORIGIN).orElseThrow().top().afterState()
        );
        assertEquals(
                Blocks.DIRT.defaultBlockState(),
                ledger.stack(second).orElseThrow().top().afterState()
        );
    }

    @Test
    void restorationOverlayAcceptsVegetationThatImmediatelyUpdatesAway() {
        MapBackedPlacementBlockWorld world =
                new MapBackedPlacementBlockWorld(Level.OVERWORLD);
        world.externalSet(ORIGIN, Blocks.AIR.defaultBlockState());
        world.changeAfterNextSuccessfulWrite(
                ORIGIN,
                ORIGIN,
                Blocks.AIR.defaultBlockState()
        );
        PlacementOwnershipLedger ledger =
                new PlacementOwnershipLedger(Level.OVERWORLD);
        BlockState upperTallGrass = Blocks.TALL_GRASS.defaultBlockState()
                .setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER);

        assertInstanceOf(
                PlacementCommitResult.Success.class,
                new BlockPlanCommitter().commitRestorationOverlay(
                        operationId(46),
                        BlockPlacementPlan.create(
                                Level.OVERWORLD,
                                List.of(new PlannedBlock(ORIGIN, upperTallGrass))
                        ),
                        world,
                        ledger
                )
        );

        assertTrue(world.blockState(ORIGIN).isAir());
        assertTrue(ledger.stack(ORIGIN).orElseThrow().top().afterState().isAir());
    }

    @Test
    void removalPublishesWhenACallbackEvolvesAnEarlierRestore() {
        BlockPos second = ORIGIN.east();
        MapBackedPlacementBlockWorld world = new MapBackedPlacementBlockWorld(Level.OVERWORLD);
        world.externalSet(ORIGIN, Blocks.DIRT.defaultBlockState());
        world.externalSet(second, Blocks.DIRT.defaultBlockState());
        PlacementOwnershipLedger ledger = new PlacementOwnershipLedger(Level.OVERWORLD);
        PlacementOperationId operationId = operationId(44);
        BlockPlacementPlan plan = BlockPlacementPlan.create(Level.OVERWORLD, List.of(
                new PlannedBlock(ORIGIN, Blocks.STONE.defaultBlockState()),
                new PlannedBlock(second, Blocks.STONE.defaultBlockState())
        ));
        assertInstanceOf(
                PlacementCommitResult.Success.class,
                new BlockPlanCommitter().commit(operationId, plan, world, ledger)
        );
        world.changeAfterNextSuccessfulWrite(second, ORIGIN, Blocks.SAND.defaultBlockState());

        assertInstanceOf(
                PlacementRemovalResult.Success.class,
                new PlacementRemovalService().remove(operationId, RemovalMode.SAFE, world, ledger)
        );

        assertEquals(Blocks.SAND.defaultBlockState(), world.blockState(ORIGIN));
        assertEquals(Blocks.DIRT.defaultBlockState(), world.blockState(second));
        assertTrue(ledger.operation(operationId).isEmpty());
    }

    private static PlacementCommitResult commit(
            PlacementOperationId operationId,
            MapBackedPlacementBlockWorld world,
            PlacementOwnershipLedger ledger,
            BlockPos position,
            BlockState targetState
    ) {
        BlockPlacementPlan plan = BlockPlacementPlan.create(
                world.dimension(),
                List.of(new PlannedBlock(position, targetState))
        );
        return new BlockPlanCommitter().commit(operationId, plan, world, ledger);
    }

    private static OverlapFixture twoLayers() {
        MapBackedPlacementBlockWorld world = worldWith(ORIGIN, Blocks.DIRT.defaultBlockState());
        PlacementOwnershipLedger ledger = new PlacementOwnershipLedger(Level.OVERWORLD);
        PlacementOperationId operationA = operationId(3);
        PlacementOperationId operationB = operationId(4);
        assertInstanceOf(
                PlacementCommitResult.Success.class,
                commit(operationA, world, ledger, ORIGIN, Blocks.STONE.defaultBlockState())
        );
        assertInstanceOf(
                PlacementCommitResult.Success.class,
                commit(operationB, world, ledger, ORIGIN, Blocks.GRASS_BLOCK.defaultBlockState())
        );
        return new OverlapFixture(world, ledger, operationA, operationB);
    }

    private static MapBackedPlacementBlockWorld worldWith(BlockPos position, BlockState state) {
        MapBackedPlacementBlockWorld world = new MapBackedPlacementBlockWorld(Level.OVERWORLD);
        world.externalSet(position, state);
        return world;
    }

    private static PlacementOperationId operationId(long lowBits) {
        return new PlacementOperationId(new UUID(0, lowBits));
    }

    private record OverlapFixture(
            MapBackedPlacementBlockWorld world,
            PlacementOwnershipLedger ledger,
            PlacementOperationId operationA,
            PlacementOperationId operationB
    ) {
        private PlacementRemovalResult.Success remove(PlacementOperationId operationId) {
            return assertInstanceOf(
                    PlacementRemovalResult.Success.class,
                    new PlacementRemovalService().remove(operationId, RemovalMode.SAFE, world, ledger)
            );
        }
    }

    private static final class MapBackedPlacementBlockWorld implements PlacementBlockWorld {
        private final ResourceKey<Level> dimension;
        private final Map<BlockPos, BlockState> states = new HashMap<>();
        private final Set<BlockPos> rejectedWritePositions = new HashSet<>();
        private final Map<BlockPos, Runnable> afterSuccessfulWrite = new HashMap<>();
        private int writeAttempts;

        private MapBackedPlacementBlockWorld(ResourceKey<Level> dimension) {
            this.dimension = dimension;
        }

        @Override
        public ResourceKey<Level> dimension() {
            return dimension;
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
            writeAttempts++;
            if (rejectedWritePositions.contains(position)) {
                return false;
            }
            states.put(position.immutable(), state);
            Runnable action = afterSuccessfulWrite.remove(position);
            if (action != null) {
                action.run();
            }
            return true;
        }

        private void externalSet(BlockPos position, BlockState state) {
            states.put(position.immutable(), state);
        }

        private void rejectWritesAt(BlockPos position) {
            rejectedWritePositions.add(position.immutable());
        }

        private void changeAfterNextSuccessfulWrite(
                BlockPos trigger,
                BlockPos changedPosition,
                BlockState changedState
        ) {
            afterSuccessfulWrite.put(
                    trigger.immutable(),
                    () -> externalSet(changedPosition, changedState)
            );
        }

        private int writeAttempts() {
            return writeAttempts;
        }

        private void resetWriteAttempts() {
            writeAttempts = 0;
        }
    }
}
