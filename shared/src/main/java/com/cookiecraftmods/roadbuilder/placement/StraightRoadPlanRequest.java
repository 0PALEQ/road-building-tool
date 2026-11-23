package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.geometry.StraightGeometry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Objects;

public record StraightRoadPlanRequest(
        ResourceKey<Level> dimension,
        StraightGeometry geometry,
        ResolvedRoadSurface surface
) {
    public StraightRoadPlanRequest {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(surface, "surface");
    }
}
