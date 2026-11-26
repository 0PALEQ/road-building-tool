package com.cookiecraftmods.roadbuilder.preset;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;

abstract class RoadPresetJsonResources extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    RoadPresetJsonResources(String directory) {
        super(GSON, directory);
    }
}
