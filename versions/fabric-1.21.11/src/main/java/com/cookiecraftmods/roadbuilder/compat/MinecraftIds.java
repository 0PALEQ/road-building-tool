package com.cookiecraftmods.roadbuilder.compat;

import net.minecraft.resources.Identifier;

public final class MinecraftIds {
    private MinecraftIds() {
    }

    public static Identifier id(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }

    public static Identifier id(String name) {
        return Identifier.parse(name);
    }
}
