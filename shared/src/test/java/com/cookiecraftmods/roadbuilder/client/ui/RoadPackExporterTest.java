package com.cookiecraftmods.roadbuilder.client.ui;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import com.cookiecraftmods.roadbuilder.preset.RoadPresetTemplates;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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
        Path pack = RoadPackExporter.export(
                temporaryDirectory,
                "City Roads",
                Map.of(
                        MinecraftIds.id("example", "urban/main"),
                        RoadPresetTemplates.blank()
                )
        );

        assertTrue(pack.getFileName().toString().endsWith(".zip"));
        try (ZipFile zip = new ZipFile(pack.toFile())) {
            try (var metadata = new InputStreamReader(zip.getInputStream(zip.getEntry("pack.mcmeta")),
                    StandardCharsets.UTF_8)) {
                var formats = JsonParser.parseReader(metadata).getAsJsonObject().getAsJsonObject("pack");
                assertEquals(SharedConstants.DATA_PACK_FORMAT, formats.get("pack_format").getAsInt());
                if (SharedConstants.RESOURCE_PACK_FORMAT != SharedConstants.DATA_PACK_FORMAT) {
                    var supported = formats.getAsJsonArray("supported_formats");
                    assertEquals(Math.min(SharedConstants.RESOURCE_PACK_FORMAT, SharedConstants.DATA_PACK_FORMAT),
                            supported.get(0).getAsInt());
                    assertEquals(Math.max(SharedConstants.RESOURCE_PACK_FORMAT, SharedConstants.DATA_PACK_FORMAT),
                            supported.get(1).getAsInt());
                }
            }
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
    }
}
