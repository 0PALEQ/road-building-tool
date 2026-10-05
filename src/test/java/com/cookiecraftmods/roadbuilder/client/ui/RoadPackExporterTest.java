package com.cookiecraftmods.roadbuilder.client.ui;

import com.cookiecraftmods.roadbuilder.preset.RoadPresetTemplates;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadPackExporterTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void exportsSelectedRoadsAsAnInstallableDatapack() throws Exception {
        Path pack = RoadPackExporter.export(
                temporaryDirectory,
                "City Roads",
                Map.of(
                        new ResourceLocation("example", "urban/main"),
                        RoadPresetTemplates.blank()
                )
        );

        assertTrue(pack.getFileName().toString().endsWith(".zip"));
        try (ZipFile zip = new ZipFile(pack.toFile())) {
            assertNotNull(zip.getEntry("pack.mcmeta"));
            assertNotNull(zip.getEntry("roadbuilder-pack.json"));
            assertNotNull(zip.getEntry(
                    "data/example/road_presets/urban/main.json"
            ));
        }
    }
}
