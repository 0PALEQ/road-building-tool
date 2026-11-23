package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.geometry.FreehandGeometry;
import com.cookiecraftmods.roadbuilder.geometry.MultiCurveGeometry;
import com.cookiecraftmods.roadbuilder.geometry.QuadraticBezierGeometry;
import com.cookiecraftmods.roadbuilder.geometry.ReferenceGeometry;
import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.test.MinecraftBootstrap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadToolLongUnevenMatrixTest {
    private static ResolvedRoadSurface surface;
    private static final ConstructionLimits LIMITS =
            ConstructionLimits.defaults();

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.bootstrap();
        surface = new ResolvedRoadSurface(
                9,
                Blocks.GRAY_CONCRETE.defaultBlockState()
        );
    }

    @Test
    void straightPreviewAndServerPlanAgreeAcrossLongGrade() {
        StraightRoadPlanner planner = new StraightRoadPlanner();
        StraightRoadPlanRequest request = new StraightRoadPlanRequest(
                Level.OVERWORLD,
                new com.cookiecraftmods.roadbuilder.geometry.StraightGeometry(
                        new RoadPoint(0.5, 64.0, 0.5),
                        new RoadPoint(120.5, 72.0, 35.5)
                ),
                surface
        );
        BlockPlanResult.Success preview = assertInstanceOf(
                BlockPlanResult.Success.class,
                planner.plan(
                        request,
                        LIMITS.maximumAffectedBlocks(),
                        LIMITS.maximumPlanningCandidates(),
                        LIMITS.maximumGrade()
                )
        );
        BlockPlanResult.Success server = assertInstanceOf(
                BlockPlanResult.Success.class,
                planner.plan(
                        request,
                        LIMITS.maximumAffectedBlocks(),
                        LIMITS.maximumPlanningCandidates(),
                        LIMITS.maximumGrade()
                )
        );
        assertEquals(preview.plan().blocks(), server.plan().blocks());
        assertEquals(preview.plan().bounds(), server.plan().bounds());
        assertTrue(preview.plan().touchedChunks().size() > 4);
    }

    @Test
    void everyCurvedToolHasExactRepeatedCanonicalPlan() {
        List<ReferenceGeometry> geometries = List.of(
                new QuadraticBezierGeometry(
                        new RoadPoint(0.5, 64.0, 0.5),
                        new RoadPoint(60.5, 74.0, 45.5),
                        new RoadPoint(120.5, 68.0, 0.5)
                ),
                new MultiCurveGeometry(List.of(
                        new RoadPoint(0.5, 64.0, 0.5),
                        new RoadPoint(35.5, 69.0, 30.5),
                        new RoadPoint(75.5, 66.0, -20.5),
                        new RoadPoint(120.5, 71.0, 10.5)
                )),
                new FreehandGeometry(List.of(
                        new RoadPoint(0.5, 64.0, 0.5),
                        new RoadPoint(20.5, 66.0, 8.5),
                        new RoadPoint(40.5, 69.0, 18.5),
                        new RoadPoint(65.5, 67.0, 5.5),
                        new RoadPoint(90.5, 70.0, -10.5),
                        new RoadPoint(120.5, 72.0, 0.5)
                ))
        );
        RoadPathPlanner planner = new RoadPathPlanner();
        for (ReferenceGeometry geometry : geometries) {
            RoadPathPlanRequest request = new RoadPathPlanRequest(
                    Level.OVERWORLD,
                    geometry,
                    surface
            );
            BlockPlanResult.Success preview = assertInstanceOf(
                    BlockPlanResult.Success.class,
                    planner.plan(
                            request,
                            LIMITS.maximumAffectedBlocks(),
                            LIMITS.maximumPlanningCandidates(),
                            LIMITS.maximumGrade()
                    )
            );
            BlockPlanResult.Success server = assertInstanceOf(
                    BlockPlanResult.Success.class,
                    planner.plan(
                            request,
                            LIMITS.maximumAffectedBlocks(),
                            LIMITS.maximumPlanningCandidates(),
                            LIMITS.maximumGrade()
                    )
            );
            assertEquals(preview.plan().blocks(), server.plan().blocks());
            assertEquals(preview.plan().bounds(), server.plan().bounds());
            assertTrue(preview.plan().touchedChunks().size() > 4);
        }
    }
}
