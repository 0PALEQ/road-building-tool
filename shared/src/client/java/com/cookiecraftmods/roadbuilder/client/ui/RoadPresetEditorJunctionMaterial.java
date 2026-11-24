package com.cookiecraftmods.roadbuilder.client.ui;

import com.cookiecraftmods.roadbuilder.preset.RoadIntersectionStyle;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;

enum RoadPresetEditorJunctionMaterial {
    PAVEMENT, MARKING, CROSSWALK, STOP_LINE;

    String key() {
        return "screen.roadbuilder.preset_editor.junction_material."
                + name().toLowerCase(java.util.Locale.ROOT);
    }

    boolean optional() {
        return this != PAVEMENT;
    }

    Optional<ResourceLocation> block(RoadIntersectionStyle style) {
        return switch (this) {
            case PAVEMENT -> Optional.of(style.pavementBlock());
            case MARKING -> style.markingBlock();
            case CROSSWALK -> style.crosswalkMaterialOverride();
            case STOP_LINE -> style.stopLineBlock();
        };
    }

    RoadIntersectionStyle replace(RoadIntersectionStyle style, Optional<ResourceLocation> block) {
        Objects.requireNonNull(block, "block");
        return new RoadIntersectionStyle(
                this == PAVEMENT ? block.orElseThrow() : style.pavementBlock(),
                this == MARKING ? block : style.markingBlock(),
                this == CROSSWALK ? block : style.crosswalkBlock(),
                this == STOP_LINE ? block : style.stopLineBlock(),
                style.defaultCrosswalks(), style.defaultControl(),
                this == PAVEMENT ? Optional.of(true) : style.pavementOverride(),
                this == CROSSWALK ? Optional.of(block.isPresent()) : style.crosswalkOverride()
        );
    }
}
