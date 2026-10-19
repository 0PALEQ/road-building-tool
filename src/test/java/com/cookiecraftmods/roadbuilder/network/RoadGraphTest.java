package com.cookiecraftmods.roadbuilder.network;

import com.cookiecraftmods.roadbuilder.geometry.CubicBezierGeometry;
import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.StraightGeometry;
import com.cookiecraftmods.roadbuilder.preset.LaneComponent;
import com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection;
import com.cookiecraftmods.roadbuilder.preset.LaneType;
import com.cookiecraftmods.roadbuilder.preset.IntersectionControl;
import com.cookiecraftmods.roadbuilder.preset.RoadPreset;
import com.cookiecraftmods.roadbuilder.preset.RoadSurfaceBand;
import com.cookiecraftmods.roadbuilder.preset.RoadSurfaceRole;
import com.cookiecraftmods.roadbuilder.preset.TrafficHandedness;
import com.cookiecraftmods.roadbuilder.test.MinecraftBootstrap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadGraphTest {
    private static final ResourceLocation PRESET_ID = new ResourceLocation("roadbuilder", "test");
    private static final RoadPreset PRESET = new RoadPreset(
            RoadPreset.CURRENT_SCHEMA_VERSION,
            4,
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
    private static final StraightGeometry EASTBOUND = new StraightGeometry(
            new RoadPoint(0.5, 64.0, 0.5),
            new RoadPoint(10.5, 64.0, 0.5)
    );
    private static final ResourceLocation NARROW_PRESET_ID =
            new ResourceLocation("roadbuilder", "narrow_ramp");
    private static final RoadPreset NARROW_PRESET = new RoadPreset(
            RoadPreset.CURRENT_SCHEMA_VERSION,
            1,
            1.0,
            new ResourceLocation("minecraft", "gray_concrete"),
            List.of(
                    new LaneComponent(
                            LaneType.VEHICLE,
                            1.0,
                            0.0,
                            LaneTravelDirection.FORWARD
                    )
            )
    );

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftBootstrap.bootstrap();
    }

    @Test
    void parallelPairPreparesWithoutMutatingAndCommitsAtOneRevision() {
        RoadGraph graph = new RoadGraph(Level.OVERWORLD);
        var parallel = com.cookiecraftmods.roadbuilder.geometry.ParallelRoadGeometry.create(EASTBOUND, 16);
        var mutation = graph.prepareParallelRoadNetwork(EASTBOUND, parallel, PRESET_ID, PRESET, TrafficHandedness.RHT);
        assertEquals(0, graph.snapshot().revision());
        assertTrue(graph.snapshot().segments().isEmpty());
        assertEquals(2, mutation.placedSegments().size());
        graph.applyNetworkMutation(mutation);
        assertEquals(1, graph.snapshot().revision());
        assertEquals(2, graph.snapshot().segments().size());
        assertTrue(graph.snapshot().segments().values().stream().anyMatch(segment -> segment.referenceGeometry().equals(parallel)));
    }

    @Test
    void parallelPairFlattensTwoCrossingsOfTheSameExistingRoad() {
        RoadGraph graph = new RoadGraph(Level.OVERWORLD);
        graph.applyNetworkMutation(graph.prepareRoadNetwork(new StraightGeometry(
                new RoadPoint(5.5, 64, -20), new RoadPoint(5.5, 64, 40)),
                PRESET_ID, PRESET, TrafficHandedness.RHT));
        var before = graph.snapshot();
        var parallel = com.cookiecraftmods.roadbuilder.geometry.ParallelRoadGeometry.create(EASTBOUND, 16);
        var mutation = graph.prepareParallelRoadNetwork(EASTBOUND, parallel, PRESET_ID, PRESET, TrafficHandedness.RHT);
        assertEquals(before, graph.snapshot());
        assertEquals(1, mutation.segmentSplits().size());
        assertEquals(3, mutation.segmentSplits().get(0).replacements().size());
        graph.applyNetworkMutation(mutation);
        assertEquals(before.revision() + 1, graph.snapshot().revision());
        assertEquals(7, graph.snapshot().segments().size());
    }

    @Test
    void invalidSecondRoadLeavesTheFirstUnbuilt() {
        RoadGraph graph = new RoadGraph(Level.OVERWORLD);
        var tooSmallRing = new com.cookiecraftmods.roadbuilder.geometry.RoundaboutGeometry(
                new RoadPoint(40, 64, 40), new RoadPoint(44, 64, 40));
        assertThrows(IllegalArgumentException.class, () -> graph.prepareParallelRoadNetwork(
                EASTBOUND, tooSmallRing, PRESET_ID, PRESET, TrafficHandedness.RHT));
        assertEquals(0, graph.snapshot().revision());
        assertTrue(graph.snapshot().segments().isEmpty());
    }

    @Test
    void manualUturnsUseTheIncomingLaneEndpointAtBothEnds() {
        RoadGraph graph = new RoadGraph(Level.OVERWORLD);
        graph.applyAddition(graph.prepareStraightRoad(
                EASTBOUND, PRESET_ID, PRESET, TrafficHandedness.RHT
        ));
        RoadSegment segment = graph.snapshot().segments().values().iterator().next();
        RoadLane forward = segment.lanes().stream().filter(lane ->
                lane.component().direction() == LaneTravelDirection.FORWARD
        ).findFirst().orElseThrow();
        RoadLane backward = segment.lanes().stream().filter(lane ->
                lane.component().direction() == LaneTravelDirection.BACKWARD
        ).findFirst().orElseThrow();
        assertDoesNotThrow(() -> graph.putManualConnector(
                forward.id(), backward.id(), graph.snapshot().revision(),
                LaneConnectorMovement.AUTOMATIC
        ));
        assertDoesNotThrow(() -> graph.putManualConnector(
                backward.id(), forward.id(), graph.snapshot().revision(),
                LaneConnectorMovement.AUTOMATIC
        ));
    }

    @Test
    void preparesAndPublishesAPathFirstStraightRoadAtomically() {
        AtomicLong ids = new AtomicLong(1);
        AtomicLong changes = new AtomicLong();
        RoadGraph graph = new RoadGraph(
                Level.OVERWORLD,
                RoadGraphLimits.defaults(),
                () -> new UUID(0, ids.getAndIncrement()),
                changes::incrementAndGet
        );

        PreparedRoadAddition addition = graph.prepareStraightRoad(
                EASTBOUND,
                PRESET_ID,
                PRESET,
                TrafficHandedness.RHT
        );

        assertEquals(0, graph.revision());
        assertTrue(graph.snapshot().roads().isEmpty());
        assertEquals(EASTBOUND.start(), addition.nodes().get(0).position());
        assertEquals(EASTBOUND.end(), addition.nodes().get(1).position());
        assertEquals(2, addition.segment().lanes().size());

        graph.applyAddition(addition);

        RoadGraphSnapshot snapshot = graph.snapshot();
        assertEquals(1, graph.revision());
        assertEquals(1, changes.get());
        assertEquals(1, snapshot.roads().size());
        assertEquals(2, snapshot.nodes().size());
        assertEquals(1, snapshot.segments().size());
        assertEquals(2, snapshot.lanes().size());
        assertEquals(addition.segment(), graph.segment(addition.segment().id()).orElseThrow());
        assertThrows(
                UnsupportedOperationException.class,
                () -> snapshot.segments().clear()
        );
    }

    @Test
    void crossingPlacementSplitsBothPathsAroundOneSharedNodeWithoutTurns() {
        AtomicLong ids = new AtomicLong(1_000);
        RoadGraph graph = new RoadGraph(
                Level.OVERWORLD,
                RoadGraphLimits.defaults(),
                () -> new UUID(0, ids.getAndIncrement()),
                () -> { }
        );
        PreparedRoadAddition existing = graph.prepareStraightRoad(
                EASTBOUND,
                PRESET_ID,
                PRESET,
                TrafficHandedness.RHT
        );
        graph.applyAddition(existing);

        PreparedRoadNetworkMutation mutation = graph.prepareStraightRoadNetwork(
                new StraightGeometry(
                        new RoadPoint(5.5, 64.0, -5.5),
                        new RoadPoint(5.5, 64.0, 5.5)
                ),
                PRESET_ID,
                PRESET,
                TrafficHandedness.RHT
        );

        assertEquals(1, mutation.segmentSplits().size());
        assertEquals(2, mutation.placedSegments().size());
        assertEquals(3, mutation.addedNodes().size());
        graph.applyNetworkMutation(mutation);

        RoadGraphSnapshot snapshot = graph.snapshot();
        RoadNode crossing = snapshot.nodes().values().stream()
                .filter(node -> node.position().equals(new RoadPoint(5.5, 64.0, 0.5)))
                .findFirst()
                .orElseThrow();
        long incident = snapshot.segments().values().stream()
                .filter(segment -> segment.startNodeId().equals(crossing.id())
                        || segment.endNodeId().equals(crossing.id()))
                .count();

        assertEquals(2, snapshot.roads().size());
        assertEquals(5, snapshot.nodes().size());
        assertEquals(4, snapshot.segments().size());
        assertEquals(4, incident);
        assertEquals(12, snapshot.connectors().size());
        long straightMovements = snapshot.connectors().values().stream().filter(connector -> {
            RoadLane from = snapshot.lanes().get(connector.fromLaneId());
            RoadLane to = snapshot.lanes().get(connector.toLaneId());
            RoadSegment fromSegment = snapshot.segments().get(from.segmentId());
            RoadSegment toSegment = snapshot.segments().get(to.segmentId());
            var fromVector = fromSegment.referenceGeometry().start()
                    .vectorTo(fromSegment.referenceGeometry().end());
            var toVector = toSegment.referenceGeometry().start()
                    .vectorTo(toSegment.referenceGeometry().end());
            return Math.abs(fromVector.x() * toVector.z()
                    - fromVector.z() * toVector.x()) < 1.0e-9;
        }).count();
        assertEquals(4, straightMovements);
        RoadLaneGraph laneGraph = RoadLaneGraph.from(snapshot);
        assertEquals(12, laneGraph.connectorPaths().size());
        assertTrue(laneGraph.connectorPaths().values().stream()
                .allMatch(path -> path.geometry().isPresent()));
        assertTrue(laneGraph.connectorPaths().values().stream().allMatch(path -> {
            RoadLane from = laneGraph.lanes().get(
                    path.connector().fromLaneId()
            );
            RoadLane to = laneGraph.lanes().get(
                    path.connector().toLaneId()
            );
            double fromDistance = path.fromLaneEndDistance()
                    .orElseThrow();
            double toDistance = path.toLaneStartDistance()
                    .orElseThrow();
            return from.geometry().sample(fromDistance).position().equals(
                    path.geometry().orElseThrow().start()
            ) && to.geometry().sample(toDistance).position().equals(
                    path.geometry().orElseThrow().end()
            );
        }));
        assertTrue(laneGraph.lanePaths().values().stream().allMatch(path ->
                path.startDistance() > 0.0
                        || path.endDistance()
                        < path.lane().geometry().length()));
        assertEquals(
                12,
                snapshot.lanes().keySet().stream()
                        .mapToInt(laneId -> laneGraph.outgoing(laneId).size())
                        .sum()
        );
        assertTrue(laneGraph.nearestLane(
                new RoadPoint(2.0, 64.0, 2.25),
                1.0
        ).isPresent());
        assertTrue(laneGraph.nearestConnector(
                crossing.position(),
                5.0
        ).isPresent());
    }

    @Test
    void endpointOnAnyInteriorRoadPositionBuildsAConnectedTJunction() {
        AtomicLong ids = new AtomicLong(1_100);
        RoadGraph graph = new RoadGraph(
                Level.OVERWORLD,
                RoadGraphLimits.defaults(),
                () -> new UUID(0, ids.getAndIncrement()),
                () -> { }
        );
        graph.applyAddition(graph.prepareStraightRoad(
                EASTBOUND,
                PRESET_ID,
                PRESET,
                TrafficHandedness.RHT
        ));
        RoadPoint attachment = new RoadPoint(4.25, 64.0, 0.5);

        PreparedRoadNetworkMutation branch = graph.prepareStraightRoadNetwork(
                new StraightGeometry(
                        attachment,
                        new RoadPoint(4.25, 64.0, 8.5)
                ),
                PRESET_ID,
                PRESET,
                TrafficHandedness.RHT
        );

        assertEquals(1, branch.segmentSplits().size());
        assertEquals(1, branch.placedSegments().size());
        graph.applyNetworkMutation(branch);

        RoadGraphSnapshot snapshot = graph.snapshot();
        RoadNode junction = snapshot.nodes().values().stream()
                .filter(node -> node.position().equals(attachment))
                .findFirst()
                .orElseThrow();
        long incident = snapshot.segments().values().stream()
                .filter(segment -> segment.startNodeId().equals(junction.id())
                        || segment.endNodeId().equals(junction.id()))
                .count();

        assertEquals(3, incident);
        assertTrue(snapshot.connectors().values().stream().anyMatch(connector -> {
            RoadLane from = snapshot.lanes().get(connector.fromLaneId());
            RoadLane to = snapshot.lanes().get(connector.toLaneId());
            return from != null && to != null
                    && !from.segmentId().equals(to.segmentId());
        }));
    }

    @Test
    void endpointOnAnInteriorRoadEdgeKeepsItsPortAndBuildsAConnectedTJunction() {
        AtomicLong ids = new AtomicLong(1_150);
        RoadGraph graph = new RoadGraph(
                Level.OVERWORLD,
                RoadGraphLimits.defaults(),
                () -> new UUID(0, ids.getAndIncrement()),
                () -> { }
        );
        graph.applyAddition(graph.prepareStraightRoad(
                EASTBOUND,
                PRESET_ID,
                PRESET,
                TrafficHandedness.RHT
        ));
        RoadPoint edgePort = new RoadPoint(4.25, 64.0, 4.0);
        RoadPoint centerlineJunction = new RoadPoint(4.25, 64.0, 0.5);

        PreparedRoadNetworkMutation branch = graph.prepareStraightRoadNetwork(
                new StraightGeometry(
                        edgePort,
                        new RoadPoint(4.25, 64.0, 8.5)
                ),
                PRESET_ID,
                PRESET,
                TrafficHandedness.RHT
        );

        assertEquals(1, branch.segmentSplits().size());
        assertEquals(1, branch.placedSegments().size());
        RoadSegment placedBranch = branch.placedSegments().get(0);
        assertEquals(edgePort, placedBranch.referenceGeometry().start());

        graph.applyNetworkMutation(branch);
        RoadGraphSnapshot snapshot = graph.snapshot();
        RoadNode junction = snapshot.nodes().values().stream()
                .filter(node -> node.position().equals(centerlineJunction))
                .findFirst()
                .orElseThrow();

        assertEquals(junction.id(), placedBranch.startNodeId());
        long incident = snapshot.segments().values().stream()
                .filter(segment -> segment.startNodeId().equals(junction.id())
                        || segment.endNodeId().equals(junction.id()))
                .count();
        assertEquals(3, incident);
        assertTrue(snapshot.connectors().values().stream().anyMatch(connector -> {
            RoadLane from = snapshot.lanes().get(connector.fromLaneId());
            RoadLane to = snapshot.lanes().get(connector.toLaneId());
            return from != null && to != null
                    && !from.segmentId().equals(to.segmentId());
        }));
    }

    @Test
    void roadSpanningOneRoadSurfaceSplitsEachGeometryOnlyOnce() {
        AtomicLong ids = new AtomicLong(1_175);
        RoadGraph graph = new RoadGraph(
                Level.OVERWORLD,
                RoadGraphLimits.defaults(),
                () -> new UUID(0, ids.getAndIncrement()),
                () -> { }
        );
        graph.applyAddition(graph.prepareStraightRoad(
                EASTBOUND,
                PRESET_ID,
                PRESET,
                TrafficHandedness.RHT
        ));

        PreparedRoadNetworkMutation crossing = graph.prepareStraightRoadNetwork(
                new StraightGeometry(
                        new RoadPoint(5.5, 64.0, -3.0),
                        new RoadPoint(5.5, 64.0, 4.0)
                ),
                PRESET_ID,
                PRESET,
                TrafficHandedness.RHT
        );

        assertEquals(1, crossing.segmentSplits().size());
        assertEquals(2, crossing.placedSegments().size());
        assertEquals(2, crossing.segmentSplits().get(0).replacements().size());
    }

    @Test
    void mapsTurnsFromOneLaneToTheCorrectSideOfAWiderRoad() {
        RoadPreset feeder = new RoadPreset(
                RoadPreset.CURRENT_SCHEMA_VERSION,
                1,
                15.0,
                new ResourceLocation("minecraft", "gray_concrete"),
                List.of(
                        new LaneComponent(
                                LaneType.BUS,
                                3.0,
                                -3.0,
                                LaneTravelDirection.BACKWARD
                        ),
                        new LaneComponent(
                                LaneType.VEHICLE,
                                3.0,
                                3.0,
                                LaneTravelDirection.FORWARD
                        )
                )
        );
        RoadPreset arterial = new RoadPreset(
                RoadPreset.CURRENT_SCHEMA_VERSION,
                1,
                23.0,
                new ResourceLocation("minecraft", "gray_concrete"),
                List.of(
                        new LaneComponent(
                                LaneType.VEHICLE,
                                3.0,
                                -7.0,
                                LaneTravelDirection.BACKWARD
                        ),
                        new LaneComponent(
                                LaneType.VEHICLE,
                                3.0,
                                -3.0,
                                LaneTravelDirection.BACKWARD
                        ),
                        new LaneComponent(
                                LaneType.VEHICLE,
                                3.0,
                                3.0,
                                LaneTravelDirection.FORWARD
                        ),
                        new LaneComponent(
                                LaneType.VEHICLE,
                                3.0,
                                7.0,
                                LaneTravelDirection.FORWARD
                        )
                )
        );
        RoadGraph graph = new RoadGraph(Level.OVERWORLD);
        graph.applyAddition(graph.prepareStraightRoad(
                new StraightGeometry(
                        new RoadPoint(-10.5, 64.0, 0.5),
                        new RoadPoint(10.5, 64.0, 0.5)
                ),
                PRESET_ID,
                feeder,
                TrafficHandedness.RHT
        ));
        graph.applyNetworkMutation(graph.prepareStraightRoadNetwork(
                new StraightGeometry(
                        new RoadPoint(0.5, 64.0, -10.5),
                        new RoadPoint(0.5, 64.0, 10.5)
                ),
                PRESET_ID,
                arterial,
                TrafficHandedness.RHT
        ));

        RoadGraphSnapshot snapshot = graph.snapshot();
        RoadNode junction = snapshot.nodes().values().stream()
                .filter(node -> node.position().equals(
                        new RoadPoint(0.5, 64.0, 0.5)
                ))
                .findFirst()
                .orElseThrow();
        List<RoadSegment> incident = snapshot.segments().values().stream()
                .filter(segment -> segment.startNodeId().equals(junction.id())
                        || segment.endNodeId().equals(junction.id()))
                .toList();
        RoadSegment west = incident.stream()
                .filter(segment -> otherNode(snapshot, segment, junction)
                        .position().x() < junction.position().x())
                .findFirst()
                .orElseThrow();
        RoadSegment north = incident.stream()
                .filter(segment -> otherNode(snapshot, segment, junction)
                        .position().z() < junction.position().z())
                .findFirst()
                .orElseThrow();
        RoadSegment south = incident.stream()
                .filter(segment -> otherNode(snapshot, segment, junction)
                        .position().z() > junction.position().z())
                .findFirst()
                .orElseThrow();

        LaneConnector rightTurn = connectorBetween(
                snapshot,
                west,
                south,
                LaneMovementType.RIGHT
        );
        LaneConnector leftTurn = connectorBetween(
                snapshot,
                west,
                north,
                LaneMovementType.LEFT
        );

        assertEquals(7.0, snapshot.lanes().get(rightTurn.toLaneId())
                .component().lateralOffset());
        assertEquals(-3.0, snapshot.lanes().get(leftTurn.toLaneId())
                .component().lateralOffset());
    }

    @Test
    void connectsLanePathsThroughATwoSegmentBend() {
        RoadGraph graph = new RoadGraph(Level.OVERWORLD);
        graph.applyAddition(graph.prepareStraightRoad(
                new StraightGeometry(
                        new RoadPoint(-20.5, 64.0, 0.5),
                        new RoadPoint(0.5, 64.0, 0.5)
                ),
                PRESET_ID,
                PRESET,
                TrafficHandedness.RHT
        ));
        graph.applyNetworkMutation(graph.prepareStraightRoadNetwork(
                new StraightGeometry(
                        new RoadPoint(0.5, 64.0, 0.5),
                        new RoadPoint(0.5, 64.0, 20.5)
                ),
                PRESET_ID,
                PRESET,
                TrafficHandedness.RHT
        ));

        RoadLaneGraph laneGraph = RoadLaneGraph.from(graph.snapshot());
        assertEquals(2, laneGraph.connectorPaths().size());
        assertTrue(laneGraph.connectorPaths().values().stream()
                .allMatch(path -> path.geometry().isPresent()));
    }

    @Test
    void elevatedCrossingKeepsTheExistingRoadAndSlopesTheNewRoadToItsNode() {
        AtomicLong ids = new AtomicLong(1_500);
        RoadGraph graph = new RoadGraph(
                Level.OVERWORLD,
                RoadGraphLimits.defaults(),
                () -> new UUID(0, ids.getAndIncrement()),
                () -> { }
        );
        graph.applyAddition(graph.prepareStraightRoad(
                EASTBOUND,
                PRESET_ID,
                PRESET,
                TrafficHandedness.RHT
        ));

        PreparedRoadNetworkMutation mutation = graph.prepareStraightRoadNetwork(
                new StraightGeometry(
                        new RoadPoint(5.5, 70.0, -12.5),
                        new RoadPoint(5.5, 70.0, 12.5)
                ),
                PRESET_ID,
                PRESET,
                TrafficHandedness.RHT
        );

        RoadNode crossing = mutation.addedNodes().stream()
                .filter(node -> node.position().equals(
                        new RoadPoint(5.5, 64.0, 0.5)
                ))
                .findFirst()
                .orElseThrow();
        assertTrue(mutation.segmentSplits().get(0).replacements().stream()
                .allMatch(segment -> segment.referenceGeometry().start().y() == 64.0
                        || segment.referenceGeometry().end().y() == 64.0));
        assertTrue(mutation.placedSegments().stream()
                .allMatch(segment -> segment.startNodeId().equals(crossing.id())
                        || segment.endNodeId().equals(crossing.id())));
        assertTrue(mutation.placedSegments().stream()
                .allMatch(segment -> segment.referenceGeometry().start().y() == 64.0
                        || segment.referenceGeometry().end().y() == 64.0));
    }

    @Test
    void joinsAnExactCollinearEndpointAndGeneratesStraightThroughConnectors() {
        AtomicLong ids = new AtomicLong(100);
        RoadGraph graph = new RoadGraph(
                Level.OVERWORLD,
                RoadGraphLimits.defaults(),
                () -> new UUID(0, ids.getAndIncrement()),
                () -> { }
        );
        PreparedRoadAddition first = graph.prepareStraightRoad(
                EASTBOUND,
                PRESET_ID,
                PRESET,
                TrafficHandedness.RHT
        );
        graph.applyAddition(first);

        PreparedRoadAddition second = graph.prepareStraightRoad(
                new StraightGeometry(
                        EASTBOUND.end(),
                        new RoadPoint(20.5, 64.0, 0.5)
                ),
                PRESET_ID,
                PRESET,
                TrafficHandedness.RHT
        );

        assertFalse(second.createsRoad());
        assertEquals(first.road(), second.road());
        assertEquals(1, second.nodes().size());
        assertEquals(first.segment().endNodeId(), second.segment().startNodeId());
        assertEquals(2, second.connectors().size());
        assertTrue(second.connectors().stream().anyMatch(connector ->
                connector.fromLaneId().equals(first.segment().lanes().get(0).id())
                        && connector.toLaneId().equals(second.segment().lanes().get(0).id())));
        assertTrue(second.connectors().stream().anyMatch(connector ->
                connector.fromLaneId().equals(second.segment().lanes().get(1).id())
                        && connector.toLaneId().equals(first.segment().lanes().get(1).id())));

        graph.applyAddition(second);

        RoadGraphSnapshot snapshot = graph.snapshot();
        assertEquals(2, graph.revision());
        assertEquals(1, snapshot.roads().size());
        assertEquals(3, snapshot.nodes().size());
        assertEquals(2, snapshot.segments().size());
        assertEquals(4, snapshot.lanes().size());
        assertEquals(2, snapshot.connectors().size());
    }

    @Test
    void joinsARoadEdgeAsAPortOnTheExistingNodeWithoutRecenteringIt() {
        AtomicLong ids = new AtomicLong(175);
        RoadGraph graph = new RoadGraph(
                Level.OVERWORLD,
                RoadGraphLimits.defaults(),
                () -> new UUID(0, ids.getAndIncrement()),
                () -> { }
        );
        PreparedRoadAddition wide = graph.prepareStraightRoad(
                EASTBOUND,
                PRESET_ID,
                PRESET,
                TrafficHandedness.RHT
        );
        graph.applyAddition(wide);
        RoadPreset narrowPreset = new RoadPreset(
                RoadPreset.CURRENT_SCHEMA_VERSION,
                1,
                3.0,
                PRESET.surfaceBlock(),
                List.of(
                        new LaneComponent(
                                LaneType.VEHICLE,
                                1.0,
                                -0.75,
                                LaneTravelDirection.FORWARD
                        ),
                        new LaneComponent(
                                LaneType.VEHICLE,
                                1.0,
                                0.75,
                                LaneTravelDirection.BACKWARD
                        )
                )
        );
        RoadPoint edgePort = new RoadPoint(10.5, 64.0, 4.0);

        PreparedRoadNetworkMutation branch = graph.prepareStraightRoadNetwork(
                new StraightGeometry(
                        edgePort,
                        new RoadPoint(20.5, 64.0, 4.0)
                ),
                new ResourceLocation("roadbuilder", "narrow_test"),
                narrowPreset,
                TrafficHandedness.RHT
        );
        RoadSegment placed = branch.placedSegments().get(0);

        assertEquals(1, branch.addedNodes().size());
        assertEquals(wide.segment().endNodeId(), placed.startNodeId());
        assertEquals(edgePort, placed.referenceGeometry().start());
        assertFalse(branch.addedConnectors().isEmpty());

        graph.applyNetworkMutation(branch);
        RoadGraphSnapshot snapshot = graph.snapshot();
        assertEquals(3, snapshot.nodes().size());
        RoadGraph restored = RoadGraph.restore(
                Level.OVERWORLD,
                snapshot.revision(),
                snapshot.roads().values(),
                snapshot.nodes().values(),
                snapshot.segments().values(),
                snapshot.connectors().values(),
                snapshot.junctionDefinitions().values(),
                RoadGraphLimits.defaults(),
                () -> { }
        );
        assertEquals(
                wide.segment().endNodeId(),
                restored.segment(placed.id()).orElseThrow().startNodeId()
        );
        assertEquals(
                edgePort,
                restored.segment(placed.id()).orElseThrow()
                        .referenceGeometry().start()
        );
    }

    @Test
    void doesNotJoinAPointThatIsOnlyFartherAlongAnEndpointGuide() {
        RoadGraph graph = new RoadGraph(Level.OVERWORLD);
        PreparedRoadAddition existing = graph.prepareStraightRoad(
                EASTBOUND,
                PRESET_ID,
                PRESET,
                TrafficHandedness.RHT
        );
        graph.applyAddition(existing);

        PreparedRoadAddition separated = graph.prepareStraightRoad(
                new StraightGeometry(
                        new RoadPoint(12.5, 64.0, 4.0),
                        new RoadPoint(20.5, 64.0, 4.0)
                ),
                PRESET_ID,
                PRESET,
                TrafficHandedness.RHT
        );

        assertEquals(2, separated.nodes().size());
        assertNotEquals(
                existing.segment().endNodeId(),
                separated.segment().startNodeId()
        );
    }

    @Test
    void connectsAPerpendicularTwoSegmentBendWithTurnMovements() {
        AtomicLong ids = new AtomicLong(200);
        RoadGraph graph = new RoadGraph(
                Level.OVERWORLD,
                RoadGraphLimits.defaults(),
                () -> new UUID(0, ids.getAndIncrement()),
                () -> { }
        );
        PreparedRoadAddition first = graph.prepareStraightRoad(
                EASTBOUND,
                PRESET_ID,
                PRESET,
                TrafficHandedness.RHT
        );
        graph.applyAddition(first);

        PreparedRoadAddition branch = graph.prepareStraightRoad(
                new StraightGeometry(
                        EASTBOUND.end(),
                        new RoadPoint(10.5, 64.0, 10.5)
                ),
                PRESET_ID,
                PRESET,
                TrafficHandedness.RHT
        );

        assertTrue(branch.createsRoad());
        assertEquals(1, branch.nodes().size());
        assertEquals(first.segment().endNodeId(), branch.segment().startNodeId());
        assertEquals(2, branch.connectors().size());
        assertTrue(branch.connectors().stream().allMatch(connector ->
                connector.movement().type() != LaneMovementType.STRAIGHT));

        graph.applyAddition(branch);
        assertEquals(2, graph.snapshot().roads().size());
        assertEquals(3, graph.snapshot().nodes().size());
    }

    @Test
    void sharesAnEndpointButDoesNotConnectOppositeTrafficHandedness() {
        AtomicLong ids = new AtomicLong(250);
        RoadGraph graph = new RoadGraph(
                Level.OVERWORLD,
                RoadGraphLimits.defaults(),
                () -> new UUID(0, ids.getAndIncrement()),
                () -> { }
        );
        PreparedRoadAddition first = graph.prepareStraightRoad(
                EASTBOUND,
                PRESET_ID,
                PRESET,
                TrafficHandedness.RHT
        );
        graph.applyAddition(first);

        PreparedRoadAddition continuation = graph.prepareStraightRoad(
                new StraightGeometry(
                        EASTBOUND.end(),
                        new RoadPoint(20.5, 64.0, 0.5)
                ),
                PRESET_ID,
                PRESET,
                TrafficHandedness.LHT
        );

        assertTrue(continuation.createsRoad());
        assertEquals(first.segment().endNodeId(), continuation.segment().startNodeId());
        assertTrue(continuation.connectors().isEmpty());
    }

    @Test
    void joinsAContinuousGradeButRejectsAnAbruptVerticalTangent() {
        AtomicLong ids = new AtomicLong(275);
        RoadGraph graph = new RoadGraph(
                Level.OVERWORLD,
                RoadGraphLimits.defaults(),
                () -> new UUID(0, ids.getAndIncrement()),
                () -> { }
        );
        PreparedRoadAddition uphill = graph.prepareStraightRoad(
                new StraightGeometry(
                        new RoadPoint(0.5, 64.0, 0.5),
                        new RoadPoint(10.5, 66.0, 0.5)
                ),
                PRESET_ID,
                PRESET,
                TrafficHandedness.RHT
        );
        graph.applyAddition(uphill);

        PreparedRoadAddition smoothContinuation = graph.prepareStraightRoad(
                new StraightGeometry(
                        new RoadPoint(10.5, 66.0, 0.5),
                        new RoadPoint(20.5, 68.0, 0.5)
                ),
                PRESET_ID,
                PRESET,
                TrafficHandedness.RHT
        );
        assertFalse(smoothContinuation.createsRoad());
        assertEquals(2, smoothContinuation.connectors().size());
        graph.applyAddition(smoothContinuation);

        PreparedRoadAddition abruptLevelContinuation = graph.prepareStraightRoad(
                new StraightGeometry(
                        new RoadPoint(20.5, 68.0, 0.5),
                        new RoadPoint(30.5, 68.0, 0.5)
                ),
                PRESET_ID,
                PRESET,
                TrafficHandedness.RHT
        );
        assertTrue(abruptLevelContinuation.createsRoad());
        assertTrue(abruptLevelContinuation.connectors().isEmpty());
        assertEquals(
                smoothContinuation.segment().endNodeId(),
                abruptLevelContinuation.segment().startNodeId()
        );
    }

    @Test
    void removingAJoinedSegmentRemovesItsConnectorsButKeepsTheSharedNode() {
        AtomicLong ids = new AtomicLong(300);
        RoadGraph graph = new RoadGraph(
                Level.OVERWORLD,
                RoadGraphLimits.defaults(),
                () -> new UUID(0, ids.getAndIncrement()),
                () -> { }
        );
        PreparedRoadAddition first = graph.prepareStraightRoad(
                EASTBOUND,
                PRESET_ID,
                PRESET,
                TrafficHandedness.RHT
        );
        graph.applyAddition(first);
        PreparedRoadAddition second = graph.prepareStraightRoad(
                new StraightGeometry(
                        EASTBOUND.end(),
                        new RoadPoint(20.5, 64.0, 0.5)
                ),
                PRESET_ID,
                PRESET,
                TrafficHandedness.RHT
        );
        graph.applyAddition(second);

        PreparedSegmentRemoval removal = graph.prepareSegmentRemoval(second.segment().id());
        assertEquals(2, removal.connectorIds().size());
        graph.applyRemoval(removal);

        assertTrue(graph.node(first.segment().endNodeId()).isPresent());
        assertTrue(graph.segment(first.segment().id()).isPresent());
        assertTrue(graph.snapshot().connectors().isEmpty());
        assertEquals(1, graph.snapshot().roads().size());
    }

    @Test
    void curveHandleEditPreservesSegmentNodeRoadAndLaneIds() {
        AtomicLong ids = new AtomicLong(350);
        RoadGraph graph = new RoadGraph(
                Level.OVERWORLD,
                RoadGraphLimits.defaults(),
                () -> new UUID(0, ids.getAndIncrement()),
                () -> { }
        );
        CubicBezierGeometry source = new CubicBezierGeometry(
                new RoadPoint(0.5, 64.0, 0.5),
                new RoadPoint(3.5, 64.0, 4.5),
                new RoadPoint(7.5, 64.0, 4.5),
                new RoadPoint(10.5, 64.0, 0.5)
        );
        PreparedRoadNetworkMutation addition = graph.prepareRoadNetwork(
                source,
                PRESET_ID,
                PRESET,
                TrafficHandedness.RHT
        );
        graph.applyNetworkMutation(addition);
        RoadSegment original = addition.placedSegments().get(0);
        List<RoadLaneId> originalLaneIds = original.lanes().stream()
                .map(RoadLane::id)
                .toList();
        CubicBezierGeometry replacement = new CubicBezierGeometry(
                source.start(),
                new RoadPoint(3.5, 65.0, 6.5),
                source.endControl(),
                source.end()
        );

        PreparedSegmentEdit edit = graph.prepareSegmentEdit(
                original.id(),
                replacement
        );
        graph.applySegmentEdit(edit);

        RoadSegment edited = graph.segment(original.id()).orElseThrow();
        assertEquals(original.id(), edited.id());
        assertEquals(original.roadId(), edited.roadId());
        assertEquals(original.startNodeId(), edited.startNodeId());
        assertEquals(original.endNodeId(), edited.endNodeId());
        assertEquals(originalLaneIds, edited.lanes().stream()
                .map(RoadLane::id)
                .toList());
        assertEquals(replacement, edited.referenceGeometry());
    }

    @Test
    void presetReplacementPreservesGeometryAndRefreshesDerivedLanes() {
        AtomicLong ids = new AtomicLong(375);
        RoadGraph graph = new RoadGraph(
                Level.OVERWORLD,
                RoadGraphLimits.defaults(),
                () -> new UUID(0, ids.getAndIncrement()),
                () -> { }
        );
        PreparedRoadAddition addition = graph.prepareStraightRoad(
                EASTBOUND, PRESET_ID, PRESET, TrafficHandedness.RHT
        );
        graph.applyAddition(addition);
        RoadPreset wider = new RoadPreset(
                RoadPreset.CURRENT_SCHEMA_VERSION,
                7,
                10.0,
                new ResourceLocation("minecraft", "black_concrete"),
                List.of(
                        new LaneComponent(LaneType.VEHICLE, 3.0, -3.0,
                                LaneTravelDirection.FORWARD),
                        new LaneComponent(LaneType.VEHICLE, 3.0, 0.0,
                                LaneTravelDirection.FORWARD),
                        new LaneComponent(LaneType.VEHICLE, 3.0, 3.0,
                                LaneTravelDirection.BACKWARD)
                )
        );
        ResourceLocation widerId = new ResourceLocation(
                "roadbuilder", "wider"
        );

        PreparedSegmentReplacement replacement =
                graph.prepareSegmentReplacement(
                        addition.segment().id(), widerId, wider
                );
        graph.applySegmentReplacement(replacement);

        RoadSegment actual = graph.segment(addition.segment().id()).orElseThrow();
        assertEquals(addition.segment().referenceGeometry(), actual.referenceGeometry());
        assertEquals(widerId, actual.presetId());
        assertEquals(7, actual.presetRevision());
        assertEquals(3, actual.lanes().size());
        assertEquals(addition.segment().lanes().get(0).id(),
                actual.lanes().get(0).id());
        assertEquals(addition.segment().lanes().get(1).id(),
                actual.lanes().get(1).id());
    }

    @Test
    void directionInversionReversesGeometryAndMigratesJunctionApproach() {
        AtomicLong ids = new AtomicLong(390);
        RoadGraph graph = new RoadGraph(
                Level.OVERWORLD,
                RoadGraphLimits.defaults(),
                () -> new UUID(0, ids.getAndIncrement()),
                () -> { }
        );
        CubicBezierGeometry geometry = new CubicBezierGeometry(
                new RoadPoint(0.5, 64.0, 0.5),
                new RoadPoint(3.5, 65.0, 5.5),
                new RoadPoint(8.5, 63.0, 5.5),
                new RoadPoint(12.5, 64.0, 0.5)
        );
        PreparedRoadNetworkMutation addition = graph.prepareRoadNetwork(
                geometry, PRESET_ID, PRESET, TrafficHandedness.RHT
        );
        graph.applyNetworkMutation(addition);
        RoadSegment original = addition.placedSegments().get(0);
        List<RoadLaneId> originalLaneIds = original.lanes().stream()
                .map(RoadLane::id)
                .toList();
        ApproachKey originalKey = ApproachKey.at(
                original, original.startNodeId()
        );
        graph.upsertJunctionDefinition(
                new JunctionDefinition(
                        original.startNodeId(),
                        JunctionStrategy.AUTO,
                        JunctionPriorityPolicy.RIGHT_OF_WAY,
                        List.of(originalKey),
                        JunctionCrossingPolicy.MANUAL,
                        Map.of(
                                originalKey,
                                new JunctionApproachOverride(
                                        IntersectionControl.FREE,
                                        true
                                )
                        ),
                        1
                ),
                graph.revision()
        );

        PreparedSegmentDirectionInversion inversion =
                graph.prepareSegmentDirectionInversion(original.id());
        graph.applySegmentDirectionInversion(inversion);

        RoadSegment inverted = graph.segment(original.id()).orElseThrow();
        assertEquals(original.id(), inverted.id());
        assertEquals(original.roadId(), inverted.roadId());
        assertEquals(original.presetId(), inverted.presetId());
        assertEquals(original.presetRevision(), inverted.presetRevision());
        assertEquals(original.startNodeId(), inverted.endNodeId());
        assertEquals(original.endNodeId(), inverted.startNodeId());
        assertEquals(originalLaneIds, inverted.lanes().stream()
                .map(RoadLane::id)
                .toList());
        for (double ratio : List.of(0.0, 0.2, 0.5, 0.8, 1.0)) {
            var expected = original.referenceGeometry().sample(
                    original.referenceGeometry().length() * (1.0 - ratio)
            );
            var actual = inverted.referenceGeometry().sample(
                    inverted.referenceGeometry().length() * ratio
            );
            assertEquals(expected.position().x(), actual.position().x(), 1.0e-7);
            assertEquals(expected.position().y(), actual.position().y(), 1.0e-7);
            assertEquals(expected.position().z(), actual.position().z(), 1.0e-7);
            assertEquals(-expected.tangent().x(), actual.tangent().x(), 1.0e-7);
            assertEquals(-expected.tangent().y(), actual.tangent().y(), 1.0e-7);
            assertEquals(-expected.tangent().z(), actual.tangent().z(), 1.0e-7);
        }
        ApproachKey migratedKey = ApproachKey.at(
                inverted, original.startNodeId()
        );
        JunctionDefinition migrated = graph.snapshot().junctionDefinitions()
                .get(original.startNodeId());
        assertEquals(List.of(migratedKey), migrated.primaryApproachKeys());
        assertFalse(migrated.approachOverrides().containsKey(originalKey));
        assertEquals(
                new JunctionApproachOverride(IntersectionControl.FREE, true),
                migrated.approachOverrides().get(migratedKey)
        );
    }

    @Test
    void manualConnectorEditPreservesTopologyAndMarksMetadataManual() {
        AtomicLong ids = new AtomicLong(400);
        RoadGraph graph = new RoadGraph(
                Level.OVERWORLD,
                RoadGraphLimits.defaults(),
                () -> new UUID(0, ids.getAndIncrement()),
                () -> { }
        );
        PreparedRoadAddition first = graph.prepareStraightRoad(
                EASTBOUND,
                PRESET_ID,
                PRESET,
                TrafficHandedness.RHT
        );
        graph.applyAddition(first);
        PreparedRoadAddition second = graph.prepareStraightRoad(
                new StraightGeometry(
                        EASTBOUND.end(),
                        new RoadPoint(20.5, 64.0, 0.5)
                ),
                PRESET_ID,
                PRESET,
                TrafficHandedness.RHT
        );
        graph.applyAddition(second);
        LaneConnector original = graph.snapshot().connectors().values()
                .stream()
                .findFirst()
                .orElseThrow();
        long revision = graph.revision();

        LaneConnector edited = graph.updateConnectorMovement(
                original.id(),
                revision,
                new LaneConnectorMovement(
                        LaneMovementType.STRAIGHT,
                        false,
                        false,
                        7
                )
        );

        assertEquals(original.id(), edited.id());
        assertEquals(original.fromLaneId(), edited.fromLaneId());
        assertEquals(original.toLaneId(), edited.toLaneId());
        assertEquals(LaneMovementType.STRAIGHT, edited.movement().type());
        assertFalse(edited.movement().allowed());
        assertTrue(edited.movement().manual());
        assertEquals(7, edited.movement().priority());
        assertThrows(
                IllegalStateException.class,
                () -> graph.updateConnectorMovement(
                        original.id(),
                        revision,
                        LaneConnectorMovement.AUTOMATIC
                )
        );

        graph.removeManualConnector(edited.id(), graph.revision());
        assertFalse(graph.snapshot().connectors().containsKey(edited.id()));

        LaneConnector replacement = graph.putManualConnector(
                original.fromLaneId(),
                original.toLaneId(),
                graph.revision(),
                new LaneConnectorMovement(
                        LaneMovementType.STRAIGHT,
                        true,
                        false,
                        3
                )
        );
        assertNotEquals(original.id(), replacement.id());
        assertEquals(original.fromLaneId(), replacement.fromLaneId());
        assertEquals(original.toLaneId(), replacement.toLaneId());
        assertTrue(replacement.movement().manual());
        assertTrue(replacement.movement().allowed());
        assertEquals(3, replacement.movement().priority());
    }

    @Test
    void manualUturnAroundAMedianEndIsAllowed() {
        AtomicLong ids = new AtomicLong(500);
        RoadGraph graph = new RoadGraph(
                Level.OVERWORLD,
                RoadGraphLimits.defaults(),
                () -> new UUID(0, ids.getAndIncrement()),
                () -> { }
        );
        RoadPreset divided = new RoadPreset(
                RoadPreset.CURRENT_SCHEMA_VERSION,
                1,
                11.0,
                new ResourceLocation("minecraft", "gray_concrete"),
                List.of(
                        new LaneComponent(
                                LaneType.VEHICLE,
                                3.0,
                                -3.5,
                                LaneTravelDirection.FORWARD
                        ),
                        new LaneComponent(
                                LaneType.VEHICLE,
                                3.0,
                                3.5,
                                LaneTravelDirection.BACKWARD
                        )
                ),
                List.of(
                        new RoadSurfaceBand(
                                RoadSurfaceRole.PAVEMENT,
                                4,
                                new ResourceLocation(
                                        "minecraft",
                                        "gray_concrete"
                                )
                        ),
                        new RoadSurfaceBand(
                                RoadSurfaceRole.GRASS_MEDIAN,
                                3,
                                new ResourceLocation(
                                        "minecraft",
                                        "green_concrete"
                                )
                        ),
                        new RoadSurfaceBand(
                                RoadSurfaceRole.PAVEMENT,
                                4,
                                new ResourceLocation(
                                        "minecraft",
                                        "gray_concrete"
                                )
                        )
                )
        );
        PreparedRoadAddition addition = graph.prepareStraightRoad(
                EASTBOUND,
                PRESET_ID,
                divided,
                TrafficHandedness.RHT
        );
        graph.applyAddition(addition);
        RoadSegment segment = graph.snapshot().segments().values()
                .iterator()
                .next();
        RoadLane forward = segment.lanes().stream()
                .filter(lane -> lane.component().direction()
                        == LaneTravelDirection.FORWARD)
                .findFirst()
                .orElseThrow();
        RoadLane backward = segment.lanes().stream()
                .filter(lane -> lane.component().direction()
                        == LaneTravelDirection.BACKWARD)
                .findFirst()
                .orElseThrow();

        assertDoesNotThrow(
                () -> graph.putManualConnector(
                        forward.id(),
                        backward.id(),
                        graph.revision(),
                        new LaneConnectorMovement(
                                LaneMovementType.MANUAL,
                                true,
                                true,
                                0
                        )
                )
        );
    }

    @Test
    void junctionRebuildPreservesSurvivingManualConnectorId() {
        AtomicLong ids = new AtomicLong(450);
        RoadGraph graph = new RoadGraph(
                Level.OVERWORLD,
                RoadGraphLimits.defaults(),
                () -> new UUID(0, ids.getAndIncrement()),
                () -> { }
        );
        PreparedRoadAddition west = graph.prepareStraightRoad(
                EASTBOUND,
                PRESET_ID,
                PRESET,
                TrafficHandedness.RHT
        );
        graph.applyAddition(west);
        PreparedRoadAddition east = graph.prepareStraightRoad(
                new StraightGeometry(
                        EASTBOUND.end(),
                        new RoadPoint(20.5, 64.0, 0.5)
                ),
                PRESET_ID,
                PRESET,
                TrafficHandedness.RHT
        );
        graph.applyAddition(east);
        LaneConnector connector = graph.snapshot().connectors().values()
                .stream()
                .findFirst()
                .orElseThrow();
        graph.updateConnectorMovement(
                connector.id(),
                graph.revision(),
                new LaneConnectorMovement(
                        LaneMovementType.STRAIGHT,
                        false,
                        true,
                        4
                )
        );

        PreparedRoadAddition branch = graph.prepareStraightRoad(
                new StraightGeometry(
                        EASTBOUND.end(),
                        new RoadPoint(10.5, 64.0, 10.5)
                ),
                PRESET_ID,
                PRESET,
                TrafficHandedness.RHT
        );
        graph.applyAddition(branch);

        LaneConnector preserved = graph.snapshot().connectors()
                .get(connector.id());
        assertEquals(connector.fromLaneId(), preserved.fromLaneId());
        assertEquals(connector.toLaneId(), preserved.toLaneId());
        assertTrue(preserved.movement().manual());
        assertFalse(preserved.movement().allowed());
        assertEquals(4, preserved.movement().priority());
    }

    @Test
    void derivesDirectedLanePathsForRhtAndLhtWithoutReversingTravelMeaning() {
        RoadSegment rht = preparedSegment(TrafficHandedness.RHT, 10);
        RoadSegment lht = preparedSegment(TrafficHandedness.LHT, 20);

        RoadLane rhtForward = rht.lanes().get(0);
        RoadLane rhtBackward = rht.lanes().get(1);
        assertEquals(-1.25, rhtForward.geometry().start().z());
        assertEquals(0.5, rhtForward.geometry().start().x());
        assertEquals(2.25, rhtBackward.geometry().start().z());
        assertEquals(10.5, rhtBackward.geometry().start().x());
        assertEquals(0.5, rhtBackward.geometry().end().x());

        RoadLane lhtForward = lht.lanes().get(0);
        RoadLane lhtBackward = lht.lanes().get(1);
        assertEquals(2.25, lhtForward.geometry().start().z());
        assertEquals(0.5, lhtForward.geometry().start().x());
        assertEquals(-1.25, lhtBackward.geometry().start().z());
        assertEquals(10.5, lhtBackward.geometry().start().x());
        assertEquals(LaneTravelDirection.FORWARD, lhtForward.component().direction());
        assertEquals(LaneTravelDirection.BACKWARD, lhtBackward.component().direction());
    }

    @Test
    void keepsExportedFourLaneForwardLanesOnThePresetRightSide() {
        RoadPreset exportedFourLane = new RoadPreset(
                RoadPreset.CURRENT_SCHEMA_VERSION,
                1,
                23.0,
                new ResourceLocation("minecraft", "gray_concrete"),
                List.of(
                        new LaneComponent(
                                LaneType.VEHICLE,
                                3.0,
                                -7.0,
                                LaneTravelDirection.BACKWARD
                        ),
                        new LaneComponent(
                                LaneType.VEHICLE,
                                3.0,
                                -3.0,
                                LaneTravelDirection.BACKWARD
                        ),
                        new LaneComponent(
                                LaneType.VEHICLE,
                                3.0,
                                3.0,
                                LaneTravelDirection.FORWARD
                        ),
                        new LaneComponent(
                                LaneType.VEHICLE,
                                3.0,
                                7.0,
                                LaneTravelDirection.FORWARD
                        )
                )
        );
        RoadSegment segment = new RoadGraph(Level.OVERWORLD)
                .prepareStraightRoad(
                        EASTBOUND,
                        PRESET_ID,
                        exportedFourLane,
                        TrafficHandedness.RHT
                ).segment();

        assertEquals(
                List.of(-6.5, -2.5),
                segment.lanes().stream()
                        .filter(lane -> lane.component().direction()
                                == LaneTravelDirection.BACKWARD)
                        .map(lane -> lane.geometry().start().z())
                        .toList()
        );
        assertEquals(
                List.of(3.5, 7.5),
                segment.lanes().stream()
                        .filter(lane -> lane.component().direction()
                                == LaneTravelDirection.FORWARD)
                        .map(lane -> lane.geometry().start().z())
                        .toList()
        );
    }

    @Test
    void removingOneSegmentPreservesItsSharedNodeAndLogicalRoad() {
        RoadId roadId = roadId(1);
        Road road = new Road(roadId, TrafficHandedness.RHT);
        RoadNode a = node(2, 0.5);
        RoadNode b = node(3, 5.5);
        RoadNode c = node(4, 10.5);
        RoadSegment first = segment(5, 6, roadId, a, b);
        RoadSegment second = segment(7, 8, roadId, b, c);
        RoadGraph graph = RoadGraph.restore(
                Level.OVERWORLD,
                9,
                List.of(road),
                List.of(a, b, c),
                List.of(first, second),
                List.of(),
                RoadGraphLimits.defaults(),
                () -> { }
        );

        PreparedSegmentRemoval removal = graph.prepareSegmentRemoval(first.id());
        assertEquals(List.of(a.id()), removal.orphanedNodeIds());
        assertFalse(removal.removeRoad());

        graph.applyRemoval(removal);

        assertEquals(10, graph.revision());
        assertTrue(graph.segment(first.id()).isEmpty());
        assertTrue(graph.node(a.id()).isEmpty());
        assertTrue(graph.node(b.id()).isPresent());
        assertTrue(graph.node(c.id()).isPresent());
        assertTrue(graph.road(roadId).isPresent());
        assertTrue(graph.segment(second.id()).isPresent());
    }

    @Test
    void restoreRejectsGeometryThatDisagreesWithBoundaryNodes() {
        RoadId roadId = roadId(30);
        Road road = new Road(roadId, TrafficHandedness.RHT);
        RoadNode start = node(31, 0.5);
        RoadNode end = node(32, 5.5);
        RoadSegment mismatched = RoadSegment.straight(
                segmentId(33),
                roadId,
                start.id(),
                end.id(),
                new StraightGeometry(
                        new RoadPoint(2.5, 64.0, 0.5),
                        end.position()
                ),
                PRESET_ID,
                PRESET.revision(),
                RoadPresetSnapshot.from(PRESET),
                TrafficHandedness.RHT,
                List.of(laneId(34), laneId(35))
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> RoadGraph.restore(
                        Level.OVERWORLD,
                        0,
                        List.of(road),
                        List.of(start, end),
                        List.of(mismatched),
                        List.of(),
                        RoadGraphLimits.defaults(),
                        () -> { }
                )
        );
    }

    @Test
    void removingAnchoringTrunkReanchorsSurvivingNodeToTheRampEndpoint() {
        AtomicLong ids = new AtomicLong(5_000);
        RoadGraph graph = new RoadGraph(
                Level.OVERWORLD,
                RoadGraphLimits.defaults(),
                () -> new UUID(0, ids.getAndIncrement()),
                () -> { }
        );
        graph.applyAddition(graph.prepareStraightRoad(
                EASTBOUND,
                PRESET_ID,
                PRESET,
                TrafficHandedness.RHT
        ));
        graph.applyAddition(graph.prepareStraightRoad(
                new StraightGeometry(
                        new RoadPoint(0.5, 64.0, 2.5),
                        new RoadPoint(0.5, 64.0, 12.5)
                ),
                NARROW_PRESET_ID,
                NARROW_PRESET,
                TrafficHandedness.RHT
        ));
        RoadGraphSnapshot before = graph.snapshot();
        RoadNode junction = before.nodes().values().stream()
                .filter(node -> node.position().equals(
                        new RoadPoint(0.5, 64.0, 0.5)
                ))
                .findFirst()
                .orElseThrow();
        RoadSegment trunk = before.segments().values().stream()
                .filter(segment -> segment.referenceGeometry().start()
                        .equals(new RoadPoint(0.5, 64.0, 0.5))
                        && segment.referenceGeometry().end()
                        .equals(new RoadPoint(10.5, 64.0, 0.5)))
                .findFirst()
                .orElseThrow();
        List<RoadSegment> incident = before.segments().values().stream()
                .filter(segment -> segment.startNodeId().equals(junction.id())
                        || segment.endNodeId().equals(junction.id()))
                .toList();
        assertTrue(RoadNodePortResolver.isValidTopology(junction, incident));

        PreparedSegmentRemoval removal = graph.prepareSegmentRemoval(
                trunk.id()
        );
        graph.applyRemoval(removal);

        RoadNode surviving = graph.node(junction.id()).orElseThrow();
        assertEquals(junction.id(), surviving.id());
        assertEquals(
                new RoadPoint(0.5, 64.0, 2.5),
                surviving.position()
        );
        RoadSegment ramp = graph.segment(
                before.segments().values().stream()
                        .filter(segment -> !segment.id().equals(trunk.id()))
                        .findFirst()
                        .orElseThrow()
                        .id()
        ).orElseThrow();
        assertEquals(
                new RoadPoint(0.5, 64.0, 2.5),
                ramp.referenceGeometry().start()
        );
        RoadGraphSnapshot after = graph.snapshot();
        assertDoesNotThrow(() -> RoadGraph.restore(
                Level.OVERWORLD,
                after.revision(),
                after.roads().values(),
                after.nodes().values(),
                after.segments().values(),
                after.connectors().values(),
                after.junctionDefinitions().values(),
                RoadGraphLimits.defaults(),
                () -> { }
        ));
    }

    @Test
    void removalSeparatesDisconnectedSurvivorsWithoutMovingTheirGeometry() {
        AtomicLong ids = new AtomicLong(5_100);
        RoadGraph graph = new RoadGraph(
                Level.OVERWORLD,
                RoadGraphLimits.defaults(),
                () -> new UUID(0, ids.getAndIncrement()),
                () -> { }
        );
        graph.applyAddition(graph.prepareStraightRoad(
                EASTBOUND,
                PRESET_ID,
                PRESET,
                TrafficHandedness.RHT
        ));
        graph.applyAddition(graph.prepareStraightRoad(
                new StraightGeometry(
                        new RoadPoint(0.5, 64.0, 2.5),
                        new RoadPoint(0.5, 64.0, 12.5)
                ),
                NARROW_PRESET_ID,
                NARROW_PRESET,
                TrafficHandedness.RHT
        ));
        graph.applyAddition(graph.prepareStraightRoad(
                new StraightGeometry(
                        new RoadPoint(0.5, 64.0, -2.5),
                        new RoadPoint(0.5, 64.0, -12.5)
                ),
                NARROW_PRESET_ID,
                NARROW_PRESET,
                TrafficHandedness.RHT
        ));
        RoadGraphSnapshot before = graph.snapshot();
        RoadSegment trunk = before.segments().values().stream()
                .filter(segment -> segment.referenceGeometry().start()
                        .equals(new RoadPoint(0.5, 64.0, 0.5))
                        && segment.referenceGeometry().end()
                        .equals(new RoadPoint(10.5, 64.0, 0.5)))
                .findFirst()
                .orElseThrow();
        RoadNode junction = before.nodes().values().stream()
                .filter(node -> node.position().equals(
                        new RoadPoint(0.5, 64.0, 0.5)
                ))
                .findFirst()
                .orElseThrow();
        List<RoadSegment> incident = before.segments().values().stream()
                .filter(segment -> segment.startNodeId().equals(junction.id())
                        || segment.endNodeId().equals(junction.id()))
                .toList();
        assertTrue(RoadNodePortResolver.isValidTopology(junction, incident));

        PreparedSegmentRemoval removal = graph.prepareSegmentRemoval(trunk.id());
        assertEquals(before, graph.snapshot());
        graph.applyRemoval(removal);
        RoadGraphSnapshot after = graph.snapshot();
        assertEquals(2, after.segments().size());
        assertEquals(4, after.nodes().size());
        for (RoadSegment survivor : after.segments().values()) {
            assertEquals(before.segments().get(survivor.id()).referenceGeometry(),
                    survivor.referenceGeometry());
            assertEquals(before.segments().get(survivor.id()).lanes(), survivor.lanes());
        }
        com.cookiecraftmods.roadbuilder.networking.RoadGraphSnapshotS2CPacket.current(after);
    }

    private static RoadSegment preparedSegment(TrafficHandedness handedness, long firstId) {
        AtomicLong ids = new AtomicLong(firstId);
        RoadGraph graph = new RoadGraph(
                Level.OVERWORLD,
                RoadGraphLimits.defaults(),
                () -> new UUID(0, ids.getAndIncrement()),
                () -> { }
        );
        return graph.prepareStraightRoad(EASTBOUND, PRESET_ID, PRESET, handedness).segment();
    }

    private static RoadNode otherNode(
            RoadGraphSnapshot snapshot,
            RoadSegment segment,
            RoadNode junction
    ) {
        RoadNodeId otherId = segment.startNodeId().equals(junction.id())
                ? segment.endNodeId()
                : segment.startNodeId();
        return snapshot.nodes().get(otherId);
    }

    private static LaneConnector connectorBetween(
            RoadGraphSnapshot snapshot,
            RoadSegment fromSegment,
            RoadSegment toSegment,
            LaneMovementType movementType
    ) {
        return snapshot.connectors().values().stream()
                .filter(connector -> connector.movement().type()
                        == movementType)
                .filter(connector -> snapshot.lanes().get(
                        connector.fromLaneId()).segmentId()
                        .equals(fromSegment.id()))
                .filter(connector -> snapshot.lanes().get(
                        connector.toLaneId()).segmentId()
                        .equals(toSegment.id()))
                .findFirst()
                .orElseThrow();
    }

    private static RoadNode node(long id, double x) {
        return new RoadNode(new RoadNodeId(new UUID(0, id)), new RoadPoint(x, 64.0, 0.5));
    }

    private static RoadSegment segment(
            long segmentId,
            long laneId,
            RoadId roadId,
            RoadNode start,
            RoadNode end
    ) {
        return RoadSegment.straight(
                segmentId(segmentId),
                roadId,
                start.id(),
                end.id(),
                new StraightGeometry(start.position(), end.position()),
                PRESET_ID,
                PRESET.revision(),
                new RoadPresetSnapshot(
                        1,
                        PRESET.surfaceBlock(),
                        List.of(new LaneComponent(
                                LaneType.VEHICLE,
                                1.0,
                                0.0,
                                LaneTravelDirection.FORWARD
                        ))
                ),
                TrafficHandedness.RHT,
                List.of(laneId(laneId))
        );
    }

    private static RoadId roadId(long id) {
        return new RoadId(new UUID(0, id));
    }

    private static RoadSegmentId segmentId(long id) {
        return new RoadSegmentId(new UUID(0, id));
    }

    private static RoadLaneId laneId(long id) {
        return new RoadLaneId(new UUID(0, id));
    }
}
