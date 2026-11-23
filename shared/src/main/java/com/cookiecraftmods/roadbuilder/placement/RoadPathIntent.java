package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.geometry.ReferenceGeometry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Objects;

public record RoadPathIntent(
        ResourceKey<Level> dimension,
        ReferenceGeometry geometry,
        ResourceLocation presetId,
        int expectedPresetRevision
) {
    public RoadPathIntent {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(presetId, "presetId");
        if (expectedPresetRevision < 1) {
            throw new IllegalArgumentException(
                    "expectedPresetRevision must be positive"
            );
        }
    }
}
