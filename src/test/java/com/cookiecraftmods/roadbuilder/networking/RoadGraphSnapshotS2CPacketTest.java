package com.cookiecraftmods.roadbuilder.networking;

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
                () -> assertSame(RoadGraphSnapshotS2CPacket.TYPE, packet.getType()),
                () -> assertEquals(
                        new ResourceLocation("roadbuilder", "road_graph_snapshot_v1"),
                        packet.getType().getId()
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
                new ResourceLocation("minecraft", "gray_concrete"),
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
                new ResourceLocation("roadbuilder", "curve"),
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
                ),
                List.of(
                        new RoadSurfaceBand(
                                RoadSurfaceRole.PAVEMENT,
                                1,
                                new ResourceLocation(
                                        "minecraft",
                                        "gray_concrete"
                                )
                        ),
                        new RoadSurfaceBand(
                                RoadSurfaceRole.MARKING,
                                1,
                                new ResourceLocation(
                                        "minecraft",
                                        "white_concrete"
                                ),
                                RoadMarkingPattern.DASHED
                        ),
                        new RoadSurfaceBand(
                                RoadSurfaceRole.PAVEMENT,
                                1,
                                new ResourceLocation(
                                        "minecraft",
                                        "gray_concrete"
                                )
                        ),
                        new RoadSurfaceBand(
                                RoadSurfaceRole.MARKING,
                                3,
                                new ResourceLocation(
                                        "minecraft",
                                        "yellow_concrete"
                                ),
                                RoadMarkingPattern.DOUBLE_CONTINUOUS
                        ),
                        new RoadSurfaceBand(
                                RoadSurfaceRole.PAVEMENT,
                                1,
                                new ResourceLocation(
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
                new ResourceLocation("roadbuilder", "two_way"),
                preset,
                handedness
        ));
        return graph.snapshot();
    }
}
