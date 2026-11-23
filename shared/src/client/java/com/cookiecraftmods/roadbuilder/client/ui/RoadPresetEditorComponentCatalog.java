package com.cookiecraftmods.roadbuilder.client.ui;

import com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

final class RoadPresetEditorComponentCatalog {
    private static final String KEY = "screen.roadbuilder.preset_editor.";

    private RoadPresetEditorComponentCatalog() {
    }

    static List<Entry> filtered(String query) {
        String filter = Objects.requireNonNull(query, "query")
                .strip()
                .toLowerCase(Locale.ROOT);
        List<Entry> entries = entries();
        if (filter.isEmpty()) {
            return entries;
        }
        return entries.stream()
                .filter(entry -> (entry.name().getString() + " "
                        + entry.subtitle().getString())
                        .toLowerCase(Locale.ROOT)
                        .contains(filter))
                .toList();
    }

    static RoadPresetEditorComponent prototype(
            Kind kind,
            RoadPresetEditorDraft draft
    ) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(draft, "draft");
        ResourceLocation pavement = draft.materialPalette().pavementBlock();
        ResourceLocation marking = draft.materialPalette().markingBlock();
        RoadPresetEditorComponent component = switch (kind) {
            case FORWARD_LANE -> RoadPresetEditorComponent.inheritedVehicleLane(
                    RoadPresetEditorComponent.DEFAULT_LANE_WIDTH,
                    pavement,
                    LaneTravelDirection.FORWARD
            );
            case BACKWARD_LANE -> RoadPresetEditorComponent.inheritedVehicleLane(
                    RoadPresetEditorComponent.DEFAULT_LANE_WIDTH,
                    pavement,
                    LaneTravelDirection.BACKWARD
            );
            case BUS_LANE -> RoadPresetEditorComponent.inheritedBusLane(
                    RoadPresetEditorComponent.DEFAULT_LANE_WIDTH,
                    pavement,
                    LaneTravelDirection.FORWARD
            );
            case PAVEMENT -> RoadPresetEditorComponent.inheritedSurface(
                    RoadPresetEditorComponent.Kind.PAVEMENT,
                    1,
                    pavement
            );
            case SOLID_MARKING -> RoadPresetEditorComponent.inheritedSurface(
                    RoadPresetEditorComponent.Kind.SOLID_MARKING,
                    1,
                    marking
            );
            case DASHED_MARKING -> RoadPresetEditorComponent.inheritedSurface(
                    RoadPresetEditorComponent.Kind.DASHED_MARKING,
                    1,
                    marking
            );
            case DOUBLE_CONTINUOUS_MARKING ->
                    RoadPresetEditorComponent.inheritedSurface(
                            RoadPresetEditorComponent.Kind
                                    .DOUBLE_CONTINUOUS_MARKING,
                            3,
                            marking
                    );
            case RAILING -> RoadPresetEditorComponent.inheritedSurface(
                    RoadPresetEditorComponent.Kind.RAILING, 1,
                    draft.materialPalette().blockFor(
                            com.cookiecraftmods.roadbuilder.preset.RoadSurfaceRole.RAILING)
            );
            case SIDEWALK -> RoadPresetEditorComponent.inheritedSurface(
                    RoadPresetEditorComponent.Kind.SIDEWALK,
                    1,
                    draft.materialPalette().sidewalkBlock()
            );
            case GRASS_MEDIAN -> RoadPresetEditorComponent.inheritedSurface(
                    RoadPresetEditorComponent.Kind.GRASS_MEDIAN,
                    2,
                    draft.materialPalette().grassMedianBlock()
            );
            case CONCRETE_MEDIAN -> RoadPresetEditorComponent.inheritedSurface(
                    RoadPresetEditorComponent.Kind.CONCRETE_MEDIAN,
                    2,
                    draft.materialPalette().concreteMedianBlock()
            );
        };
        return component.withInheritedBlockSet(
                draft.materialPalette().blockSetFor(
                        component.kind().surfaceRole()
                )
        );
    }

    private static List<Entry> entries() {
        return List.of(
                entry(Kind.FORWARD_LANE, "lane_forward", "forward"),
                entry(Kind.BACKWARD_LANE, "lane_backward", "backward"),
                entry(Kind.BUS_LANE, "bus_lane", "material_override_hint"),
                entry(Kind.PAVEMENT, "pavement", "drag_to_add"),
                entry(Kind.SOLID_MARKING, "solid_marking", "continuous"),
                entry(Kind.DASHED_MARKING, "dashed_marking", "dashed"),
                entry(
                        Kind.DOUBLE_CONTINUOUS_MARKING,
                        "double_marking",
                        "three_blocks_wide"
                ),
                entry(Kind.RAILING, "railing", "drag_to_add"),
                entry(Kind.SIDEWALK, "sidewalk", "drag_to_add"),
                entry(Kind.GRASS_MEDIAN, "grass_median", "median"),
                entry(Kind.CONCRETE_MEDIAN, "concrete_median", "median")
        );
    }

    private static Entry entry(Kind kind, String name, String subtitle) {
        return new Entry(
                kind,
                Component.translatable(KEY + name),
                Component.translatable(KEY + subtitle)
        );
    }

    enum Kind {
        FORWARD_LANE,
        BACKWARD_LANE,
        BUS_LANE,
        PAVEMENT,
        SOLID_MARKING,
        DASHED_MARKING,
        DOUBLE_CONTINUOUS_MARKING,
        SIDEWALK,
        RAILING,
        GRASS_MEDIAN,
        CONCRETE_MEDIAN
    }

    record Entry(Kind kind, Component name, Component subtitle) {
        Entry {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(subtitle, "subtitle");
        }
    }
}
