package com.cookiecraftmods.roadbuilder.client.config;

import com.cookiecraftmods.roadbuilder.RoadBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class RoadPresetSelectionPreferences {
    private static final Path PATH = FabricLoader.getInstance().getConfigDir()
            .resolve("roadbuilder-road-selection.json");

    private RoadPresetSelectionPreferences() {
    }

    public static Optional<ResourceLocation> load() {
        if (!Files.isRegularFile(PATH)) {
            return Optional.empty();
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(
                    PATH,
                    StandardCharsets.UTF_8
            )).getAsJsonObject();
            if (root.size() != 2
                    || root.get("schema_version").getAsInt() != 1) {
                return Optional.empty();
            }
            return Optional.ofNullable(ResourceLocation.tryParse(
                    root.get("selected_preset").getAsString()
            ));
        } catch (RuntimeException | java.io.IOException invalid) {
            RoadBuilder.LOGGER.warn(
                    "Could not read selected Road Builder preset: {}",
                    invalid.getMessage()
            );
            return Optional.empty();
        }
    }

    public static void save(ResourceLocation id) {
        try {
            Files.createDirectories(PATH.toAbsolutePath().getParent());
            JsonObject root = new JsonObject();
            root.addProperty("schema_version", 1);
            root.addProperty("selected_preset", id.toString());
            Files.writeString(
                    PATH,
                    root + System.lineSeparator(),
                    StandardCharsets.UTF_8
            );
        } catch (java.io.IOException exception) {
            RoadBuilder.LOGGER.warn(
                    "Could not save selected Road Builder preset",
                    exception
            );
        }
    }
}
