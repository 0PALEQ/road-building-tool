package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.geometry.StraightGeometry;
import com.cookiecraftmods.roadbuilder.preset.RoadPreset;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record ResolvedStraightRoadPlan(
        BlockPlacementPlan blockPlan,
        StraightGeometry geometry,
        ResourceLocation presetId,
        RoadPreset preset
) {
    public ResolvedStraightRoadPlan {
        Objects.requireNonNull(blockPlan, "blockPlan");
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(presetId, "presetId");
        Objects.requireNonNull(preset, "preset");
    }
}
