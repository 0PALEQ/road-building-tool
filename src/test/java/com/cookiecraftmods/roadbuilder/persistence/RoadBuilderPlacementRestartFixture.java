package com.cookiecraftmods.roadbuilder.persistence;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.StraightGeometry;
import com.cookiecraftmods.roadbuilder.network.PreparedRoadAddition;
import com.cookiecraftmods.roadbuilder.network.PreparedRoadNetworkMutation;
import com.cookiecraftmods.roadbuilder.network.RoadGraph;
import com.cookiecraftmods.roadbuilder.network.RoadGraphDelta;
import com.cookiecraftmods.roadbuilder.network.RoadGraphSnapshot;
import com.cookiecraftmods.roadbuilder.placement.BlockChangeLayer;
import com.cookiecraftmods.roadbuilder.placement.BlockPlacementPlan;
import com.cookiecraftmods.roadbuilder.placement.BlockPlanCommitter;
import com.cookiecraftmods.roadbuilder.placement.BlockPlanResult;
import com.cookiecraftmods.roadbuilder.placement.ConstructionLimits;
import com.cookiecraftmods.roadbuilder.placement.ConstructionJob;
import com.cookiecraftmods.roadbuilder.placement.ConstructionJobBlockTransition;
import com.cookiecraftmods.roadbuilder.placement.ConstructionJobPhase;
import com.cookiecraftmods.roadbuilder.placement.IncrementalPlacementMetadata;
import com.cookiecraftmods.roadbuilder.placement.PlacementBlockWorld;
import com.cookiecraftmods.roadbuilder.placement.PlacementCommitResult;
import com.cookiecraftmods.roadbuilder.placement.PlacementOperationId;
import com.cookiecraftmods.roadbuilder.placement.PlannedBlock;
import com.cookiecraftmods.roadbuilder.placement.PositionChangeStack;
import com.cookiecraftmods.roadbuilder.placement.RoadMaterializationOwner;
import com.cookiecraftmods.roadbuilder.placement.StraightRoadSegmentMaterializer;
import com.cookiecraftmods.roadbuilder.preset.LaneComponent;
import com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection;
import com.cookiecraftmods.roadbuilder.preset.LaneType;
import com.cookiecraftmods.roadbuilder.preset.RoadPreset;
import com.cookiecraftmods.roadbuilder.preset.TrafficHandedness;
import com.cookiecraftmods.roadbuilder.test.MinecraftBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class RoadBuilderPlacementRestartFixture {
    private static final BlockPos WEST = new BlockPos(-19, 64, 7);
    private static final BlockPos EAST = new BlockPos(23, 64, 7);
    private static final PlacementOperationId OLDER = operationId(101);
    private static final PlacementOperationId NEWER = operationId(102);
    private static final PlacementOperationId ROAD_OPERATION = operationId(103);
    private static final UUID CONSTRUCTION_JOB_ID =
            UUID.fromString("7e86bc8c-eb51-4197-83e6-7e733317c04a");
    private static final UUID CONSTRUCTION_JOB_PLAYER =
            UUID.fromString("d2162112-e29a-4ee8-be5f-dd296f8a8b0e");
    private static final ResourceLocation PRESET_ID = new ResourceLocation(
            "roadbuilder",
            "restart_fixture"
    );
    private static final RoadPreset PRESET = new RoadPreset(
            RoadPreset.CURRENT_SCHEMA_VERSION,
            3,
            1.0,
            new ResourceLocation("minecraft", "stone"),
            List.of(new LaneComponent(
                    LaneType.VEHICLE,
                    1.0,
                    0.0,
                    LaneTravelDirection.FORWARD
            ))
    );

    private RoadBuilderPlacementRestartFixture() {
    }

    public static void main(String[] arguments) throws Exception {
        require(arguments.length == 2, "Expected <prepare|verify> <data-directory>");
        Path dataDirectory = Path.of(arguments[1]).toAbsolutePath().normalize();
        MinecraftBootstrap.bootstrap();

        switch (arguments[0]) {
            case "prepare" -> prepare(dataDirectory);
            case "verify" -> verify(dataDirectory);
            default -> throw new IllegalArgumentException(
                    "Unknown restart-fixture phase: " + arguments[0]
            );
        }
    }

    private static void prepare(Path dataDirectory) throws IOException {
        Files.createDirectories(dataDirectory);
        Path placementFile = placementDataFile(dataDirectory);
        Path networkFile = networkDataFile(dataDirectory);
        Path constructionJobFile = constructionJobDataFile(dataDirectory);
        require(!Files.exists(placementFile), "Restart fixture was not cleaned: " + placementFile);
        require(!Files.exists(networkFile), "Restart fixture was not cleaned: " + networkFile);
        require(!Files.exists(constructionJobFile),
                "Restart fixture was not cleaned: " + constructionJobFile);

        RoadBuilderPlacementSavedData data = RoadBuilderPlacementSavedData.create(Level.OVERWORLD);
        RoadBuilderNetworkSavedData networkData = RoadBuilderNetworkSavedData.create(Level.OVERWORLD);
        RoadBuilderConstructionJobSavedData constructionJobData =
                RoadBuilderConstructionJobSavedData.create(Level.OVERWORLD);
        RoadGraph pendingGraph = new RoadGraph(Level.OVERWORLD);
        RoadGraphSnapshot pendingGraphBefore = pendingGraph.snapshot();
        PreparedRoadNetworkMutation pendingMutation =
                pendingGraph.prepareStraightRoadNetwork(
                        new StraightGeometry(
                                new RoadPoint(90.5, 70.0, 90.5),
                                new RoadPoint(91.5, 70.0, 90.5)
                        ),
                        PRESET_ID,
                        PRESET,
                        TrafficHandedness.RHT
                );
        pendingGraph.applyNetworkMutation(pendingMutation);
        ConstructionJob applyingJob = ConstructionJob.preparedPlacement(
                CONSTRUCTION_JOB_ID,
                CONSTRUCTION_JOB_PLAYER,
                List.of(
                        new ConstructionJobBlockTransition(
                                new BlockPos(90, 70, 90),
                                Blocks.DIRT.defaultBlockState(),
                                Blocks.GRAY_CONCRETE.defaultBlockState()
                        ),
                        new ConstructionJobBlockTransition(
                                new BlockPos(91, 70, 90),
                                Blocks.GRASS_BLOCK.defaultBlockState(),
                                Blocks.GRAY_CONCRETE.defaultBlockState()
                        )
                ),
                new IncrementalPlacementMetadata(
                        Optional.of(RoadMaterializationOwner.segment(
                                pendingMutation.placedSegments().get(0).id()
                        )),
                        List.of(),
                        RoadGraphDelta.between(
                                pendingGraphBefore,
                                pendingGraph.snapshot()
                        )
                )
        ).start().advance(1);
        constructionJobData.add(applyingJob);
        StraightGeometry geometry = new StraightGeometry(
                new RoadPoint(0.5, 64.0, 20.5),
                new RoadPoint(4.5, 66.0, 20.5)
        );
        PreparedRoadAddition graphAddition = networkData.graph().prepareStraightRoad(
                geometry,
                PRESET_ID,
                PRESET,
                TrafficHandedness.RHT
        );
        networkData.graph().applyAddition(graphAddition);
        MapWorld world = new MapWorld(Level.OVERWORLD);
        world.states.put(WEST, Blocks.DIRT.defaultBlockState());
        world.states.put(EAST, Blocks.GRASS_BLOCK.defaultBlockState());

        BlockPlacementPlan roadPlan = requirePlan(new StraightRoadSegmentMaterializer().plan(
                Level.OVERWORLD,
                graphAddition.segment(),
                ConstructionLimits.defaults()
        ));
        requireSuccess(new BlockPlanCommitter().commit(
                ROAD_OPERATION,
                RoadMaterializationOwner.segment(graphAddition.segment().id()),
                roadPlan,
                world,
                data.ledger()
        ));
        requireSuccess(new BlockPlanCommitter().commit(
                OLDER,
                BlockPlacementPlan.create(Level.OVERWORLD, List.of(
                        new PlannedBlock(WEST, Blocks.STONE.defaultBlockState()),
                        new PlannedBlock(EAST, Blocks.STONE.defaultBlockState())
                )),
                world,
                data.ledger()
        ));
        requireSuccess(new BlockPlanCommitter().commit(
                NEWER,
                BlockPlacementPlan.create(Level.OVERWORLD, List.of(
                        new PlannedBlock(EAST, Blocks.COBBLESTONE.defaultBlockState())
                )),
                world,
                data.ledger()
        ));
        require(data.isDirty(), "Committed ownership must be dirty before the disk save");
        require(networkData.isDirty(), "Published road graph must be dirty before the disk save");
        require(constructionJobData.isDirty(),
                "Persisted construction job must be dirty before the disk save");

        DimensionDataStorage storage = storage(dataDirectory);
        storage.set(RoadBuilderPlacementSavedData.DATA_NAME, data);
        storage.set(RoadBuilderNetworkSavedData.DATA_NAME, networkData);
        storage.set(RoadBuilderConstructionJobSavedData.DATA_NAME, constructionJobData);
        storage.save();

        require(Files.isRegularFile(placementFile), "Placement SavedData file was not created");
        require(Files.size(placementFile) > 0, "Placement SavedData file is empty");
        require(Files.isRegularFile(networkFile), "Road graph SavedData file was not created");
        require(Files.size(networkFile) > 0, "Road graph SavedData file is empty");
        require(Files.isRegularFile(constructionJobFile),
                "Construction-job SavedData file was not created");
        require(Files.size(constructionJobFile) > 0,
                "Construction-job SavedData file is empty");
        require(!data.isDirty(), "A successful Minecraft SavedData save must clear dirty state");
        require(!networkData.isDirty(), "A successful graph save must clear dirty state");
        require(!constructionJobData.isDirty(),
                "A successful construction-job save must clear dirty state");
    }

    private static void verify(Path dataDirectory) throws IOException {
        Path placementFile = placementDataFile(dataDirectory);
        Path networkFile = networkDataFile(dataDirectory);
        Path constructionJobFile = constructionJobDataFile(dataDirectory);
        require(Files.isRegularFile(placementFile), "Prepared SavedData file is missing: " + placementFile);
        require(Files.isRegularFile(networkFile), "Prepared graph file is missing: " + networkFile);
        require(Files.isRegularFile(constructionJobFile),
                "Prepared construction-job file is missing: " + constructionJobFile);

        DimensionDataStorage storage = storage(dataDirectory);
        RoadBuilderPlacementSavedData data = storage.computeIfAbsent(
                tag -> RoadBuilderPlacementSavedData.load(Level.OVERWORLD, tag),
                () -> {
                    throw new IllegalStateException("Restart verification fell back to new data");
                },
                RoadBuilderPlacementSavedData.DATA_NAME
        );
        RoadBuilderNetworkSavedData networkData = storage.computeIfAbsent(
                tag -> RoadBuilderNetworkSavedData.load(Level.OVERWORLD, tag),
                () -> {
                    throw new IllegalStateException("Graph restart verification fell back to new data");
                },
                RoadBuilderNetworkSavedData.DATA_NAME
        );
        RoadBuilderConstructionJobSavedData constructionJobData =
                storage.computeIfAbsent(
                        tag -> RoadBuilderConstructionJobSavedData.load(
                                Level.OVERWORLD,
                                tag
                        ),
                        () -> {
                            throw new IllegalStateException(
                                    "Construction-job restart verification fell back to new data"
                            );
                        },
                        RoadBuilderConstructionJobSavedData.DATA_NAME
                );

        require(data.isCompatible(), "Restarted SavedData must remain schema-compatible");
        require(!data.isDirty(), "Disk-loaded SavedData must start clean");
        require(networkData.isCompatible(), "Restarted graph must remain schema-compatible");
        require(!networkData.isDirty(), "Disk-loaded graph must start clean");
        require(constructionJobData.isCompatible(),
                "Restarted construction jobs must remain schema-compatible");
        require(!constructionJobData.isDirty(),
                "Disk-loaded construction jobs must start clean");
        ConstructionJob restartedJob = constructionJobData.job(
                CONSTRUCTION_JOB_ID
        ).orElseThrow();
        require(restartedJob.phase() == ConstructionJobPhase.APPLYING,
                "Construction-job phase changed across process restart");
        require(restartedJob.cursor() == 1,
                "Construction-job cursor changed across process restart");
        require(restartedJob.transitions().get(0).beforeState().equals(
                        Blocks.DIRT.defaultBlockState()),
                "Construction-job before-state changed across process restart");
        require(restartedJob.transitions().get(1).afterState().equals(
                        Blocks.GRAY_CONCRETE.defaultBlockState()),
                "Construction-job after-state changed across process restart");
        require(restartedJob.placementMetadata().isPresent(),
                "Placement publication metadata changed across process restart");
        require(restartedJob.placementMetadata().orElseThrow()
                        .graphDelta().baseRevision() == 0
                        && restartedJob.placementMetadata().orElseThrow()
                        .graphDelta().targetRevision() == 1,
                "Prepared placement graph delta changed across process restart");
        require(
                data.ledger().operation(OLDER).orElseThrow().positions().equals(List.of(WEST, EAST)),
                "Older operation positions changed across the process restart"
        );
        require(
                data.ledger().operation(NEWER).orElseThrow().positions().equals(List.of(EAST)),
                "Newer operation positions changed across the process restart"
        );
        RoadMaterializationOwner owner = data.ledger().operation(ROAD_OPERATION).orElseThrow()
                .materializationOwner().orElseThrow();
        require(owner.type() == RoadMaterializationOwner.Type.SEGMENT,
                "Older operation lost its segment materialization type");
        RoadGraphSnapshot graph = networkData.graph().snapshot();
        require(graph.revision() == 1, "Graph revision changed across process restart");
        require(graph.roads().size() == 1, "Logical road count changed across restart");
        require(graph.nodes().size() == 2, "Boundary node count changed across restart");
        require(graph.segments().size() == 1, "Segment count changed across restart");
        require(graph.lanes().size() == 1, "Derived lane count changed across restart");
        var segment = networkData.graph().segment(owner.segmentId()).orElseThrow();
        require(segment.referenceGeometry().start().equals(
                        new RoadPoint(0.5, 64.0, 20.5)),
                "Segment start changed across restart");
        require(segment.referenceGeometry().end().equals(
                        new RoadPoint(4.5, 66.0, 20.5)),
                "Segment end changed across restart");
        require(segment.presetId().equals(PRESET_ID), "Preset ID changed across restart");
        require(segment.presetRevision() == PRESET.revision(),
                "Preset revision changed across restart");
        require(
                segment.lanes().get(0).geometry().start().y() == 64.0
                        && segment.lanes().get(0).geometry().end().y() == 66.0,
                "Derived graded lane geometry changed across restart"
        );
        BlockPlacementPlan regenerated = requirePlan(new StraightRoadSegmentMaterializer().plan(
                Level.OVERWORLD,
                segment,
                ConstructionLimits.defaults()
        ));
        require(
                data.ledger().operation(ROAD_OPERATION).orElseThrow().positions().equals(
                        regenerated.positions()
                ),
                "Segment materialization footprint changed across restart"
        );

        PositionChangeStack westStack = data.ledger().stack(WEST).orElseThrow();
        PositionChangeStack eastStack = data.ledger().stack(EAST).orElseThrow();
        require(westStack.layers().size() == 1, "West stack depth changed across restart");
        require(eastStack.layers().size() == 2, "East overlap depth changed across restart");
        assertLayer(
                westStack.layers().get(0),
                OLDER,
                Blocks.DIRT.defaultBlockState(),
                Blocks.STONE.defaultBlockState()
        );
        assertLayer(
                eastStack.layers().get(0),
                OLDER,
                Blocks.GRASS_BLOCK.defaultBlockState(),
                Blocks.STONE.defaultBlockState()
        );
        assertLayer(
                eastStack.layers().get(1),
                NEWER,
                Blocks.STONE.defaultBlockState(),
                Blocks.COBBLESTONE.defaultBlockState()
        );
    }

    private static DimensionDataStorage storage(Path dataDirectory) {
        return new DimensionDataStorage(dataDirectory.toFile(), DataFixers.getDataFixer());
    }

    private static Path placementDataFile(Path dataDirectory) {
        return dataDirectory.resolve(RoadBuilderPlacementSavedData.DATA_NAME + ".dat");
    }

    private static Path networkDataFile(Path dataDirectory) {
        return dataDirectory.resolve(RoadBuilderNetworkSavedData.DATA_NAME + ".dat");
    }

    private static Path constructionJobDataFile(Path dataDirectory) {
        return dataDirectory.resolve(
                RoadBuilderConstructionJobSavedData.DATA_NAME + ".dat"
        );
    }

    private static void requireSuccess(PlacementCommitResult result) {
        require(
                result instanceof PlacementCommitResult.Success,
                "Fixture placement failed: " + result
        );
    }

    private static BlockPlacementPlan requirePlan(BlockPlanResult result) {
        require(result instanceof BlockPlanResult.Success, "Fixture planning failed: " + result);
        return ((BlockPlanResult.Success) result).plan();
    }

    private static void assertLayer(
            BlockChangeLayer layer,
            PlacementOperationId operationId,
            BlockState beforeState,
            BlockState afterState
    ) {
        require(layer.operationId().equals(operationId), "Layer operation changed across restart");
        require(layer.beforeState().equals(beforeState), "Layer before-state changed across restart");
        require(layer.afterState().equals(afterState), "Layer after-state changed across restart");
    }

    private static PlacementOperationId operationId(long lowBits) {
        return new PlacementOperationId(new UUID(0L, lowBits));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static final class MapWorld implements PlacementBlockWorld {
        private final ResourceKey<Level> dimension;
        private final Map<BlockPos, BlockState> states = new HashMap<>();

        private MapWorld(ResourceKey<Level> dimension) {
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
            states.put(position.immutable(), state);
            return true;
        }
    }
}
