package com.cookiecraftmods.roadbuilder.client.ui;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import com.cookiecraftmods.roadbuilder.preset.RoadPreset;
import com.cookiecraftmods.roadbuilder.preset.RoadPresetDisplay;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.function.Predicate;

final class RoadPresetEditorCopyFactory {
    private static final String WORLD_NAMESPACE = "roadbuilder_world";
    private static final int MAXIMUM_BASE_PATH_LENGTH = 90;
    private static final int MAXIMUM_NAME_LENGTH = 64;

    private RoadPresetEditorCopyFactory() {
    }

    static Copy create(
            ResourceLocation sourceId,
            RoadPreset source,
            String proposedName,
            String category,
            Predicate<ResourceLocation> idExists
    ) {
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(idExists, "idExists");

        String basePath = sourceId.getPath();
        if (basePath.length() > MAXIMUM_BASE_PATH_LENGTH) {
            basePath = basePath.substring(0, MAXIMUM_BASE_PATH_LENGTH);
        }
        ResourceLocation copyId = uniqueId(basePath + "_copy", idExists);
        String copyName = boundedName(proposedName);
        RoadPresetDisplay display = new RoadPresetDisplay(
                "",
                copyName,
                category,
                source.display().thumbnailKey()
        );
        RoadPreset copy = new RoadPreset(
                RoadPreset.CURRENT_SCHEMA_VERSION,
                1,
                source.surfaceWidth(),
                source.surfaceBlock(),
                source.lanes(),
                source.surfaceBands(),
                display,
                source.classification(),
                source.intersectionStyle(),
                source.materialPalette()
        );
        return new Copy(copyId, copyName, copy);
    }

    static ResourceLocation uniqueId(
            String basePath,
            Predicate<ResourceLocation> idExists
    ) {
        ResourceLocation candidate = id(basePath);
        int suffix = 1;
        while (idExists.test(candidate)) {
            candidate = id(basePath + "_" + suffix++);
        }
        return candidate;
    }

    private static String boundedName(String proposedName) {
        String name = Objects.requireNonNull(proposedName, "proposedName")
                .strip();
        if (name.length() <= MAXIMUM_NAME_LENGTH) {
            return name;
        }
        return name.substring(0, MAXIMUM_NAME_LENGTH).stripTrailing();
    }

    private static ResourceLocation id(String path) {
        return MinecraftIds.id(WORLD_NAMESPACE, path);
    }

    record Copy(ResourceLocation id, String name, RoadPreset preset) {
        Copy {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(preset, "preset");
        }
    }
}
