package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.StraightGeometry;
import com.cookiecraftmods.roadbuilder.network.PreparedRoadNetworkMutation;
import com.cookiecraftmods.roadbuilder.network.RoadGraph;
import com.cookiecraftmods.roadbuilder.persistence.RoadBuilderConstructionJobSavedData;
import com.cookiecraftmods.roadbuilder.preset.LaneComponent;
import com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection;
import com.cookiecraftmods.roadbuilder.preset.LaneType;
import com.cookiecraftmods.roadbuilder.preset.RoadPreset;
import com.cookiecraftmods.roadbuilder.preset.TrafficHandedness;
import com.cookiecraftmods.roadbuilder.test.MinecraftBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncrementalPlacementJobServiceTest {
    private static final UUID PLAYER_ID =
            UUID.fromString("eea5cb50-bc1a-4fb8-8d38-eb6682697866");
    private static final PlacementOperationId OPERATION_ID =
            new PlacementOperationId(
                    UUID.fromString("1331a8d9-940a-44f0-b266-143bde7f0654")
            );
    private static final ResourceLocation PRESET_ID =
            new ResourceLocation("roadbuilder", "incremental_placement_test");
    private static final RoadPreset PRESET = new RoadPreset(
            RoadPreset.CURRENT_SCHEMA_VERSION,
            1,
            1.0,
            new ResourceLocation("minecraft", "gray_concrete"),
            List.of(new LaneComponent(
                    LaneType.VEHICLE,
                    1.0,
                    0.0,
                    LaneTravelDirection.FORWARD
            ))
    );

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftBootstrap.bootstrap();
    }

    @Test
    void persistedPlacementAppliesInBatchesThenPublishesOwnershipAndGraph() {
        Fixture fixture = fixture();
        IncrementalPlacementJobService service =
                new IncrementalPlacementJobService(1);
        ConstructionJob queued = assertInstanceOf(
                IncrementalPlacementJobService.EnqueueResult.Queued.class,
                service.enqueueIfExpensive(
                        PLAYER_ID,
                        OPERATION_ID,
                        fixture.plan,
                        Optional.of(RoadMaterializationOwner.segment(
                                fixture.mutation.placedSegments().get(0).id()
                        )),
                        List.of(),
                        fixture.mutation,
                        fixture.world,
                        fixture.ledger,
                        fixture.graph,
                        fixture.jobs
                )
        ).job();

        assertEquals(0, fixture.graph.revision());
        assertTrue(fixture.ledger.operations().isEmpty());
        assertEquals(
                Blocks.DIRT.defaultBlockState(),
                fixture.world.blockState(position(0))
        );

        CompoundTag persisted = fixture.jobs.save(new CompoundTag());
        RoadBuilderConstructionJobSavedData reloaded =
                RoadBuilderConstructionJobSavedData.load(
                        Level.OVERWORLD,
                        persisted
                );
        ConstructionJob resumed = reloaded.job(queued.id()).orElseThrow();
        assertEquals(queued.placementMetadata(), resumed.placementMetadata());

        IncrementalPlacementJobService.TickResult first = service.tick(
                resumed,
                1,
                fixture.world,
                fixture.ledger,
                fixture.graph
        );
        assertEquals(ConstructionJobPhase.APPLYING, first.job().phase());
        assertEquals(0, fixture.graph.revision());
        assertTrue(fixture.ledger.operations().isEmpty());

        IncrementalPlacementJobService.TickResult second = service.tick(
                first.job(),
                1,
                fixture.world,
                fixture.ledger,
                fixture.graph
        );
        IncrementalPlacementJobService.TickResult third = service.tick(
                second.job(),
                1,
                fixture.world,
                fixture.ledger,
                fixture.graph
        );

        assertEquals(
                IncrementalPlacementJobService.TickOutcome.COMPLETED,
                third.outcome()
        );
        assertTrue(third.graphChanged());
        assertEquals(1, fixture.graph.revision());
        assertTrue(fixture.ledger.operation(OPERATION_ID).isPresent());
        assertTrue(fixture.graph.segment(
                fixture.mutation.placedSegments().get(0).id()
        ).isPresent());
        for (int index = 0; index < 3; index++) {
            assertEquals(
                    Blocks.GRAY_CONCRETE.defaultBlockState(),
                    fixture.world.blockState(position(index))
            );
        }
    }

    @Test
    void incrementalBatchesUseConstructionWritesThatDoNotDisturbLaterCells() {
        Fixture fixture = fixture();
        fixture.world.disturbLaterCellOnOrdinaryWrite = true;
        IncrementalPlacementJobService service =
                new IncrementalPlacementJobService(1);
        ConstructionJob job = assertInstanceOf(
                IncrementalPlacementJobService.EnqueueResult.Queued.class,
                service.enqueueIfExpensive(
                        PLAYER_ID,
                        OPERATION_ID,
                        fixture.plan,
                        Optional.of(RoadMaterializationOwner.segment(
                                fixture.mutation.placedSegments().get(0).id()
                        )),
                        List.of(),
                        fixture.mutation,
                        fixture.world,
                        fixture.ledger,
                        fixture.graph,
                        fixture.jobs
                )
        ).job();

        IncrementalPlacementJobService.TickResult result = null;
        for (int index = 0; index < 3; index++) {
            result = service.tick(
                    job,
                    1,
                    fixture.world,
                    fixture.ledger,
                    fixture.graph
            );
            job = result.job();
        }

        assertEquals(
                IncrementalPlacementJobService.TickOutcome.COMPLETED,
                result.outcome()
        );
        assertEquals(3, fixture.world.constructionWrites);
        assertEquals(0, fixture.world.ordinaryWrites);
    }

    @Test
    void structuralTargetsBuildBottomUpBeforeTopDownClearance() {
        Fixture fixture = fixture();
        BlockPos lowerSupport = new BlockPos(0, 50, 0);
        BlockPos upperSupport = new BlockPos(0, 60, 0);
        BlockPos clearance = new BlockPos(0, 70, 0);
        fixture.world.states.put(lowerSupport, Blocks.DIRT.defaultBlockState());
        fixture.world.states.put(upperSupport, Blocks.DIRT.defaultBlockState());
        fixture.world.states.put(clearance, Blocks.STONE.defaultBlockState());
        BlockPlacementPlan plan = BlockPlacementPlan.create(
                Level.OVERWORLD,
                List.of(
                        new PlannedBlock(clearance, Blocks.AIR.defaultBlockState()),
                        new PlannedBlock(upperSupport, Blocks.STONE_BRICKS.defaultBlockState()),
                        new PlannedBlock(lowerSupport, Blocks.STONE_BRICKS.defaultBlockState())
                )
        );

        ConstructionJob job = assertInstanceOf(
                IncrementalPlacementJobService.EnqueueResult.Queued.class,
                new IncrementalPlacementJobService(1).enqueueIfExpensive(
                        PLAYER_ID,
                        OPERATION_ID,
                        plan,
                        Optional.of(RoadMaterializationOwner.segment(
                                fixture.mutation.placedSegments().get(0).id()
                        )),
                        List.of(),
                        fixture.mutation,
                        fixture.world,
                        fixture.ledger,
                        fixture.graph,
                        fixture.jobs
                )
        ).job();

        assertEquals(
                List.of(lowerSupport, upperSupport, clearance),
                job.transitions().stream()
                        .map(ConstructionJobBlockTransition::position)
                        .toList()
        );
    }

    @Test
    void finalizationPublishesNaturallyUpdatedTerrainFill() {
        Fixture fixture = fixture();
        for (int index = 0; index < 3; index++) {
            fixture.world.states.put(position(index), Blocks.STONE.defaultBlockState());
        }
        BlockPlacementPlan fillPlan = BlockPlacementPlan.create(
                Level.OVERWORLD,
                List.of(
                        new PlannedBlock(position(0), Blocks.DIRT.defaultBlockState()),
                        new PlannedBlock(position(1), Blocks.DIRT.defaultBlockState()),
                        new PlannedBlock(position(2), Blocks.DIRT.defaultBlockState())
                )
        );
        IncrementalPlacementJobService service =
                new IncrementalPlacementJobService(1);
        ConstructionJob job = assertInstanceOf(
                IncrementalPlacementJobService.EnqueueResult.Queued.class,
                service.enqueueIfExpensive(
                        PLAYER_ID,
                        OPERATION_ID,
                        fillPlan,
                        Optional.of(RoadMaterializationOwner.segment(
                                fixture.mutation.placedSegments().get(0).id()
                        )),
                        List.of(),
                        fixture.mutation,
                        fixture.world,
                        fixture.ledger,
                        fixture.graph,
                        fixture.jobs
                )
        ).job();

        job = service.tick(
                job,
                2,
                fixture.world,
                fixture.ledger,
                fixture.graph
        ).job();
        fixture.world.states.put(
                position(0),
                Blocks.GRASS_BLOCK.defaultBlockState()
        );
        IncrementalPlacementJobService.TickResult completed = service.tick(
                job,
                1,
                fixture.world,
                fixture.ledger,
                fixture.graph
        );

        assertEquals(
                IncrementalPlacementJobService.TickOutcome.COMPLETED,
                completed.outcome()
        );
        assertEquals(
                Blocks.GRASS_BLOCK,
                fixture.ledger.expectedVisibleState(position(0))
                        .orElseThrow()
                        .getBlock()
        );
    }

    @Test
    void cancellationRestoresAppliedPrefixWithoutPublishingMetadata() {
        Fixture fixture = fixture();
        IncrementalPlacementJobService service =
                new IncrementalPlacementJobService(1);
        ConstructionJob queued = assertInstanceOf(
                IncrementalPlacementJobService.EnqueueResult.Queued.class,
                service.enqueueIfExpensive(
                        PLAYER_ID,
                        OPERATION_ID,
                        fixture.plan,
                        Optional.of(RoadMaterializationOwner.segment(
                                fixture.mutation.placedSegments().get(0).id()
                        )),
                        List.of(),
                        fixture.mutation,
                        fixture.world,
                        fixture.ledger,
                        fixture.graph,
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

        IncrementalPlacementJobService.TickResult cancelled = service.tick(
                applying.requestCancellation(),
                1,
                fixture.world,
                fixture.ledger,
                fixture.graph
        );

        assertEquals(
                IncrementalPlacementJobService.TickOutcome.CANCELLED,
                cancelled.outcome()
        );
        assertEquals(0, fixture.graph.revision());
        assertTrue(fixture.ledger.operations().isEmpty());
        for (int index = 0; index < 3; index++) {
            assertEquals(
                    Blocks.DIRT.defaultBlockState(),
                    fixture.world.blockState(position(index))
            );
        }
    }

    @Test
    void terrainUpdateConflictRollsBackTargetsWithoutPublishingOrphans() {
        Fixture fixture = fixture();
        IncrementalPlacementJobService service =
                new IncrementalPlacementJobService(1);
        ConstructionJob queued = assertInstanceOf(
                IncrementalPlacementJobService.EnqueueResult.Queued.class,
                service.enqueueIfExpensive(
                        PLAYER_ID,
                        OPERATION_ID,
                        fixture.plan,
                        Optional.of(RoadMaterializationOwner.segment(
                                fixture.mutation.placedSegments().get(0).id()
                        )),
                        List.of(),
                        fixture.mutation,
                        fixture.world,
                        fixture.ledger,
                        fixture.graph,
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

        fixture.world.states.put(
                position(1),
                Blocks.GOLD_BLOCK.defaultBlockState()
        );
        // Simulate a neighbor update producing a later planned target before
        // the persisted cursor reaches it.
        fixture.world.states.put(
                position(2),
                Blocks.GRAY_CONCRETE.defaultBlockState()
        );
        IncrementalPlacementJobService.TickResult conflict = service.tick(
                applying,
                2,
                fixture.world,
                fixture.ledger,
                fixture.graph
        );

        assertEquals(
                IncrementalPlacementJobService.TickOutcome.QUARANTINED,
                conflict.outcome()
        );
        assertEquals(ConstructionJobPhase.QUARANTINED, conflict.job().phase());
        assertEquals(1, conflict.job().cursor());

        IncrementalPlacementJobService.TickResult cleaned = service.tick(
                conflict.job(),
                3,
                fixture.world,
                fixture.ledger,
                fixture.graph
        );

        assertEquals(
                IncrementalPlacementJobService.TickOutcome.CANCELLED,
                cleaned.outcome()
        );
        assertEquals(Blocks.DIRT.defaultBlockState(),
                fixture.world.blockState(position(0)));
        assertEquals(Blocks.GOLD_BLOCK.defaultBlockState(),
                fixture.world.blockState(position(1)));
        assertEquals(Blocks.DIRT.defaultBlockState(),
                fixture.world.blockState(position(2)));
        assertEquals(0, fixture.graph.revision());
        assertTrue(fixture.ledger.operations().isEmpty());
    }

    @Test
    void smallPlacementKeepsTheSynchronousFallbackAvailable() {
        Fixture fixture = fixture();
        IncrementalPlacementJobService service =
                new IncrementalPlacementJobService(3);

        IncrementalPlacementJobService.EnqueueResult result =
                service.enqueueIfExpensive(
                        PLAYER_ID,
                        OPERATION_ID,
                        fixture.plan,
                        Optional.of(RoadMaterializationOwner.segment(
                                fixture.mutation.placedSegments().get(0).id()
                        )),
                        List.of(),
                        fixture.mutation,
                        fixture.world,
                        fixture.ledger,
                        fixture.graph,
                        fixture.jobs
                );

        assertInstanceOf(
                IncrementalPlacementJobService.EnqueueResult.Immediate.class,
                result
        );
        assertTrue(fixture.jobs.jobs().isEmpty());
        assertFalse(fixture.ledger.operation(OPERATION_ID).isPresent());
    }

    @Test
    void legacyPlacementWithoutPublicationMetadataQuarantinesBeforeWriting() {
        Fixture fixture = fixture();
        ConstructionJob legacy = ConstructionJob.prepared(
                OPERATION_ID.value(),
                PLAYER_ID,
                ConstructionJobAction.PLACE,
                List.of(new ConstructionJobBlockTransition(
                        position(0),
                        Blocks.DIRT.defaultBlockState(),
                        Blocks.GRAY_CONCRETE.defaultBlockState()
                ))
        );

        IncrementalPlacementJobService.TickResult result =
                new IncrementalPlacementJobService(1).tick(
                        legacy,
                        1,
                        fixture.world,
                        fixture.ledger,
                        fixture.graph
                );

        assertEquals(
                IncrementalPlacementJobService.TickOutcome.QUARANTINED,
                result.outcome()
        );
        assertEquals(0, result.examinedTransitions());
        assertEquals(
                Blocks.DIRT.defaultBlockState(),
                fixture.world.blockState(position(0))
        );
    }

    private static Fixture fixture() {
        MapWorld world = new MapWorld();
        for (int index = 0; index < 3; index++) {
            world.states.put(position(index), Blocks.DIRT.defaultBlockState());
        }
        BlockPlacementPlan plan = BlockPlacementPlan.create(
                Level.OVERWORLD,
                List.of(planned(0), planned(1), planned(2))
        );
        RoadGraph graph = new RoadGraph(Level.OVERWORLD);
        PreparedRoadNetworkMutation mutation = graph.prepareStraightRoadNetwork(
                new StraightGeometry(
                        new RoadPoint(0.5, 65.0, 0.5),
                        new RoadPoint(2.5, 65.0, 0.5)
                ),
                PRESET_ID,
                PRESET,
                TrafficHandedness.RHT
        );
        return new Fixture(
                world,
                plan,
                new PlacementOwnershipLedger(Level.OVERWORLD),
                graph,
                mutation,
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
            BlockPlacementPlan plan,
            PlacementOwnershipLedger ledger,
            RoadGraph graph,
            PreparedRoadNetworkMutation mutation,
            RoadBuilderConstructionJobSavedData jobs
    ) {
    }

    private static final class MapWorld implements PlacementBlockWorld {
        private final Map<BlockPos, BlockState> states = new HashMap<>();
        private boolean disturbLaterCellOnOrdinaryWrite;
        private int ordinaryWrites;
        private int constructionWrites;

        @Override
        public ResourceKey<Level> dimension() {
            return Level.OVERWORLD;
        }

        @Override
        public BlockState blockState(BlockPos position) {
            return states.getOrDefault(
                    position,
                    Blocks.AIR.defaultBlockState()
            );
        }

        @Override
        public boolean hasBlockEntity(BlockPos position) {
            return false;
        }

        @Override
        public boolean setBlockState(BlockPos position, BlockState state) {
            ordinaryWrites++;
            states.put(position.immutable(), state);
            if (disturbLaterCellOnOrdinaryWrite && position.equals(position(0))) {
                states.put(position(1), Blocks.GOLD_BLOCK.defaultBlockState());
            }
            return true;
        }

        @Override
        public boolean setBlockStateForConstruction(
                BlockPos position,
                BlockState state
        ) {
            constructionWrites++;
            states.put(position.immutable(), state);
            return true;
        }
    }
}
