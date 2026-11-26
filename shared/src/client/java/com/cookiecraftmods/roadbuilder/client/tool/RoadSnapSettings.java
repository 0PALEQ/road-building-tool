package com.cookiecraftmods.roadbuilder.client.tool;

import java.util.Objects;

public record RoadSnapSettings(
        double range,
        double gridSpacing,
        RoadSnapPriority priority,
        boolean gridEnabled,
        boolean tangentEnabled,
        double guidelineRange
) {
    public static final double MIN_RANGE = 0.25;
    public static final double MAX_RANGE = 16.0;
    public static final double MIN_GRID_SPACING = 0.25;
    public static final double MAX_GRID_SPACING = 64.0;
    public static final double MIN_GUIDELINE_RANGE = 1.0;
    public static final double MAX_GUIDELINE_RANGE = 4_096.0;
    public static final double DEFAULT_GUIDELINE_RANGE = 150.0;
    public static final RoadSnapSettings DEFAULTS = new RoadSnapSettings(
            2.5,
            1.0,
            RoadSnapPriority.TANGENT_ANGLE_GRID,
            true,
            true,
            DEFAULT_GUIDELINE_RANGE
    );

    public RoadSnapSettings(
            double range,
            double gridSpacing,
            RoadSnapPriority priority,
            boolean gridEnabled,
            boolean tangentEnabled
    ) {
        this(
                range,
                gridSpacing,
                priority,
                gridEnabled,
                tangentEnabled,
                DEFAULT_GUIDELINE_RANGE
        );
    }

    public RoadSnapSettings {
        if (!Double.isFinite(range) || range < MIN_RANGE || range > MAX_RANGE) {
            throw new IllegalArgumentException(
                    "snap range must be between 0.25 and 16"
            );
        }
        if (!Double.isFinite(gridSpacing)
                || gridSpacing < MIN_GRID_SPACING
                || gridSpacing > MAX_GRID_SPACING) {
            throw new IllegalArgumentException(
                    "grid spacing must be between 0.25 and 64"
            );
        }
        Objects.requireNonNull(priority, "priority");
        if (!Double.isFinite(guidelineRange)
                || guidelineRange < MIN_GUIDELINE_RANGE
                || guidelineRange > MAX_GUIDELINE_RANGE) {
            throw new IllegalArgumentException(
                    "guideline range must be between 1 and 4096"
            );
        }
    }
}
