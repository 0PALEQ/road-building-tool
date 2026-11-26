package com.cookiecraftmods.roadbuilder.client.ui;

import com.cookiecraftmods.roadbuilder.preset.RoadPreset;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class RoadPackExporter {
    private static final int MINIMUM_PACK_FORMAT = 15;
    private static final int MAXIMUM_PACK_FORMAT = 94;
    private static final int MAXIMUM_PACK_FORMAT_MINOR = 1;

    private RoadPackExporter() {
    }

    public static Path export(
            Path outputDirectory,
            String packName,
            Map<ResourceLocation, RoadPreset> presets
    ) throws IOException {
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        Objects.requireNonNull(packName, "packName");
        Objects.requireNonNull(presets, "presets");
        String normalizedName = packName.strip();
        if (normalizedName.isEmpty() || normalizedName.length() > 64) {
            throw new IllegalArgumentException(
                    "road pack name must contain between 1 and 64 characters"
            );
        }
        if (presets.isEmpty()) {
            throw new IllegalArgumentException(
                    "a road pack must contain at least one road"
            );
        }

        TreeMap<ResourceLocation, RoadPreset> ordered = new TreeMap<>();
        presets.forEach((id, preset) -> ordered.put(
                Objects.requireNonNull(id, "preset id"),
                Objects.requireNonNull(preset, "preset")
        ));
        Files.createDirectories(outputDirectory);
        String fileStem = safeFileStem(normalizedName);
        Path destination = uniqueDestination(outputDirectory, fileStem);
        Path temporary = Files.createTempFile(
                outputDirectory,
                "." + fileStem + "-",
                ".tmp"
        );
        try {
            writePack(temporary, normalizedName, ordered);
            try {
                Files.move(
                        temporary,
                        destination,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, destination);
            }
            return destination;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void writePack(
            Path destination,
            String packName,
            Map<ResourceLocation, RoadPreset> presets
    ) throws IOException {
        try (OutputStream stream = Files.newOutputStream(destination);
             ZipOutputStream zip = new ZipOutputStream(stream)) {
            writeMetadata(zip, packName, presets.size());
            for (var entry : presets.entrySet()) {
                var id = entry.getKey();
                String json = RoadPreset.CODEC.encodeStart(JsonOps.INSTANCE, entry.getValue())
                        .result().orElseThrow(() -> new IllegalArgumentException(
                                "road preset could not be encoded: " + id
                        )).toString();
                String path = id.getNamespace() + "/road_presets/" + id.getPath() + ".json";
                write(zip, "assets/" + path, json);
                write(zip, "data/" + path, json);
            }
        }
    }

    private static void writeMetadata(ZipOutputStream zip, String name, int roadCount) throws IOException {
        JsonObject metadata = new JsonObject();
        // 1.20.1 reads only pack_format, 1.21.1 reads supported_formats, and 1.21.11 reads the minor-aware range.
        metadata.addProperty("pack_format", MINIMUM_PACK_FORMAT);
        JsonArray supported = new JsonArray();
        supported.add(MINIMUM_PACK_FORMAT);
        supported.add(MAXIMUM_PACK_FORMAT);
        metadata.add("supported_formats", supported);
        metadata.addProperty("min_format", MINIMUM_PACK_FORMAT);
        JsonArray maximum = new JsonArray();
        maximum.add(MAXIMUM_PACK_FORMAT);
        maximum.add(MAXIMUM_PACK_FORMAT_MINOR);
        metadata.add("max_format", maximum);
        metadata.addProperty("description", name + " - Road Builder road pack");

        JsonObject pack = new JsonObject();
        pack.add("pack", metadata);
        write(zip, "pack.mcmeta", pack.toString());

        JsonObject manifest = new JsonObject();
        manifest.addProperty("name", name);
        manifest.addProperty("road_count", roadCount);
        write(zip, "roadbuilder-pack.json", manifest.toString());
    }

    private static void write(ZipOutputStream zip, String name, String value) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0L);
        zip.putNextEntry(entry);
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static Path uniqueDestination(Path directory, String stem) {
        Path candidate = directory.resolve(stem + ".zip");
        int suffix = 2;
        while (Files.exists(candidate)) {
            candidate = directory.resolve(stem + "-" + suffix++ + ".zip");
        }
        return candidate;
    }

    private static String safeFileStem(String name) {
        String stem = name.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("^-+|-+$", "");
        return stem.isEmpty() ? "road-pack" : stem;
    }
}
