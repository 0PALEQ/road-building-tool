package com.cookiecraftmods.roadbuilder.preset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Arrays;

public enum RoadClass {
    ALLEY("alley", 0),
    LOCAL("local", 1),
    COLLECTOR("collector", 2),
    ARTERIAL("arterial", 3),
    HIGHWAY("highway", 4);

    public static final Codec<RoadClass> CODEC = Codec.STRING.comapFlatMap(
            name -> Arrays.stream(values())
                    .filter(value -> value.serializedName.equals(name))
                    .findFirst()
                    .map(DataResult::success)
                    .orElseGet(() -> DataResult.error(
                            () -> "Unknown road class '" + name + "'")),
            RoadClass::serializedName
    );

    private final String serializedName;
    private final int hierarchy;

    RoadClass(String serializedName, int hierarchy) {
        this.serializedName = serializedName;
        this.hierarchy = hierarchy;
    }

    public String serializedName() {
        return serializedName;
    }

    public int hierarchy() {
        return hierarchy;
    }
}
