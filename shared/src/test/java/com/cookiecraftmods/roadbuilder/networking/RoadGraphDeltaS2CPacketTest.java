package com.cookiecraftmods.roadbuilder.networking;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.StraightGeometry;
import com.cookiecraftmods.roadbuilder.network.RoadGraph;
import com.cookiecraftmods.roadbuilder.network.RoadGraphDelta;
import com.cookiecraftmods.roadbuilder.network.RoadGraphSnapshot;
import com.cookiecraftmods.roadbuilder.network.JunctionDefinition;
import com.cookiecraftmods.roadbuilder.preset.LaneComponent;
import com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection;
import com.cookiecraftmods.roadbuilder.preset.LaneType;
import com.cookiecraftmods.roadbuilder.preset.RoadPreset;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadGraphDeltaS2CPacketTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        MinecraftBootstrap.bootstrap();
    }

    @Test
    void additionAndRemovalDeltasRoundTripAndRebuildCanonicalSnapshots() {
        RoadGraph graph = graph();
        RoadGraphSnapshot empty = graph.snapshot();
        var addition = graph.prepareStraightRoad(
                new StraightGeometry(
                        new RoadPoint(0.5, 64.0, 0.5),
                        new RoadPoint(20.5, 64.0, 0.5)
                ),
                MinecraftIds.id("roadbuilder", "two_way"),
                preset(),
                TrafficHandedness.LHT
        );
        graph.applyAddition(addition);
        RoadGraphSnapshot populated = graph.snapshot();

        RoadGraphDelta additionDelta = roundTrip(RoadGraphDelta.between(empty, populated));
        assertEquals(populated, additionDelta.applyTo(empty));
        assertEquals(2, additionDelta.nodes().size());
        assertEquals(1, additionDelta.segments().size());

        var continuation = graph.prepareStraightRoad(
                new StraightGeometry(
                        new RoadPoint(20.5, 64.0, 0.5),
                        new RoadPoint(40.5, 64.0, 0.5)
                ),
                MinecraftIds.id("roadbuilder", "two_way"),
                preset(),
                TrafficHandedness.LHT
        );
        graph.applyAddition(continuation);
        RoadGraphSnapshot joined = graph.snapshot();
        RoadGraphDelta joinDelta = roundTrip(RoadGraphDelta.between(populated, joined));
        assertEquals(joined, joinDelta.applyTo(populated));
        assertTrue(joinDelta.roads().isEmpty());
        assertEquals(1, joinDelta.nodes().size());
        assertEquals(1, joinDelta.segments().size());
        assertEquals(2, joinDelta.connectors().size());

        graph.applyRemoval(graph.prepareSegmentRemoval(continuation.segment().id()));
        RoadGraphSnapshot removed = graph.snapshot();
        RoadGraphDelta removalDelta = roundTrip(RoadGraphDelta.between(joined, removed));
        assertEquals(removed, removalDelta.applyTo(joined));
        assertEquals(List.of(continuation.segment().id()), removalDelta.removedSegmentIds());
        assertEquals(2, removalDelta.removedConnectorIds().size());
        assertTrue(removalDelta.segments().isEmpty());
    }

    @Test
    void decoderRejectsTrailingDataAndInvalidRevisionRange() {
        FriendlyByteBuf trailing = PacketByteBufs.create();
        RoadGraph graph = graph();
        RoadGraphSnapshot before = graph.snapshot();
        var addition = graph.prepareStraightRoad(
                new StraightGeometry(
                        new RoadPoint(0.5, 64.0, 0.5),
                        new RoadPoint(5.5, 64.0, 0.5)
                ),
                MinecraftIds.id("roadbuilder", "two_way"),
                preset(),
                TrafficHandedness.RHT
        );
        graph.applyAddition(addition);
        RoadGraphDeltaS2CPacket.current(
                RoadGraphDelta.between(before, graph.snapshot())
        ).write(trailing);
        trailing.writeByte(0x5a);

        FriendlyByteBuf invalidRevision = PacketByteBufs.create();
        invalidRevision.writeVarInt(RoadGraphDeltaS2CPacket.CURRENT_PROTOCOL_VERSION);
        invalidRevision.writeUtf(Level.OVERWORLD.location().toString());
        invalidRevision.writeVarLong(4);
        invalidRevision.writeVarLong(4);

        assertThrows(
                RuntimeException.class,
                () -> RoadGraphDeltaS2CPacket.TYPE.read(trailing)
        );
        assertThrows(
                RuntimeException.class,
                () -> RoadGraphDeltaS2CPacket.TYPE.read(invalidRevision)
        );
    }

    @Test
    void junctionDefinitionUpsertRoundTripsInTheSameRevisionedDelta() {
        RoadGraph graph = graph();
        graph.applyNetworkMutation(graph.prepareRoadNetwork(
                new StraightGeometry(
                        new RoadPoint(-10.5, 64.0, 0.5),
                        new RoadPoint(10.5, 64.0, 0.5)
                ),
                MinecraftIds.id("roadbuilder", "two_way"),
                preset(),
                TrafficHandedness.RHT
        ));
        graph.applyNetworkMutation(graph.prepareRoadNetwork(
                new StraightGeometry(
                        new RoadPoint(0.5, 64.0, -10.5),
                        new RoadPoint(0.5, 64.0, 10.5)
                ),
                MinecraftIds.id("roadbuilder", "two_way"),
                preset(),
                TrafficHandedness.RHT
        ));
        var junction = graph.snapshot().nodes().values().stream()
                .filter(node -> node.position().equals(
                        new RoadPoint(0.5, 64.0, 0.5)
                ))
                .findFirst()
                .orElseThrow();
        RoadGraphSnapshot before = graph.snapshot();

        graph.upsertJunctionDefinition(
                JunctionDefinition.automatic(junction.id()),
                graph.revision()
        );
        RoadGraphSnapshot after = graph.snapshot();

        RoadGraphDelta delta = roundTrip(
                RoadGraphDelta.between(before, after)
        );
        assertEquals(
                after.junctionDefinitions(),
                delta.applyTo(before).junctionDefinitions()
        );
        assertEquals(
                JunctionDefinition.automatic(junction.id()),
                delta.junctionDefinitions().get(junction.id())
        );
    }

    private static RoadGraphDelta roundTrip(RoadGraphDelta delta) {
        RoadGraphDeltaS2CPacket packet = RoadGraphDeltaS2CPacket.current(delta);
        FriendlyByteBuf buffer = PacketByteBufs.create();
        packet.write(buffer);

        assertTrue(buffer.readableBytes() <= RoadGraphDeltaS2CPacket.MAX_PAYLOAD_BYTES);
        RoadGraphDeltaS2CPacket decoded = RoadGraphDeltaS2CPacket.TYPE.read(buffer);
        assertEquals(packet, decoded);
        assertEquals(0, buffer.readableBytes());
        assertSame(RoadGraphDeltaS2CPacket.TYPE, packet.packetType());
        assertEquals(
                MinecraftIds.id("roadbuilder", "road_graph_delta_v2"),
                packet.packetType().id()
        );
        return decoded.delta();
    }

    private static RoadGraph graph() {
        AtomicLong ids = new AtomicLong(1);
        return new RoadGraph(
                Level.OVERWORLD,
                com.cookiecraftmods.roadbuilder.network.RoadGraphLimits.defaults(),
                () -> new UUID(0, ids.getAndIncrement()),
                () -> {
                }
        );
    }

    private static RoadPreset preset() {
        return new RoadPreset(
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
                )
        );
    }
}
