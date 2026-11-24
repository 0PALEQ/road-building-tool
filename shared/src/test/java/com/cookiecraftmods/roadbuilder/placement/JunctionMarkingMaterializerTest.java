package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.StraightGeometry;
import com.cookiecraftmods.roadbuilder.network.JunctionDefinition;
import com.cookiecraftmods.roadbuilder.network.JunctionSolveResult;
import com.cookiecraftmods.roadbuilder.network.JunctionSolver;
import com.cookiecraftmods.roadbuilder.network.RoadId;
import com.cookiecraftmods.roadbuilder.network.RoadLaneId;
import com.cookiecraftmods.roadbuilder.network.RoadLane;
import com.cookiecraftmods.roadbuilder.network.RoadNode;
import com.cookiecraftmods.roadbuilder.network.RoadNodeId;
import com.cookiecraftmods.roadbuilder.network.RoadPresetSnapshot;
import com.cookiecraftmods.roadbuilder.network.RoadSegment;
import com.cookiecraftmods.roadbuilder.network.RoadSegmentId;
import com.cookiecraftmods.roadbuilder.network.SolvedJunctionApproach;
import com.cookiecraftmods.roadbuilder.preset.IntersectionControl;
import com.cookiecraftmods.roadbuilder.preset.LaneComponent;
import com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection;
import com.cookiecraftmods.roadbuilder.preset.LaneType;
import com.cookiecraftmods.roadbuilder.preset.RoadClass;
import com.cookiecraftmods.roadbuilder.preset.RoadClassification;
import com.cookiecraftmods.roadbuilder.preset.RoadIntersectionStyle;
import com.cookiecraftmods.roadbuilder.preset.RoadMarkingPattern;
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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JunctionMarkingMaterializerTest {
    private static final RoadNodeId NODE_ID = id(RoadNodeId::new, 1);
    private static final RoadNode NODE = new RoadNode(
            NODE_ID,
            new RoadPoint(0.5, 64.0, 0.5)
    );

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftBootstrap.bootstrap();
    }

    @Test
    void largeIntersectionUsesPriorityCrosswalkRowsAndDashedYieldLines() {
        RoadId arterial = id(RoadId::new, 10);
        RoadId local = id(RoadId::new, 20);
        RoadClassification high = new RoadClassification(
                RoadClass.ARTERIAL,
                12,
                80.0
        );
        RoadClassification low = new RoadClassification(
                RoadClass.LOCAL,
                2,
                40.0
        );
        RoadSegment west = segment(100, arterial, -30.5, 0.5, high);
        RoadSegment east = segment(200, arterial, 30.5, 0.5, high);
        RoadSegment north = segment(300, local, 0.5, -30.5, low);
        RoadSegment south = segment(400, local, 0.5, 30.5, low);
        List<RoadSegment> incident = List.of(west, east, north, south);
        JunctionDefinition definition = JunctionDefinition.automatic(NODE_ID);

        JunctionSolveResult.Success solved = assertInstanceOf(
                JunctionSolveResult.Success.class,
                new JunctionSolver().solve(NODE, incident, definition)
        );
        assertTrue(solved.approaches().stream()
                .filter(approach -> approach.segment().roadId().equals(arterial))
                .allMatch(approach -> approach.primary()
                        && approach.control() == IntersectionControl.FREE));
        assertTrue(solved.approaches().stream()
                .filter(approach -> approach.segment().roadId().equals(local))
                .allMatch(approach -> !approach.primary()
                        && approach.control() == IntersectionControl.YIELD));

        BlockPlacementPlan markings = assertInstanceOf(
                BlockPlanResult.Success.class,
                new JunctionMarkingMaterializer().plan(
                        Level.OVERWORLD,
                        NODE,
                        incident,
                        definition,
                        10_000
                )
        ).plan();
        for (SolvedJunctionApproach primary : solved.approaches().stream()
                .filter(SolvedJunctionApproach::primary).toList()) {
            for (int distance = 2;
                    distance <= (int) Math.floor(primary.trimDistance());
                    distance++) {
                BlockPos center = position(primary, distance, 0);
                assertFalse(markings.blocks().stream().anyMatch(block ->
                        block.position().equals(center)),
                        "Priority-road center lines must stop outside the junction");
            }
        }
        SolvedJunctionApproach approach = solved.approaches().stream()
                .filter(value -> value.segment().id().equals(north.id()))
                .findFirst()
                .orElseThrow();
        int crosswalkDistance = (int) Math.ceil(approach.trimDistance()) + 1;
        for (int offset = 0; offset < 5; offset++) {
            BlockPos center = position(approach, crosswalkDistance + offset, 0);
            assertTrue(markings.blocks().stream().anyMatch(block ->
                    block.position().equals(center)
                            && block.targetState().is(Blocks.WHITE_CONCRETE)));
            assertFalse(markings.blocks().stream().anyMatch(block ->
                    block.position().equals(center)
                            && block.targetState().is(Blocks.YELLOW_CONCRETE)));
        }
        BlockPos clearedGap = position(
                approach,
                crosswalkDistance,
                1
        );
        assertTrue(markings.blocks().stream().anyMatch(block ->
                block.position().equals(clearedGap)
                        && block.targetState().is(Blocks.GRAY_CONCRETE)));
        for (int distance = crosswalkDistance;
                distance < crosswalkDistance + 5;
                distance++) {
            for (int lateral = -4; lateral <= 4; lateral++) {
                BlockPos position = position(approach, distance, lateral);
                assertFalse(markings.blocks().stream().anyMatch(block ->
                        block.position().equals(position)
                                && block.targetState().is(
                                        Blocks.YELLOW_CONCRETE
                                )));
            }
        }
        int yieldDistance = crosswalkDistance + 6;
        int halfWidth = (int) Math.floor(
                approach.drivableHalfWidth() - 0.5
        );
        long yieldBlocks = markings.blocks().stream()
                .filter(block -> block.targetState().is(Blocks.YELLOW_CONCRETE))
                .filter(block -> {
                    for (int lateral = -halfWidth;
                            lateral <= halfWidth;
                            lateral++) {
                        if (block.position().equals(position(
                                approach,
                                yieldDistance,
                                lateral
                        ))) {
                            return true;
                        }
                    }
                    return false;
                })
                .count();
        assertEquals(5, yieldBlocks);
    }

    @Test
    void markingsAreAnchoredAtAnOffsetRoadPort() {
        RoadClassification classification = RoadClassification.DEFAULT;
        RoadSegment west = segment(
                500,
                id(RoadId::new, 50),
                -30.5,
                0.5,
                classification
        );
        RoadSegment east = segment(
                510,
                id(RoadId::new, 51),
                30.5,
                0.5,
                classification
        );
        RoadPoint offsetPort = new RoadPoint(5.5, 64.0, 0.5);
        RoadSegment north = segment(
                520,
                id(RoadId::new, 52),
                offsetPort,
                new RoadPoint(5.5, 64.0, -30.5),
                classification
        );
        RoadSegment south = segment(
                530,
                id(RoadId::new, 53),
                0.5,
                30.5,
                classification
        );
        List<RoadSegment> incident = List.of(west, east, north, south);
        JunctionDefinition definition = JunctionDefinition.automatic(NODE_ID);
        JunctionSolveResult.Success solved = assertInstanceOf(
                JunctionSolveResult.Success.class,
                new JunctionSolver().solve(NODE, incident, definition)
        );
        SolvedJunctionApproach northApproach = solved.approaches().stream()
                .filter(approach -> approach.segment().id().equals(north.id()))
                .findFirst()
                .orElseThrow();
        assertEquals(offsetPort, northApproach.boundaryPosition());

        BlockPlacementPlan markings = assertInstanceOf(
                BlockPlanResult.Success.class,
                new JunctionMarkingMaterializer().plan(
                        Level.OVERWORLD,
                        NODE,
                        incident,
                        definition,
                        10_000
                )
        ).plan();
        int crosswalkDistance =
                (int) Math.ceil(northApproach.trimDistance()) + 1;
        BlockPos expected = position(
                northApproach,
                crosswalkDistance,
                0
        );
        assertTrue(markings.blocks().stream().anyMatch(block ->
                block.position().equals(expected)
                        && block.targetState().is(Blocks.WHITE_CONCRETE)));
        RoadPoint oldCenter = NODE.position()
                .add(northApproach.outwardTangent().scale(crosswalkDistance));
        BlockPos incorrectlyCentered = BlockPos.containing(
                oldCenter.x(),
                oldCenter.y(),
                oldCenter.z()
        );
        assertFalse(markings.blocks().stream().anyMatch(block ->
                block.position().equals(incorrectlyCentered)
                        && block.targetState().is(Blocks.WHITE_CONCRETE)));
    }

    @Test
    void explicitCrosswalkCanBeMaterializedAtATerminalNode() {
        RoadSegment arm = segment(
                600,
                id(RoadId::new, 60),
                24.5,
                0.5,
                RoadClassification.DEFAULT
        );
        BlockPlacementPlan markings = assertInstanceOf(
                BlockPlanResult.Success.class,
                new JunctionMarkingMaterializer().planTerminalCrosswalk(
                        Level.OVERWORLD,
                        NODE,
                        arm,
                        10_000
                )
        ).plan();

        assertTrue(markings.blocks().stream().anyMatch(block ->
                block.targetState().is(Blocks.WHITE_CONCRETE)));
    }

    @Test
    void terminalAndJunctionCrosswalksUseTheRoadPalettesWithLegacyDefaults() {
        var cobble = MinecraftIds.id("minecraft", "cobblestone");
        var orange = MinecraftIds.id("minecraft", "orange_concrete");
        var gray = MinecraftIds.id("minecraft", "gray_concrete");
        var white = MinecraftIds.id("minecraft", "white_concrete");
        var pavement = new RoadSurfaceBand(RoadSurfaceRole.PAVEMENT, 4, cobble, RoadMarkingPattern.CONTINUOUS,
                List.of(cobble, MinecraftIds.id("minecraft", "andesite")));
        var marking = new RoadSurfaceBand(RoadSurfaceRole.MARKING, 1, orange, RoadMarkingPattern.CONTINUOUS,
                List.of(orange, MinecraftIds.id("minecraft", "yellow_concrete")));
        var snapshot = new RoadPresetSnapshot(9, cobble,
                List.of(new LaneComponent(LaneType.VEHICLE, 9, 0, LaneTravelDirection.FORWARD)),
                List.of(pavement, marking, pavement), RoadClassification.DEFAULT,
                new RoadIntersectionStyle(gray, Optional.of(white), Optional.of(white), Optional.empty(),
                        true, IntersectionControl.FREE));
        var ends = List.of(new RoadPoint(30.5, 64, 0.5), new RoadPoint(-30.5, 64, 0.5),
                new RoadPoint(0.5, 64, 30.5), new RoadPoint(0.5, 64, -30.5));
        var approaches = new java.util.ArrayList<RoadSegment>();
        for (int index = 0; index < ends.size(); index++) {
            long seed = 1000 + index * 10;
            approaches.add(RoadSegment.straight(id(RoadSegmentId::new, seed), id(RoadId::new, seed), NODE_ID,
                    id(RoadNodeId::new, seed), new StraightGeometry(NODE.position(), ends.get(index)),
                    MinecraftIds.id("roadbuilder", "textured_crosswalk"), 1, snapshot, TrafficHandedness.RHT,
                    List.of(id(RoadLaneId::new, seed))));
        }

        var materializer = new JunctionMarkingMaterializer();
        var terminal = assertInstanceOf(BlockPlanResult.Success.class, materializer.planTerminalCrosswalk(
                Level.OVERWORLD, NODE, approaches.get(0), 10_000)).plan();
        var junction = assertInstanceOf(BlockPlanResult.Success.class, materializer.plan(
                Level.OVERWORLD, NODE, approaches, JunctionDefinition.automatic(NODE_ID), 10_000)).plan();
        var roadBlocks = Set.of(Blocks.COBBLESTONE, Blocks.ANDESITE, Blocks.ORANGE_CONCRETE, Blocks.YELLOW_CONCRETE);
        for (BlockPlacementPlan plan : List.of(terminal, junction)) {
            assertEquals(roadBlocks, plan.blocks().stream()
                    .map(block -> block.targetState().getBlock()).collect(Collectors.toSet()));
        }
        assertTrue(terminal.blocks().stream().filter(block -> block.position().getZ() % 2 != 0)
                .allMatch(block -> block.targetState().is(Blocks.COBBLESTONE) || block.targetState().is(Blocks.ANDESITE)));
    }

    @Test
    void highwayTerminalRejectsExplicitCrosswalks() {
        RoadSegment highway = segment(610, id(RoadId::new, 61), 24.5, 0.5,
                new RoadClassification(com.cookiecraftmods.roadbuilder.preset.RoadClass.HIGHWAY, 15, 120));
        assertInstanceOf(BlockPlanResult.Failure.class,
                new JunctionMarkingMaterializer().planTerminalCrosswalk(
                        Level.OVERWORLD, NODE, highway, 10_000));
    }

    @Test
    void curvedCrosswalksFollowTheRoadElevationAtBothEnds() {
        var geometry = new com.cookiecraftmods.roadbuilder.geometry.QuadraticBezierGeometry(
                new RoadPoint(0.5, 64, 0.5),
                new RoadPoint(0.5, 70, 8.5),
                new RoadPoint(18.5, 76, 8.5));
        RoadSegment template = segment(620, id(RoadId::new, 62), 24.5, 0.5,
                RoadClassification.DEFAULT);
        RoadSegment curve = RoadSegment.path(template.id(), template.roadId(),
                template.startNodeId(), template.endNodeId(), geometry,
                template.presetId(), template.presetRevision(), template.presetSnapshot(),
                TrafficHandedness.RHT, template.lanes().stream().map(RoadLane::id).toList());
        for (boolean atStart : List.of(true, false)) {
            RoadNode node = new RoadNode(atStart ? curve.startNodeId() : curve.endNodeId(),
                    atStart ? geometry.start() : geometry.end());
            BlockPlacementPlan plan = assertInstanceOf(BlockPlanResult.Success.class,
                    new JunctionMarkingMaterializer().planTerminalCrosswalk(
                            Level.OVERWORLD, node, curve, 10_000)).plan();
            assertTrue(plan.blocks().stream().noneMatch(block ->
                    block.position().getY() == (int) node.position().y()),
                    "A sloping curve must not be painted on the endpoint's horizontal plane");
            for (int distance = 2; distance <= 4; distance++) {
                RoadPoint center = geometry.sample(atStart ? distance
                        : geometry.length() - distance).position();
                BlockPos expected = BlockPos.containing(center.x(),
                        Math.floor(center.y() + 0.5), center.z());
                assertTrue(plan.blocks().stream().anyMatch(block -> block.position().equals(expected)),
                        "Crosswalk must cover the actual curved centerline at " + expected);
            }
        }
    }

    @Test
    void junctionCrosswalkUsesTheCurveBeyondTheTrimInsteadOfTheEndpointTangent() {
        var geometry = new com.cookiecraftmods.roadbuilder.geometry.QuadraticBezierGeometry(
                new RoadPoint(0.5, 64, 0.5),
                new RoadPoint(0.5, 70, 8.5),
                new RoadPoint(18.5, 76, 8.5));
        RoadSegment template = segment(630, id(RoadId::new, 63), 24.5, 0.5,
                RoadClassification.DEFAULT);
        RoadSegment curve = RoadSegment.path(template.id(), template.roadId(),
                template.startNodeId(), template.endNodeId(), geometry,
                template.presetId(), template.presetRevision(), template.presetSnapshot(),
                TrafficHandedness.RHT, template.lanes().stream().map(RoadLane::id).toList());
        List<RoadSegment> arms = List.of(curve,
                segment(640, id(RoadId::new, 64), -30.5, 0.5, RoadClassification.DEFAULT),
                segment(650, id(RoadId::new, 65), 0.5, -30.5, RoadClassification.DEFAULT));
        JunctionDefinition definition = JunctionDefinition.automatic(NODE_ID);
        var solved = assertInstanceOf(JunctionSolveResult.Success.class,
                new JunctionSolver().solve(NODE, arms, definition));
        SolvedJunctionApproach approach = solved.approaches().stream()
                .filter(value -> value.segment().id().equals(curve.id())).findFirst().orElseThrow();
        assertTrue(approach.crosswalk());
        int start = (int) Math.ceil(approach.trimDistance()) + 1;
        BlockPlacementPlan plan = assertInstanceOf(BlockPlanResult.Success.class,
                new JunctionMarkingMaterializer().plan(
                        Level.OVERWORLD, NODE, arms, definition, 10_000)).plan();
        var elevation = new JunctionSurfaceElevation(NODE, arms);
        for (int distance = start; distance <= start + 4; distance++) {
            RoadPoint center = geometry.sample(distance).position();
            BlockPos expected = BlockPos.containing(center.x(),
                    Math.floor(elevation.height(Math.floor(center.x()) + 0.5,
                            Math.floor(center.z()) + 0.5) + 0.5), center.z());
            assertTrue(plan.blocks().stream().anyMatch(block -> block.position().equals(expected)),
                    "Junction crossing must follow its curved arm at " + expected);
        }
    }

    private static BlockPos position(
            SolvedJunctionApproach approach,
            int distance,
            int lateral
    ) {
        RoadPoint point = approach.boundaryPosition()
                .add(approach.outwardTangent().scale(distance))
                .add(approach.left().scale(lateral));
        return BlockPos.containing(point.x(), point.y(), point.z());
    }

    private static RoadSegment segment(
            long seed,
            RoadId roadId,
            double endX,
            double endZ,
            RoadClassification classification
    ) {
        return segment(
                seed,
                roadId,
                NODE.position(),
                new RoadPoint(endX, 64.0, endZ),
                classification
        );
    }

    private static RoadSegment segment(
            long seed,
            RoadId roadId,
            RoadPoint start,
            RoadPoint end,
            RoadClassification classification
    ) {
        ResourceLocation gray = MinecraftIds.id(
                "minecraft",
                "gray_concrete"
        );
        RoadPresetSnapshot snapshot = new RoadPresetSnapshot(
                9,
                gray,
                List.of(new LaneComponent(
                        LaneType.VEHICLE,
                        9.0,
                        0.0,
                        LaneTravelDirection.FORWARD
                )),
                List.of(
                        new RoadSurfaceBand(
                                RoadSurfaceRole.PAVEMENT,
                                4,
                                gray
                        ),
                        new RoadSurfaceBand(
                                RoadSurfaceRole.MARKING,
                                1,
                                MinecraftIds.id(
                                        "minecraft",
                                        "yellow_concrete"
                                )
                        ),
                        new RoadSurfaceBand(
                                RoadSurfaceRole.PAVEMENT,
                                4,
                                gray
                        )
                ),
                classification,
                new RoadIntersectionStyle(
                        gray,
                        Optional.of(MinecraftIds.id(
                                "minecraft",
                                "yellow_concrete"
                        )),
                        Optional.of(MinecraftIds.id(
                                "minecraft",
                                "white_concrete"
                        )),
                        Optional.of(MinecraftIds.id(
                                "minecraft",
                                "yellow_concrete"
                        )),
                        true,
                        IntersectionControl.FREE
                )
        );
        return RoadSegment.straight(
                id(RoadSegmentId::new, seed),
                roadId,
                NODE_ID,
                id(RoadNodeId::new, seed + 1),
                new StraightGeometry(start, end),
                MinecraftIds.id("roadbuilder", "marking_test"),
                1,
                snapshot,
                TrafficHandedness.RHT,
                List.of(id(RoadLaneId::new, seed + 2))
        );
    }

    private static <T> T id(
            java.util.function.Function<UUID, T> factory,
            long seed
    ) {
        return factory.apply(new UUID(0, seed));
    }
}
