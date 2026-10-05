package com.cookiecraftmods.roadbuilder.client.tool;

import com.cookiecraftmods.roadbuilder.networking.PlacementResultCode;
import com.cookiecraftmods.roadbuilder.networking.RemovalResultCode;
import com.cookiecraftmods.roadbuilder.test.MinecraftBootstrap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StraightRoadToolContractTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        MinecraftBootstrap.bootstrap();
    }

    @Test
    void doesNotBundleADefaultRoadPreset() {
        assertNull(StraightRoadToolContractTest.class.getClassLoader()
                .getResource("data/roadbuilder/road_presets/two_way.json"));
    }

    @Test
    void everyPlacementAndRemovalResultHasAnEnglishLabel() throws IOException {
        JsonObject language = resourceJson(
                "assets/roadbuilder/lang/en_us.json"
        );

        for (PlacementResultCode code : PlacementResultCode.values()) {
            assertTranslation(language, "result.roadbuilder.placement.", code.name());
        }
        for (RemovalResultCode code : RemovalResultCode.values()) {
            assertTranslation(language, "result.roadbuilder.removal.", code.name());
        }
    }

    @Test
    void controllerSourceHasNoRoadPlannersOrCrossingAnalyzer()
            throws IOException {
        String source = sourceFile(
                "src/client/java/com/cookiecraftmods/roadbuilder/client/tool/StraightRoadToolController.java"
        );
        assertTrue(
                source.contains("rawPreview"),
                "controller must build a raw preview"
        );
        assertFalse(
                source.contains("StraightRoadPlanner"),
                "controller must not reference StraightRoadPlanner"
        );
        assertFalse(
                source.contains("RoadPathPlanner"),
                "controller must not reference RoadPathPlanner"
        );
        assertFalse(
                source.contains("RoadPathCrossingAnalyzer"),
                "controller must not reference RoadPathCrossingAnalyzer"
        );
        assertFalse(
                source.contains("previewPlan"),
                "controller must not reference previewPlan"
        );
        assertFalse(
                source.contains("predictedJunctions"),
                "controller must not reference predictedJunctions"
        );
        assertFalse(
                source.contains("MAX_PREVIEW_BLOCKS"),
                "controller must not reference MAX_PREVIEW_BLOCKS"
        );
        assertFalse(
                source.contains("submittedPreviewBlockCount"),
                "controller must not reference submittedPreviewBlockCount"
        );
    }

    @Test
    void rendererSourceConsumesRawPreviewInsteadOfPlannedBlocks()
            throws IOException {
        String source = sourceFile(
                "src/client/java/com/cookiecraftmods/roadbuilder/client/render/StraightRoadPreviewRenderer.java"
        );
        assertTrue(
                source.contains("rawPreview"),
                "renderer must consume the raw preview"
        );
        assertFalse(
                source.contains("previewPlan"),
                "renderer must not consume a planned preview"
        );
        assertFalse(
                source.contains("PlannedBlock"),
                "renderer must not consume PlannedBlock"
        );
        assertFalse(
                source.contains("predictedJunctions"),
                "renderer must not render predicted junctions"
        );
    }

    private static String sourceFile(String relativePath) throws IOException {
        Path path = Path.of(relativePath);
        if (!Files.isRegularFile(path)) {
            throw new IOException(
                    "Source file not found relative to the test working directory: "
                            + relativePath
            );
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static void assertTranslation(
            JsonObject language,
            String prefix,
            String enumName
    ) {
        String key = prefix + enumName.toLowerCase(Locale.ROOT);
        assertTrue(language.has(key), () -> "Missing translation " + key);
        assertFalse(language.get(key).getAsString().isBlank(), () -> "Blank translation " + key);
    }

    private static JsonObject resourceJson(String path) throws IOException {
        try (InputStream input = StraightRoadToolContractTest.class
                .getClassLoader()
                .getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("Resource was not found on the test classpath: " + path);
            }
            return JsonParser.parseReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8)
            ).getAsJsonObject();
        }
    }
}
