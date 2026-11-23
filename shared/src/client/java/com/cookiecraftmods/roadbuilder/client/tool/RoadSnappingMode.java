package com.cookiecraftmods.roadbuilder.client.tool;

public enum RoadSnappingMode {
    NONE(false, false, false),
    NODES(true, false, false),
    GUIDELINES(false, true, false),
    ANGLES(false, false, true),
    NODES_AND_GUIDELINES(true, true, false),
    NODES_AND_ANGLES(true, false, true),
    GUIDELINES_AND_ANGLES(false, true, true),
    ALL(true, true, true);

    private final boolean nodes;
    private final boolean guidelines;
    private final boolean angles;

    RoadSnappingMode(
            boolean nodes,
            boolean guidelines,
            boolean angles
    ) {
        this.nodes = nodes;
        this.guidelines = guidelines;
        this.angles = angles;
    }

    public boolean snapsNodes() {
        return nodes;
    }

    public boolean snapsAngles() {
        return angles;
    }

    public boolean snapsGuidelines() {
        return guidelines;
    }

    public RoadSnappingMode toggleNodes() {
        return from(!nodes, guidelines, angles);
    }

    public RoadSnappingMode toggleGuidelines() {
        return from(nodes, !guidelines, angles);
    }

    public RoadSnappingMode toggleAngles() {
        return from(nodes, guidelines, !angles);
    }

    public RoadSnappingMode next() {
        return toggleAngles();
    }

    private static RoadSnappingMode from(
            boolean nodes,
            boolean guidelines,
            boolean angles
    ) {
        if (nodes && guidelines && angles) {
            return ALL;
        }
        if (nodes && guidelines) {
            return NODES_AND_GUIDELINES;
        }
        if (nodes && angles) {
            return NODES_AND_ANGLES;
        }
        if (guidelines && angles) {
            return GUIDELINES_AND_ANGLES;
        }
        if (nodes) {
            return NODES;
        }
        if (guidelines) {
            return GUIDELINES;
        }
        return angles ? ANGLES : NONE;
    }
}
