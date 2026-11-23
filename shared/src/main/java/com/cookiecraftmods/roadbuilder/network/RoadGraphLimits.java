package com.cookiecraftmods.roadbuilder.network;

public record RoadGraphLimits(
        int maximumRoadCount,
        int maximumNodeCount,
        int maximumSegmentCount,
        int maximumLaneCount,
        int maximumConnectorCount
) {
    public static final int DEFAULT_MAXIMUM_ROAD_COUNT = 100_000;
    public static final int DEFAULT_MAXIMUM_NODE_COUNT = 200_000;
    public static final int DEFAULT_MAXIMUM_SEGMENT_COUNT = 200_000;
    public static final int DEFAULT_MAXIMUM_LANE_COUNT = 800_000;
    public static final int DEFAULT_MAXIMUM_CONNECTOR_COUNT = 800_000;

    public RoadGraphLimits {
        requirePositive(maximumRoadCount, "maximumRoadCount");
        requirePositive(maximumNodeCount, "maximumNodeCount");
        requirePositive(maximumSegmentCount, "maximumSegmentCount");
        requirePositive(maximumLaneCount, "maximumLaneCount");
        requirePositive(maximumConnectorCount, "maximumConnectorCount");
    }

    public static RoadGraphLimits defaults() {
        return new RoadGraphLimits(
                DEFAULT_MAXIMUM_ROAD_COUNT,
                DEFAULT_MAXIMUM_NODE_COUNT,
                DEFAULT_MAXIMUM_SEGMENT_COUNT,
                DEFAULT_MAXIMUM_LANE_COUNT,
                DEFAULT_MAXIMUM_CONNECTOR_COUNT
        );
    }

    private static void requirePositive(int value, String name) {
        if (value < 1) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
