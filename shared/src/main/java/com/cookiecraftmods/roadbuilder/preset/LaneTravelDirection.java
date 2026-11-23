package com.cookiecraftmods.roadbuilder.preset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Arrays;
import java.util.Optional;

public enum LaneTravelDirection {
    FORWARD("forward"),
    BACKWARD("backward");

    public static final Codec<LaneTravelDirection> CODEC = Codec.STRING.comapFlatMap(
            serializedName -> Arrays.stream(values())
                    .filter(value -> value.serializedName.equals(serializedName))
                    .findFirst()
                    .map(DataResult::success)
                    .orElseGet(() -> DataResult.error(
                            () -> "Unknown lane travel direction '" + serializedName
                                    + "'; expected forward or backward")),
            LaneTravelDirection::serializedName
    );

    private final String serializedName;

    LaneTravelDirection(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public boolean followsReferenceDirection() {
        return this == FORWARD;
    }

    public static Optional<LaneTravelDirection> fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(value -> value.serializedName.equals(serializedName))
                .findFirst();
    }
}
