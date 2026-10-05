package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.geometry.ReferenceGeometry;
import com.cookiecraftmods.roadbuilder.preset.RoadPreset;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record ResolvedRoadPathPlan(
        BlockPlacementPlan blockPlan,
        ReferenceGeometry geometry,
        ResourceLocation presetId,
        RoadPreset preset
) {
    public ResolvedRoadPathPlan {
        Objects.requireNonNull(blockPlan, "blockPlan");
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(presetId, "presetId");
        Objects.requireNonNull(preset, "preset");
    }
}
