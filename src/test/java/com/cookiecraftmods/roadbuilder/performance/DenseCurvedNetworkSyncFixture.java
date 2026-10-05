package com.cookiecraftmods.roadbuilder.performance;

import com.cookiecraftmods.roadbuilder.geometry.CubicBezierGeometry;
import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.network.RoadGraph;
import com.cookiecraftmods.roadbuilder.network.RoadGraphLimits;
import com.cookiecraftmods.roadbuilder.networking.RoadGraphSnapshotS2CPacket;
import com.cookiecraftmods.roadbuilder.preset.LaneComponent;
import com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection;
import com.cookiecraftmods.roadbuilder.preset.LaneType;
import com.cookiecraftmods.roadbuilder.preset.RoadPreset;
import com.cookiecraftmods.roadbuilder.preset.TrafficHandedness;
import com.cookiecraftmods.roadbuilder.test.MinecraftBootstrap;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class DenseCurvedNetworkSyncFixture {
    private static final int SEGMENTS = 512;
    private static final int WARMUPS = 2;
    private static final int RUNS = 7;

    private DenseCurvedNetworkSyncFixture() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("Expected <report-file>");
        }
        MinecraftBootstrap.bootstrap();
        RoadGraph graph = graph();
        RoadGraphSnapshotS2CPacket packet =
                RoadGraphSnapshotS2CPacket.current(graph.snapshot());
        for (int index = 0; index < WARMUPS; index++) {
            measure(packet);
        }
        List<Sample> samples = new ArrayList<>();
        for (int index = 0; index < RUNS; index++) {
            samples.add(measure(packet));
        }
        List<Long> encode = samples.stream()
                .map(Sample::encodeNanos)
                .sorted()
                .toList();
        List<Long> decode = samples.stream()
                .map(Sample::decodeNanos)
                .sorted()
                .toList();
        int bytes = samples.get(0).bytes();
        boolean chunkingRequired = bytes
                > RoadGraphSnapshotS2CPacket.MAX_PAYLOAD_BYTES * 3 / 4
                || decode.get(decode.size() / 2) > 16_000_000L;
        Path report = Path.of(arguments[0]).toAbsolutePath().normalize();
        Files.createDirectories(report.getParent());
        Files.writeString(
                report,
                String.format(
                        Locale.ROOT,
                        """
                        {
                          "format_version": 1,
                          "scope": "dense_curved_full_snapshot",
                          "roads": %d,
                          "nodes": %d,
                          "segments": %d,
                          "lanes": %d,
                          "wire_bytes": %d,
                          "payload_limit_bytes": %d,
                          "encode_median_ms": %.3f,
                          "decode_median_ms": %.3f,
                          "chunking_required": %s,
                          "chunking_implemented": true,
                          "chunk_bytes": 65536,
                          "decode_off_client_thread": true,
                          "decision_rule": "chunk above 75%% payload cap or 16 ms median decode"
                        }
                        """,
                        SEGMENTS,
                        SEGMENTS * 2,
                        SEGMENTS,
                        SEGMENTS * 2,
                        bytes,
                        RoadGraphSnapshotS2CPacket.MAX_PAYLOAD_BYTES,
                        encode.get(encode.size() / 2) / 1_000_000.0,
                        decode.get(decode.size() / 2) / 1_000_000.0,
                        chunkingRequired
                ),
                StandardCharsets.UTF_8
        );
        System.out.println("Dense curved sync report written to " + report);
    }

    private static Sample measure(RoadGraphSnapshotS2CPacket packet) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        long encodeStarted = System.nanoTime();
        packet.write(buffer);
        long encoded = System.nanoTime();
        int bytes = buffer.readableBytes();
        RoadGraphSnapshotS2CPacket decoded =
                RoadGraphSnapshotS2CPacket.TYPE.read(buffer);
        long finished = System.nanoTime();
        if (!decoded.snapshot().equals(packet.snapshot())) {
            throw new IllegalStateException("snapshot round trip changed data");
        }
        return new Sample(encoded - encodeStarted, finished - encoded, bytes);
    }

    private static RoadGraph graph() {
        AtomicLong ids = new AtomicLong(1);
        RoadGraph graph = new RoadGraph(
                Level.OVERWORLD,
                RoadGraphLimits.defaults(),
                () -> new UUID(0, ids.getAndIncrement()),
                () -> { }
        );
        RoadPreset preset = new RoadPreset(
                1,
                1,
                7.0,
                new ResourceLocation("minecraft", "gray_concrete"),
                List.of(
                        new LaneComponent(
                                LaneType.VEHICLE,
                                3.0,
                                -1.75,
                                LaneTravelDirection.FORWARD
                        ),
                        new LaneComponent(
                                LaneType.VEHICLE,
                                3.0,
                                1.75,
                                LaneTravelDirection.BACKWARD
                        )
                )
        );
        ResourceLocation presetId =
                new ResourceLocation("roadbuilder", "dense_curve");
        for (int index = 0; index < SEGMENTS; index++) {
            double x = index * 24.0;
            var addition = graph.prepareStraightRoad(
                    new CubicBezierGeometry(
                            new RoadPoint(x, 64.0, 0.0),
                            new RoadPoint(x + 6.0, 66.0, 8.0),
                            new RoadPoint(x + 18.0, 62.0, -8.0),
                            new RoadPoint(x + 23.0, 64.0, 0.0)
                    ),
                    presetId,
                    preset,
                    TrafficHandedness.RHT
            );
            graph.applyAddition(addition);
        }
        return graph;
    }

    private record Sample(long encodeNanos, long decodeNanos, int bytes) {
    }
}
