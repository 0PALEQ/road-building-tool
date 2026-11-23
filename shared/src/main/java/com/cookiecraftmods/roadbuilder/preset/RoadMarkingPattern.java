package com.cookiecraftmods.roadbuilder.preset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Arrays;
import java.util.Optional;

public enum RoadMarkingPattern {
    CONTINUOUS("continuous"),
    DASHED("dashed"),
    DOUBLE_CONTINUOUS("double_continuous");

    public static final double DASH_LENGTH = 3.0;
    public static final double DASH_PERIOD = 6.0;

    public static final Codec<RoadMarkingPattern> CODEC =
            Codec.STRING.comapFlatMap(
                    serializedName -> fromSerializedName(serializedName)
                            .map(DataResult::success)
                            .orElseGet(() -> DataResult.error(
                                    () -> "Unknown road marking pattern '"
                                            + serializedName + "'")),
                    RoadMarkingPattern::serializedName
            );

    private final String serializedName;

    RoadMarkingPattern(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

        public boolean paintsAt(double pathDistance) {
        if (!Double.isFinite(pathDistance) || pathDistance < 0.0) {
            throw new IllegalArgumentException(
                    "pathDistance must be finite and non-negative"
            );
        }
        if (this != DASHED) {
            return true;
        }
        double phase = pathDistance % DASH_PERIOD;
        return phase < DASH_LENGTH;
    }

    public static Optional<RoadMarkingPattern> fromSerializedName(
            String serializedName
    ) {
        return Arrays.stream(values())
                .filter(value -> value.serializedName.equals(serializedName))
                .findFirst();
    }
}
