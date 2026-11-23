package com.cookiecraftmods.roadbuilder.network;

import com.cookiecraftmods.roadbuilder.preset.IntersectionControl;

import java.util.Objects;

public record JunctionApproachOverride(
        IntersectionControl control,
        boolean crosswalk
) {
    public JunctionApproachOverride {
        Objects.requireNonNull(control, "control");
    }
}
