package com.cookiecraftmods.roadbuilder.client.config;

import com.cookiecraftmods.roadbuilder.client.tool.RoadSnapPriority;
import com.cookiecraftmods.roadbuilder.client.tool.RoadSnapSettings;

public record RoadBuilderClientPreferences(
        int schemaVersion,
        boolean invertHorizontalMovement,
        boolean invertHorizontalMouseDrag,
        boolean invertVerticalMouseDrag,
        double snapRange,
        double snapGridSpacing,
        RoadSnapPriority snapPriority,
        boolean snapGridEnabled,
        boolean snapTangentEnabled,
        double guidelineRange
) {
    public static final int CURRENT_SCHEMA_VERSION = 3;
    public static final RoadBuilderClientPreferences DEFAULTS =
            new RoadBuilderClientPreferences(
                    CURRENT_SCHEMA_VERSION,
                    true,
                    true,
                    true,
                    RoadSnapSettings.DEFAULTS.range(),
                    RoadSnapSettings.DEFAULTS.gridSpacing(),
                    RoadSnapSettings.DEFAULTS.priority(),
                    RoadSnapSettings.DEFAULTS.gridEnabled(),
                    RoadSnapSettings.DEFAULTS.tangentEnabled(),
                    RoadSnapSettings.DEFAULTS.guidelineRange()
            );

    public RoadBuilderClientPreferences {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported client preference schema version " + schemaVersion
            );
        }
        new RoadSnapSettings(
                snapRange,
                snapGridSpacing,
                snapPriority,
                snapGridEnabled,
                snapTangentEnabled,
                guidelineRange
        );
    }

    public RoadSnapSettings snapSettings() {
        return new RoadSnapSettings(
                snapRange,
                snapGridSpacing,
                snapPriority,
                snapGridEnabled,
                snapTangentEnabled,
                guidelineRange
        );
    }
}
