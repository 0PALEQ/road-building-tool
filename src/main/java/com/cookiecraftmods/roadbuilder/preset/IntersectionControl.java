package com.cookiecraftmods.roadbuilder.preset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Arrays;

public enum IntersectionControl {
    FREE("free"),
    YIELD("yield"),
    STOP("stop");

    public static final Codec<IntersectionControl> CODEC =
            Codec.STRING.comapFlatMap(
                    name -> Arrays.stream(values())
                            .filter(value -> value.serializedName.equals(name))
                            .findFirst()
                            .map(DataResult::success)
                            .orElseGet(() -> DataResult.error(
                                    () -> "Unknown intersection control '" + name + "'")),
                    IntersectionControl::serializedName
            );

    private final String serializedName;

    IntersectionControl(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }
}
