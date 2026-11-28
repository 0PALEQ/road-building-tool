package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.geometry.FreehandGeometry;
import com.cookiecraftmods.roadbuilder.geometry.MultiCurveGeometry;
import com.cookiecraftmods.roadbuilder.geometry.QuadraticBezierGeometry;
import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.StraightGeometry;
import com.cookiecraftmods.roadbuilder.preset.RoadMarkingPattern;
import com.cookiecraftmods.roadbuilder.test.MinecraftBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadPathPlannerTest {
    private static final RoadPathPlanner PLANNER = new RoadPathPlanner();

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftBootstrap.bootstrap();
    }

    @Test
    void rasterizesAQuadraticSweepThroughItsBend() {
        QuadraticBezierGeometry geometry = new QuadraticBezierGeometry(
                new RoadPoint(0.5, 64.0, 0.5),
                new RoadPoint(5.5, 64.0, 8.5),
                new RoadPoint(10.5, 64.0, 0.5)
        );

        BlockPlacementPlan plan = success(geometry);
        Set<BlockPos> positions = plan.blocks().stream()
                .map(PlannedBlock::position)
                .collect(Collectors.toSet());

        assertTrue(positions.stream().anyMatch(position ->
                position.getX() >= 4
                        && position.getX() <= 6
                        && position.getZ() >= 3));
        assertTrue(positions.contains(new BlockPos(0, 64, 0)));
        assertTrue(positions.contains(new BlockPos(10, 64, 0)));
    }

    @Test
    void rasterizesComplexAndFreehandGeometryWithTheSamePlanner() {
        MultiCurveGeometry complex = new MultiCurveGeometry(List.of(
                new RoadPoint(0.5, 70.0, 0.5),
                new RoadPoint(5.5, 70.0, 5.5),
                new RoadPoint(10.5, 70.0, -2.5),
                new RoadPoint(16.5, 70.0, 3.5)
        ));
        FreehandGeometry freehand = new FreehandGeometry(List.of(
                new RoadPoint(0.5, 70.0, 0.5),
                new RoadPoint(3.5, 70.0, 1.5),
                new RoadPoint(6.5, 70.0, 5.5),
                new RoadPoint(10.5, 70.0, 3.5)
        ), 0.05);

        assertTrue(success(complex).affectedBlockCount() > 30);
        assertTrue(success(freehand).affectedBlockCount() > 20);
    }

    @Test
    void rejectsCurvedGradesAndBoundedPlanningWork() {
        QuadraticBezierGeometry steep = new QuadraticBezierGeometry(
                new RoadPoint(0.5, 64.0, 0.5),
                new RoadPoint(2.5, 70.0, 2.5),
                new RoadPoint(5.5, 72.0, 0.5)
        );
        BlockPlanResult gradeResult = PLANNER.plan(
                request(steep),
                10_000,
                100_000,
                0.5
        );
        BlockPlanResult workResult = PLANNER.plan(
                request(new QuadraticBezierGeometry(
                        new RoadPoint(0.5, 64.0, 0.5),
                        new RoadPoint(20.5, 64.0, 20.5),
                        new RoadPoint(40.5, 64.0, 0.5)
                )),
                10_000,
                4,
                0.5
        );

        assertEquals(
                BlockPlanFailureCode.GRADE_LIMIT_EXCEEDED,
                assertInstanceOf(
                        BlockPlanResult.Failure.class,
                        gradeResult
                ).code()
        );
        assertEquals(
                BlockPlanFailureCode.RASTERIZATION_WORK_LIMIT_EXCEEDED,
                assertInstanceOf(
                        BlockPlanResult.Failure.class,
                        workResult
                ).code()
        );
    }

    @Test
    void appliesDashedMarkingsByReferenceDistanceOnThePathSweep() {
        ResolvedRoadSurface dashed = new ResolvedRoadSurface(
                List.of(Blocks.WHITE_CONCRETE.defaultBlockState()),
                List.of(Blocks.GRAY_CONCRETE.defaultBlockState()),
                List.of(RoadMarkingPattern.DASHED)
        );
        BlockPlacementPlan plan = assertInstanceOf(
                BlockPlanResult.Success.class,
                PLANNER.plan(
                        new RoadPathPlanRequest(
                                Level.OVERWORLD,
                                new StraightGeometry(
                                        new RoadPoint(0.5, 64.0, 0.5),
                                        new RoadPoint(8.5, 64.0, 0.5)
                                ),
                                dashed
                        ),
                        100,
                        10_000,
                        0.5
                )
        ).plan();

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
    void curvedSurfaceMaterialsFollowTheNearestColumnInsteadOfIterationOrder() {
        QuadraticBezierGeometry geometry = new QuadraticBezierGeometry(
                new RoadPoint(0.5, 64, 0.5),
                new RoadPoint(20.5, 64, 30.5),
                new RoadPoint(40.5, 64, 0.5));
        ResolvedRoadSurface surface = new ResolvedRoadSurface(List.of(
                Blocks.RED_CONCRETE.defaultBlockState(),
                Blocks.WHITE_CONCRETE.defaultBlockState(),
                Blocks.BLUE_CONCRETE.defaultBlockState()));
        BlockPlacementPlan plan = assertInstanceOf(BlockPlanResult.Success.class,
                PLANNER.plan(new RoadPathPlanRequest(Level.OVERWORLD, geometry, surface),
                        10_000, 100_000)).plan();
        int checked = 0;
        for (PlannedBlock block : plan.blocks()) {
            double best = Double.POSITIVE_INFINITY;
            double next = Double.POSITIVE_INFINITY;
            int nearest = -1;
            for (int column = 0; column < 3; column++) {
                double columnDistance = Double.POSITIVE_INFINITY;
                for (double distance = 0; distance <= geometry.length(); distance += 0.05) {
                    RoadPoint point = geometry.sample(distance, 1 - column).position();
                    double dx = block.position().getX() + 0.5 - point.x();
                    double dz = block.position().getZ() + 0.5 - point.z();
                    columnDistance = Math.min(columnDistance, dx * dx + dz * dz);
                }
                if (columnDistance < best) {
                    next = best;
                    best = columnDistance;
                    nearest = column;
                } else {
                    next = Math.min(next, columnDistance);
                }
            }
            // Ignore voxel-boundary ties and boundary-only supercover cells.
            if (best < 0.20 && next - best > 0.15) {
                assertEquals(surface.targetState(nearest), block.targetState(),
                        "Wrong surface band at " + block.position());
                checked++;
            }
        }
        assertTrue(checked > 50);
    }

    @Test
    void parallelPlanningPreservesOrderedBlocksAndPlanningLimits() {
        QuadraticBezierGeometry geometry = new QuadraticBezierGeometry(
                new RoadPoint(0.5, 64.0, 0.5),
                new RoadPoint(200.5, 78.0, 170.5),
                new RoadPoint(400.5, 92.0, 0.5)
        );
        ResolvedRoadSurface surface = patternedSurface(24);
        RoadPathPlanRequest request = new RoadPathPlanRequest(Level.OVERWORLD, geometry, surface);
        RoadPathPlanner serial = new RoadPathPlanner(1);
        BlockPlacementPlan expected = assertInstanceOf(BlockPlanResult.Success.class,
                serial.plan(request, 20_000, 100_000)).plan();
        BlockPlacementPlan actual = assertInstanceOf(BlockPlanResult.Success.class,
                PLANNER.plan(request, 20_000, 100_000)).plan();
        assertEquals(expected.blocks(), actual.blocks());
        assertEquals(expected.touchedChunks(), actual.touchedChunks());

        assertEquals(new BlockPlanResult.Failure(BlockPlanFailureCode.RASTERIZATION_WORK_LIMIT_EXCEEDED),
                PLANNER.plan(request, 20_000, 20_000));
        assertEquals(new BlockPlanResult.Failure(BlockPlanFailureCode.BLOCK_LIMIT_EXCEEDED),
                PLANNER.plan(request, 100, 100_000));
    }

    private static ResolvedRoadSurface patternedSurface(int width) {
        var materials = new ArrayList<BlockState>(width);
        var underlay = new ArrayList<BlockState>(width);
        var patterns = new ArrayList<RoadMarkingPattern>(width);
        for (int column = 0; column < width; column++) {
            boolean markings = column % 3 == 0;
            materials.add(markings ? Blocks.WHITE_CONCRETE.defaultBlockState()
                    : Blocks.STONE_SLAB.defaultBlockState());
            underlay.add(Blocks.STONE_SLAB.defaultBlockState());
            patterns.add(markings ? RoadMarkingPattern.DASHED : RoadMarkingPattern.CONTINUOUS);
        }
        return new ResolvedRoadSurface(materials, underlay, patterns);
    }

    private static BlockPlacementPlan success(
            com.cookiecraftmods.roadbuilder.geometry.ReferenceGeometry geometry
    ) {
        return assertInstanceOf(
                BlockPlanResult.Success.class,
                PLANNER.plan(request(geometry), 10_000, 100_000, 0.5)
        ).plan();
    }

    private static RoadPathPlanRequest request(
            com.cookiecraftmods.roadbuilder.geometry.ReferenceGeometry geometry
    ) {
        return new RoadPathPlanRequest(
                Level.OVERWORLD,
                geometry,
                new ResolvedRoadSurface(
                        3,
                        Blocks.STONE.defaultBlockState()
                )
        );
    }
}
