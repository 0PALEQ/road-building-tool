package com.cookiecraftmods.roadbuilder.client.tool;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.RoadVector;

import java.util.Objects;

public record CursorRay(RoadPoint origin, RoadVector direction, double maximumDistance) {
    public CursorRay {
        Objects.requireNonNull(origin, "origin");
        direction = Objects.requireNonNull(direction, "direction").normalize();
        if (!Double.isFinite(maximumDistance) || maximumDistance <= 0.0) {
            throw new IllegalArgumentException("maximumDistance must be finite and positive");
        }
    }
}
