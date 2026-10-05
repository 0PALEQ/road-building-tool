package com.cookiecraftmods.roadbuilder.client.tool;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;

import java.util.Objects;

public record PredictedJunction(
        RoadPoint position,
        boolean atGrade,
        double footprintRadius
) {
    public PredictedJunction(RoadPoint position, boolean atGrade) {
        this(position, atGrade, 0.75);
    }

    public PredictedJunction {
        Objects.requireNonNull(position, "position");
        if (!Double.isFinite(footprintRadius)
                || footprintRadius <= 0.0) {
            throw new IllegalArgumentException(
                    "junction footprint radius must be finite and positive"
            );
        }
    }
}
