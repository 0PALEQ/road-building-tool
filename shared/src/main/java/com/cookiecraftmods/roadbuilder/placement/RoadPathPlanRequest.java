package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.geometry.ReferenceGeometry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Objects;

public record RoadPathPlanRequest(
        ResourceKey<Level> dimension,
        ReferenceGeometry geometry,
        ResolvedRoadSurface surface
) {
    public RoadPathPlanRequest {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(surface, "surface");
    }
}
