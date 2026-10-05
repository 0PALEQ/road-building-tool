package com.cookiecraftmods.roadbuilder.preset;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;

public interface RoadPresetProvider {
    ResourceLocation providerId();

    Map<ResourceLocation, RoadPreset> presets();
}
