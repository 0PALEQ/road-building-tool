package com.cookiecraftmods.roadbuilder.networking;

import com.cookiecraftmods.roadbuilder.persistence.RoadNbtIo;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.StraightGeometry;
import com.cookiecraftmods.roadbuilder.geometry.QuadraticBezierGeometry;
import com.cookiecraftmods.roadbuilder.network.RoadGraph;
import com.cookiecraftmods.roadbuilder.network.RoadGraphLimits;
import com.cookiecraftmods.roadbuilder.network.RoadGraphSnapshot;
import com.cookiecraftmods.roadbuilder.preset.LaneComponent;
import com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection;
import com.cookiecraftmods.roadbuilder.preset.LaneType;
import com.cookiecraftmods.roadbuilder.preset.RoadPreset;
import com.cookiecraftmods.roadbuilder.preset.RoadMarkingPattern;
import com.cookiecraftmods.roadbuilder.preset.RoadSurfaceBand;
import com.cookiecraftmods.roadbuilder.preset.RoadSurfaceRole;
import com.cookiecraftmods.roadbuilder.preset.TrafficHandedness;
import com.cookiecraftmods.roadbuilder.test.MinecraftBootstrap;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadGraphSnapshotS2CPacketTest {
    @Test
    void largeGraphRoundTripsThroughChunksAndAcceptsFurtherDeltas() {
        RoadGraph graph = new RoadGraph(Level.OVERWORLD);
        RoadPreset preset = new RoadPreset(
                RoadPreset.CURRENT_SCHEMA_VERSION, 3, 3.0,
                MinecraftIds.id("minecraft", "gray_concrete"),
                List.of(new LaneComponent(LaneType.VEHICLE, 3.0, 0.0,
                        LaneTravelDirection.FORWARD))
        );
        for (int index = 0; index < 3000; index++) {
            graph.applyAddition(graph.prepareStraightRoad(
                    new StraightGeometry(new RoadPoint(index * 30.0, 64, 0),
                            new RoadPoint(index * 30.0 + 20, 64, 0)),
                    MinecraftIds.id("roadbuilder", "large_graph"),
                    preset, TrafficHandedness.RHT));
        }
        RoadGraphSnapshot snapshot = graph.snapshot();
        byte[] encoded = RoadGraphSnapshotS2CPacket.current(snapshot).encode();
        assertTrue(encoded.length > RoadGraphSnapshotS2CPacket.MAX_PAYLOAD_BYTES);
        assertChunkRoundTrip(snapshot, encoded);
        FriendlyByteBuf direct = new FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(encoded));
        try {
            assertThrows(RuntimeException.class,
                    () -> RoadGraphSnapshotS2CPacket.TYPE.read(direct));
        } finally {
            direct.release();
        }
        graph.applyAddition(graph.prepareStraightRoad(
                new StraightGeometry(new RoadPoint(-30, 64, 0), new RoadPoint(-10, 64, 0)),
                MinecraftIds.id("roadbuilder", "large_graph"), preset, TrafficHandedness.RHT));
        var delta = com.cookiecraftmods.roadbuilder.network.RoadGraphDelta.between(snapshot, graph.snapshot());
        assertEquals(delta, RoadGraphPacketCodecs.decodeDelta(RoadGraphPacketCodecs.encodeDelta(delta)));
    }

    @Test
    void suppliedSavedGraphCanSynchronize() throws Exception {
        String file = System.getenv("ROADBUILDER_VERIFY_GRAPH");
        org.junit.jupiter.api.Assumptions.assumeTrue(file != null);
        var data = com.cookiecraftmods.roadbuilder.persistence.RoadBuilderNetworkSavedData.load(
                Level.OVERWORLD, RoadNbtIo.readCompressed(java.nio.file.Path.of(file)).getCompound("data"));
        assertTrue(data.isCompatible());
        RoadGraphSnapshot snapshot = data.graph().snapshot();
        byte[] encoded = RoadGraphSnapshotS2CPacket.current(snapshot).encode();
        assertChunkRoundTrip(snapshot, encoded);
        System.out.println("Verified saved graph: " + snapshot.segments().size()
                + " segments, " + encoded.length + " encoded bytes");
    }

    private static void assertChunkRoundTrip(RoadGraphSnapshot snapshot, byte[] encoded) {
        var assembler = new com.cookiecraftmods.roadbuilder.client.networking.RoadGraphSnapshotChunkAssembler();
        var checksum = new java.util.zip.CRC32();
        checksum.update(encoded);
        int size = RoadGraphSnapshotChunkS2CPacket.MAXIMUM_CHUNK_BYTES;
        int count = (encoded.length + size - 1) / size;
        UUID transfer = UUID.randomUUID();
        java.util.Optional<RoadGraphSnapshot> result = java.util.Optional.empty();
        for (int index = count - 1; index >= 0; index--) {
            result = assembler.accept(RoadGraphSnapshotChunkS2CPacket.current(
                    transfer, snapshot.dimension().location(), snapshot.revision(),
                    index, count, encoded.length, checksum.getValue(),
                    java.util.Arrays.copyOfRange(encoded, index * size,
                            Math.min(encoded.length, (index + 1) * size))));
        }
        assertEquals(snapshot, result.orElseThrow());
    }

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        MinecraftBootstrap.bootstrap();
    }

    @Test
    void fullPathGraphRoundTripsWithStableDerivedLanes() {
        RoadGraphSnapshot snapshot = populatedSnapshot(TrafficHandedness.LHT);
        RoadGraphSnapshotS2CPacket packet = RoadGraphSnapshotS2CPacket.current(snapshot);
        FriendlyByteBuf buffer = PacketByteBufs.create();

        packet.write(buffer);

        assertTrue(buffer.readableBytes() <= RoadGraphSnapshotS2CPacket.MAX_PAYLOAD_BYTES);
        RoadGraphSnapshotS2CPacket decoded = RoadGraphSnapshotS2CPacket.TYPE.read(buffer);
        assertAll(
                () -> assertEquals(packet, decoded),
                () -> assertEquals(snapshot.lanes(), decoded.snapshot().lanes()),
                () -> assertEquals(
                        List.of(
                                RoadMarkingPattern.DASHED,
                                RoadMarkingPattern.DOUBLE_CONTINUOUS
                        ),
                        decoded.snapshot().segments().values().iterator().next()
                                .presetSnapshot().surfaceBands().stream()
                                .filter(band -> band.role()
                                        == RoadSurfaceRole.MARKING)
                                .map(RoadSurfaceBand::pattern)
                                .toList()
                ),
                () -> assertEquals(0, buffer.readableBytes()),
                () -> assertSame(RoadGraphSnapshotS2CPacket.TYPE, packet.packetType()),
                () -> assertEquals(
                        MinecraftIds.id("roadbuilder", "road_graph_snapshot_v2"),
                        packet.packetType().id()
                )
        );
    }

    @Test
    void decoderRejectsCountOverflowAndTrailingData() {
        FriendlyByteBuf excessiveRoads = PacketByteBufs.create();
        excessiveRoads.writeVarInt(RoadGraphSnapshotS2CPacket.CURRENT_PROTOCOL_VERSION);
        excessiveRoads.writeUtf(Level.OVERWORLD.location().toString());
        excessiveRoads.writeVarLong(0);
        excessiveRoads.writeVarInt(RoadGraphPacketCodecs.MAXIMUM_ROAD_COUNT + 1);

        FriendlyByteBuf trailing = PacketByteBufs.create();
        RoadGraphSnapshotS2CPacket.current(
                new RoadGraph(Level.OVERWORLD).snapshot()
        ).write(trailing);
        trailing.writeByte(0x5a);

        assertAll(
                () -> assertThrows(
                        RuntimeException.class,
                        () -> RoadGraphSnapshotS2CPacket.TYPE.read(excessiveRoads)
                ),
                () -> assertThrows(
                        RuntimeException.class,
                        () -> RoadGraphSnapshotS2CPacket.TYPE.read(trailing)
                )
        );
    }

    @Test
    void resyncRequestRoundTripsKnownAndUnknownRevisions() {
        for (long revision : List.of(RoadGraphResyncC2SPacket.NO_KNOWN_REVISION, 42L)) {
            RoadGraphResyncC2SPacket packet = RoadGraphResyncC2SPacket.current(
                    Level.OVERWORLD.location(),
                    revision
            );
            FriendlyByteBuf buffer = PacketByteBufs.create();
            packet.write(buffer);

            assertEquals(packet, RoadGraphResyncC2SPacket.TYPE.read(buffer));
            assertEquals(0, buffer.readableBytes());
        }
    }

    @Test
    void curvedCanonicalGeometryRoundTripsWithoutSamplePayloads() {
        RoadPreset preset = new RoadPreset(
                RoadPreset.CURRENT_SCHEMA_VERSION,
                3,
                3.0,
                MinecraftIds.id("minecraft", "gray_concrete"),
                List.of(new LaneComponent(
                        LaneType.VEHICLE,
                        3.0,
                        0.0,
                        LaneTravelDirection.FORWARD
                ))
        );
        RoadGraph graph = new RoadGraph(Level.OVERWORLD);
        graph.applyNetworkMutation(graph.prepareRoadNetwork(
                new QuadraticBezierGeometry(
                        new RoadPoint(0.5, 64.0, 0.5),
                        new RoadPoint(5.5, 64.0, 6.5),
                        new RoadPoint(12.5, 64.0, 0.5)
                ),
                MinecraftIds.id("roadbuilder", "curve"),
                preset,
                TrafficHandedness.RHT
        ));
        FriendlyByteBuf buffer = PacketByteBufs.create();
        RoadGraphSnapshotS2CPacket.current(graph.snapshot()).write(buffer);

        RoadGraphSnapshot decoded =
                RoadGraphSnapshotS2CPacket.TYPE.read(buffer).snapshot();

        assertInstanceOf(
                QuadraticBezierGeometry.class,
                decoded.segments().values().iterator().next()
                        .referenceGeometry()
        );
        assertEquals(graph.snapshot(), decoded);
    }

    private static RoadGraphSnapshot populatedSnapshot(TrafficHandedness handedness) {
        RoadPreset preset = new RoadPreset(
                RoadPreset.CURRENT_SCHEMA_VERSION,
                3,
                7.0,
                MinecraftIds.id("minecraft", "gray_concrete"),
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
                ),
                List.of(
                        new RoadSurfaceBand(
                                RoadSurfaceRole.PAVEMENT,
                                1,
                                MinecraftIds.id(
                                        "minecraft",
                                        "gray_concrete"
                                )
                        ),
                        new RoadSurfaceBand(
                                RoadSurfaceRole.MARKING,
                                1,
                                MinecraftIds.id(
                                        "minecraft",
                                        "white_concrete"
                                ),
                                RoadMarkingPattern.DASHED
                        ),
                        new RoadSurfaceBand(
                                RoadSurfaceRole.PAVEMENT,
                                1,
                                MinecraftIds.id(
                                        "minecraft",
                                        "gray_concrete"
                                )
                        ),
                        new RoadSurfaceBand(
                                RoadSurfaceRole.MARKING,
                                3,
                                MinecraftIds.id(
                                        "minecraft",
                                        "yellow_concrete"
                                ),
                                RoadMarkingPattern.DOUBLE_CONTINUOUS
                        ).withUnderlayBlockSet(List.of(MinecraftIds.id("minecraft", "stone"),
                                MinecraftIds.id("minecraft", "andesite"))),
                        new RoadSurfaceBand(
                                RoadSurfaceRole.PAVEMENT,
                                1,
                                MinecraftIds.id(
                                        "minecraft",
                                        "gray_concrete"
                                )
                        )
                )
        );
        AtomicLong ids = new AtomicLong(1);
        RoadGraph graph = new RoadGraph(
                Level.OVERWORLD,
                RoadGraphLimits.defaults(),
                () -> new UUID(0, ids.getAndIncrement()),
                () -> {
                }
        );
        graph.applyAddition(graph.prepareStraightRoad(
                new StraightGeometry(
                        new RoadPoint(0.5, 64.0, 0.5),
                        new RoadPoint(20.5, 64.0, 0.5)
                ),
                MinecraftIds.id("roadbuilder", "two_way"),
                preset,
                handedness
        ));
        return graph.snapshot();
    }
}
