package com.cookiecraftmods.roadbuilder.preset;

import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

@FunctionalInterface
public interface RoadPresetLookup {
    Optional<RoadPreset> find(ResourceLocation id);
}
