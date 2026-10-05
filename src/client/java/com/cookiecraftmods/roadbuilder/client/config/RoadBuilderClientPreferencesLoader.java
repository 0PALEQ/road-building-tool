package com.cookiecraftmods.roadbuilder.client.config;

import com.cookiecraftmods.roadbuilder.RoadBuilder;
import com.cookiecraftmods.roadbuilder.client.tool.RoadSnapPriority;
import com.cookiecraftmods.roadbuilder.config.JsonConfigIO;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static com.cookiecraftmods.roadbuilder.config.JsonConfigIO.bool;
import static com.cookiecraftmods.roadbuilder.config.JsonConfigIO.enumValue;
import static com.cookiecraftmods.roadbuilder.config.JsonConfigIO.integer;
import static com.cookiecraftmods.roadbuilder.config.JsonConfigIO.number;

public final class RoadBuilderClientPreferencesLoader {
    public static final String FILE_NAME = "roadbuilder-client.json";

    private static final Set<String> LEGACY_KEYS = Set.of(
            "schema_version",
            "invert_horizontal_movement",
            "invert_horizontal_mouse_drag",
            "invert_vertical_mouse_drag",
            "snap_range",
            "snap_grid_spacing",
            "snap_priority",
            "snap_grid_enabled",
            "snap_tangent_enabled"
    );
    private static final Set<String> EXPECTED_KEYS = Set.of(
            "schema_version",
            "invert_horizontal_movement",
            "invert_horizontal_mouse_drag",
            "invert_vertical_mouse_drag",
            "snap_range",
            "snap_grid_spacing",
            "snap_priority",
            "snap_grid_enabled",
            "snap_tangent_enabled",
            "guideline_range"
    );
    private RoadBuilderClientPreferencesLoader() {
    }

    public static RoadBuilderClientPreferences load() {
        return load(FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME));
    }

    public static RoadBuilderClientPreferences load(Path path) {
        if (!Files.exists(path)) {
            RoadBuilderClientPreferences defaults = RoadBuilderClientPreferences.DEFAULTS;
            try {
                write(path, defaults);
            } catch (IOException exception) {
                RoadBuilder.LOGGER.warn(
                        "Could not write default Road Builder client preferences to {}",
                        path,
                        exception
                );
            }
            return defaults;
        }

        try {
            JsonObject root = JsonConfigIO.readObject(path);
            RoadBuilderClientPreferences preferences = decode(root);
            if (integer(root, "schema_version")
                    != RoadBuilderClientPreferences.CURRENT_SCHEMA_VERSION) {
                try {
                    write(path, preferences);
                } catch (IOException exception) {
                    RoadBuilder.LOGGER.warn(
                            "Could not upgrade Road Builder client preferences at {}",
                            path,
                            exception
                    );
                }
            }
            return preferences;
        } catch (IOException | RuntimeException exception) {
            RoadBuilder.LOGGER.warn(
                    "Invalid Road Builder client preferences at {}; using defaults: {}",
                    path,
                    exception.getMessage()
            );
            return RoadBuilderClientPreferences.DEFAULTS;
        }
    }

    static RoadBuilderClientPreferences decode(JsonObject root) {
        int schemaVersion = integer(root, "schema_version");
        if (schemaVersion == 2) {
            JsonConfigIO.requireExactKeys(root, LEGACY_KEYS);
        } else {
            JsonConfigIO.requireExactKeys(root, EXPECTED_KEYS);
        }
        return new RoadBuilderClientPreferences(
                schemaVersion == 2
                        ? RoadBuilderClientPreferences.CURRENT_SCHEMA_VERSION
                        : schemaVersion,
                bool(root, "invert_horizontal_movement"),
                bool(root, "invert_horizontal_mouse_drag"),
                bool(root, "invert_vertical_mouse_drag"),
                number(root, "snap_range"),
                number(root, "snap_grid_spacing"),
                enumValue(root, "snap_priority", RoadSnapPriority.class),
                bool(root, "snap_grid_enabled"),
                bool(root, "snap_tangent_enabled"),
                schemaVersion == 2
                        ? RoadBuilderClientPreferences.DEFAULTS.guidelineRange()
                        : number(root, "guideline_range")
        );
    }

    private static void write(Path path, RoadBuilderClientPreferences preferences)
            throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", preferences.schemaVersion());
        root.addProperty(
                "invert_horizontal_movement",
                preferences.invertHorizontalMovement()
        );
        root.addProperty(
                "invert_horizontal_mouse_drag",
                preferences.invertHorizontalMouseDrag()
        );
        root.addProperty(
                "invert_vertical_mouse_drag",
                preferences.invertVerticalMouseDrag()
        );
        root.addProperty("snap_range", preferences.snapRange());
        root.addProperty(
                "snap_grid_spacing",
                preferences.snapGridSpacing()
        );
        root.addProperty("snap_priority", preferences.snapPriority().name());
        root.addProperty(
                "snap_grid_enabled",
                preferences.snapGridEnabled()
        );
        root.addProperty(
                "snap_tangent_enabled",
                preferences.snapTangentEnabled()
        );
        root.addProperty("guideline_range", preferences.guidelineRange());

        JsonConfigIO.writeAtomic(path, FILE_NAME, root);
    }
}
