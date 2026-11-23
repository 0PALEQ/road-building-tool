package com.cookiecraftmods.roadbuilder.compat;

import net.minecraft.resources.ResourceLocation;

public final class MinecraftIds {
    private MinecraftIds() {
    }

    public static ResourceLocation id(String namespace, String path) {
        return new ResourceLocation(namespace, path);
    }

    public static ResourceLocation id(String name) {
        return new ResourceLocation(name);
    }
}
