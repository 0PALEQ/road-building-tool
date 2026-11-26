package com.cookiecraftmods.roadbuilder.client.ui;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;
import com.cookiecraftmods.roadbuilder.preset.RoadPackArchive;
import com.cookiecraftmods.roadbuilder.preset.RoadPreset;
import com.cookiecraftmods.roadbuilder.preset.RoadPresetReloadListener;
import com.cookiecraftmods.roadbuilder.preset.RoadPresetTemplates;
import com.cookiecraftmods.roadbuilder.test.MinecraftBootstrap;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadPackExporterTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void exportsSelectedRoadsAsResourceAndDataPack() throws Exception {
        MinecraftBootstrap.bootstrap();
        var id = MinecraftIds.id("example", "urban/main");
        var preset = RoadPresetTemplates.blank();
        Path pack = RoadPackExporter.export(
                temporaryDirectory,
                "City Roads",
                Map.of(id, preset)
        );

        assertTrue(pack.getFileName().toString().endsWith(".zip"));
        assertEquals(Map.of(id, preset), RoadPackArchive.read(Files.readAllBytes(pack)));
        try (ZipFile zip = new ZipFile(pack.toFile())) {
            assertEquals(4, zip.size());
            assertNotNull(zip.getEntry("pack.mcmeta"));
            assertNotNull(zip.getEntry("roadbuilder-pack.json"));
            assertNotNull(zip.getEntry("assets/example/road_presets/urban/main.json"));
            assertNotNull(zip.getEntry(
                    "data/example/road_presets/urban/main.json"
            ));
            try (var assets = zip.getInputStream(zip.getEntry(
                    "assets/example/road_presets/urban/main.json"));
                 var data = zip.getInputStream(zip.getEntry(
                         "data/example/road_presets/urban/main.json"))) {
                assertArrayEquals(assets.readAllBytes(), data.readAllBytes());
            }
        }

        for (PackType type : PackType.values()) {
            var repository = new PackRepository(RoadPackFormatTestSupport.folderSource(temporaryDirectory, type));
            repository.reload();
            var loaded = repository.getPack("file/" + pack.getFileName());
            assertNotNull(loaded, "Minecraft must discover the ZIP as " + type);
            assertTrue(loaded.getCompatibility().isCompatible(), "ZIP must match the format for " + type);
            try (var resources = new MultiPackResourceManager(type, List.of(loaded.open()));
                 var reader = resources.getResource(
                         MinecraftIds.id("example", "road_presets/urban/main.json")
                 ).orElseThrow().openAsReader()) {
                var decoded = RoadPreset.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader))
                        .result().orElseThrow();
                assertEquals(preset, decoded);
                assertTrue(RoadPresetReloadListener.validateSurfaceBlock(id, decoded));
            }
        }
    }
}
