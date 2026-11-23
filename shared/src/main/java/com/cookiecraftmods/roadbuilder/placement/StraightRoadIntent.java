package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Objects;

public record StraightRoadIntent(
        ResourceKey<Level> dimension,
        RoadPoint start,
        RoadPoint end,
        ResourceLocation presetId,
        int expectedPresetRevision
) {
    public StraightRoadIntent {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(presetId, "presetId");
    }
}
