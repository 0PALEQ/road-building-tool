package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.StraightGeometry;
import com.cookiecraftmods.roadbuilder.preset.RoadMarkingPattern;
import com.cookiecraftmods.roadbuilder.test.MinecraftBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StraightRoadPlannerTest {
    private static final int DEFAULT_CANDIDATE_LIMIT = 10_000;

    private final StraightRoadPlanner planner = new StraightRoadPlanner();

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftBootstrap.bootstrap();
    }

    @Test
    void rasterizesAThreeBlockWideHorizontalSurface() {
        BlockPlacementPlan plan = successfulPlan(new StraightGeometry(
                new RoadPoint(0.5, 64.0, 0.5),
                new RoadPoint(4.5, 64.0, 0.5)
        ), surface(3), 100, DEFAULT_CANDIDATE_LIMIT);

        assertEquals(15, plan.affectedBlockCount());
        assertEquals(new ConstructionBounds(new BlockPos(0, 64, -1), new BlockPos(4, 64, 1)), plan.bounds());
        assertTrue(plan.blocks().stream().allMatch(block -> block.targetState().is(Blocks.STONE)));
    }

    @Test
    void reversingTheReferenceDirectionProducesTheSameSurfaceTargets() {
        RoadPoint start = new RoadPoint(0.5, 64.0, 0.5);
        RoadPoint end = new RoadPoint(4.5, 64.0, 0.5);

        BlockPlacementPlan forward = successfulPlan(
                new StraightGeometry(start, end), surface(3), 100, DEFAULT_CANDIDATE_LIMIT);
        BlockPlacementPlan backward = successfulPlan(
                new StraightGeometry(end, start), surface(3), 100, DEFAULT_CANDIDATE_LIMIT);

        assertEquals(forward.blocks(), backward.blocks());
    }

    @Test
    void rasterizesASeparateCustomBlockForEveryCrossSectionColumn() {
        ResolvedRoadSurface styled = new ResolvedRoadSurface(List.of(
                Blocks.SMOOTH_STONE.defaultBlockState(),
                Blocks.GRAY_CONCRETE.defaultBlockState(),
                Blocks.YELLOW_CONCRETE.defaultBlockState()
        ));
        BlockPlacementPlan plan = successfulPlan(new StraightGeometry(
                new RoadPoint(0.5, 64.0, 0.5),
                new RoadPoint(2.5, 64.0, 0.5)
        ), styled, 100, DEFAULT_CANDIDATE_LIMIT);

        assertTrue(plan.blocks().stream()
                .filter(block -> block.position().getZ() == -1)
                .allMatch(block -> block.targetState().is(Blocks.SMOOTH_STONE)));
        assertTrue(plan.blocks().stream()
                .filter(block -> block.position().getZ() == 0)
                .allMatch(block -> block.targetState().is(Blocks.GRAY_CONCRETE)));
        assertTrue(plan.blocks().stream()
                .filter(block -> block.position().getZ() == 1)
                .allMatch(block -> block.targetState().is(Blocks.YELLOW_CONCRETE)));
    }

    @Test
    void alternatesDashedMarkingsOverTheUnderlyingRoadWithoutHoles() {
        ResolvedRoadSurface dashed = new ResolvedRoadSurface(
                List.of(Blocks.WHITE_CONCRETE.defaultBlockState()),
                List.of(Blocks.GRAY_CONCRETE.defaultBlockState()),
                List.of(RoadMarkingPattern.DASHED)
        );
        BlockPlacementPlan plan = successfulPlan(new StraightGeometry(
                new RoadPoint(0.5, 64.0, 0.5),
                new RoadPoint(8.5, 64.0, 0.5)
        ), dashed, 100, DEFAULT_CANDIDATE_LIMIT);

        assertEquals(9, plan.affectedBlockCount());
        for (PlannedBlock block : plan.blocks()) {
            int x = block.position().getX();
            boolean marking = x <= 2 || x >= 6;
            assertTrue(block.targetState().is(
                    marking ? Blocks.WHITE_CONCRETE : Blocks.GRAY_CONCRETE
            ));
        }
    }

    @Test
    void rasterizesAReversibleTerracedGradeBetweenDifferentElevations() {
        RoadPoint low = new RoadPoint(0.5, 64.0, 0.5);
        RoadPoint high = new RoadPoint(4.5, 66.0, 0.5);

        BlockPlacementPlan ascending = successfulPlan(
                new StraightGeometry(low, high),
                surface(1),
                100,
                DEFAULT_CANDIDATE_LIMIT
        );
        BlockPlacementPlan descending = successfulPlan(
                new StraightGeometry(high, low),
                surface(1),
                100,
                DEFAULT_CANDIDATE_LIMIT
        );

        assertEquals(List.of(
                new BlockPos(0, 64, 0),
                new BlockPos(1, 65, 0),
                new BlockPos(2, 65, 0),
                new BlockPos(3, 66, 0),
                new BlockPos(4, 66, 0)
        ), ascending.blocks().stream().map(PlannedBlock::position).toList());
        assertEquals(ascending.blocks(), descending.blocks());
        assertEquals(
                new ConstructionBounds(
                        new BlockPos(0, 64, 0),
                        new BlockPos(4, 66, 0)
                ),
                ascending.bounds()
        );
    }

    @Test
    void usesSlabsForReversibleHalfBlockSlopeSteps() {
        ResolvedRoadSurface slabSurface = new ResolvedRoadSurface(
                List.of(Blocks.STONE_SLAB.defaultBlockState())
        );
        RoadPoint low = new RoadPoint(0.5, 64.0, 0.5);
        RoadPoint high = new RoadPoint(4.5, 66.0, 0.5);

        BlockPlacementPlan ascending = successfulPlan(
                new StraightGeometry(low, high),
                slabSurface,
                100,
                DEFAULT_CANDIDATE_LIMIT
        );
        BlockPlacementPlan descending = successfulPlan(
                new StraightGeometry(high, low),
                slabSurface,
                100,
                DEFAULT_CANDIDATE_LIMIT
        );

        assertEquals(ascending.blocks(), descending.blocks());
        assertEquals(List.of(
                new BlockPos(0, 64, 0),
                new BlockPos(1, 65, 0),
                new BlockPos(2, 65, 0),
                new BlockPos(3, 66, 0),
                new BlockPos(4, 66, 0)
        ), ascending.blocks().stream().map(PlannedBlock::position).toList());
        assertEquals(List.of(
                SlabType.DOUBLE,
                SlabType.BOTTOM,
                SlabType.DOUBLE,
                SlabType.BOTTOM,
                SlabType.DOUBLE
        ), ascending.blocks().stream()
                .map(block -> block.targetState().getValue(SlabBlock.TYPE))
                .toList());
    }

    @Test
    void automaticallyUsesTheMatchingSlabForCobblestoneHalfSteps() {
        ResolvedRoadSurface cobblestone = new ResolvedRoadSurface(
                List.of(Blocks.COBBLESTONE.defaultBlockState())
        );
        BlockPlacementPlan plan = successfulPlan(
                new StraightGeometry(
                        new RoadPoint(0.5, 64.0, 0.5),
                        new RoadPoint(4.5, 66.0, 0.5)
                ),
                cobblestone,
                100,
                DEFAULT_CANDIDATE_LIMIT
        );

        assertEquals(List.of(
                Blocks.COBBLESTONE,
                Blocks.COBBLESTONE_SLAB,
                Blocks.COBBLESTONE,
                Blocks.COBBLESTONE_SLAB,
                Blocks.COBBLESTONE
        ), plan.blocks().stream()
                .map(block -> block.targetState().getBlock())
                .toList());
        assertTrue(plan.blocks().get(1).targetState()
                .hasProperty(SlabBlock.TYPE));
        assertEquals(
                SlabType.BOTTOM,
                plan.blocks().get(1).targetState().getValue(SlabBlock.TYPE)
        );
    }

    @Test
    void distributesBlockSetVariantsDeterministically() {
        List<net.minecraft.world.level.block.state.BlockState> choices = List.of(
                Blocks.STONE.defaultBlockState(),
                Blocks.ANDESITE.defaultBlockState(),
                Blocks.TUFF.defaultBlockState()
        );
        ResolvedRoadSurface textured = new ResolvedRoadSurface(
                List.of(choices.get(0)),
                List.of(choices.get(0)),
                List.of(RoadMarkingPattern.CONTINUOUS),
                List.of(choices),
                List.of(choices)
        );
        StraightGeometry geometry = new StraightGeometry(
                new RoadPoint(0.5, 64.0, 0.5),
                new RoadPoint(30.5, 64.0, 0.5)
        );

        BlockPlacementPlan first = successfulPlan(
                geometry, textured, 100, DEFAULT_CANDIDATE_LIMIT
        );
        BlockPlacementPlan second = successfulPlan(
                geometry, textured, 100, DEFAULT_CANDIDATE_LIMIT
        );

        assertEquals(first.blocks(), second.blocks());
        assertTrue(first.blocks().stream()
                .map(block -> block.targetState().getBlock())
                .distinct()
                .count() > 1);
    }

    @Test
    void keepsDiagonalGradedSurfaceConnectedWithoutVerticalGaps() {
        BlockPlacementPlan plan = successfulPlan(
                new StraightGeometry(
                        new RoadPoint(0.5, 64.0, 0.5),
                        new RoadPoint(6.5, 67.0, 6.5)
                ),
                surface(3),
                500,
                DEFAULT_CANDIDATE_LIMIT
        );

        for (PlannedBlock block : plan.blocks()) {
            boolean connected = plan.blocks().stream().anyMatch(other -> {
                int horizontalDistance = Math.abs(
                        other.position().getX() - block.position().getX()
                ) + Math.abs(other.position().getZ() - block.position().getZ());
                int verticalDistance = Math.abs(
                        other.position().getY() - block.position().getY()
                );
                return horizontalDistance == 1 && verticalDistance <= 1;
            });
            assertTrue(connected, "graded surface block should have a walkable neighbor");
        }
    }

    @Test
    void supercoverProducesConnectedGoldenCellsAtDiagonalCorners() {
        BlockPlacementPlan plan = successfulPlan(new StraightGeometry(
                new RoadPoint(0.5, 64.0, 0.5),
                new RoadPoint(2.5, 64.0, 2.5)
        ), surface(1), 100, DEFAULT_CANDIDATE_LIMIT);

        assertEquals(List.of(
                new BlockPos(0, 64, 0),
                new BlockPos(0, 64, 1),
                new BlockPos(1, 64, 0),
                new BlockPos(1, 64, 1),
                new BlockPos(1, 64, 2),
                new BlockPos(2, 64, 1),
                new BlockPos(2, 64, 2)
        ), plan.blocks().stream().map(PlannedBlock::position).toList());
    }

    @Test
    void traversesShallowAndNegativeCoordinatesWithoutSkippedCells() {
        BlockPlacementPlan shallow = successfulPlan(new StraightGeometry(
                new RoadPoint(0.2, 64.0, 0.2),
                new RoadPoint(4.8, 64.0, 1.2)
        ), surface(1), 100, DEFAULT_CANDIDATE_LIMIT);
        BlockPlacementPlan negative = successfulPlan(new StraightGeometry(
                new RoadPoint(-0.2, 64.0, -0.2),
                new RoadPoint(-4.8, 64.0, -1.2)
        ), surface(1), 100, DEFAULT_CANDIDATE_LIMIT);

        assertEquals(List.of(
                new BlockPos(0, 64, 0),
                new BlockPos(1, 64, 0),
                new BlockPos(2, 64, 0),
                new BlockPos(3, 64, 0),
                new BlockPos(3, 64, 1),
                new BlockPos(4, 64, 1)
        ), shallow.blocks().stream().map(PlannedBlock::position).toList());
        assertEquals(new ConstructionBounds(
                new BlockPos(-5, 64, -2),
                new BlockPos(-1, 64, -1)
        ), negative.bounds());
    }

    @Test
    void returnsStableFailuresBeforeUnboundedWorkOrAllocation() {
        StraightGeometry ordinary = new StraightGeometry(
                new RoadPoint(0.5, 64.0, 0.5),
                new RoadPoint(4.5, 64.0, 0.5)
        );
        StraightGeometry tooSteep = new StraightGeometry(
                new RoadPoint(0.5, 64.0, 0.5),
                new RoadPoint(4.5, 67.0, 0.5)
        );
        StraightGeometry enormous = new StraightGeometry(
                new RoadPoint(0.5, 64.0, 0.5),
                new RoadPoint(100_000.5, 64.0, 0.5)
        );

        assertFailure(ordinary, surface(3), 14, DEFAULT_CANDIDATE_LIMIT,
                BlockPlanFailureCode.BLOCK_LIMIT_EXCEEDED);
        assertFailure(tooSteep, surface(1), 100, DEFAULT_CANDIDATE_LIMIT,
                BlockPlanFailureCode.GRADE_LIMIT_EXCEEDED);
        assertFailure(enormous, surface(1), 100_000, 100,
                BlockPlanFailureCode.RASTERIZATION_WORK_LIMIT_EXCEEDED);
    }

    @Test
    void rejectsInvalidLimitsSurfacesAndOutOfRangeTargets() {
        StraightGeometry ordinary = new StraightGeometry(
                new RoadPoint(0.5, 64.0, 0.5),
                new RoadPoint(4.5, 64.0, 0.5)
        );
        StraightGeometry outOfRange = new StraightGeometry(
                new RoadPoint((double) Integer.MAX_VALUE + 100.5, 64.0, 0.5),
                new RoadPoint((double) Integer.MAX_VALUE + 104.5, 64.0, 0.5)
        );

        assertThrows(IllegalArgumentException.class,
                () -> planner.plan(request(ordinary, surface(1)), 0, 100));
        assertThrows(IllegalArgumentException.class,
                () -> planner.plan(request(ordinary, surface(1)), 100, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ResolvedRoadSurface(65, Blocks.STONE.defaultBlockState()));
        assertThrows(IllegalArgumentException.class,
                () -> new ResolvedRoadSurface(1, Blocks.AIR.defaultBlockState()));
        assertFailure(outOfRange, surface(1), 100, DEFAULT_CANDIDATE_LIMIT,
                BlockPlanFailureCode.COORDINATE_OUT_OF_RANGE);
    }

    private BlockPlacementPlan successfulPlan(
            StraightGeometry geometry,
            ResolvedRoadSurface surface,
            int maximumAffectedBlocks,
            int maximumPlanningCandidates
    ) {
        BlockPlanResult result = planner.plan(
                request(geometry, surface),
                maximumAffectedBlocks,
                maximumPlanningCandidates
        );
        return assertInstanceOf(BlockPlanResult.Success.class, result).plan();
    }

    private void assertFailure(
            StraightGeometry geometry,
            ResolvedRoadSurface surface,
            int maximumAffectedBlocks,
            int maximumPlanningCandidates,
            BlockPlanFailureCode expectedCode
    ) {
        BlockPlanResult result = planner.plan(
                request(geometry, surface),
                maximumAffectedBlocks,
                maximumPlanningCandidates
        );
        assertEquals(expectedCode, assertInstanceOf(BlockPlanResult.Failure.class, result).code());
    }

    private static StraightRoadPlanRequest request(
            StraightGeometry geometry,
            ResolvedRoadSurface surface
    ) {
        return new StraightRoadPlanRequest(Level.OVERWORLD, geometry, surface);
    }

    private static ResolvedRoadSurface surface(int width) {
        return new ResolvedRoadSurface(width, Blocks.STONE.defaultBlockState());
    }
}
