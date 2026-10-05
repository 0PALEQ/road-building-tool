package com.cookiecraftmods.roadbuilder.client.tool;

import java.util.List;

public enum RoadSnapPriority {
    TANGENT_ANGLE_GRID(List.of(Target.TANGENT, Target.ANGLE, Target.GRID)),
    TANGENT_GRID_ANGLE(List.of(Target.TANGENT, Target.GRID, Target.ANGLE)),
    GRID_TANGENT_ANGLE(List.of(Target.GRID, Target.TANGENT, Target.ANGLE)),
    ANGLE_TANGENT_GRID(List.of(Target.ANGLE, Target.TANGENT, Target.GRID));

    private final List<Target> targets;

    RoadSnapPriority(List<Target> targets) {
        this.targets = targets;
    }

    public List<Target> targets() {
        return targets;
    }

    public enum Target {
        TANGENT,
        GRID,
        ANGLE
    }
}
