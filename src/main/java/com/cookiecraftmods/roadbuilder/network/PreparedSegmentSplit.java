package com.cookiecraftmods.roadbuilder.network;

import java.util.List;
import java.util.Objects;

public record PreparedSegmentSplit(
        RoadSegment original,
        List<RoadSegment> replacements
) {
    public PreparedSegmentSplit {
        Objects.requireNonNull(original, "original");
        replacements = List.copyOf(Objects.requireNonNull(replacements, "replacements"));
        if (replacements.size() < 2) {
            throw new IllegalArgumentException("a segment split requires at least two replacements");
        }
    }
}
