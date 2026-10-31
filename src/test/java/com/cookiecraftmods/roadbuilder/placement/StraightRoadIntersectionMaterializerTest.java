package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.geometry.QuadraticBezierGeometry;
import com.cookiecraftmods.roadbuilder.geometry.ReferenceGeometry;
import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.StraightGeometry;
import com.cookiecraftmods.roadbuilder.network.JunctionConnectorPlanner;
import com.cookiecraftmods.roadbuilder.network.JunctionDefinition;
import com.cookiecraftmods.roadbuilder.network.JunctionSolveResult;
import com.cookiecraftmods.roadbuilder.network.JunctionSolver;
import com.cookiecraftmods.roadbuilder.network.LaneConnector;
import com.cookiecraftmods.roadbuilder.network.LaneConnectorGeometryPlanner;
import com.cookiecraftmods.roadbuilder.network.LaneConnectorId;
import com.cookiecraftmods.roadbuilder.network.LaneConnectorMovement;
import com.cookiecraftmods.roadbuilder.network.LaneMovementType;
import com.cookiecraftmods.roadbuilder.network.PreparedRoadAddition;
import com.cookiecraftmods.roadbuilder.network.PreparedRoadNetworkMutation;
import com.cookiecraftmods.roadbuilder.network.RoadGraph;
import com.cookiecraftmods.roadbuilder.network.RoadLane;
import com.cookiecraftmods.roadbuilder.network.RoadNode;
import com.cookiecraftmods.roadbuilder.network.RoadSegment;
import com.cookiecraftmods.roadbuilder.network.RoadSegmentId;
import com.cookiecraftmods.roadbuilder.network.RoadNodeId;
import com.cookiecraftmods.roadbuilder.network.RoadId;
import com.cookiecraftmods.roadbuilder.network.RoadLaneId;
import com.cookiecraftmods.roadbuilder.network.RoadPresetSnapshot;
import com.cookiecraftmods.roadbuilder.preset.LaneComponent;
import com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection;
import com.cookiecraftmods.roadbuilder.preset.LaneType;
import com.cookiecraftmods.roadbuilder.preset.RoadPreset;
import com.cookiecraftmods.roadbuilder.preset.RoadSurfaceBand;
import com.cookiecraftmods.roadbuilder.preset.RoadSurfaceRole;
import com.cookiecraftmods.roadbuilder.preset.TrafficHandedness;
import com.cookiecraftmods.roadbuilder.test.MinecraftBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StraightRoadIntersectionMaterializerTest {
    private static final ResourceLocation PRESET_ID =
            new ResourceLocation("roadbuilder", "intersection_test");
    private static final RoadPreset PRESET = new RoadPreset(
            RoadPreset.CURRENT_SCHEMA_VERSION,
            1,
            7.0,
            new ResourceLocation("minecraft", "gray_concrete"),
            List.of(new LaneComponent(
                    LaneType.VEHICLE,
                    3.0,
                    0.0,
                    LaneTravelDirection.FORWARD
            ))
    );

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftBootstrap.bootstrap();
    }

    @Test
    void leavesUncrossedGrassAtAnApproachForTheSegmentLayer() {
        var gray = new ResourceLocation("minecraft", "gray_concrete");
        var grass = new ResourceLocation("minecraft", "grass_block");
        RoadNodeId centerId = new RoadNodeId(UUID.randomUUID());
        RoadPoint point = new RoadPoint(0.5, 64, 0.5);
        RoadNode center = new RoadNode(centerId, point);
        var lane = new LaneComponent(LaneType.VEHICLE, 3, 0, LaneTravelDirection.FORWARD);
        var preset = new RoadPresetSnapshot(9, gray, List.of(lane), List.of(
                new RoadSurfaceBand(RoadSurfaceRole.GRASS_MEDIAN, 3, grass),
                new RoadSurfaceBand(RoadSurfaceRole.PAVEMENT, 3, gray),
                new RoadSurfaceBand(RoadSurfaceRole.GRASS_MEDIAN, 3, grass)));
        var arms = new java.util.ArrayList<RoadSegment>();
        for (RoadPoint end : List.of(new RoadPoint(30.5, 64, 0.5), new RoadPoint(0.5, 64, 30.5))) {
            arms.add(RoadSegment.path(new RoadSegmentId(UUID.randomUUID()), new RoadId(UUID.randomUUID()),
                    centerId, new RoadNodeId(UUID.randomUUID()), new StraightGeometry(point, end),
                    PRESET_ID, 1, preset, TrafficHandedness.RHT, List.of(new RoadLaneId(UUID.randomUUID()))));
        }
        var plan = assertInstanceOf(BlockPlanResult.Success.class,
                new StraightRoadIntersectionMaterializer().plan(Level.OVERWORLD, center, arms,
                        gray, new ResourceLocation("minecraft", "smooth_stone"), 5000, 100000)).plan();
        BlockPos verge = new BlockPos(1, 64, -3);
        org.junit.jupiter.api.Assertions.assertFalse(plan.positions().contains(verge));
        var segmentPlan = assertInstanceOf(BlockPlanResult.Success.class,
                new StraightRoadSegmentMaterializer().plan(Level.OVERWORLD, arms.get(0),
                        new ConstructionLimits(512, 131072))).plan();
        assertTrue(segmentPlan.blocks().stream().anyMatch(block -> block.position().equals(verge)
                && block.targetState().is(Blocks.GRASS_BLOCK)));
    }

    @Test
    void angledHalfHeightJunctionUsesTheSameBottomSlabsAsItsApproaches() {
        RoadNodeId centerId = new RoadNodeId(UUID.randomUUID());
        RoadPoint point = new RoadPoint(0.5, 64.5, 0.5);
        RoadNode center = new RoadNode(centerId, point);
        List<RoadSegment> arms = List.of(
                segment(point, new RoadPoint(20.5, 64.5, 10.5), centerId, new RoadNodeId(UUID.randomUUID())),
                segment(point, new RoadPoint(-20.5, 64.5, -10.5), centerId, new RoadNodeId(UUID.randomUUID())),
                segment(point, new RoadPoint(-10.5, 64.5, 20.5), centerId, new RoadNodeId(UUID.randomUUID())));
        BlockPlacementPlan plan = assertInstanceOf(BlockPlanResult.Success.class,
                new StraightRoadIntersectionMaterializer().plan(Level.OVERWORLD, center, arms,
                        new ResourceLocation("minecraft", "smooth_stone_slab"),
                        new ResourceLocation("minecraft", "stone_brick_slab"), 5000, 100000)).plan();
        assertTrue(plan.positions().contains(new BlockPos(0, 65, 0)));
        assertTrue(plan.blocks().stream().allMatch(block -> block.position().getY() == 65
                && block.targetState().getValue(net.minecraft.world.level.block.SlabBlock.TYPE)
                == net.minecraft.world.level.block.state.properties.SlabType.BOTTOM));
    }

    @Test
    void pavesShallowHighwayExitsAndTheirLaneConnections() {
        for (double angle : new double[]{2.0, 5.0, 9.0}) {
            RoadNodeId centerId = new RoadNodeId(UUID.randomUUID());
            RoadNode center = new RoadNode(centerId, new RoadPoint(0.5, 64.0, 0.5));
            List<RoadSegment> incident = List.of(
                    segment(new RoadPoint(-60.5, 64.0, 0.5), center.position(),
                            new RoadNodeId(UUID.randomUUID()), centerId, 15),
                    segment(center.position(), new RoadPoint(60.5, 64.0, 0.5),
                            centerId, new RoadNodeId(UUID.randomUUID()), 15),
                    segment(center.position(),
                            new RoadPoint(60.5, 64.0, 0.5 + 60 * Math.tan(Math.toRadians(angle))),
                            centerId, new RoadNodeId(UUID.randomUUID()), 7)
            );
            BlockPlacementPlan plan = assertInstanceOf(BlockPlanResult.Success.class,
                    new StraightRoadIntersectionMaterializer().plan(
                            Level.OVERWORLD, center, incident,
                            new ResourceLocation("minecraft", "gray_concrete"),
                            new ResourceLocation("minecraft", "smooth_stone"),
                            10_000, 100_000)).plan();
            assertConnectorPathsArePaved(center, incident, plan);
            // The overlap extends well beyond the node. It must not be clipped
            // to the small endpoint cap and leave old markings in the merge.
            assertTrue(plan.positions().contains(new BlockPos(10, 64, 0)));
            assertTrue(plan.affectedBlockCount() < 2_000);
        }
    }

    @Test
    void preservesBranchesThatStartTangentAndThenCurveApart() {
        RoadNodeId centerId = new RoadNodeId(UUID.randomUUID());
        RoadNode center = new RoadNode(centerId, new RoadPoint(0.5, 64.0, 0.5));
        List<RoadSegment> incident = List.of(
                sidewalkSegment(new RoadPoint(-60.5, 64.0, 0.5), center.position(),
                        new RoadNodeId(UUID.randomUUID()), centerId, 15),
                sidewalkSegment(center.position(), new RoadPoint(60.5, 64.0, 0.5),
                        centerId, new RoadNodeId(UUID.randomUUID()), 7),
                sidewalkSegment(new QuadraticBezierGeometry(center.position(),
                        new RoadPoint(10.5, 64.0, 0.5), new RoadPoint(50.5, 64.0, 30.5)),
                        centerId, new RoadNodeId(UUID.randomUUID()), 7));
        BlockPlacementPlan plan = assertInstanceOf(BlockPlanResult.Success.class,
                new StraightRoadIntersectionMaterializer().plan(
                        Level.OVERWORLD, center, incident,
                        new ResourceLocation("minecraft", "gray_concrete"),
                        new ResourceLocation("minecraft", "smooth_stone"),
                        10_000, 100_000)).plan();
        assertConnectorPathsArePaved(center, incident, plan);
        assertTrue(plan.positions().contains(new BlockPos(10, 64, 0)));
    }

    @Test
    void pavesEqualWidthHighwayContinuation() {
        RoadNodeId centerId = new RoadNodeId(UUID.randomUUID());
        RoadNode center = new RoadNode(centerId, new RoadPoint(0.5, 64.0, 0.5));
        List<RoadSegment> incident = List.of(
                segment(new RoadPoint(-60.5, 64.0, 0.5), center.position(),
                        new RoadNodeId(UUID.randomUUID()), centerId, 15),
                segment(center.position(), new RoadPoint(60.5, 64.0, 0.5),
                        centerId, new RoadNodeId(UUID.randomUUID()), 15));
        BlockPlacementPlan plan = assertInstanceOf(BlockPlanResult.Success.class,
                new StraightRoadIntersectionMaterializer().plan(
                        Level.OVERWORLD, center, incident,
                        new ResourceLocation("minecraft", "gray_concrete"),
                        new ResourceLocation("minecraft", "smooth_stone"),
                        10_000, 100_000)).plan();
        assertConnectorPathsArePaved(center, incident, plan);
        assertTrue(plan.positions().contains(new BlockPos(0, 64, 0)));
    }

    @Test
    void fillsTheConflictAndAddsTangentCornerFillets() {
        // Leave room beyond the edge intersection for the tangent attachments.
        RoadGraph graph = new RoadGraph(Level.OVERWORLD);
        PreparedRoadAddition eastWest = graph.prepareStraightRoad(
                new StraightGeometry(
                        new RoadPoint(-4.5, 64.0, 0.5),
                        new RoadPoint(15.5, 64.0, 0.5)
                ),
                PRESET_ID,
                PRESET,
                TrafficHandedness.RHT
        );
        graph.applyAddition(eastWest);
        PreparedRoadNetworkMutation crossing = graph.prepareStraightRoadNetwork(
                new StraightGeometry(
                        new RoadPoint(5.5, 64.0, -10.5),
                        new RoadPoint(5.5, 64.0, 10.5)
                ),
                PRESET_ID,
                PRESET,
                TrafficHandedness.RHT
        );
        RoadNode node = crossing.addedNodes().stream()
                .filter(candidate -> incidentCount(candidate, crossing) == 4)
                .findFirst()
                .orElseThrow();
        List<RoadSegment> incident = crossing.allUpsertedSegments().stream()
                .filter(segment -> segment.startNodeId().equals(node.id())
                        || segment.endNodeId().equals(node.id()))
                .toList();

        BlockPlacementPlan plan = assertInstanceOf(
                BlockPlanResult.Success.class,
                new StraightRoadIntersectionMaterializer().plan(
                        Level.OVERWORLD,
                        node,
                        incident,
                        new ResourceLocation("minecraft", "gray_concrete"),
                        new ResourceLocation("minecraft", "gray_concrete"),
                        1_000,
                        10_000
                )
        ).plan();

        assertTrue(plan.affectedBlockCount() > 0);
        assertConnectorPathsArePaved(node, incident, plan);
        assertTrue(plan.blocks().stream().allMatch(block ->
                block.targetState().is(Blocks.GRAY_CONCRETE)));
        assertTrue(plan.positions().contains(new BlockPos(5, 64, 0)));
        assertTrue(plan.positions().contains(new BlockPos(9, 64, 4)));
        assertTrue(!plan.positions().contains(new BlockPos(11, 64, 6)));
    }

    @Test
    void keepsNarrowConflictOpenAndWrapsOuterFilletsWithSidewalk() {
        // Leave room beyond the edge intersection for the tangent attachments.
        RoadPreset styledPreset = styledPreset();
        RoadGraph graph = new RoadGraph(Level.OVERWORLD);
        graph.applyAddition(graph.prepareStraightRoad(
                new StraightGeometry(
                        new RoadPoint(-4.5, 64.0, 0.5),
                        new RoadPoint(15.5, 64.0, 0.5)
                ),
                PRESET_ID,
                styledPreset,
                TrafficHandedness.RHT
        ));
        PreparedRoadNetworkMutation crossing = graph.prepareStraightRoadNetwork(
                new StraightGeometry(
                        new RoadPoint(5.5, 64.0, -10.5),
                        new RoadPoint(5.5, 64.0, 10.5)
                ),
                PRESET_ID,
                styledPreset,
                TrafficHandedness.RHT
        );
        RoadNode node = crossing.addedNodes().stream()
                .filter(candidate -> incidentCount(candidate, crossing) == 4)
                .findFirst()
                .orElseThrow();
        List<RoadSegment> incident = crossing.allUpsertedSegments().stream()
                .filter(segment -> segment.startNodeId().equals(node.id())
                        || segment.endNodeId().equals(node.id()))
                .toList();

        BlockPlacementPlan plan = assertInstanceOf(
                BlockPlanResult.Success.class,
                new StraightRoadIntersectionMaterializer().plan(
                        Level.OVERWORLD,
                        node,
                        incident,
                        new ResourceLocation("minecraft", "gray_concrete"),
                        new ResourceLocation("minecraft", "smooth_stone"),
                        1_000,
                        10_000
                )
        ).plan();

        assertTrue(plan.affectedBlockCount() > 0);
        assertConnectorPathsArePaved(node, incident, plan);
        assertTrue(plan.blocks().stream().anyMatch(block ->
                block.targetState().is(Blocks.SMOOTH_STONE)));
        assertTrue(plan.blocks().stream().noneMatch(block ->
                block.targetState().is(Blocks.YELLOW_CONCRETE)));
        assertTrue(plan.blocks().stream()
                .filter(block -> block.targetState().is(Blocks.SMOOTH_STONE))
                .map(PlannedBlock::position)
                .allMatch(position -> {
                    int x = Math.abs(position.getX() - 5);
                    int z = Math.abs(position.getZ());
                    return x + z >= 4;
                }));
        assertTrue(plan.blocks().stream().anyMatch(block ->
                block.position().equals(new BlockPos(9, 64, 5))
                        && block.targetState().is(Blocks.SMOOTH_STONE)));
        assertTrue(plan.blocks().stream().anyMatch(block ->
                block.position().equals(new BlockPos(8, 64, 3))
                        && block.targetState().is(Blocks.GRAY_CONCRETE)));
        assertTrue(plan.blocks().stream().anyMatch(block ->
                block.position().equals(new BlockPos(7, 64, 2))
                        && block.targetState().is(Blocks.GRAY_CONCRETE)));
        BlockPos center = new BlockPos(5, 64, 0);
        for (int xDirection : List.of(-1, 1)) {
            for (int zDirection : List.of(-1, 1)) {
                assertTrue(plan.blocks().stream().anyMatch(block ->
                        block.position().equals(center.offset(
                                xDirection * 4,
                                0,
                                zDirection * 5
                        )) && block.targetState().is(Blocks.SMOOTH_STONE)));
                assertTrue(plan.blocks().stream().anyMatch(block ->
                        block.position().equals(center.offset(
                                xDirection * 5,
                                0,
                                zDirection * 4
                        )) && block.targetState().is(Blocks.SMOOTH_STONE)));
                assertTrue(plan.blocks().stream().anyMatch(block ->
                        block.position().equals(center.offset(
                                xDirection * 3,
                                0,
                                zDirection * 3
                        )) && block.targetState().is(Blocks.GRAY_CONCRETE)));
                assertTrue(plan.blocks().stream().anyMatch(block ->
                        block.position().equals(center.offset(
                                xDirection * 2,
                                0,
                                zDirection * 2
                        )) && block.targetState().is(Blocks.GRAY_CONCRETE)));
            }
        }
        assertTrue(plan.blocks().stream()
                .filter(block -> !block.targetState().is(Blocks.SMOOTH_STONE))
                .allMatch(block -> block.targetState().is(Blocks.GRAY_CONCRETE)));
    }

    @Test
    void manualConnectorPathIsPavedWhenPassedAsExisting() {
        RoadPreset styledPreset = styledPreset();
        RoadGraph graph = new RoadGraph(Level.OVERWORLD);
        graph.applyAddition(graph.prepareStraightRoad(
                new StraightGeometry(
                        new RoadPoint(0.5, 64.0, 0.5),
                        new RoadPoint(10.5, 64.0, 0.5)
                ),
                PRESET_ID,
                styledPreset,
                TrafficHandedness.RHT
        ));
        PreparedRoadNetworkMutation crossing = graph.prepareStraightRoadNetwork(
                new StraightGeometry(
                        new RoadPoint(5.5, 64.0, -5.5),
                        new RoadPoint(5.5, 64.0, 5.5)
                ),
                PRESET_ID,
                styledPreset,
                TrafficHandedness.RHT
        );
        RoadNode node = crossing.addedNodes().stream()
                .filter(candidate -> incidentCount(candidate, crossing) == 4)
                .findFirst()
                .orElseThrow();
        List<RoadSegment> incident = crossing.allUpsertedSegments().stream()
                .filter(segment -> segment.startNodeId().equals(node.id())
                        || segment.endNodeId().equals(node.id()))
                .toList();
        LaneConnector turn = firstTurnConnector(node, incident);

        BlockPlacementPlan plan = assertInstanceOf(
                BlockPlanResult.Success.class,
                new StraightRoadIntersectionMaterializer().plan(
                        Level.OVERWORLD,
                        node,
                        incident,
                        new ResourceLocation("minecraft", "gray_concrete"),
                        new ResourceLocation("minecraft", "smooth_stone"),
                        100,
                        10_000,
                        JunctionDefinition.automatic(node.id()),
                        List.of(turn)
                )
        ).plan();

        assertConnectorPathIsPaved(node, incident, turn, plan);
    }

    @Test
    void disabledManualConnectorDoesNotMarkActiveConnectorPaths() {
        RoadPreset styledPreset = styledPreset();
        RoadGraph graph = new RoadGraph(Level.OVERWORLD);
        graph.applyAddition(graph.prepareStraightRoad(
                new StraightGeometry(
                        new RoadPoint(0.5, 64.0, 0.5),
                        new RoadPoint(10.5, 64.0, 0.5)
                ),
                PRESET_ID,
                styledPreset,
                TrafficHandedness.RHT
        ));
        PreparedRoadNetworkMutation crossing = graph.prepareStraightRoadNetwork(
                new StraightGeometry(
                        new RoadPoint(5.5, 64.0, -5.5),
                        new RoadPoint(5.5, 64.0, 5.5)
                ),
                PRESET_ID,
                styledPreset,
                TrafficHandedness.RHT
        );
        RoadNode node = crossing.addedNodes().stream()
                .filter(candidate -> incidentCount(candidate, crossing) == 4)
                .findFirst()
                .orElseThrow();
        List<RoadSegment> incident = crossing.allUpsertedSegments().stream()
                .filter(segment -> segment.startNodeId().equals(node.id())
                        || segment.endNodeId().equals(node.id()))
                .toList();
        LaneConnector turn = firstTurnConnector(node, incident);
        LaneConnector disabled = new LaneConnector(
                turn.id(),
                turn.fromLaneId(),
                turn.toLaneId(),
                new LaneConnectorMovement(
                        turn.movement().type(),
                        false,
                        true,
                        0
                )
        );

        BlockPlacementPlan plan = assertInstanceOf(
                BlockPlanResult.Success.class,
                new StraightRoadIntersectionMaterializer().plan(
                        Level.OVERWORLD,
                        node,
                        incident,
                        new ResourceLocation("minecraft", "gray_concrete"),
                        new ResourceLocation("minecraft", "smooth_stone"),
                        100,
                        10_000,
                        JunctionDefinition.automatic(node.id()),
                        List.of(disabled)
                )
        ).plan();

        var connectors = new JunctionConnectorPlanner().plan(
                node.id(), incident, List.of(disabled),
                () -> new LaneConnectorId(UUID.randomUUID())
        );
        assertTrue(connectors.stream().anyMatch(connector ->
                !connector.movement().allowed()));
        for (LaneConnector connector : connectors) {
            if (connector.movement().allowed()) {
                assertConnectorPathIsPaved(node, incident, connector, plan);
            }
        }
    }

    @Test
    void mergesSlightlyMisalignedRoadHalvesInATJunction() {
        RoadNodeId centerId = new RoadNodeId(UUID.randomUUID());
        RoadNode center = new RoadNode(
                centerId,
                new RoadPoint(0.5, 64.0, 0.5)
        );
        List<RoadSegment> incident = List.of(
                segment(
                        new RoadPoint(-20.5, 64.0, 0.5),
                        center.position(),
                        new RoadNodeId(UUID.randomUUID()),
                        centerId
                ),
                segment(
                        center.position(),
                        new RoadPoint(20.5, 64.0, 0.7),
                        centerId,
                        new RoadNodeId(UUID.randomUUID())
                ),
                segment(
                        center.position(),
                        new RoadPoint(0.5, 64.0, 20.5),
                        centerId,
                        new RoadNodeId(UUID.randomUUID())
                )
        );

        BlockPlacementPlan plan = assertInstanceOf(
                BlockPlanResult.Success.class,
                new StraightRoadIntersectionMaterializer().plan(
                        Level.OVERWORLD,
                        center,
                        incident,
                        new ResourceLocation("minecraft", "gray_concrete"),
                        new ResourceLocation("minecraft", "smooth_stone"),
                        2_000,
                        10_000
                )
        ).plan();

        assertTrue(plan.affectedBlockCount() < 1_000);
        assertTrue(plan.bounds().maximum().getX()
                - plan.bounds().minimum().getX() + 1 <= 65);
        assertTrue(plan.bounds().maximum().getZ()
                - plan.bounds().minimum().getZ() + 1 <= 65);
        assertTrue(plan.positions().contains(new BlockPos(0, 64, 0)));
        assertTrue(!plan.positions().contains(new BlockPos(0, 64, -5)));
    }

    @Test
    void roundsWideCornersWithoutClosingTheConflictCenter() {
        ResourceLocation gray = new ResourceLocation(
                "minecraft",
                "gray_concrete"
        );
        ResourceLocation stone = new ResourceLocation(
                "minecraft",
                "smooth_stone"
        );
        RoadPreset wide = new RoadPreset(
                RoadPreset.CURRENT_SCHEMA_VERSION,
                1,
                23.0,
                gray,
                List.of(new LaneComponent(
                        LaneType.VEHICLE,
                        13.0,
                        0.0,
                        LaneTravelDirection.FORWARD
                )),
                List.of(
                        new RoadSurfaceBand(RoadSurfaceRole.SIDEWALK, 3, stone),
                        new RoadSurfaceBand(
                                RoadSurfaceRole.GRASS_MEDIAN,
                                3,
                                new ResourceLocation(
                                        "minecraft",
                                        "grass_block"
                                )
                        ),
                        new RoadSurfaceBand(RoadSurfaceRole.SIDEWALK, 1, stone),
                        new RoadSurfaceBand(RoadSurfaceRole.PAVEMENT, 4, gray),
                        new RoadSurfaceBand(
                                RoadSurfaceRole.MARKING,
                                1,
                                new ResourceLocation(
                                        "minecraft",
                                        "yellow_concrete"
                                )
                        ),
                        new RoadSurfaceBand(RoadSurfaceRole.PAVEMENT, 4, gray),
                        new RoadSurfaceBand(RoadSurfaceRole.SIDEWALK, 1, stone),
                        new RoadSurfaceBand(
                                RoadSurfaceRole.GRASS_MEDIAN,
                                3,
                                new ResourceLocation(
                                        "minecraft",
                                        "grass_block"
                                )
                        ),
                        new RoadSurfaceBand(RoadSurfaceRole.SIDEWALK, 3, stone)
                )
        );
        RoadGraph graph = new RoadGraph(Level.OVERWORLD);
        graph.applyAddition(graph.prepareStraightRoad(
                new StraightGeometry(
                        new RoadPoint(-20.5, 64.0, 0.5),
                        new RoadPoint(20.5, 64.0, 0.5)
                ),
                PRESET_ID,
                wide,
                TrafficHandedness.RHT
        ));
        PreparedRoadNetworkMutation crossing = graph.prepareStraightRoadNetwork(
                new StraightGeometry(
                        new RoadPoint(0.5, 64.0, -20.5),
                        new RoadPoint(0.5, 64.0, 20.5)
                ),
                PRESET_ID,
                wide,
                TrafficHandedness.RHT
        );
        RoadNode node = crossing.addedNodes().stream()
                .filter(candidate -> incidentCount(candidate, crossing) == 4)
                .findFirst()
                .orElseThrow();
        List<RoadSegment> incident = crossing.allUpsertedSegments().stream()
                .filter(segment -> segment.startNodeId().equals(node.id())
                        || segment.endNodeId().equals(node.id()))
                .toList();

        BlockPlacementPlan plan = assertInstanceOf(
                BlockPlanResult.Success.class,
                new StraightRoadIntersectionMaterializer().plan(
                        Level.OVERWORLD,
                        node,
                        incident,
                        gray,
                        stone,
                        5_000,
                        20_000
                )
        ).plan();

        assertTrue(plan.blocks().stream().anyMatch(block ->
                block.targetState().is(Blocks.SMOOTH_STONE)));
        assertTrue(plan.blocks().stream().anyMatch(block ->
                block.position().equals(new BlockPos(11, 64, 11))
                        && block.targetState().is(Blocks.SMOOTH_STONE)));
        assertTrue(plan.blocks().stream().anyMatch(block ->
                block.position().equals(new BlockPos(7, 64, 7))
                        && block.targetState().is(Blocks.GRAY_CONCRETE)));
        assertTrue(plan.blocks().stream().anyMatch(block ->
                block.position().equals(new BlockPos(9, 64, 9))
                        && block.targetState().is(Blocks.GRAY_CONCRETE)));
        assertTrue(plan.blocks().stream().anyMatch(block ->
                block.position().equals(new BlockPos(0, 64, 0))
                        && block.targetState().is(Blocks.GRAY_CONCRETE)));
    }

    @Test
    void supportsFiveApproachIntersectionsWithoutInventingExtraArms() {
        RoadNodeId centerId = new RoadNodeId(UUID.randomUUID());
        RoadNode center = new RoadNode(
                centerId,
                new RoadPoint(0.5, 64.0, 0.5)
        );
        List<RoadSegment> incident = new java.util.ArrayList<>();
        for (int index = 0; index < 5; index++) {
            double angle = Math.PI * 2.0 * index / 5.0;
            incident.add(segment(
                    center.position(),
                    new RoadPoint(
                            center.position().x() + Math.cos(angle) * 20.0,
                            64.0,
                            center.position().z() + Math.sin(angle) * 20.0
                    ),
                    centerId,
                    new RoadNodeId(UUID.randomUUID())
            ));
        }

        BlockPlacementPlan plan = assertInstanceOf(
                BlockPlanResult.Success.class,
                new StraightRoadIntersectionMaterializer().plan(
                        Level.OVERWORLD,
                        center,
                        incident,
                        new ResourceLocation("minecraft", "gray_concrete"),
                        new ResourceLocation("minecraft", "smooth_stone"),
                        2_000,
                        10_000
                )
        ).plan();

        assertTrue(plan.affectedBlockCount() < 500);
        assertTrue(plan.positions().contains(new BlockPos(0, 64, 0)));
    }

    @Test
    void keepsParallelSplitArmsOnTheirOwnNodePorts() {
        RoadNodeId centerId = new RoadNodeId(UUID.randomUUID());
        RoadNode center = new RoadNode(
                centerId,
                new RoadPoint(0.5, 64.0, 0.5)
        );
        List<RoadSegment> incident = List.of(
                segment(
                        new RoadPoint(-20.5, 64.0, 0.5),
                        center.position(),
                        new RoadNodeId(UUID.randomUUID()),
                        centerId,
                        12
                ),
                segment(
                        new RoadPoint(0.5, 64.0, -3.5),
                        new RoadPoint(20.5, 64.0, -3.5),
                        centerId,
                        new RoadNodeId(UUID.randomUUID()),
                        3
                ),
                segment(
                        new RoadPoint(0.5, 64.0, 4.5),
                        new RoadPoint(20.5, 64.0, 4.5),
                        centerId,
                        new RoadNodeId(UUID.randomUUID()),
                        3
                )
        );

        BlockPlacementPlan plan = assertInstanceOf(
                BlockPlanResult.Success.class,
                new StraightRoadIntersectionMaterializer().plan(
                        Level.OVERWORLD,
                        center,
                        incident,
                        new ResourceLocation("minecraft", "gray_concrete"),
                        new ResourceLocation("minecraft", "gray_concrete"),
                        2_000,
                        10_000
                )
        ).plan();

        assertTrue(plan.positions().contains(new BlockPos(1, 64, -4)));
        assertTrue(plan.positions().contains(new BlockPos(1, 64, 4)));
    }

    @Test
    void keepsAnAngledRoadForkInsideTheLocalMergeArea() {
        RoadNodeId centerId = new RoadNodeId(UUID.randomUUID());
        RoadNode center = new RoadNode(
                centerId,
                new RoadPoint(0.5, 64.0, 0.5)
        );
        List<RoadSegment> incident = List.of(
                segment(
                        center.position(),
                        new RoadPoint(30.5, 64.0, 0.5),
                        centerId,
                        new RoadNodeId(UUID.randomUUID()),
                        12
                ),
                segment(
                        new RoadPoint(0.5, 64.0, -3.5),
                        new RoadPoint(-30.5, 64.0, -12.5),
                        centerId,
                        new RoadNodeId(UUID.randomUUID()),
                        5
                ),
                segment(
                        new RoadPoint(0.5, 64.0, 4.5),
                        new RoadPoint(-30.5, 64.0, 13.5),
                        centerId,
                        new RoadNodeId(UUID.randomUUID()),
                        5
                )
        );

        BlockPlacementPlan plan = assertInstanceOf(
                BlockPlanResult.Success.class,
                new StraightRoadIntersectionMaterializer().plan(
                        Level.OVERWORLD,
                        center,
                        incident,
                        new ResourceLocation("minecraft", "gray_concrete"),
                        new ResourceLocation("minecraft", "smooth_stone"),
                        2_000,
                        10_000
                )
        ).plan();

        double farthest = plan.positions().stream()
                .mapToDouble(position -> Math.hypot(
                        position.getX() + 0.5 - center.position().x(),
                        position.getZ() + 0.5 - center.position().z()
                ))
                .max()
                .orElseThrow();
        assertTrue(
                farthest <= 20.0,
                () -> "fork surface escaped its local merge area: " + farthest
        );
    }

    @Test
    void preservesSidewalksAroundAnAngledThreeWayFork() {
        RoadNodeId centerId = new RoadNodeId(UUID.randomUUID());
        RoadNode center = new RoadNode(
                centerId,
                new RoadPoint(0.5, 64.0, 0.5)
        );
        List<RoadSegment> incident = List.of(
                sidewalkSegment(
                        center.position(),
                        new RoadPoint(30.5, 64.0, 0.5),
                        centerId,
                        new RoadNodeId(UUID.randomUUID()),
                        14
                ),
                sidewalkSegment(
                        new RoadPoint(0.5, 64.0, -3.5),
                        new RoadPoint(-30.5, 64.0, -12.5),
                        centerId,
                        new RoadNodeId(UUID.randomUUID()),
                        7
                ),
                sidewalkSegment(
                        new RoadPoint(0.5, 64.0, 4.5),
                        new RoadPoint(-30.5, 64.0, 13.5),
                        centerId,
                        new RoadNodeId(UUID.randomUUID()),
                        7
                )
        );

        BlockPlacementPlan plan = assertInstanceOf(
                BlockPlanResult.Success.class,
                new StraightRoadIntersectionMaterializer().plan(
                        Level.OVERWORLD,
                        center,
                        incident,
                        new ResourceLocation("minecraft", "gray_concrete"),
                        new ResourceLocation("minecraft", "smooth_stone"),
                        2_000,
                        10_000
                )
        ).plan();

        java.util.Map<BlockPos, PlannedBlock> finalSurface =
                new java.util.HashMap<>();
        StraightRoadSegmentMaterializer segmentMaterializer =
                new StraightRoadSegmentMaterializer();
        for (RoadSegment segment : incident) {
            BlockPlacementPlan segmentPlan = assertInstanceOf(
                    BlockPlanResult.Success.class,
                    segmentMaterializer.plan(
                            Level.OVERWORLD,
                            segment,
                            ConstructionLimits.defaults()
                    )
            ).plan();
            segmentPlan.blocks().forEach(block ->
                    finalSurface.put(block.position(), block));
        }
        plan.blocks().forEach(block ->
                finalSurface.put(block.position(), block));

        for (BlockPos position : List.of(
                new BlockPos(0, 64, -7),
                new BlockPos(1, 64, -7),
                new BlockPos(0, 64, 7),
                new BlockPos(1, 64, 7)
        )) {
            PlannedBlock block = finalSurface.get(position);
            assertTrue(
                    block != null
                            && block.targetState().is(Blocks.SMOOTH_STONE),
                    () -> "junction broke the trunk sidewalk at " + position
            );
        }
    }

    @Test
    void intersectionSurfaceRespondsToApproachCurvature() {
        BlockPlacementPlan southBending = curvedTrunkPlan(12.5);
        BlockPlacementPlan northBending = curvedTrunkPlan(-11.5);

        assertNotEquals(
                southBending.blocks(),
                northBending.blocks(),
                "mirrored curves with identical endpoint tangents must "
                        + "produce different intersection edges"
        );
    }

    @Test
    void nearStraightCurvedTrunkDoesNotCreateDistantFilletWings() {
        BlockPlacementPlan plan = curvedTrunkPlan(0.5);

        assertTrue(plan.blocks().stream().noneMatch(block -> {
            BlockPos position = block.position();
            return Math.max(
                    Math.abs(position.getX()),
                    Math.abs(position.getZ())
            ) >= 10 && Math.min(
                    Math.abs(position.getX()),
                    Math.abs(position.getZ())
            ) >= 4;
        }), () -> "distant fillet wing outside the bounded corner patch: "
                + plan.blocks().stream()
                .filter(block -> {
                    BlockPos position = block.position();
                    return Math.max(
                            Math.abs(position.getX()),
                            Math.abs(position.getZ())
                    ) >= 10 && Math.min(
                            Math.abs(position.getX()),
                            Math.abs(position.getZ())
                    ) >= 4;
                })
                .map(PlannedBlock::position)
                .toList());
    }

    @Test
    void shortApproachTJunctionRemainsBounded() {
        RoadNodeId centerId = new RoadNodeId(UUID.randomUUID());
        RoadNode center = new RoadNode(
                centerId,
                new RoadPoint(0.5, 64.0, 0.5)
        );
        List<RoadSegment> incident = List.of(
                sidewalkSegment(
                        new QuadraticBezierGeometry(
                                new RoadPoint(-12.5, 64.0, 0.5),
                                new RoadPoint(-3.5, 64.0, 0.5),
                                center.position()
                        ),
                        new RoadNodeId(UUID.randomUUID()),
                        centerId,
                        7
                ),
                sidewalkSegment(
                        new QuadraticBezierGeometry(
                                center.position(),
                                new RoadPoint(3.5, 64.0, 0.5),
                                new RoadPoint(12.5, 64.0, 0.5)
                        ),
                        centerId,
                        new RoadNodeId(UUID.randomUUID()),
                        7
                ),
                sidewalkSegment(
                        center.position(),
                        new RoadPoint(0.5, 64.0, -5.5),
                        centerId,
                        new RoadNodeId(UUID.randomUUID()),
                        7
                )
        );

        BlockPlacementPlan plan = assertInstanceOf(
                BlockPlanResult.Success.class,
                new StraightRoadIntersectionMaterializer().plan(
                        Level.OVERWORLD,
                        center,
                        incident,
                        new ResourceLocation("minecraft", "gray_concrete"),
                        new ResourceLocation("minecraft", "smooth_stone"),
                        2_000,
                        20_000
                )
        ).plan();

        assertTrue(plan.blocks().stream().noneMatch(block -> {
            BlockPos position = block.position();
            return position.getZ() >= 5 || position.getZ() <= -7;
        }), () -> "distant fillet wing beyond the short branch: "
                + plan.blocks().stream()
                .filter(block -> {
                    BlockPos position = block.position();
                    return position.getZ() >= 5 || position.getZ() <= -7;
                })
                .map(PlannedBlock::position)
                .toList());
    }

    @Test
    void acuteShortCrossingsDoNotGrowCornersPastTheirRoadEnds() {
        for (boolean curved : List.of(false, true)) {
            for (double degrees : List.of(15.0, 20.0, 30.0, 45.0)) {
                for (double length : List.of(6.0, 10.0, 16.0)) {
                    RoadNodeId centerId = new RoadNodeId(UUID.randomUUID());
                    RoadPoint origin = new RoadPoint(0.5, 64, 0.5);
                    RoadNode center = new RoadNode(centerId, origin);
                    List<RoadSegment> arms = new java.util.ArrayList<>();
                    for (double direction : List.of(17.0, 17.0 + degrees,
                            197.0, 197.0 + degrees)) {
                        double radians = Math.toRadians(direction);
                        RoadPoint end = new RoadPoint(origin.x() + length * Math.cos(radians),
                                64, origin.z() + length * Math.sin(radians));
                        boolean incoming = arms.size() < 2;
                        RoadPoint startPoint = incoming ? end : origin;
                        RoadPoint endPoint = incoming ? origin : end;
                        ReferenceGeometry geometry = curved
                                ? new QuadraticBezierGeometry(startPoint,
                                        new RoadPoint((origin.x() + end.x()) / 2, 64,
                                                (origin.z() + end.z()) / 2), endPoint)
                                : new StraightGeometry(startPoint, endPoint);
                        RoadNodeId outerId = new RoadNodeId(UUID.randomUUID());
                        arms.add(sidewalkSegment(geometry, incoming ? outerId : centerId,
                                incoming ? centerId : outerId, 7));
                    }
                    BlockPlacementPlan plan = assertInstanceOf(BlockPlanResult.Success.class,
                            new StraightRoadIntersectionMaterializer().plan(Level.OVERWORLD,
                                    center, arms,
                                    new ResourceLocation("minecraft", "gray_concrete"),
                                    new ResourceLocation("minecraft", "smooth_stone"),
                                    10_000, 100_000)).plan();
                    double limit = Math.hypot(length, 3.5) + Math.sqrt(0.5);
                    assertTrue(plan.positions().stream().allMatch(pos ->
                                    Math.hypot(pos.getX(), pos.getZ()) <= limit),
                            "Corner escaped finite arms: angle=" + degrees + ", length=" + length
                                    + ", curved=" + curved + ", farthest="
                                    + plan.positions().stream().mapToDouble(pos ->
                                            Math.hypot(pos.getX(), pos.getZ())).max().orElseThrow());
                    assertConnectorPathsArePaved(center, arms, plan);
                    var connected = new java.util.HashSet<BlockPos>();
                    for (RoadSegment arm : arms) {
                        connected.addAll(assertInstanceOf(BlockPlanResult.Success.class,
                                new StraightRoadSegmentMaterializer().plan(Level.OVERWORLD,
                                        arm, ConstructionLimits.defaults())).plan().positions());
                    }
                    var remaining = new java.util.HashSet<>(plan.positions());
                    remaining.removeAll(connected);
                    var queue = new java.util.ArrayDeque<>(connected);
                    while (!queue.isEmpty()) {
                        BlockPos position = queue.removeFirst();
                        for (int dx = -1; dx <= 1; dx++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                BlockPos neighbor = position.offset(dx, 0, dz);
                                if (remaining.remove(neighbor)) queue.addLast(neighbor);
                            }
                        }
                    }
                    assertTrue(remaining.isEmpty(),
                            "Detached junction geometry: " + remaining);
                }
            }
        }
    }

    private static RoadPreset styledPreset() {
        return new RoadPreset(
                RoadPreset.CURRENT_SCHEMA_VERSION,
                1,
                7.0,
                new ResourceLocation("minecraft", "gray_concrete"),
                List.of(new LaneComponent(
                        LaneType.VEHICLE,
                        3.0,
                        0.0,
                        LaneTravelDirection.FORWARD
                )),
                List.of(
                        new RoadSurfaceBand(
                                RoadSurfaceRole.SIDEWALK,
                                1,
                                new ResourceLocation("minecraft", "smooth_stone")
                        ),
                        new RoadSurfaceBand(
                                RoadSurfaceRole.PAVEMENT,
                                2,
                                new ResourceLocation("minecraft", "gray_concrete")
                        ),
                        new RoadSurfaceBand(
                                RoadSurfaceRole.MARKING,
                                1,
                                new ResourceLocation("minecraft", "yellow_concrete")
                        ),
                        new RoadSurfaceBand(
                                RoadSurfaceRole.PAVEMENT,
                                2,
                                new ResourceLocation("minecraft", "gray_concrete")
                        ),
                        new RoadSurfaceBand(
                                RoadSurfaceRole.SIDEWALK,
                                1,
                                new ResourceLocation("minecraft", "smooth_stone")
                        )
                )
        );
    }

    private static LaneConnector firstTurnConnector(
            RoadNode node,
            List<RoadSegment> incident
    ) {
        return new JunctionConnectorPlanner().plan(
                node.id(),
                incident,
                List.of(),
                () -> new LaneConnectorId(UUID.randomUUID())
        ).stream()
                .filter(connector ->
                        connector.movement().type() == LaneMovementType.LEFT
                                || connector.movement().type()
                                == LaneMovementType.RIGHT)
                .findFirst()
                .orElseThrow();
    }

    private static void assertConnectorPathIsPaved(
            RoadNode node,
            List<RoadSegment> incident,
            LaneConnector connector,
            BlockPlacementPlan plan
    ) {
        var blocks = plan.blocks().stream().collect(Collectors.toMap(
                PlannedBlock::position,
                PlannedBlock::targetState
        ));
        var geometry = connectorGeometry(node, incident, connector);
        for (double distance = 0;
                distance <= geometry.length();
                distance += 0.1) {
            var point = geometry.sample(distance).position();
            BlockPos position = BlockPos.containing(
                    point.x(),
                    node.position().y(),
                    point.z()
            );
            assertTrue(
                    blocks.containsKey(position)
                            && blocks.get(position).is(Blocks.GRAY_CONCRETE),
                    () -> "connector crosses unpaved cell " + position
            );
        }
    }

    private static com.cookiecraftmods.roadbuilder.geometry.ReferenceGeometry
            connectorGeometry(
            RoadNode node,
            List<RoadSegment> incident,
            LaneConnector connector
    ) {
        var solved = assertInstanceOf(
                com.cookiecraftmods.roadbuilder.network.JunctionSolveResult.Success.class,
                new com.cookiecraftmods.roadbuilder.network.JunctionSolver().solve(
                        node,
                        incident,
                        com.cookiecraftmods.roadbuilder.network.JunctionDefinition
                                .automatic(node.id())
                )
        );
        var trims = new HashMap<RoadSegmentId, Double>();
        var lanes = new HashMap<RoadLaneId, RoadLane>();
        solved.approaches().forEach(approach -> trims.put(
                approach.segment().id(),
                approach.trimDistance()
        ));
        incident.forEach(segment -> segment.lanes().forEach(lane ->
                lanes.put(lane.id(), lane)));
        return new LaneConnectorGeometryPlanner().plan(
                connector,
                lanes,
                trims.get(lanes.get(connector.fromLaneId()).segmentId()),
                trims.get(lanes.get(connector.toLaneId()).segmentId())
        );
    }

    private static void assertConnectorPathsArePaved(
            RoadNode node, List<RoadSegment> incident, BlockPlacementPlan plan
    ) {
        var solved = assertInstanceOf(
                com.cookiecraftmods.roadbuilder.network.JunctionSolveResult.Success.class,
                new com.cookiecraftmods.roadbuilder.network.JunctionSolver().solve(
                        node, incident,
                        com.cookiecraftmods.roadbuilder.network.JunctionDefinition.automatic(node.id()))
        );
        var trims = new java.util.HashMap<RoadSegmentId, Double>();
        var lanes = new java.util.HashMap<RoadLaneId, com.cookiecraftmods.roadbuilder.network.RoadLane>();
        solved.approaches().forEach(approach -> trims.put(
                approach.segment().id(), approach.trimDistance()));
        incident.forEach(segment -> segment.lanes().forEach(lane -> lanes.put(lane.id(), lane)));
        var connectors = new com.cookiecraftmods.roadbuilder.network.JunctionConnectorPlanner().plan(
                node.id(), incident, List.of(),
                () -> new com.cookiecraftmods.roadbuilder.network.LaneConnectorId(UUID.randomUUID()));
        assertTrue(!connectors.isEmpty());
        var blocks = plan.blocks().stream().collect(java.util.stream.Collectors.toMap(
                PlannedBlock::position, PlannedBlock::targetState));
        for (var connector : connectors) {
            var geometry = new com.cookiecraftmods.roadbuilder.network.LaneConnectorGeometryPlanner().plan(
                    connector, lanes, trims.get(lanes.get(connector.fromLaneId()).segmentId()),
                    trims.get(lanes.get(connector.toLaneId()).segmentId()));
            for (double distance = 0; distance <= geometry.length(); distance += 0.1) {
                var point = geometry.sample(distance).position();
                BlockPos position = BlockPos.containing(point.x(), node.position().y(), point.z());
                assertTrue(blocks.containsKey(position) && blocks.get(position).is(Blocks.GRAY_CONCRETE),
                        () -> "connector crosses unpaved cell " + position);
            }
        }
    }

    private static BlockPlacementPlan curvedTrunkPlan(double endZ) {
        RoadNodeId centerId = new RoadNodeId(UUID.randomUUID());
        RoadNode center = new RoadNode(
                centerId,
                new RoadPoint(0.5, 64.0, 0.5)
        );
        List<RoadSegment> incident = List.of(
                sidewalkSegment(
                        new QuadraticBezierGeometry(
                                new RoadPoint(-12.5, 64.0, endZ),
                                new RoadPoint(-3.5, 64.0, 0.5),
                                center.position()
                        ),
                        new RoadNodeId(UUID.randomUUID()),
                        centerId,
                        7
                ),
                sidewalkSegment(
                        new QuadraticBezierGeometry(
                                center.position(),
                                new RoadPoint(3.5, 64.0, 0.5),
                                new RoadPoint(12.5, 64.0, endZ)
                        ),
                        centerId,
                        new RoadNodeId(UUID.randomUUID()),
                        7
                ),
                sidewalkSegment(
                        center.position(),
                        new RoadPoint(0.5, 64.0, -20.5),
                        centerId,
                        new RoadNodeId(UUID.randomUUID()),
                        7
                )
        );

        BlockPlacementPlan plan = assertInstanceOf(
                BlockPlanResult.Success.class,
                new StraightRoadIntersectionMaterializer().plan(
                        Level.OVERWORLD,
                        center,
                        incident,
                        new ResourceLocation("minecraft", "gray_concrete"),
                        new ResourceLocation("minecraft", "smooth_stone"),
                        2_000,
                        20_000
                )
        ).plan();
        assertConnectorPathsArePaved(center, incident, plan);
        return plan;
    }

    private static RoadSegment segment(
            RoadPoint start,
            RoadPoint end,
            RoadNodeId startId,
            RoadNodeId endId
    ) {
        return segment(start, end, startId, endId, (int) PRESET.surfaceBlockWidth());
    }

    private static RoadSegment segment(
            RoadPoint start,
            RoadPoint end,
            RoadNodeId startId,
            RoadNodeId endId,
            int surfaceWidth
    ) {
        LaneComponent lane = new LaneComponent(
                LaneType.VEHICLE,
                1.0,
                0.0,
                LaneTravelDirection.FORWARD
        );
        return RoadSegment.straight(
                new RoadSegmentId(UUID.randomUUID()),
                new RoadId(UUID.randomUUID()),
                startId,
                endId,
                new StraightGeometry(start, end),
                PRESET_ID,
                PRESET.revision(),
                new RoadPresetSnapshot(
                        surfaceWidth,
                        PRESET.surfaceBlock(),
                        List.of(lane)
                ),
                TrafficHandedness.RHT,
                List.of(new RoadLaneId(UUID.randomUUID()))
        );
    }

    private static RoadSegment sidewalkSegment(
            RoadPoint start,
            RoadPoint end,
            RoadNodeId startId,
            RoadNodeId endId,
            int surfaceWidth
    ) {
        return sidewalkSegment(
                new StraightGeometry(start, end),
                startId,
                endId,
                surfaceWidth
        );
    }

    private static RoadSegment sidewalkSegment(
            ReferenceGeometry geometry,
            RoadNodeId startId,
            RoadNodeId endId,
            int surfaceWidth
    ) {
        LaneComponent lane = new LaneComponent(
                LaneType.VEHICLE,
                1.0,
                0.0,
                LaneTravelDirection.FORWARD
        );
        return RoadSegment.path(
                new RoadSegmentId(UUID.randomUUID()),
                new RoadId(UUID.randomUUID()),
                startId,
                endId,
                geometry,
                PRESET_ID,
                PRESET.revision(),
                new RoadPresetSnapshot(
                        surfaceWidth,
                        new ResourceLocation("minecraft", "gray_concrete"),
                        List.of(lane),
                        List.of(
                                new RoadSurfaceBand(
                                        RoadSurfaceRole.SIDEWALK,
                                        1,
                                        new ResourceLocation(
                                                "minecraft",
                                                "smooth_stone"
                                        )
                                ),
                                new RoadSurfaceBand(
                                        RoadSurfaceRole.PAVEMENT,
                                        surfaceWidth - 2,
                                        new ResourceLocation(
                                                "minecraft",
                                                "gray_concrete"
                                        )
                                ),
                                new RoadSurfaceBand(
                                        RoadSurfaceRole.SIDEWALK,
                                        1,
                                        new ResourceLocation(
                                                "minecraft",
                                                "smooth_stone"
                                        )
                                )
                        )
                ),
                TrafficHandedness.RHT,
                List.of(new RoadLaneId(UUID.randomUUID()))
        );
    }

    private static long incidentCount(
            RoadNode node,
            PreparedRoadNetworkMutation mutation
    ) {
        return mutation.allUpsertedSegments().stream()
                .filter(segment -> segment.startNodeId().equals(node.id())
                        || segment.endNodeId().equals(node.id()))
                .count();
    }
}
