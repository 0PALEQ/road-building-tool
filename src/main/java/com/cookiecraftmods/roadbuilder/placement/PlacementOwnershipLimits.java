package com.cookiecraftmods.roadbuilder.placement;

public record PlacementOwnershipLimits(
        int maximumPositionCount,
        int maximumTotalLayerCount,
        int maximumStackDepth
) {
    public static final int DEFAULT_MAXIMUM_POSITION_COUNT = 8_000_000;
    public static final int DEFAULT_MAXIMUM_TOTAL_LAYER_COUNT = 16_000_000;
    public static final int DEFAULT_MAXIMUM_STACK_DEPTH = 256;

    public PlacementOwnershipLimits {
        if (maximumPositionCount <= 0 || maximumTotalLayerCount <= 0 || maximumStackDepth <= 0) {
            throw new IllegalArgumentException("Ownership limits must be greater than zero");
        }
        if (maximumTotalLayerCount < maximumPositionCount) {
            throw new IllegalArgumentException(
                    "maximumTotalLayerCount must be at least maximumPositionCount"
            );
        }
    }

    public static PlacementOwnershipLimits defaults() {
        return new PlacementOwnershipLimits(
                DEFAULT_MAXIMUM_POSITION_COUNT,
                DEFAULT_MAXIMUM_TOTAL_LAYER_COUNT,
                DEFAULT_MAXIMUM_STACK_DEPTH
        );
    }
}
