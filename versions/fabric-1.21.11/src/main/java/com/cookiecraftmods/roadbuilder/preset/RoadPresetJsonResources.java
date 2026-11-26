package com.cookiecraftmods.roadbuilder.preset;

import com.google.gson.JsonElement;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;

abstract class RoadPresetJsonResources extends SimpleJsonResourceReloadListener<JsonElement> {
    RoadPresetJsonResources(String directory) {
        super(ExtraCodecs.JSON, FileToIdConverter.json(directory));
    }
}
