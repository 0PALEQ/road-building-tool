package com.cookiecraftmods.roadbuilder.preset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Arrays;
import java.util.Optional;

public enum LaneType {
    VEHICLE("vehicle"),
    BUS("bus");

    public static final Codec<LaneType> CODEC = Codec.STRING.comapFlatMap(
            serializedName -> Arrays.stream(values())
                    .filter(value -> value.serializedName.equals(serializedName))
                    .findFirst()
                    .map(DataResult::success)
                    .orElseGet(() -> DataResult.error(
                            () -> "Unknown lane type '" + serializedName + "'")),
            LaneType::serializedName
    );

    private final String serializedName;

    LaneType(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static Optional<LaneType> fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(value -> value.serializedName.equals(serializedName))
                .findFirst();
    }
}
