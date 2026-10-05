package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.StraightGeometry;
import com.cookiecraftmods.roadbuilder.network.JunctionDefinition;
import com.cookiecraftmods.roadbuilder.network.JunctionSolveResult;
import com.cookiecraftmods.roadbuilder.network.JunctionSolver;
import com.cookiecraftmods.roadbuilder.network.RoadId;
import com.cookiecraftmods.roadbuilder.network.RoadLaneId;
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
        ResourceLocation gray = new ResourceLocation(
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
                                new ResourceLocation(
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
                        Optional.of(new ResourceLocation(
                                "minecraft",
                                "yellow_concrete"
                        )),
                        Optional.of(new ResourceLocation(
                                "minecraft",
                                "white_concrete"
                        )),
                        Optional.of(new ResourceLocation(
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
                new ResourceLocation("roadbuilder", "marking_test"),
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
