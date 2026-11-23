package com.cookiecraftmods.roadbuilder.gametest;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.StraightGeometry;
import com.cookiecraftmods.roadbuilder.network.RoadGraph;
import com.cookiecraftmods.roadbuilder.network.RoadGraphDelta;
import com.cookiecraftmods.roadbuilder.network.RoadGraphSnapshot;
import com.cookiecraftmods.roadbuilder.networking.RoadGraphDeltaS2CPacket;
import com.cookiecraftmods.roadbuilder.persistence.RoadBuilderPlacementSavedData;
import com.cookiecraftmods.roadbuilder.placement.BlockPlacementPlan;
import com.cookiecraftmods.roadbuilder.placement.BlockPlanCommitter;
import com.cookiecraftmods.roadbuilder.placement.PlacementCommitResult;
import com.cookiecraftmods.roadbuilder.placement.PlacementOperationId;
import com.cookiecraftmods.roadbuilder.placement.PlacementRemovalResult;
import com.cookiecraftmods.roadbuilder.placement.PlacementRemovalService;
import com.cookiecraftmods.roadbuilder.placement.PlannedBlock;
import com.cookiecraftmods.roadbuilder.placement.RemovalMode;
import com.cookiecraftmods.roadbuilder.placement.ServerLevelPlacementBlockWorld;
import com.cookiecraftmods.roadbuilder.preset.RoadPreset;
import com.cookiecraftmods.roadbuilder.preset.RoadPresetResources;
import com.cookiecraftmods.roadbuilder.preset.TrafficHandedness;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.ToLongFunction;

public final class RoadBuilderServerLevelProfileGameTests implements FabricGameTest {
    public static final String REPORT_FILE_PROPERTY =
            "roadbuilder.serverProfile.reportFile";

    private static final int[] SAMPLE_WIDTHS = {32, 64, 128};
    private static final int WARMUP_RUNS = 2;
    private static final int MEASURED_RUNS = 5;
    private static final int SAMPLE_Y = 80;
    private static final int SAMPLE_ORIGIN = 4_096;
    private static final int SAMPLE_SPACING = 256;
    private static final int OBSERVER_COUNT = 2;
    private static final ResourceLocation PRESET_ID =
            MinecraftIds.id("roadbuilder", "two_way");

    @GameTest(
            template = FabricGameTest.EMPTY_STRUCTURE,
            batch = "roadbuilderServerProfile",
            timeoutTicks = 600
    )
    public void measureRepresentativeRealServerLevelTicks(GameTestHelper helper) {
        String configuredReport = System.getProperty(REPORT_FILE_PROPERTY);
        if (configuredReport == null || configuredReport.isBlank()) {
            helper.succeed();
            return;
        }

        Path reportFile = Path.of(configuredReport).toAbsolutePath().normalize();
        new ProfileRun(helper, reportFile).prepareRun(0, 0);
    }

    private static final class ProfileRun {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final MinecraftServer server;
        private final Path reportFile;
        private final List<SampleSeries> series = new ArrayList<>(SAMPLE_WIDTHS.length);
        private final List<Sample> currentSamples = new ArrayList<>(MEASURED_RUNS);

        private ProfileRun(GameTestHelper helper, Path reportFile) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.server = level.getServer();
            this.reportFile = reportFile;
        }

        private void prepareRun(int sampleIndex, int runIndex) {
            try {
                BlockPlacementPlan plan = createPlan(
                        level,
                        sampleIndex,
                        SAMPLE_WIDTHS[sampleIndex]
                );
                for (PlannedBlock block : plan.blocks()) {
                    level.setBlock(
                            block.position(),
                            Blocks.DIRT.defaultBlockState(),
                            Block.UPDATE_NONE | Block.UPDATE_SUPPRESS_DROPS
                    );
                }
                level.getChunkSource().save(true);
                level.getDataStorage().save();
                helper.runAfterDelay(1, () -> executeRun(sampleIndex, runIndex, plan));
            } catch (RuntimeException exception) {
                fail("preparing sample " + sampleIndex + " run " + runIndex, exception);
            }
        }

        private void executeRun(
                int sampleIndex,
                int runIndex,
                BlockPlacementPlan plan
        ) {
            try {
                long profiledTick = server.getTickCount();
                long totalStarted = System.nanoTime();
                PlacementOperationId operationId = new PlacementOperationId(UUID.randomUUID());

                long commitStarted = System.nanoTime();
                PlacementCommitResult commit = new BlockPlanCommitter(() -> operationId).commit(
                        plan,
                        new ServerLevelPlacementBlockWorld(level),
                        RoadBuilderPlacementSavedData.get(level).ledger()
                );
                long commitNanos = System.nanoTime() - commitStarted;
                helper.assertTrue(
                        commit instanceof PlacementCommitResult.Success,
                        "real ServerLevel profile commit should succeed; got " + commit
                );

                EncodedObservers observers = encodeObserverDelta(level, sampleIndex);

                long saveStarted = System.nanoTime();
                level.getChunkSource().save(true);
                level.getDataStorage().save();
                long saveNanos = System.nanoTime() - saveStarted;

                PendingSample pending = new PendingSample(
                        sampleIndex,
                        runIndex,
                        plan,
                        operationId,
                        commitNanos,
                        observers.elapsedNanos(),
                        observers.encodedBytesPerObserver(),
                        saveNanos,
                        System.nanoTime() - totalStarted,
                        profiledTick
                );
                helper.runAfterDelay(1, () -> captureTickAndContinue(pending));
            } catch (RuntimeException exception) {
                fail("executing sample " + sampleIndex + " run " + runIndex, exception);
            }
        }

        private void captureTickAndContinue(PendingSample pending) {
            try {
                if (pending.runIndex() >= WARMUP_RUNS) {
                    currentSamples.add(pending.withTickNanos(GameTestTickTimes.nanosAt(server, pending.profiledTick())));
                }

                PlacementRemovalResult removal = new PlacementRemovalService().remove(
                        pending.operationId(),
                        RemovalMode.FORCE,
                        new ServerLevelPlacementBlockWorld(level),
                        RoadBuilderPlacementSavedData.get(level).ledger()
                );
                helper.assertTrue(
                        removal instanceof PlacementRemovalResult.Success,
                        "profile cleanup should succeed; got " + removal
                );
                level.getChunkSource().save(true);
                level.getDataStorage().save();

                int nextRun = pending.runIndex() + 1;
                if (nextRun < WARMUP_RUNS + MEASURED_RUNS) {
                    helper.runAfterDelay(
                            1,
                            () -> prepareRun(pending.sampleIndex(), nextRun)
                    );
                    return;
                }

                series.add(new SampleSeries(
                        SAMPLE_WIDTHS[pending.sampleIndex()],
                        pending.plan().affectedBlockCount(),
                        pending.plan().touchedChunks().size(),
                        pending.observerBytes(),
                        List.copyOf(currentSamples)
                ));
                currentSamples.clear();
                int nextSample = pending.sampleIndex() + 1;
                if (nextSample < SAMPLE_WIDTHS.length) {
                    helper.runAfterDelay(1, () -> prepareRun(nextSample, 0));
                    return;
                }

                writeReport(reportFile, series);
                helper.succeed();
            } catch (RuntimeException | IOException exception) {
                fail("capturing or writing the profile", exception);
            }
        }

        private void fail(String action, Exception exception) {
            helper.fail("Failed while " + action + ": " + exception);
        }
    }

    private static BlockPlacementPlan createPlan(
            ServerLevel level,
            int sampleIndex,
            int width
    ) {
        int originX = SAMPLE_ORIGIN + sampleIndex * SAMPLE_SPACING;
        int originZ = SAMPLE_ORIGIN;
        List<PlannedBlock> blocks = new ArrayList<>(width * width);
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < width; z++) {
                blocks.add(new PlannedBlock(
                        new BlockPos(originX + x, SAMPLE_Y, originZ + z),
                        Blocks.GRAY_CONCRETE.defaultBlockState()
                ));
            }
        }
        return BlockPlacementPlan.create(level.dimension(), blocks);
    }

    private static EncodedObservers encodeObserverDelta(
            ServerLevel level,
            int sampleIndex
    ) {
        RoadPreset preset = RoadPresetResources.repository()
                .find(PRESET_ID)
                .orElseThrow(() -> new IllegalStateException("built-in preset is unavailable"));
        RoadGraph graph = new RoadGraph(level.dimension());
        RoadGraphSnapshot before = graph.snapshot();
        double coordinate = SAMPLE_ORIGIN + sampleIndex * SAMPLE_SPACING + 0.5;
        graph.applyAddition(graph.prepareStraightRoad(
                new StraightGeometry(
                        new RoadPoint(coordinate, SAMPLE_Y, SAMPLE_ORIGIN + 0.5),
                        new RoadPoint(
                                coordinate + SAMPLE_WIDTHS[sampleIndex] - 1.0,
                                SAMPLE_Y,
                                SAMPLE_ORIGIN + 0.5
                        )
                ),
                PRESET_ID,
                preset,
                TrafficHandedness.RHT
        ));
        RoadGraphDeltaS2CPacket packet = RoadGraphDeltaS2CPacket.current(
                RoadGraphDelta.between(before, graph.snapshot())
        );

        int encodedBytes = -1;
        long started = System.nanoTime();
        for (int observer = 0; observer < OBSERVER_COUNT; observer++) {
            FriendlyByteBuf buffer = PacketByteBufs.create();
            try {
                packet.write(buffer);
                if (encodedBytes < 0) {
                    encodedBytes = buffer.readableBytes();
                }
            } finally {
                buffer.release();
            }
        }
        return new EncodedObservers(System.nanoTime() - started, encodedBytes);
    }

    private static void writeReport(Path reportFile, List<SampleSeries> series)
            throws IOException {
        Files.createDirectories(reportFile.getParent());
        StringBuilder sampleJson = new StringBuilder();
        for (int index = 0; index < series.size(); index++) {
            if (index > 0) {
                sampleJson.append(",\n");
            }
            sampleJson.append(seriesJson(series.get(index)));
        }

        String report = String.format(
                Locale.ROOT,
                """
                {
                  "format_version": 2,
                  "scope": "real_server_level_machine_local_baseline",
                  "java_version": "%s",
                  "os": "%s",
                  "warmup_runs": %d,
                  "measured_runs": %d,
                  "notes": [
                    "Block writes use the production ServerLevel adapter with normal client and neighbor updates.",
                    "Each size is warmed, measured repeatedly on the same loaded chunks, then force-restored between runs.",
                    "Lighting is left on Minecraft's supported threaded path; its queueing/completion is included in block-write, forced-save, and enclosing-tick timings rather than invoked through an unsupported direct drain.",
                    "Observer work measures canonical graph-delta encoding for two observers, not live socket or remote-client handling.",
                    "Forced saves intentionally measure durability pressure separately; production synchronous placement marks state dirty but does not force a save in the request tick.",
                    "The enclosing tick includes normal GameTest server overhead and is evidence, not a universal release threshold."
                  ],
                  "samples": [
                %s
                  ]
                }
                """,
                escape(System.getProperty("java.version")),
                escape(System.getProperty("os.name")),
                WARMUP_RUNS,
                MEASURED_RUNS,
                indent(sampleJson.toString(), 4)
        );
        Files.writeString(reportFile, report, StandardCharsets.UTF_8);
        System.out.println("Road Builder real ServerLevel baseline written to " + reportFile);
    }

    private static String seriesJson(SampleSeries series) {
        return String.format(
                Locale.ROOT,
                """
                    {
                      "surface": "%dx%d",
                      "affected_blocks": %d,
                      "touched_chunks": %d,
                      "observer_count": %d,
                      "observer_delta_bytes_each": %d,
                      "commit": %s,
                      "observer_delta_encode": %s,
                      "forced_chunk_and_saved_data_save": %s,
                      "measured_tick_work": %s,
                      "enclosing_server_tick": %s
                    }""",
                series.width(),
                series.width(),
                series.affectedBlocks(),
                series.touchedChunks(),
                OBSERVER_COUNT,
                series.observerBytes(),
                timingJson(series.samples(), Sample::commitNanos),
                timingJson(series.samples(), Sample::observerNanos),
                timingJson(series.samples(), Sample::saveNanos),
                timingJson(series.samples(), Sample::measuredWorkNanos),
                timingJson(series.samples(), Sample::tickNanos)
        );
    }

    private static String timingJson(
            List<Sample> samples,
            ToLongFunction<Sample> value
    ) {
        List<Long> values = samples.stream()
                .mapToLong(value)
                .boxed()
                .toList();
        List<Long> ordered = values.stream().sorted(Comparator.naturalOrder()).toList();
        long median = ordered.get(ordered.size() / 2);
        long maximum = ordered.get(ordered.size() - 1);
        String sampleValues = values.stream()
                .map(nanos -> String.format(Locale.ROOT, "%.3f", milliseconds(nanos)))
                .reduce((left, right) -> left + ", " + right)
                .orElseThrow();
        return String.format(
                Locale.ROOT,
                "{\"samples_ms\": [%s], \"median_ms\": %.3f, \"maximum_ms\": %.3f}",
                sampleValues,
                milliseconds(median),
                milliseconds(maximum)
        );
    }

    private static String indent(String value, int spaces) {
        return value.replace("\n", "\n" + " ".repeat(spaces));
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static double milliseconds(long nanoseconds) {
        return nanoseconds / 1_000_000.0;
    }

    private record EncodedObservers(long elapsedNanos, int encodedBytesPerObserver) {
    }

    private record PendingSample(
            int sampleIndex,
            int runIndex,
            BlockPlacementPlan plan,
            PlacementOperationId operationId,
            long commitNanos,
            long observerNanos,
            int observerBytes,
            long saveNanos,
            long measuredWorkNanos,
            long profiledTick
    ) {
        private Sample withTickNanos(long tickNanos) {
            return new Sample(
                    commitNanos,
                    observerNanos,
                    saveNanos,
                    measuredWorkNanos,
                    tickNanos
            );
        }
    }

    private record Sample(
            long commitNanos,
            long observerNanos,
            long saveNanos,
            long measuredWorkNanos,
            long tickNanos
    ) {
    }

    private record SampleSeries(
            int width,
            int affectedBlocks,
            int touchedChunks,
            int observerBytes,
            List<Sample> samples
    ) {
        private SampleSeries {
            samples = List.copyOf(samples);
            if (samples.size() != MEASURED_RUNS) {
                throw new IllegalArgumentException(
                        "sample series must contain exactly " + MEASURED_RUNS + " runs"
                );
            }
        }
    }
}
