package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.StraightGeometry;
import com.cookiecraftmods.roadbuilder.network.PreparedRoadAddition;
import com.cookiecraftmods.roadbuilder.network.RoadGraph;
import com.cookiecraftmods.roadbuilder.persistence.RoadBuilderConstructionJobSavedData;
import com.cookiecraftmods.roadbuilder.preset.LaneComponent;
import com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection;
import com.cookiecraftmods.roadbuilder.preset.LaneType;
import com.cookiecraftmods.roadbuilder.preset.RoadPreset;
import com.cookiecraftmods.roadbuilder.preset.TrafficHandedness;
import com.cookiecraftmods.roadbuilder.test.MinecraftBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncrementalRemovalJobServiceTest {
    private static final PlacementOperationId OPERATION_ID = new PlacementOperationId(
            UUID.fromString("47ed2e75-c218-4818-b657-bd847b2cf791")
    );
    private static final UUID PLAYER_ID =
            UUID.fromString("284cc72c-a33f-4435-b4b6-ee92b7082b61");

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftBootstrap.bootstrap();
    }

    @Test
    void expensiveRemovalAppliesInBatchesThenPublishesOwnershipRemoval() {
        Fixture fixture = fixture();
        IncrementalRemovalJobService service =
                new IncrementalRemovalJobService(new PlacementRemovalService(), 1);
        IncrementalRemovalJobService.EnqueueResult.Queued queued =
                assertInstanceOf(
                        IncrementalRemovalJobService.EnqueueResult.Queued.class,
                        service.enqueueIfExpensive(
                                PLAYER_ID,
                                OPERATION_ID,
                                RemovalMode.SAFE,
                                fixture.world,
                                fixture.ledger,
                                fixture.jobs
                        )
                );

        IncrementalRemovalJobService.TickResult first = service.tick(
                queued.job(),
                1,
                fixture.world,
                fixture.ledger,
                fixture.graph
        );
        IncrementalRemovalJobService.TickResult second = service.tick(
                first.job(),
                1,
                fixture.world,
                fixture.ledger,
                fixture.graph
        );
        IncrementalRemovalJobService.TickResult third = service.tick(
                second.job(),
                1,
                fixture.world,
                fixture.ledger,
                fixture.graph
        );

        assertEquals(ConstructionJobPhase.APPLYING, first.job().phase());
        assertEquals(1, first.job().cursor());
        assertEquals(2, second.job().cursor());
        assertEquals(
                IncrementalRemovalJobService.TickOutcome.COMPLETED,
                third.outcome()
        );
        assertEquals(3, third.job().cursor());
        assertFalse(fixture.ledger.operation(OPERATION_ID).isPresent());
        for (int index = 0; index < 3; index++) {
            assertEquals(
                    Blocks.DIRT.defaultBlockState(),
                    fixture.world.blockState(position(index))
            );
        }
    }

    @Test
    void smallValidRemovalRemainsImmediateInsteadOfBeingRejected() {
        Fixture fixture = fixture();
        IncrementalRemovalJobService service =
                new IncrementalRemovalJobService(new PlacementRemovalService(), 3);

        IncrementalRemovalJobService.EnqueueResult result =
                service.enqueueIfExpensive(
                        PLAYER_ID,
                        OPERATION_ID,
                        RemovalMode.SAFE,
                        fixture.world,
                        fixture.ledger,
                        fixture.jobs
                );

        assertInstanceOf(
                IncrementalRemovalJobService.EnqueueResult.Immediate.class,
                result
        );
        assertTrue(fixture.jobs.jobs().isEmpty());
        assertTrue(fixture.ledger.operation(OPERATION_ID).isPresent());
    }

    @Test
    void cancellationRollsBackAppliedPrefixAndKeepsOwnership() {
        Fixture fixture = fixture();
        IncrementalRemovalJobService service =
                new IncrementalRemovalJobService(new PlacementRemovalService(), 1);
        ConstructionJob queued = assertInstanceOf(
                IncrementalRemovalJobService.EnqueueResult.Queued.class,
                service.enqueueIfExpensive(
                        PLAYER_ID,
                        OPERATION_ID,
                        RemovalMode.SAFE,
                        fixture.world,
                        fixture.ledger,
                        fixture.jobs
                )
        ).job();
        ConstructionJob applying = service.tick(
                queued,
                1,
                fixture.world,
                fixture.ledger,
                fixture.graph
        ).job();

        IncrementalRemovalJobService.TickResult cancelled = service.tick(
                applying.requestCancellation(),
                1,
                fixture.world,
                fixture.ledger,
                fixture.graph
        );

        assertEquals(
                IncrementalRemovalJobService.TickOutcome.CANCELLED,
                cancelled.outcome()
        );
        assertTrue(fixture.ledger.operation(OPERATION_ID).isPresent());
        for (int index = 0; index < 3; index++) {
            assertEquals(
                    Blocks.GRAY_CONCRETE.defaultBlockState(),
                    fixture.world.blockState(position(index))
            );
        }
    }

    @Test
    void terminalBatchRemovesOwnedSegmentAfterWorldCompletion() {
        Fixture fixture = graphOwnedFixture();
        IncrementalRemovalJobService service =
                new IncrementalRemovalJobService(new PlacementRemovalService(), 1);
        ConstructionJob queued = assertInstanceOf(
                IncrementalRemovalJobService.EnqueueResult.Queued.class,
                service.enqueueIfExpensive(
                        PLAYER_ID,
                        OPERATION_ID,
                        RemovalMode.SAFE,
                        fixture.world,
                        fixture.ledger,
                        fixture.jobs
                )
        ).job();
        var segmentId = fixture.ledger.operation(OPERATION_ID)
                .orElseThrow()
                .materializationOwner()
                .orElseThrow()
                .segmentId();

        IncrementalRemovalJobService.TickResult result = service.tick(
                queued,
                3,
                fixture.world,
                fixture.ledger,
                fixture.graph
        );

        assertEquals(
                IncrementalRemovalJobService.TickOutcome.COMPLETED,
                result.outcome()
        );
        assertTrue(result.graphChanged());
        assertFalse(fixture.graph.segment(segmentId).isPresent());
        assertFalse(fixture.ledger.operation(OPERATION_ID).isPresent());
    }

    @Test
    void finalizationAcceptsAnEarlierRestoreThatEvolvedBetweenBatches() {
        Fixture fixture = fixture();
        IncrementalRemovalJobService service =
                new IncrementalRemovalJobService(new PlacementRemovalService(), 1);
        ConstructionJob queued = assertInstanceOf(
                IncrementalRemovalJobService.EnqueueResult.Queued.class,
                service.enqueueIfExpensive(
                        PLAYER_ID,
                        OPERATION_ID,
                        RemovalMode.SAFE,
                        fixture.world,
                        fixture.ledger,
                        fixture.jobs
                )
        ).job();
        ConstructionJob firstBatch = service.tick(
                queued,
                1,
                fixture.world,
                fixture.ledger,
                fixture.graph
        ).job();
        fixture.world.states.put(
                position(0),
                Blocks.SAND.defaultBlockState()
        );

        IncrementalRemovalJobService.TickResult result = service.tick(
                firstBatch,
                2,
                fixture.world,
                fixture.ledger,
                fixture.graph
        );

        assertEquals(
                IncrementalRemovalJobService.TickOutcome.COMPLETED,
                result.outcome()
        );
        assertEquals(
                Blocks.SAND.defaultBlockState(),
                fixture.world.blockState(position(0))
        );
        assertFalse(fixture.ledger.operation(OPERATION_ID).isPresent());
    }

    @Test
    void legacyFinalizationQuarantineRecoversWithoutRewritingTerrain() {
        Fixture fixture = fixture();
        IncrementalRemovalJobService service =
                new IncrementalRemovalJobService(new PlacementRemovalService(), 1);
        ConstructionJob queued = assertInstanceOf(
                IncrementalRemovalJobService.EnqueueResult.Queued.class,
                service.enqueueIfExpensive(
                        PLAYER_ID,
                        OPERATION_ID,
                        RemovalMode.SAFE,
                        fixture.world,
                        fixture.ledger,
                        fixture.jobs
                )
        ).job();
        for (int index = 0; index < 3; index++) {
            fixture.world.states.put(
                    position(index),
                    Blocks.DIRT.defaultBlockState()
            );
        }
        ConstructionJob legacy = queued.start().advance(3).quarantine(
                null,
                "incremental removal finalization failed: completed removal transition changed before publication"
        );

        IncrementalRemovalJobService.TickResult result = service.tick(
                legacy,
                3,
                fixture.world,
                fixture.ledger,
                fixture.graph
        );

        assertEquals(
                IncrementalRemovalJobService.TickOutcome.COMPLETED,
                result.outcome()
        );
        assertFalse(fixture.ledger.operation(OPERATION_ID).isPresent());
    }

    @Test
    void legacyAcceptedWriteQuarantineContinuesAtTheNextTransition() {
        Fixture fixture = fixture();
        IncrementalRemovalJobService service =
                new IncrementalRemovalJobService(new PlacementRemovalService(), 1);
        ConstructionJob queued = assertInstanceOf(
                IncrementalRemovalJobService.EnqueueResult.Queued.class,
                service.enqueueIfExpensive(
                        PLAYER_ID,
                        OPERATION_ID,
                        RemovalMode.SAFE,
                        fixture.world,
                        fixture.ledger,
                        fixture.jobs
                )
        ).job();
        fixture.world.states.put(position(0), Blocks.AIR.defaultBlockState());
        ConstructionJob legacy = queued.start().quarantine(
                position(0),
                "world write did not retain its target state"
        );

        IncrementalRemovalJobService.TickResult result = service.tick(
                legacy,
                2,
                fixture.world,
                fixture.ledger,
                fixture.graph
        );

        assertEquals(
                IncrementalRemovalJobService.TickOutcome.COMPLETED,
                result.outcome()
        );
        assertFalse(fixture.ledger.operation(OPERATION_ID).isPresent());
    }

    @Test
    void externalEditQuarantinesWithoutReleasingOwnership() {
        Fixture fixture = fixture();
        IncrementalRemovalJobService service =
                new IncrementalRemovalJobService(new PlacementRemovalService(), 1);
        ConstructionJob queued = assertInstanceOf(
                IncrementalRemovalJobService.EnqueueResult.Queued.class,
                service.enqueueIfExpensive(
                        PLAYER_ID,
                        OPERATION_ID,
                        RemovalMode.SAFE,
                        fixture.world,
                        fixture.ledger,
                        fixture.jobs
                )
        ).job();
        fixture.world.states.put(
                position(1),
                Blocks.GOLD_BLOCK.defaultBlockState()
        );

        IncrementalRemovalJobService.TickResult result = service.tick(
                queued,
                3,
                fixture.world,
                fixture.ledger,
                fixture.graph
        );

        assertEquals(
                IncrementalRemovalJobService.TickOutcome.QUARANTINED,
                result.outcome()
        );
        assertEquals(position(1), result.job().conflictPosition().orElseThrow());
        assertEquals(
                Blocks.GOLD_BLOCK.defaultBlockState(),
                fixture.world.blockState(position(1))
        );
        assertTrue(fixture.ledger.operation(OPERATION_ID).isPresent());
    }

    private static Fixture fixture() {
        return fixture(null);
    }

    private static Fixture graphOwnedFixture() {
        RoadPreset preset = new RoadPreset(
                RoadPreset.CURRENT_SCHEMA_VERSION,
                1,
                1.0,
                MinecraftIds.id("minecraft", "gray_concrete"),
                List.of(new LaneComponent(
                        LaneType.VEHICLE,
                        1.0,
                        0.0,
                        LaneTravelDirection.FORWARD
                ))
        );
        RoadGraph graph = new RoadGraph(Level.OVERWORLD);
        PreparedRoadAddition addition = graph.prepareStraightRoad(
                new StraightGeometry(
                        new RoadPoint(0.5, 65.0, 0.5),
                        new RoadPoint(2.5, 65.0, 0.5)
                ),
                MinecraftIds.id("roadbuilder", "incremental_test"),
                preset,
                TrafficHandedness.RHT
        );
        graph.applyAddition(addition);
        return fixture(new GraphOwnership(graph, addition));
    }

    private static Fixture fixture(GraphOwnership graphOwnership) {
        MapWorld world = new MapWorld();
        for (int index = 0; index < 3; index++) {
            world.states.put(position(index), Blocks.DIRT.defaultBlockState());
        }
        BlockPlacementPlan plan = BlockPlacementPlan.create(
                Level.OVERWORLD,
                List.of(
                        planned(0),
                        planned(1),
                        planned(2)
                )
        );
        PlacementOwnershipLedger ledger =
                new PlacementOwnershipLedger(Level.OVERWORLD);
        RoadGraph graph = graphOwnership == null
                ? new RoadGraph(Level.OVERWORLD)
                : graphOwnership.graph();
        BlockPlanCommitter committer = new BlockPlanCommitter(() -> OPERATION_ID);
        PlacementCommitResult result = graphOwnership == null
                ? committer.commit(plan, world, ledger)
                : committer.commit(
                        OPERATION_ID,
                        RoadMaterializationOwner.segment(
                                graphOwnership.addition().segment().id()
                        ),
                        plan,
                        world,
                        ledger
                );
        assertInstanceOf(PlacementCommitResult.Success.class, result);
        return new Fixture(
                world,
                ledger,
                graph,
                RoadBuilderConstructionJobSavedData.create(Level.OVERWORLD)
        );
    }

    private static PlannedBlock planned(int index) {
        return new PlannedBlock(
                position(index),
                Blocks.GRAY_CONCRETE.defaultBlockState()
        );
    }

    private static BlockPos position(int index) {
        return new BlockPos(index, 64, 0);
    }

    private record Fixture(
            MapWorld world,
            PlacementOwnershipLedger ledger,
            RoadGraph graph,
            RoadBuilderConstructionJobSavedData jobs
    ) {
    }

    private record GraphOwnership(
            RoadGraph graph,
            PreparedRoadAddition addition
    ) {
    }

    private static final class MapWorld implements PlacementBlockWorld {
        private final Map<BlockPos, BlockState> states = new HashMap<>();

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
            states.put(position.immutable(), state);
            return true;
        }
    }
}
