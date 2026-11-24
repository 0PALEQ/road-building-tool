package com.cookiecraftmods.roadbuilder.client.ui;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import com.cookiecraftmods.roadbuilder.preset.LaneComponent;
import com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection;
import com.cookiecraftmods.roadbuilder.preset.LaneType;
import com.cookiecraftmods.roadbuilder.preset.RoadMarkingPattern;
import com.cookiecraftmods.roadbuilder.preset.RoadPreset;
import com.cookiecraftmods.roadbuilder.preset.RoadSurfaceBand;
import com.cookiecraftmods.roadbuilder.preset.RoadSurfaceRole;
import com.cookiecraftmods.roadbuilder.preset.RoadClassification;
import com.cookiecraftmods.roadbuilder.preset.RoadClass;
import com.cookiecraftmods.roadbuilder.preset.RoadIntersectionStyle;
import com.cookiecraftmods.roadbuilder.preset.IntersectionControl;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadPresetEditorDraftTest {
    private static final ResourceLocation ASPHALT =
            MinecraftIds.id("minecraft", "gray_concrete");
    private static final ResourceLocation STONE =
            MinecraftIds.id("minecraft", "stone");
    private static final ResourceLocation WHITE =
            MinecraftIds.id("minecraft", "white_concrete");

    @Test
    void railingCanBeAddedAsAnEdgeAndSurvivesSaveAndNetworkSync() {
        RoadPresetEditorDraft draft = oneLaneDraft(3);
        RoadPresetEditorComponent railing = RoadPresetEditorComponentCatalog.prototype(
                RoadPresetEditorComponentCatalog.Kind.RAILING, draft);
        assertEquals(1, railing.width());
        draft.insertComponent(0, railing);
        draft.addComponent(railing);
        RoadPreset preset = draft.build(1);
        assertEquals(5, preset.surfaceBlockWidth());
        assertEquals(RoadSurfaceRole.RAILING, preset.surfaceBands().get(0).role());
        assertEquals(1, preset.lanes().size());
        var json = RoadPreset.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, preset)
                .result().orElseThrow();
        RoadPreset decoded = RoadPreset.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json)
                .result().orElseThrow();
        assertEquals(preset, decoded);
        assertEquals(RoadPresetEditorComponent.Kind.RAILING,
                new RoadPresetEditorDraft(decoded).components().get(0).kind());
        var graph = new com.cookiecraftmods.roadbuilder.network.RoadGraph(
                net.minecraft.world.level.Level.OVERWORLD);
        graph.applyAddition(graph.prepareStraightRoad(
                new com.cookiecraftmods.roadbuilder.geometry.StraightGeometry(
                        new com.cookiecraftmods.roadbuilder.geometry.RoadPoint(0, 64, 0),
                        new com.cookiecraftmods.roadbuilder.geometry.RoadPoint(20, 64, 0)),
                MinecraftIds.id("roadbuilder", "railing_test"), preset,
                com.cookiecraftmods.roadbuilder.preset.TrafficHandedness.RHT));
        var packet = com.cookiecraftmods.roadbuilder.networking.RoadGraphSnapshotS2CPacket
                .current(graph.snapshot());
        var buffer = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        try {
            packet.write(buffer);
            assertEquals(graph.snapshot(),
                    com.cookiecraftmods.roadbuilder.networking.RoadGraphSnapshotS2CPacket.TYPE
                            .read(buffer).snapshot());
        } finally {
            buffer.release();
        }
    }

    @Test
    void projectsLegacyLanesAndBandsIntoOneOrderedCrossSection() {
        RoadPresetEditorDraft draft = new RoadPresetEditorDraft(preset(
                9,
                List.of(
                        lane(3, -2, LaneTravelDirection.FORWARD),
                        lane(3, 2, LaneTravelDirection.BACKWARD)
                ),
                List.of(
                        band(RoadSurfaceRole.SIDEWALK, 1, STONE),
                        band(RoadSurfaceRole.PAVEMENT, 3, ASPHALT),
                        band(RoadSurfaceRole.MARKING, 1, WHITE),
                        band(RoadSurfaceRole.PAVEMENT, 3, ASPHALT),
                        band(RoadSurfaceRole.SIDEWALK, 1, STONE)
                )
        ));

        assertAll(
                () -> assertEquals(9, draft.surfaceBlockWidth()),
                () -> assertEquals(2, draft.laneCount()),
                () -> assertEquals(
                        List.of(
                                RoadPresetEditorComponent.Kind.SIDEWALK,
                                RoadPresetEditorComponent.Kind.VEHICLE_LANE,
                                RoadPresetEditorComponent.Kind.SOLID_MARKING,
                                RoadPresetEditorComponent.Kind.VEHICLE_LANE,
                                RoadPresetEditorComponent.Kind.SIDEWALK
                        ),
                        draft.components().stream()
                                .map(RoadPresetEditorComponent::kind)
                                .toList()
                ),
                () -> assertEquals(
                        List.of(1, 3, 1, 3, 1),
                        draft.components().stream()
                                .map(RoadPresetEditorComponent::width)
                                .toList()
                )
        );
    }

    @Test
    void insertionAndReorderingDeriveLaneOffsetsFromTheVisualOrder() {
        RoadPresetEditorDraft draft = oneLaneDraft(3);
        int sidewalk = draft.insertComponent(
                0,
                RoadPresetEditorComponent.surface(
                        RoadPresetEditorComponent.Kind.SIDEWALK,
                        1,
                        STONE
                )
        );
        int backwardLane = draft.addComponent(
                RoadPresetEditorComponent.vehicleLane(
                        2,
                        ASPHALT,
                        LaneTravelDirection.BACKWARD
                )
        );
        int moved = draft.moveComponent(backwardLane, sidewalk + 1);
        RoadPreset built = draft.build(2);

        assertAll(
                () -> assertEquals(1, moved),
                () -> assertEquals(6, built.surfaceBlockWidth()),
                () -> assertEquals(2, built.lanes().size()),
                () -> assertEquals(-1.0,
                        built.lanes().get(0).lateralOffset()),
                () -> assertEquals(1.5,
                        built.lanes().get(1).lateralOffset()),
                () -> assertEquals(
                        LaneTravelDirection.BACKWARD,
                        built.lanes().get(0).direction()
                )
        );
    }

    @Test
    void laneOwnsItsWidthAndPlacedBlockMaterial() {
        RoadPresetEditorDraft draft = oneLaneDraft(3);
        RoadPresetEditorComponent lane = draft.components().get(0);
        draft.replaceComponent(
                0,
                lane.withWidth(5).withBlock(STONE)
        );

        RoadPreset built = draft.build(2);

        assertAll(
                () -> assertEquals(5, built.surfaceBlockWidth()),
                () -> assertEquals(5.0, built.lanes().get(0).width()),
                () -> assertEquals(5, built.surfaceBands().get(0).width()),
                () -> assertEquals(STONE,
                        built.surfaceBands().get(0).block())
        );
    }

    @Test
    void supportsDashedAndFixedThreeBlockDoubleMarkings() {
        RoadPresetEditorDraft draft = oneLaneDraft(3);
        draft.addComponent(RoadPresetEditorComponent.surface(
                RoadPresetEditorComponent.Kind.DASHED_MARKING,
                1,
                WHITE
        ));
        draft.addComponent(RoadPresetEditorComponent.surface(
                RoadPresetEditorComponent.Kind.DOUBLE_CONTINUOUS_MARKING,
                3,
                WHITE
        ));

        RoadPreset built = draft.build(2);

        assertAll(
                () -> assertEquals(
                        RoadMarkingPattern.DASHED,
                        built.surfaceBands().get(1).pattern()
                ),
                () -> assertEquals(
                        RoadMarkingPattern.DOUBLE_CONTINUOUS,
                        built.surfaceBands().get(2).pattern()
                ),
                () -> assertEquals(3,
                        built.surfaceBands().get(2).width()),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> draft.components().get(2).withWidth(2)
                )
        );
    }

    @Test
    void markingUnderlaysInheritTheWholePaletteAndKeepIndependentOverrides() {
        var cobble = MinecraftIds.id("minecraft", "cobblestone");
        var andesite = MinecraftIds.id("minecraft", "andesite");
        var draft = oneLaneDraft(3);
        draft.setRoadMaterialSet(RoadSurfaceRole.PAVEMENT, List.of(STONE, andesite));
        draft.addComponent(RoadPresetEditorComponent.surface(
                RoadPresetEditorComponent.Kind.DASHED_MARKING, 1, WHITE));
        draft.addComponent(RoadPresetEditorComponent.surface(
                RoadPresetEditorComponent.Kind.DOUBLE_CONTINUOUS_MARKING, 3, WHITE)
                .withUnderlayBlockSet(List.of(cobble, andesite)));

        var built = draft.build(2);
        var reopened = new RoadPresetEditorDraft(built);
        var snapshot = com.cookiecraftmods.roadbuilder.network.RoadPresetSnapshot.from(built);
        assertEquals(List.of(STONE, andesite), snapshot.surfaceColumns().get(3).underlayBlocks());
        assertEquals(List.of(cobble, andesite), snapshot.surfaceColumns().get(5).blocks());
        assertEquals(List.of(WHITE), snapshot.surfaceColumns().get(4).blocks());
        assertEquals(List.of(WHITE), snapshot.surfaceColumns().get(6).blocks());
        assertEquals(List.of(cobble, andesite), reopened.components().get(2).underlayBlockSet());
        assertEquals(built, reopened.build(2));
    }

    @Test
    void maximumWidthAndFinalLaneGuardsAreTransactional() {
        RoadPresetEditorDraft draft = oneLaneDraft(
                RoadPreset.MAXIMUM_SURFACE_BLOCK_WIDTH
        );
        List<RoadPresetEditorComponent> before =
                List.copyOf(draft.components());

        assertThrows(
                IllegalArgumentException.class,
                () -> draft.addComponent(RoadPresetEditorComponent.surface(
                        RoadPresetEditorComponent.Kind.SIDEWALK,
                        1,
                        STONE
                ))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> draft.removeComponent(0)
        );

        assertAll(
                () -> assertEquals(before, draft.components()),
                () -> assertEquals(
                        RoadPreset.MAXIMUM_SURFACE_BLOCK_WIDTH,
                        draft.surfaceBlockWidth()
                )
        );
    }

    @Test
    void mirrorReversesTheExactRenderedSequence() {
        RoadPresetEditorDraft draft = oneLaneDraft(3);
        draft.addComponent(RoadPresetEditorComponent.surface(
                RoadPresetEditorComponent.Kind.SIDEWALK,
                1,
                STONE
        ));

        draft.mirror();

        assertEquals(
                List.of(
                        RoadPresetEditorComponent.Kind.SIDEWALK,
                        RoadPresetEditorComponent.Kind.VEHICLE_LANE
                ),
                draft.components().stream()
                        .map(RoadPresetEditorComponent::kind)
                        .toList()
        );
    }

    @Test
    void validatesRawBlankDisplayNameWithoutMutatingTheDraft() {
        RoadPresetEditorDraft draft = oneLaneDraft(3);
        String originalName = draft.display().fallbackName();

        String error = draft.validationError(2, "   ", "roads")
                .orElseThrow();

        assertAll(
                () -> assertTrue(error.contains("fallbackName")),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> draft.build(2, "   ", "roads")
                ),
                () -> assertEquals(originalName,
                        draft.display().fallbackName()),
                () -> assertTrue(draft.validationError(2).isEmpty())
        );
    }

    @Test
    void roadMaterialsFlowToInheritedComponentsAndKeepBusOverrides() {
        RoadPresetEditorDraft draft = oneLaneDraft(3);
        assertTrue(draft.components().get(0).inheritsRoadMaterial());
        draft.setRoadMaterial(RoadSurfaceRole.PAVEMENT, STONE);
        draft.addComponent(RoadPresetEditorComponent.inheritedBusLane(
                3,
                STONE,
                LaneTravelDirection.FORWARD
        ));
        draft.setComponentMaterialOverride(1, WHITE);
        draft.setRoadMaterial(RoadSurfaceRole.PAVEMENT, ASPHALT);

        RoadPreset built = draft.build(2);

        assertAll(
                () -> assertEquals(ASPHALT,
                        built.surfaceBands().get(0).block()),
                () -> assertEquals(WHITE,
                        built.surfaceBands().get(1).block()),
                () -> assertEquals(LaneType.BUS,
                        built.lanes().get(1).type()),
                () -> assertEquals(ASPHALT,
                        built.materialPalette().pavementBlock())
        );
    }

    @Test
    void layoutAndPaletteEditsPreserveExplicitJunctionSettings() {
        RoadPresetEditorDraft draft = oneLaneDraft(3);
        RoadIntersectionStyle junction = new RoadIntersectionStyle(STONE, Optional.of(WHITE),
                Optional.empty(), Optional.of(ASPHALT), false, IntersectionControl.STOP);
        draft.setIntersectionStyle(junction);
        draft.setRoadMaterial(RoadSurfaceRole.MARKING, STONE);
        draft.setRoadMaterial(RoadSurfaceRole.PAVEMENT, WHITE);
        draft.replaceComponent(0, draft.components().get(0).withWidth(5));
        RoadPreset built = draft.build(2);
        assertEquals(junction, built.intersectionStyle());
        assertEquals(junction, new RoadPresetEditorDraft(built).build(3).intersectionStyle());
    }

    @Test
    void classificationAndEveryJunctionMaterialRoundTripIndependently() {
        RoadPresetEditorDraft draft = oneLaneDraft(3);
        RoadClassification classification = new RoadClassification(RoadClass.ARTERIAL, 12, 72.5);
        draft.setClassification(classification);
        for (RoadPresetEditorJunctionMaterial field : RoadPresetEditorJunctionMaterial.values()) {
            RoadIntersectionStyle before = draft.intersectionStyle();
            draft.setIntersectionStyle(field.replace(before, Optional.of(STONE)));
            RoadIntersectionStyle after = draft.build(2).intersectionStyle();
            assertEquals(Optional.of(STONE), field.block(after));
            for (RoadPresetEditorJunctionMaterial other : RoadPresetEditorJunctionMaterial.values()) {
                if (other != field) {
                    assertEquals(other.block(before), other.block(after));
                }
            }
            if (field.optional()) {
                draft.setIntersectionStyle(field.replace(after, Optional.empty()));
                assertTrue(field.block(draft.build(2).intersectionStyle()).isEmpty());
            }
        }
        assertEquals(classification, new RoadPresetEditorDraft(draft.build(2)).build(3).classification());
    }

    @Test
    void supportsGrassAndConcreteMedianComponents() {
        RoadPresetEditorDraft draft = oneLaneDraft(3);
        draft.addComponent(RoadPresetEditorComponent.inheritedSurface(
                RoadPresetEditorComponent.Kind.GRASS_MEDIAN,
                2,
                draft.materialPalette().grassMedianBlock()
        ));
        draft.addComponent(RoadPresetEditorComponent.inheritedSurface(
                RoadPresetEditorComponent.Kind.CONCRETE_MEDIAN,
                1,
                draft.materialPalette().concreteMedianBlock()
        ));

        RoadPreset built = draft.build(2);

        assertEquals(
                List.of(
                        RoadSurfaceRole.PAVEMENT,
                        RoadSurfaceRole.GRASS_MEDIAN,
                        RoadSurfaceRole.CONCRETE_MEDIAN
                ),
                built.surfaceBands().stream()
                        .map(RoadSurfaceBand::role)
                        .toList()
        );
    }

    @Test
    void globalMaterialSetsPropagateToInheritedRoadParts() {
        RoadPresetEditorDraft draft = oneLaneDraft(3);
        List<ResourceLocation> blocks = List.of(
                MinecraftIds.id("minecraft", "cobblestone"),
                MinecraftIds.id("minecraft", "andesite")
        );

        draft.setRoadMaterialSet(RoadSurfaceRole.PAVEMENT, blocks);
        RoadPreset built = draft.build(2);

        assertEquals(
                blocks,
                built.materialPalette().blockSetFor(RoadSurfaceRole.PAVEMENT)
        );
        assertTrue(built.surfaceBands().stream()
                .filter(band -> band.role() == RoadSurfaceRole.PAVEMENT)
                .allMatch(band -> band.blockSet().equals(blocks)));
    }

    private static RoadPresetEditorDraft oneLaneDraft(int width) {
        return new RoadPresetEditorDraft(preset(
                width,
                List.of(lane(width, 0, LaneTravelDirection.FORWARD)),
                List.of(band(RoadSurfaceRole.PAVEMENT, width, ASPHALT))
        ));
    }

    private static RoadPreset preset(
            int surfaceWidth,
            List<LaneComponent> lanes,
            List<RoadSurfaceBand> bands
    ) {
        return new RoadPreset(
                RoadPreset.CURRENT_SCHEMA_VERSION,
                1,
                surfaceWidth,
                ASPHALT,
                lanes,
                bands
        );
    }

    private static LaneComponent lane(
            double width,
            double offset,
            LaneTravelDirection direction
    ) {
        return new LaneComponent(
                LaneType.VEHICLE,
                width,
                offset,
                direction
        );
    }

    private static RoadSurfaceBand band(
            RoadSurfaceRole role,
            int width,
            ResourceLocation block
    ) {
        return new RoadSurfaceBand(role, width, block);
    }
}
