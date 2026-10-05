package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.network.RoadGraph;
import com.cookiecraftmods.roadbuilder.network.RoadSegment;
import com.cookiecraftmods.roadbuilder.preset.LaneComponent;
import com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection;
import com.cookiecraftmods.roadbuilder.preset.LaneType;
import com.cookiecraftmods.roadbuilder.preset.RoadPreset;
import com.cookiecraftmods.roadbuilder.preset.RoadMarkingPattern;
import com.cookiecraftmods.roadbuilder.preset.RoadSurfaceBand;
import com.cookiecraftmods.roadbuilder.preset.RoadSurfaceRole;
import com.cookiecraftmods.roadbuilder.preset.TrafficHandedness;
import com.cookiecraftmods.roadbuilder.test.MinecraftBootstrap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class StraightRoadSegmentMaterializerTest {
    private static final ResourceLocation PRESET_ID = new ResourceLocation("roadbuilder", "snapshot");
    private static final RoadPreset PRESET = new RoadPreset(
            RoadPreset.CURRENT_SCHEMA_VERSION,
            7,
            3.0,
            new ResourceLocation("minecraft", "stone"),
            List.of(new LaneComponent(
                    LaneType.VEHICLE,
                    1.0,
                    0.0,
                    LaneTravelDirection.FORWARD
            )),
            List.of(new RoadSurfaceBand(
                    RoadSurfaceRole.MARKING,
                    3,
                    new ResourceLocation("minecraft", "yellow_concrete"),
                    RoadMarkingPattern.DOUBLE_CONTINUOUS
            ))
    );

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftBootstrap.bootstrap();
    }

    @Test
    void persistedSegmentRegeneratesTheOriginalPlanWithoutPresetLookup() {
        StraightRoadIntent intent = new StraightRoadIntent(
                Level.OVERWORLD,
                new RoadPoint(0.5, 64.0, 0.5),
                new RoadPoint(8.5, 67.0, 3.5),
                PRESET_ID,
                PRESET.revision()
        );
        ConstructionLimits limits = ConstructionLimits.defaults();
        StraightRoadPlanningService planningService = new StraightRoadPlanningService(
                id -> id.equals(PRESET_ID) ? Optional.of(PRESET) : Optional.empty(),
                new StraightRoadPlanner()
        );
        ResolvedStraightRoadPlan original = assertInstanceOf(
                StraightRoadPlanningResult.Success.class,
                planningService.resolve(intent, limits)
        ).roadPlan();
        RoadSegment segment = new RoadGraph(Level.OVERWORLD)
                .prepareStraightRoad(
                        original.geometry(),
                        original.presetId(),
                        original.preset(),
                        TrafficHandedness.RHT
                )
                .segment();

        BlockPlacementPlan regenerated = assertInstanceOf(
                BlockPlanResult.Success.class,
                new StraightRoadSegmentMaterializer().plan(
                        Level.OVERWORLD,
                        segment,
                        limits
                )
        ).plan();

        assertEquals(original.blockPlan().blocks(), regenerated.blocks());
        assertEquals(original.blockPlan().bounds(), regenerated.bounds());
        assertEquals(original.blockPlan().touchedChunks(), regenerated.touchedChunks());
    }
}
