package com.cookiecraftmods.roadbuilder.placement;

public enum MaterialLayer {
    TERRAIN_SUPPORT(0),
    ROAD_STRUCTURE(1),
    SEGMENT_SURFACE(2),
    JUNCTION_SURFACE(3),
    SEGMENT_MARKING(4),
    JUNCTION_MARKING(5),
    CLEARANCE(6),
    TUNNEL(7);

    private final int priority;

    MaterialLayer(int priority) {
        this.priority = priority;
    }

    public int priority() {
        return priority;
    }
}
