package com.cookiecraftmods.roadbuilder.client.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadBuilderClientPreferencesLoaderTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void missingFileCreatesAndReturnsRequestedInvertedDefaults() throws Exception {
        Path path = temporaryDirectory.resolve("nested").resolve("client.json");

        RoadBuilderClientPreferences preferences =
                RoadBuilderClientPreferencesLoader.load(path);

        assertEquals(RoadBuilderClientPreferences.DEFAULTS, preferences);
        assertTrue(preferences.invertHorizontalMovement());
        assertTrue(preferences.invertHorizontalMouseDrag());
        assertTrue(preferences.invertVerticalMouseDrag());
        assertEquals(150.0, preferences.guidelineRange());
        assertTrue(Files.isRegularFile(path));
    }

    @Test
    void validFileCanRestoreConventionalDirections() throws Exception {
        Path path = temporaryDirectory.resolve("client.json");
        Files.writeString(path, """
                {
                  "schema_version": 2,
                  "invert_horizontal_movement": false,
                  "invert_horizontal_mouse_drag": false,
                  "invert_vertical_mouse_drag": false,
                  "snap_range": 4.0,
                  "snap_grid_spacing": 2.0,
                  "snap_priority": "GRID_TANGENT_ANGLE",
                  "snap_grid_enabled": true,
                  "snap_tangent_enabled": false
                }
                """);

        RoadBuilderClientPreferences preferences =
                RoadBuilderClientPreferencesLoader.load(path);

        assertFalse(preferences.invertHorizontalMovement());
        assertFalse(preferences.invertHorizontalMouseDrag());
        assertFalse(preferences.invertVerticalMouseDrag());
        assertEquals(4.0, preferences.snapRange());
        assertEquals(2.0, preferences.snapGridSpacing());
        assertFalse(preferences.snapTangentEnabled());
        assertEquals(150.0, preferences.guidelineRange());
        String upgraded = Files.readString(path);
        assertTrue(upgraded.contains("\"schema_version\": 3"));
        assertTrue(upgraded.contains("\"guideline_range\": 150.0"));
    }

    @Test
    void configuredGuidelineRangeIsLoaded() throws Exception {
        Path path = temporaryDirectory.resolve("client.json");
        Files.writeString(path, """
                {
                  "schema_version": 3,
                  "invert_horizontal_movement": true,
                  "invert_horizontal_mouse_drag": true,
                  "invert_vertical_mouse_drag": true,
                  "snap_range": 2.5,
                  "snap_grid_spacing": 1.0,
                  "snap_priority": "TANGENT_ANGLE_GRID",
                  "snap_grid_enabled": true,
                  "snap_tangent_enabled": true,
                  "guideline_range": 320.0
                }
                """);

        RoadBuilderClientPreferences preferences =
                RoadBuilderClientPreferencesLoader.load(path);

        assertEquals(320.0, preferences.guidelineRange());
        assertEquals(320.0, preferences.snapSettings().guidelineRange());
    }

    @Test
    void malformedOrUnknownSchemaFallsBackWithoutOverwritingTheFile() throws Exception {
        Path path = temporaryDirectory.resolve("client.json");
        String invalid = """
                {
                  "schema_version": 3,
                  "invert_horizontal_movement": true,
                  "invert_horizontal_mouse_drag": true,
                  "invert_vertical_mouse_drag": true
                }
                """;
        Files.writeString(path, invalid);

        assertEquals(
                RoadBuilderClientPreferences.DEFAULTS,
                RoadBuilderClientPreferencesLoader.load(path)
        );
        assertEquals(invalid, Files.readString(path));
    }
}
