package com.cookiecraftmods.roadbuilder.preset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Arrays;
import java.util.Optional;

public enum RoadSurfaceRole {
    PAVEMENT("pavement"),
    MARKING("marking"),
    SIDEWALK("sidewalk"),
    GRASS_MEDIAN("grass_median"),
    CONCRETE_MEDIAN("concrete_median");

    public static final Codec<RoadSurfaceRole> CODEC = Codec.STRING.comapFlatMap(
            serializedName -> fromSerializedName(serializedName)
                    .map(DataResult::success)
                    .orElseGet(() -> DataResult.error(
                            () -> "Unknown road surface role '" + serializedName + "'")),
            RoadSurfaceRole::serializedName
    );

    private final String serializedName;

    RoadSurfaceRole(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static Optional<RoadSurfaceRole> fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(value -> value.serializedName.equals(serializedName))
                .findFirst();
    }
}
