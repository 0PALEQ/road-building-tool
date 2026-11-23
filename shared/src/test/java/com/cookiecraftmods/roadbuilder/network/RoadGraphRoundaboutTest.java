package com.cookiecraftmods.roadbuilder.network;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import com.cookiecraftmods.roadbuilder.geometry.CubicBezierGeometry;
import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.RoundaboutGeometry;
import com.cookiecraftmods.roadbuilder.preset.LaneComponent;
import com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection;
import com.cookiecraftmods.roadbuilder.preset.LaneType;
import com.cookiecraftmods.roadbuilder.preset.RoadPreset;
import com.cookiecraftmods.roadbuilder.preset.TrafficHandedness;
import com.cookiecraftmods.roadbuilder.test.MinecraftBootstrap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadGraphRoundaboutTest {
    private static final ResourceLocation PRESET_ID =
            MinecraftIds.id("roadbuilder", "roundabout_test");
    private static final RoadPreset PRESET = new RoadPreset(
            RoadPreset.CURRENT_SCHEMA_VERSION,
            1,
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

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftBootstrap.bootstrap();
    }

    @Test
    void storesAClosedRoundaboutAsFourEditableConnectedSegments() {
        AtomicLong ids = new AtomicLong(20_000);
        RoadGraph graph = new RoadGraph(
                Level.OVERWORLD,
                RoadGraphLimits.defaults(),
                () -> new UUID(0L, ids.getAndIncrement()),
                () -> { }
        );
        RoundaboutGeometry geometry = new RoundaboutGeometry(
                new RoadPoint(0.5, 64.0, 0.5),
                new RoadPoint(12.5, 64.0, 0.5)
        );

        PreparedRoadNetworkMutation mutation = graph.prepareRoadNetwork(
                geometry,
                PRESET_ID,
                PRESET,
                TrafficHandedness.RHT
        );

        assertTrue(mutation.createsRoad());
        assertEquals(4, mutation.addedNodes().size());
        assertEquals(4, mutation.placedSegments().size());
        mutation.placedSegments().forEach(segment -> assertInstanceOf(
                CubicBezierGeometry.class,
                segment.referenceGeometry()
        ));

        graph.applyNetworkMutation(mutation);
        RoadGraphSnapshot snapshot = graph.snapshot();

        assertEquals(1, snapshot.roads().size());
        assertEquals(4, snapshot.nodes().size());
        assertEquals(4, snapshot.segments().size());
        assertEquals(8, snapshot.lanes().size());
        assertEquals(8, snapshot.connectors().size());
        assertTrue(snapshot.lanes().values().stream().allMatch(lane ->
                lane.component().direction() == LaneTravelDirection.FORWARD
        ));
        assertTrue(mutation.placedSegments().get(0)
                .referenceGeometry().sample(0.0).tangent().z() < 0.0);
        RoadLaneGraph laneGraph = RoadLaneGraph.from(snapshot);
        assertEquals(8, laneGraph.connectorPaths().size());
        assertTrue(laneGraph.connectorPaths().values().stream()
                .allMatch(path -> path.geometry().isPresent()));
        for (RoadNode node : snapshot.nodes().values()) {
            long incident = snapshot.segments().values().stream()
                    .filter(segment -> segment.startNodeId().equals(node.id())
                            || segment.endNodeId().equals(node.id()))
                    .count();
            long starts = snapshot.segments().values().stream()
                    .filter(segment -> segment.startNodeId().equals(node.id()))
                    .count();
            long ends = snapshot.segments().values().stream()
                    .filter(segment -> segment.endNodeId().equals(node.id()))
                    .count();
            assertEquals(2, incident);
            assertEquals(1, starts);
            assertEquals(1, ends);
        }
    }

    @Test
    void convertsIntersectionAndPreservesConnectedEditableApproaches() {
        AtomicLong ids = new AtomicLong(40_000);
        RoadGraph graph = new RoadGraph(Level.OVERWORLD, RoadGraphLimits.defaults(),
                () -> new UUID(0L, ids.getAndIncrement()), () -> { });
        RoadPoint center = new RoadPoint(0.5, 64, 0.5);
        for (RoadPoint end : List.of(new RoadPoint(40.5, 64, 0.5), new RoadPoint(-39.5, 64, 0.5),
                new RoadPoint(0.5, 64, 40.5), new RoadPoint(0.5, 64, -39.5))) {
            graph.applyNetworkMutation(graph.prepareStraightRoadNetwork(
                    new com.cookiecraftmods.roadbuilder.geometry.StraightGeometry(center, end),
                    PRESET_ID, PRESET, TrafficHandedness.RHT));
        }
        RoadNode originalCenter = graph.snapshot().nodes().values().stream()
                .filter(node -> node.position().distanceTo(center) < 0.1).findFirst().orElseThrow();
        var mutation = graph.prepareRoadNetwork(new RoundaboutGeometry(center, new RoadPoint(12.5, 64, 0.5)),
                PRESET_ID, PRESET, TrafficHandedness.RHT);
        assertEquals(4, mutation.segmentSplits().size());
        graph.applyNetworkMutation(mutation);
        assertTrue(graph.snapshot().segments().values().stream().noneMatch(segment ->
                segment.startNodeId().equals(originalCenter.id()) || segment.endNodeId().equals(originalCenter.id())));
        var snapshot = graph.snapshot();
        assertTrue(!snapshot.nodes().containsKey(originalCenter.id()));
        assertEquals(snapshot, com.cookiecraftmods.roadbuilder.networking.RoadGraphSnapshotS2CPacket.decode(
                com.cookiecraftmods.roadbuilder.networking.RoadGraphSnapshotS2CPacket.current(snapshot).encode()).snapshot());
        for (RoadSegment ring : mutation.placedSegments()) {
            assertTrue(ring.lanes().stream().allMatch(lane -> lane.component().type() == LaneType.ROUNDABOUT));
        }
        for (var split : mutation.segmentSplits()) {
            RoadSegment approach = split.replacements().get(0);
            assertTrue(approach.referenceGeometry().length() < split.original().referenceGeometry().length());
            for (RoadLane lane : approach.lanes()) {
                assertTrue(snapshot.connectors().values().stream().anyMatch(connector ->
                        connector.fromLaneId().equals(lane.id()) && snapshot.lanes().get(connector.toLaneId()).component().type() == LaneType.ROUNDABOUT
                        || connector.toLaneId().equals(lane.id()) && snapshot.lanes().get(connector.fromLaneId()).component().type() == LaneType.ROUNDABOUT));
            }
        }
        RoadSegment ring = mutation.placedSegments().get(0);
        var edit = graph.prepareSegmentReplacement(ring.id(), PRESET_ID, PRESET);
        graph.applySegmentReplacement(edit);
        assertTrue(edit.replacement().lanes().stream().allMatch(lane -> lane.component().type() == LaneType.ROUNDABOUT
                && lane.component().direction() == LaneTravelDirection.FORWARD));
    }

    @Test
    void convertsSkewedThreeWayJunctionWithCurvedApproachForBothTrafficDirections() {
        for (TrafficHandedness handedness : TrafficHandedness.values()) {
            RoadGraph graph = new RoadGraph(Level.OVERWORLD);
            RoadPoint center = new RoadPoint(0.5, 64, 0.5);
            for (double angle : List.of(0.2, 2.4, 4.4)) {
                var outward = new com.cookiecraftmods.roadbuilder.geometry.RoadVector(Math.cos(angle), 0, Math.sin(angle));
                var sideways = new com.cookiecraftmods.roadbuilder.geometry.RoadVector(-Math.sin(angle), 0, Math.cos(angle));
                var geometry = new CubicBezierGeometry(center, center.add(outward.scale(12)),
                        center.add(outward.scale(28)).add(sideways.scale(3)), center.add(outward.scale(40)));
                graph.applyNetworkMutation(graph.prepareRoadNetwork(geometry, PRESET_ID, PRESET, handedness));
            }
            var before = graph.snapshot();
            var mutation = graph.prepareRoadNetwork(new RoundaboutGeometry(center, center.add(
                    new com.cookiecraftmods.roadbuilder.geometry.RoadVector(12, 0, 0))), PRESET_ID, PRESET, handedness);
            assertEquals(3, mutation.segmentSplits().size());
            graph.applyNetworkMutation(mutation);
            var delta = RoadGraphDelta.between(before, graph.snapshot());
            assertEquals(graph.snapshot(), delta.applyTo(before));
            for (var replacement : mutation.replacementSegments()) {
                assertInstanceOf(CubicBezierGeometry.class, replacement.referenceGeometry());
                assertEquals(4, graph.snapshot().connectors().values().stream().filter(connector ->
                        replacement.lanes().stream().anyMatch(lane -> lane.id().equals(connector.fromLaneId())
                                || lane.id().equals(connector.toLaneId()))).count());
            }
        }
    }

    @Test
    void roundaboutLanesConnectToEveryCategoryInBothDirections() {
        for (LaneType type : LaneType.values()) {
            assertTrue(LaneConnectionCompatibility.canTransition(type, LaneType.ROUNDABOUT));
            assertTrue(LaneConnectionCompatibility.canTransition(LaneType.ROUNDABOUT, type));
        }
    }

    @Test
    void leftHandTrafficCirculatesClockwise() {
        AtomicLong ids = new AtomicLong(25_000);
        RoadGraph graph = new RoadGraph(
                Level.OVERWORLD,
                RoadGraphLimits.defaults(),
                () -> new UUID(0L, ids.getAndIncrement()),
                () -> { }
        );
        RoundaboutGeometry geometry = new RoundaboutGeometry(
                new RoadPoint(0.5, 64.0, 0.5),
                new RoadPoint(12.5, 64.0, 0.5)
        );

        PreparedRoadNetworkMutation mutation = graph.prepareRoadNetwork(
                geometry,
                PRESET_ID,
                PRESET,
                TrafficHandedness.LHT
        );

        assertTrue(mutation.placedSegments().get(0)
                .referenceGeometry().sample(0.0).tangent().z() > 0.0);
        assertTrue(mutation.placedSegments().stream()
                .flatMap(segment -> segment.lanes().stream())
                .allMatch(lane -> lane.component().direction()
                        == LaneTravelDirection.FORWARD));
    }

    @Test
    void rejectsARadiusThatCannotFitTheSelectedRoad() {
        AtomicLong ids = new AtomicLong(27_000);
        RoadGraph graph = new RoadGraph(
                Level.OVERWORLD,
                RoadGraphLimits.defaults(),
                () -> new UUID(0L, ids.getAndIncrement()),
                () -> { }
        );
        RoundaboutGeometry geometry = new RoundaboutGeometry(
                new RoadPoint(0.5, 64.0, 0.5),
                new RoadPoint(8.5, 64.0, 0.5)
        );

        assertThrows(IllegalArgumentException.class, () ->
                graph.prepareRoadNetwork(
                        geometry,
                        PRESET_ID,
                        PRESET,
                        TrafficHandedness.RHT
                ));
    }

    @Test
    void aFeederCanAttachToAnyPointOnTheRoundaboutArc() {
        AtomicLong ids = new AtomicLong(30_000);
        RoadGraph graph = new RoadGraph(
                Level.OVERWORLD,
                RoadGraphLimits.defaults(),
                () -> new UUID(0L, ids.getAndIncrement()),
                () -> { }
        );
        RoundaboutGeometry geometry = new RoundaboutGeometry(
                new RoadPoint(0.5, 64.0, 0.5),
                new RoadPoint(12.5, 64.0, 0.5)
        );
        graph.applyNetworkMutation(graph.prepareRoadNetwork(
                geometry,
                PRESET_ID,
                PRESET,
                TrafficHandedness.RHT
        ));
        CubicBezierGeometry arc = geometry.spans().get(0);
        RoadPoint attachment = arc.sample(arc.length() / 2.0).position();
        var outward = geometry.center().vectorTo(attachment).normalize();

        PreparedRoadNetworkMutation feeder =
                graph.prepareStraightRoadNetwork(
                        new com.cookiecraftmods.roadbuilder.geometry.StraightGeometry(
                                attachment,
                                attachment.add(outward.scale(12.0))
                        ),
                        PRESET_ID,
                        PRESET,
                        TrafficHandedness.RHT
                );

        assertEquals(1, feeder.segmentSplits().size());
        assertEquals(1, feeder.placedSegments().size());
        graph.applyNetworkMutation(feeder);
        assertTrue(graph.snapshot().nodes().values().stream().anyMatch(node ->
                graph.snapshot().segments().values().stream()
                        .filter(segment ->
                                segment.startNodeId().equals(node.id())
                                        || segment.endNodeId().equals(node.id()))
                        .count() == 3
        ));
    }
}
