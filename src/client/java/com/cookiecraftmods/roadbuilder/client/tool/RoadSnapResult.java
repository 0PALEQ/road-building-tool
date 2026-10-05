package com.cookiecraftmods.roadbuilder.client.tool;

import java.util.List;
import java.util.Objects;

public record RoadSnapResult(
        CursorTarget target,
        List<RoadSnapGuide> guides
) {
    public RoadSnapResult {
        Objects.requireNonNull(target, "target");
        guides = List.copyOf(Objects.requireNonNull(guides, "guides"));
    }

    public static RoadSnapResult unsnapped(CursorTarget target) {
        return new RoadSnapResult(target, List.of());
    }
}
