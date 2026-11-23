package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.preset.LaneComponent;
import com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection;
import com.cookiecraftmods.roadbuilder.preset.LaneType;
import com.cookiecraftmods.roadbuilder.preset.RoadPreset;
import com.cookiecraftmods.roadbuilder.preset.RoadMarkingPattern;
import com.cookiecraftmods.roadbuilder.preset.RoadSurfaceBand;
import com.cookiecraftmods.roadbuilder.preset.RoadSurfaceRole;
import com.cookiecraftmods.roadbuilder.test.MinecraftBootstrap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class StraightRoadPlanningServiceTest {
    private static final ResourceLocation PRESET_ID = MinecraftIds.id("roadbuilder", "test");
    private static final RoadPreset PRESET = new RoadPreset(
            RoadPreset.CURRENT_SCHEMA_VERSION,
            3,
            1.0,
            MinecraftIds.id("minecraft", "stone"),
            List.of(new LaneComponent(LaneType.VEHICLE, 1.0, 0.0, LaneTravelDirection.FORWARD))
    );

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftBootstrap.bootstrap();
    }

    @Test
    void resolvesTheServerPresetAndBuildsFromIntent() {
        StraightRoadPlanningService service = serviceWithPreset();
        BlockPlanResult result = service.plan(intent(PRESET_ID, 3), ConstructionLimits.defaults());

        BlockPlacementPlan plan = assertInstanceOf(BlockPlanResult.Success.class, result).plan();
        assertEquals(Level.OVERWORLD, plan.dimension());
        assertEquals(5, plan.affectedBlockCount());
    }

    @Test
    void rejectsUnknownStaleAndDegenerateIntentWithoutPlanningBlocks() {
        StraightRoadPlanningService service = serviceWithPreset();

        assertFailure(service.plan(
                intent(MinecraftIds.id("roadbuilder", "missing"), 3),
                ConstructionLimits.defaults()
        ), BlockPlanFailureCode.UNKNOWN_PRESET);
        assertFailure(service.plan(
                intent(PRESET_ID, 2),
                ConstructionLimits.defaults()
        ), BlockPlanFailureCode.PRESET_REVISION_MISMATCH);

        RoadPoint samePoint = new RoadPoint(0.5, 64.0, 0.5);
        StraightRoadIntent degenerate = new StraightRoadIntent(
                Level.OVERWORLD,
                samePoint,
                samePoint,
                PRESET_ID,
                3
        );
        assertFailure(
                service.plan(degenerate, ConstructionLimits.defaults()),
                BlockPlanFailureCode.INVALID_GEOMETRY
        );
    }

    @Test
    void resolvesSurfaceBlocksStrictlyWithoutTheDefaultedRegistryFallback() {
        RoadPreset missingBlock = presetWithSurface("roadbuilder:missing_block");
        RoadPreset air = presetWithSurface("minecraft:air");

        assertFailure(
                serviceFor(missingBlock).plan(intent(PRESET_ID, 3), ConstructionLimits.defaults()),
                BlockPlanFailureCode.UNKNOWN_SURFACE_BLOCK
        );
        assertFailure(
                serviceFor(air).plan(intent(PRESET_ID, 3), ConstructionLimits.defaults()),
                BlockPlanFailureCode.AIR_SURFACE_BLOCK
        );
    }

    @Test
    void acceptsAuthoredGradesWithoutAPlacementPolicyRejection() {
        StraightRoadPlanningService service = serviceWithPreset();
        StraightRoadIntent graded = new StraightRoadIntent(
                Level.OVERWORLD,
                new RoadPoint(0.5, 64.0, 0.5),
                new RoadPoint(4.5, 66.0, 0.5),
                PRESET_ID,
                PRESET.revision()
        );

        BlockPlanResult accepted = service.plan(graded, ConstructionLimits.defaults());
        assertInstanceOf(BlockPlanResult.Success.class, accepted);

        ConstructionLimits stricter = new ConstructionLimits(
                128.0,
                100,
                16,
                1_000,
                0.25
        );
        assertInstanceOf(BlockPlanResult.Success.class,
                service.plan(graded, stricter));
    }

    @Test
    void expandsDoubleContinuousMarkingsAroundTheirRoadUnderlay() {
        RoadPreset doubleMarked = new RoadPreset(
                RoadPreset.CURRENT_SCHEMA_VERSION,
                3,
                3.0,
                MinecraftIds.id("minecraft", "gray_concrete"),
                List.of(new LaneComponent(
                        LaneType.VEHICLE,
                        3.0,
                        0.0,
                        LaneTravelDirection.FORWARD
                )),
                List.of(new RoadSurfaceBand(
                        RoadSurfaceRole.MARKING,
                        3,
                        MinecraftIds.id("minecraft", "yellow_concrete"),
                        RoadMarkingPattern.DOUBLE_CONTINUOUS
                ))
        );

        BlockPlacementPlan plan = assertInstanceOf(
                BlockPlanResult.Success.class,
                serviceFor(doubleMarked).plan(
                        intent(PRESET_ID, 3),
                        ConstructionLimits.defaults()
                )
        ).plan();

        assertEquals(15, plan.affectedBlockCount());
        assertEquals(5, plan.blocks().stream()
                .filter(block -> block.targetState().is(Blocks.GRAY_CONCRETE))
                .count());
        assertEquals(10, plan.blocks().stream()
                .filter(block -> block.targetState().is(Blocks.YELLOW_CONCRETE))
                .count());
    }

    @Test
    void resolvesTheMarkingUnderlayEvenWhenEveryVisibleColumnIsAMarking() {
        RoadPreset missingUnderlay = new RoadPreset(
                RoadPreset.CURRENT_SCHEMA_VERSION,
                3,
                1.0,
                MinecraftIds.id("roadbuilder", "missing_underlay"),
                PRESET.lanes(),
                List.of(new RoadSurfaceBand(
                        RoadSurfaceRole.MARKING,
                        1,
                        MinecraftIds.id("minecraft", "white_concrete"),
                        RoadMarkingPattern.DASHED
                ))
        );

        assertFailure(
                serviceFor(missingUnderlay).plan(
                        intent(PRESET_ID, 3),
                        ConstructionLimits.defaults()
                ),
                BlockPlanFailureCode.UNKNOWN_SURFACE_BLOCK
        );
    }

    private static StraightRoadPlanningService serviceWithPreset() {
        return serviceFor(PRESET);
    }

    private static StraightRoadPlanningService serviceFor(RoadPreset preset) {
        return new StraightRoadPlanningService(
                id -> id.equals(PRESET_ID) ? Optional.of(preset) : Optional.empty(),
                new StraightRoadPlanner()
        );
    }

    private static RoadPreset presetWithSurface(String surfaceBlock) {
        return new RoadPreset(
                RoadPreset.CURRENT_SCHEMA_VERSION,
                3,
                1.0,
                MinecraftIds.id(surfaceBlock),
                PRESET.lanes()
        );
    }

    private static StraightRoadIntent intent(ResourceLocation presetId, int revision) {
        return new StraightRoadIntent(
                Level.OVERWORLD,
                new RoadPoint(0.5, 64.0, 0.5),
                new RoadPoint(4.5, 64.0, 0.5),
                presetId,
                revision
        );
    }

    private static void assertFailure(BlockPlanResult result, BlockPlanFailureCode expectedCode) {
        assertEquals(expectedCode, assertInstanceOf(BlockPlanResult.Failure.class, result).code());
    }
}
