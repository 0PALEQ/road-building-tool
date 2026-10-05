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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
