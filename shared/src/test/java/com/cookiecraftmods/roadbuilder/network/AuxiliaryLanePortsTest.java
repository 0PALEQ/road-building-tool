package com.cookiecraftmods.roadbuilder.network;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import com.cookiecraftmods.roadbuilder.geometry.CubicBezierGeometry;
import com.cookiecraftmods.roadbuilder.geometry.LaneConnectionGeometry;
import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.RoadVector;
import com.cookiecraftmods.roadbuilder.geometry.StraightGeometry;
import com.cookiecraftmods.roadbuilder.placement.BlockPlanResult;
import com.cookiecraftmods.roadbuilder.placement.StraightRoadIntersectionMaterializer;
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

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AuxiliaryLanePortsTest {
    private static final ResourceLocation PAVEMENT = MinecraftIds.id("minecraft", "gray_concrete");
    private static final ResourceLocation MAIN_ID = MinecraftIds.id("roadbuilder", "main_test");
    private static final ResourceLocation RAMP_ID = MinecraftIds.id("roadbuilder", "ramp_test");
    private static final RoadPreset MAIN = new RoadPreset(RoadPreset.CURRENT_SCHEMA_VERSION, 1, 9,
            PAVEMENT, List.of(lane(-2), lane(2)));
    private static final RoadPreset RAMP = new RoadPreset(RoadPreset.CURRENT_SCHEMA_VERSION, 1, 3,
            PAVEMENT, List.of(lane(0)));

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.bootstrap();
    }

    @Test
    void onAndOffRampsAttachToOneLaneWithoutMovingOrDisconnectingTheTrunk() {
        for (TrafficHandedness handedness : TrafficHandedness.values()) {
            for (boolean offRamp : new boolean[]{true, false}) {
                RoadGraph graph = trunk(handedness);

                var before = graph.snapshot();

                var port = AuxiliaryLanePorts.ports(before.segments().values()).stream()
                        .filter(candidate -> candidate.incoming() == offRamp)
                        .filter(candidate -> Math.abs(candidate.position().x() - 0.5) < 0.001)
                        .max(Comparator.comparingDouble(candidate -> candidate.position().z())).orElseThrow();
                RoadPoint free = new RoadPoint(offRamp ? 30.5 : -30.5, 64, 24.5);

                var geometry = curve(offRamp ? port.position() : free,
                        offRamp ? free : port.position(), offRamp ? port.direction() : null,
                        offRamp ? null : port.direction());

                var mutation = graph.prepareRoadNetwork(geometry, RAMP_ID, RAMP, handedness);
                assertTrue(mutation.segmentSplits().isEmpty());
                graph.applyNetworkMutation(mutation);

                var after = graph.snapshot();
                before.segments().forEach((id, segment) -> assertEquals(segment, after.segments().get(id)));
                before.connectors().forEach((id, connector) -> assertEquals(connector, after.connectors().get(id)));
                assertEquals(2, before.connectors().size());
                RoadSegment ramp = mutation.placedSegments().get(0);
                assertEquals(port.nodeId(), offRamp ? ramp.startNodeId() : ramp.endNodeId());
                assertEquals(geometry, ramp.referenceGeometry());
                RoadLaneId rampLane = ramp.lanes().get(0).id();

                var connections = after.connectors().values().stream().filter(connector ->
                        connector.fromLaneId().equals(rampLane) || connector.toLaneId().equals(rampLane)).toList();
                assertEquals(1, connections.size());
                assertEquals(offRamp ? port.laneId() : rampLane, connections.get(0).fromLaneId());
                assertEquals(offRamp ? rampLane : port.laneId(), connections.get(0).toLaneId());

                var incident = after.segments().values().stream().filter(segment ->
                        segment.startNodeId().equals(port.nodeId()) || segment.endNodeId().equals(port.nodeId())).toList();

                var node = after.nodes().get(port.nodeId());
                assertTrue(RoadNodePortResolver.isValidTopology(node, incident));

                var solved = assertInstanceOf(JunctionSolveResult.Success.class,
                        new JunctionSolver().solve(node, incident, null));
                assertTrue(solved.approaches().stream().noneMatch(SolvedJunctionApproach::crosswalk));

                var plan = assertInstanceOf(BlockPlanResult.Success.class,
                        new StraightRoadIntersectionMaterializer().plan(Level.OVERWORLD, node, incident,
                                PAVEMENT, MinecraftIds.id("minecraft", "smooth_stone"), 10000, 1000000)).plan();

                // The other through lane occupies negative Z. No junction box may paint across it.
                assertTrue(plan.blocks().stream().allMatch(block -> block.position().getZ() >= 0));
                assertDoesNotThrow(() -> RoadGraph.restore(after.dimension(), after.revision(),
                        after.roads().values(), after.nodes().values(), after.segments().values(),
                        after.connectors().values(), after.junctionDefinitions().values(),
                        RoadGraphLimits.defaults(), () -> { }));
            }
        }
    }

    @Test
    void twoWayRoadPortsFollowEachCarriagewaysTravelDirection() {
        RoadPreset twoWay = new RoadPreset(RoadPreset.CURRENT_SCHEMA_VERSION, 1, 9, PAVEMENT,
                List.of(lane(-2), new LaneComponent(LaneType.VEHICLE, 3, 2, LaneTravelDirection.BACKWARD)));
        for (TrafficHandedness handedness : TrafficHandedness.values()) {
            for (boolean atStart : new boolean[]{true, false}) {
                for (int side : new int[]{-1, 1}) {
                    RoadGraph graph = new RoadGraph(Level.OVERWORLD);
                    for (int i = 0; i < 2; i++) {
                        graph.applyAddition(graph.prepareStraightRoad(new StraightGeometry(
                                new RoadPoint(-40 + 40 * i, 64, 0), new RoadPoint(40 * i, 64, 0)),
                                MAIN_ID, twoWay, handedness));
                    }
                    var port = AuxiliaryLanePorts.ports(graph.snapshot().segments().values()).stream()
                            .filter(candidate -> candidate.incoming() == atStart)
                            .filter(candidate -> Math.abs(candidate.position().x()) < 0.001)
                            .filter(candidate -> Math.signum(candidate.position().z()) == side).findFirst().orElseThrow();
                    RoadPoint free = port.position().add(port.direction().scale(atStart ? 30 : -30))
                            .add(new RoadVector(0, 0, side * 20));
                    var geometry = curve(atStart ? port.position() : free,
                            atStart ? free : port.position(), atStart ? port.direction() : null,
                            atStart ? null : port.direction());
                    var mutation = graph.prepareRoadNetwork(geometry, RAMP_ID, RAMP, handedness);
                    assertTrue(mutation.segmentSplits().isEmpty());
                    graph.applyNetworkMutation(mutation);
                    RoadLaneId branch = mutation.placedSegments().get(0).lanes().get(0).id();
                    assertEquals(1, graph.snapshot().connectors().values().stream().filter(connector ->
                            atStart ? connector.fromLaneId().equals(port.laneId()) && connector.toLaneId().equals(branch)
                                    : connector.fromLaneId().equals(branch) && connector.toLaneId().equals(port.laneId())).count());
                    graph.rebuildAutomaticConnectors();
                    assertEquals(1, graph.snapshot().connectors().values().stream().filter(connector ->
                            connector.fromLaneId().equals(branch) || connector.toLaneId().equals(branch)).count());
                }
            }
        }
    }

    @Test
    void aCurvedTrunkKeepsItsOwnLaneTangentAtTheRampPort() {
        var graph = new RoadGraph(Level.OVERWORLD);
        var trunk = new com.cookiecraftmods.roadbuilder.geometry.QuadraticBezierGeometry(
                new RoadPoint(-40, 64, 0), new RoadPoint(-20, 64, -10), new RoadPoint(0, 64, 0));
        var added = graph.prepareStraightRoad(trunk, MAIN_ID, MAIN, TrafficHandedness.RHT);
        graph.applyAddition(added);
        var port = AuxiliaryLanePorts.ports(graph.snapshot().segments().values()).stream()
                .filter(AuxiliaryLanePorts.Port::incoming)
                .max(Comparator.comparingDouble(candidate -> candidate.position().z())).orElseThrow();
        var free = port.position().add(port.direction().scale(30))
                .add(port.direction().horizontalLeftUnit().scale(20));
        var geometry = curve(port.position(), free, port.direction(), null);
        assertTrue(geometry.sample(0).tangent().dot(port.direction()) > 0.999);
        var mutation = graph.prepareRoadNetwork(geometry, RAMP_ID, RAMP, TrafficHandedness.RHT);
        graph.applyNetworkMutation(mutation);
        assertEquals(trunk, graph.snapshot().segments().get(added.segment().id()).referenceGeometry());
        assertEquals(port.nodeId(), mutation.placedSegments().get(0).startNodeId());
    }

    @Test
    void straightAndSimpleCurveKeepLaneEndpointAtTheSharedLogicalNode() {
        for (boolean simpleCurve : new boolean[]{false, true}) {
            var graph = trunk(TrafficHandedness.RHT);
            var before = graph.snapshot();
            var port = AuxiliaryLanePorts.ports(before.segments().values()).stream()
                    .filter(candidate -> candidate.incoming()
                            && Math.abs(candidate.position().x() - 0.5) < 0.001)
                    .max(Comparator.comparingDouble(candidate -> candidate.position().z())).orElseThrow();
            var end = port.position().add(new RoadVector(30, 0, 20));
            com.cookiecraftmods.roadbuilder.geometry.ReferenceGeometry geometry = simpleCurve
                    ? LaneConnectionGeometry.align(new com.cookiecraftmods.roadbuilder.geometry.QuadraticBezierGeometry(
                            port.position(), port.position().add(new RoadVector(12, 0, 5)), end), port.direction(), null)
                    : new StraightGeometry(port.position(), end);
            var mutation = graph.prepareRoadNetwork(geometry, RAMP_ID, RAMP, TrafficHandedness.RHT);
            graph.applyNetworkMutation(mutation);
            var branch = mutation.placedSegments().get(0);
            assertEquals(port.nodeId(), branch.startNodeId());
            assertEquals(port.position(), branch.referenceGeometry().start());
            assertNotEquals(before.nodes().get(port.nodeId()).position(), branch.referenceGeometry().start());
            assertEquals(before.nodes().get(port.nodeId()), graph.snapshot().nodes().get(port.nodeId()));
            assertTrue(mutation.segmentSplits().isEmpty());
        }
    }

    private static CubicBezierGeometry curve(RoadPoint start, RoadPoint end,
                                               RoadVector entry, RoadVector exit) {
        var chord = start.vectorTo(end);
        return (CubicBezierGeometry) LaneConnectionGeometry.align(new CubicBezierGeometry(
                start, start.add(chord.scale(1.0 / 3)),
                start.add(chord.scale(2.0 / 3)), end), entry, exit);
    }

    private static RoadGraph trunk(TrafficHandedness handedness) {
        RoadGraph graph = new RoadGraph(Level.OVERWORLD);
        graph.applyAddition(graph.prepareStraightRoad(new StraightGeometry(
                new RoadPoint(-40.5, 64, 0.5), new RoadPoint(0.5, 64, 0.5)), MAIN_ID, MAIN, handedness));
        graph.applyAddition(graph.prepareStraightRoad(new StraightGeometry(
                new RoadPoint(0.5, 64, 0.5), new RoadPoint(40.5, 64, 0.5)), MAIN_ID, MAIN, handedness));
        return graph;
    }

    private static LaneComponent lane(double offset) {
        return new LaneComponent(LaneType.VEHICLE, 3, offset, LaneTravelDirection.FORWARD);
    }
}
