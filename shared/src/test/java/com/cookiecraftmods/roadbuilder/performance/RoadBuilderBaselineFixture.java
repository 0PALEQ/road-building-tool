package com.cookiecraftmods.roadbuilder.performance;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.StraightGeometry;
import com.cookiecraftmods.roadbuilder.network.RoadSegmentId;
import com.cookiecraftmods.roadbuilder.persistence.RoadBuilderNetworkSavedData;
import com.cookiecraftmods.roadbuilder.persistence.RoadSavedDataFixtures;
import com.cookiecraftmods.roadbuilder.persistence.RoadBuilderPlacementSavedData;
import com.cookiecraftmods.roadbuilder.placement.BlockPlacementPlan;
import com.cookiecraftmods.roadbuilder.placement.BlockPlanCommitter;
import com.cookiecraftmods.roadbuilder.placement.PlacementBlockWorld;
import com.cookiecraftmods.roadbuilder.placement.PlacementCommitResult;
import com.cookiecraftmods.roadbuilder.placement.PlannedBlock;
import com.cookiecraftmods.roadbuilder.placement.RoadMaterializationOwner;
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
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class RoadBuilderBaselineFixture {
    private static final int PLAN_WIDTH = 128;
    private static final int PLAN_LENGTH = 128;
    private static final int PLAN_BLOCK_COUNT = PLAN_WIDTH * PLAN_LENGTH;
    private static final int WARMUP_RUNS = 2;
    private static final int MEASURED_RUNS = 7;
    private static final int GRAPH_SEGMENT_COUNT = 512;
    private static final ResourceLocation PRESET_ID = MinecraftIds.id(
            "roadbuilder",
            "baseline"
    );
    private static final RoadPreset PRESET = new RoadPreset(
            RoadPreset.CURRENT_SCHEMA_VERSION,
            1,
            5.0,
            MinecraftIds.id("minecraft", "gray_concrete"),
            List.of(
                    new LaneComponent(
                            LaneType.VEHICLE,
                            2.5,
                            -1.25,
                            LaneTravelDirection.FORWARD
                    ),
                    new LaneComponent(
                            LaneType.VEHICLE,
                            2.5,
                            1.25,
                            LaneTravelDirection.BACKWARD
                    )
            )
    );

    private RoadBuilderBaselineFixture() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 2) {
            throw new IllegalArgumentException("Expected <report-file> <data-directory>");
        }
        Path reportFile = Path.of(arguments[0]).toAbsolutePath().normalize();
        Path dataDirectory = Path.of(arguments[1]).toAbsolutePath().normalize();
        MinecraftBootstrap.bootstrap();

        BlockPlacementPlan plan = createPlan();
        for (int index = 0; index < WARMUP_RUNS; index++) {
            measureCommit(plan, index);
        }
        List<Long> samples = new ArrayList<>(MEASURED_RUNS);
        for (int index = 0; index < MEASURED_RUNS; index++) {
            samples.add(measureCommit(plan, WARMUP_RUNS + index));
        }
        List<Long> sortedSamples = new ArrayList<>(samples);
        Collections.sort(sortedSamples);

        Files.createDirectories(dataDirectory);
        SavedSizes savedSizes = writeSavedData(plan, dataDirectory);
        Files.createDirectories(reportFile.getParent());
        Files.writeString(
                reportFile,
                reportJson(samples, sortedSamples, savedSizes),
                StandardCharsets.UTF_8
        );
        System.out.println("Road Builder baseline written to " + reportFile);
    }

    private static BlockPlacementPlan createPlan() {
        List<PlannedBlock> blocks = new ArrayList<>(PLAN_BLOCK_COUNT);
        for (int x = 0; x < PLAN_WIDTH; x++) {
            for (int z = 0; z < PLAN_LENGTH; z++) {
                blocks.add(new PlannedBlock(
                        new BlockPos(x, 64, z),
                        Blocks.GRAY_CONCRETE.defaultBlockState()
                ));
            }
        }
        return BlockPlacementPlan.create(Level.OVERWORLD, blocks);
    }

    private static long measureCommit(BlockPlacementPlan plan, int runIndex) {
        MapWorld world = MapWorld.dirtUnder(plan);
        var ledger = new com.cookiecraftmods.roadbuilder.placement.PlacementOwnershipLedger(
                Level.OVERWORLD
        );
        long started = System.nanoTime();
        PlacementCommitResult result = new BlockPlanCommitter().commit(
                operationId(10_000L + runIndex),
                plan,
                world,
                ledger
        );
        long elapsed = System.nanoTime() - started;
        require(result instanceof PlacementCommitResult.Success, "baseline commit failed");
        require(
                ledger.operations().size() == 1 && world.writeCount == PLAN_BLOCK_COUNT,
                "baseline commit did not publish the complete plan"
        );
        return elapsed;
    }

    private static SavedSizes writeSavedData(
            BlockPlacementPlan plan,
            Path dataDirectory
    ) throws Exception {
        RoadBuilderPlacementSavedData placement = RoadBuilderPlacementSavedData.create(
                Level.OVERWORLD
        );
        MapWorld world = MapWorld.dirtUnder(plan);
        PlacementCommitResult placementResult = new BlockPlanCommitter().commit(
                operationId(20_000L),
                RoadMaterializationOwner.segment(new RoadSegmentId(new UUID(0L, 30_000L))),
                plan,
                world,
                placement.ledger()
        );
        require(
                placementResult instanceof PlacementCommitResult.Success,
                "placement SavedData fixture commit failed"
        );

        RoadBuilderNetworkSavedData network = RoadBuilderNetworkSavedData.create(
                Level.OVERWORLD
        );
        for (int index = 0; index < GRAPH_SEGMENT_COUNT; index++) {
            double x = index * 4.0;
            var addition = network.graph().prepareStraightRoad(
                    new StraightGeometry(
                            new RoadPoint(x + 0.5, 64.0, 256.5),
                            new RoadPoint(x + 3.5, 64.0, 256.5)
                    ),
                    PRESET_ID,
                    PRESET,
                    TrafficHandedness.RHT
            );
            network.graph().applyAddition(addition);
        }

        DimensionDataStorage storage = RoadSavedDataFixtures.open(dataDirectory);
        storage.set(RoadBuilderPlacementSavedData.DATA_NAME, placement);
        storage.set(RoadBuilderNetworkSavedData.DATA_NAME, network);
        storage.save();
        Path placementFile = dataDirectory.resolve(
                RoadBuilderPlacementSavedData.DATA_NAME + ".dat"
        );
        Path networkFile = dataDirectory.resolve(
                RoadBuilderNetworkSavedData.DATA_NAME + ".dat"
        );
        return new SavedSizes(Files.size(placementFile), Files.size(networkFile));
    }

    private static String reportJson(
            List<Long> samples,
            List<Long> sortedSamples,
            SavedSizes sizes
    ) {
        double medianMilliseconds = nanosToMilliseconds(
                sortedSamples.get(sortedSamples.size() / 2)
        );
        double maximumMilliseconds = nanosToMilliseconds(
                sortedSamples.get(sortedSamples.size() - 1)
        );
        String sampleMilliseconds = samples.stream()
                .map(value -> String.format(Locale.ROOT, "%.3f", nanosToMilliseconds(value)))
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
        return String.format(
                Locale.ROOT,
                """
                {
                  "format_version": 1,
                  "scope": "in_memory_component_baseline",
                  "java_version": "%s",
                  "os": "%s",
                  "commit": {
                    "affected_blocks": %d,
                    "touched_chunks": 64,
                    "warmup_runs": %d,
                    "measured_runs": %d,
                    "samples_ms": [%s],
                    "median_ms": %.3f,
                    "maximum_ms": %.3f
                  },
                  "saved_data": {
                    "placement_schema": %d,
                    "placement_positions": %d,
                    "placement_stack_depth": 1,
                    "placement_compressed_bytes": %d,
                    "graph_schema": %d,
                    "graph_roads": %d,
                    "graph_nodes": %d,
                    "graph_segments": %d,
                    "graph_lanes": %d,
                    "graph_compressed_bytes": %d
                  }
                }
                """,
                escape(System.getProperty("java.version")),
                escape(System.getProperty("os.name")),
                PLAN_BLOCK_COUNT,
                WARMUP_RUNS,
                MEASURED_RUNS,
                sampleMilliseconds,
                medianMilliseconds,
                maximumMilliseconds,
                RoadBuilderPlacementSavedData.CURRENT_SCHEMA_VERSION,
                PLAN_BLOCK_COUNT,
                sizes.placementBytes(),
                RoadBuilderNetworkSavedData.CURRENT_SCHEMA_VERSION,
                GRAPH_SEGMENT_COUNT,
                GRAPH_SEGMENT_COUNT * 2,
                GRAPH_SEGMENT_COUNT,
                GRAPH_SEGMENT_COUNT * PRESET.lanes().size(),
                sizes.networkBytes()
        );
    }

    private static double nanosToMilliseconds(long nanoseconds) {
        return nanoseconds / 1_000_000.0;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static com.cookiecraftmods.roadbuilder.placement.PlacementOperationId operationId(
            long lowBits
    ) {
        return new com.cookiecraftmods.roadbuilder.placement.PlacementOperationId(
                new UUID(0L, lowBits)
        );
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private record SavedSizes(long placementBytes, long networkBytes) {
    }

    private static final class MapWorld implements PlacementBlockWorld {
        private final ResourceKey<Level> dimension;
        private final Map<BlockPos, BlockState> states;
        private int writeCount;

        private MapWorld(ResourceKey<Level> dimension, Map<BlockPos, BlockState> states) {
            this.dimension = dimension;
            this.states = states;
        }

        private static MapWorld dirtUnder(BlockPlacementPlan plan) {
            Map<BlockPos, BlockState> states = new HashMap<>(plan.affectedBlockCount());
            for (BlockPos position : plan.positions()) {
                states.put(position, Blocks.DIRT.defaultBlockState());
            }
            return new MapWorld(plan.dimension(), states);
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
            writeCount++;
            return true;
        }
    }
}
