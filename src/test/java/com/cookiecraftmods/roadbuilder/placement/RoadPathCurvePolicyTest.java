package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.geometry.*;
import com.cookiecraftmods.roadbuilder.preset.*;
import com.cookiecraftmods.roadbuilder.test.MinecraftBootstrap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class RoadPathCurvePolicyTest {
    @BeforeAll
    static void bootstrap() { MinecraftBootstrap.bootstrap(); }

    @Test
    void serverRejectsUnsafeIntentAndMaterializesTheAcceptedCanonicalCurve() {
        var id = new ResourceLocation("roadbuilder", "curve_policy_test");
        var preset = new RoadPreset(RoadPreset.CURRENT_SCHEMA_VERSION, 1, 3,
                new ResourceLocation("minecraft", "stone"),
                List.of(new LaneComponent(LaneType.VEHICLE, 3, 0, LaneTravelDirection.FORWARD)));
        var service = new RoadPathPlanningService(key -> Optional.of(preset), new RoadPathPlanner());
        var loop = new CubicBezierGeometry(p(0, 0), p(100, 100), p(-100, 100), p(20, 0));
        var failure = assertInstanceOf(RoadPathPlanningResult.Failure.class,
                service.resolve(new RoadPathIntent(Level.OVERWORLD, loop, id, 1), ConstructionLimits.defaults()));
        assertEquals(BlockPlanFailureCode.SELF_INTERSECTION, failure.code());
        var tight = new CubicBezierGeometry(p(0, 0), p(1, 0), p(2, 1), p(3, 1));
        failure = assertInstanceOf(RoadPathPlanningResult.Failure.class,
                service.resolve(new RoadPathIntent(Level.OVERWORLD, tight, id, 1), ConstructionLimits.defaults()));
        assertEquals(BlockPlanFailureCode.TOO_SHARP_TURN, failure.code());
        var safe = FreeformGeometry.create(p(0, 0), p(40, 40), new RoadVector(1, 0, 0), null);
        var result = assertInstanceOf(RoadPathPlanningResult.Success.class,
                service.resolve(new RoadPathIntent(Level.OVERWORLD, safe, id, 1), ConstructionLimits.defaults()));
        assertEquals(safe, result.roadPlan().geometry());
        assertTrue(result.roadPlan().blockPlan().affectedBlockCount() > 0);
    }

    private static RoadPoint p(double x, double z) { return new RoadPoint(x, 64, z); }
}
