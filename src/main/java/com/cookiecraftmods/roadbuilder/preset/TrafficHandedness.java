package com.cookiecraftmods.roadbuilder.preset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Arrays;
import java.util.Optional;

public enum TrafficHandedness {
    RHT("rht", 1.0),
    LHT("lht", -1.0);

    public static final Codec<TrafficHandedness> CODEC = Codec.STRING.comapFlatMap(
            serializedName -> Arrays.stream(values())
                    .filter(value -> value.serializedName.equals(serializedName))
                    .findFirst()
                    .map(DataResult::success)
                    .orElseGet(() -> DataResult.error(
                            () -> "Unknown traffic handedness '" + serializedName
                                    + "'; expected rht or lht")),
            TrafficHandedness::serializedName
    );

    private final String serializedName;
    private final double offsetMultiplier;

    TrafficHandedness(String serializedName, double offsetMultiplier) {
        this.serializedName = serializedName;
        this.offsetMultiplier = offsetMultiplier;
    }

    public String serializedName() {
        return serializedName;
    }

    public static Optional<TrafficHandedness> fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(value -> value.serializedName.equals(serializedName))
                .findFirst();
    }

        public double resolveOffset(double rhtOffset) {
        if (!Double.isFinite(rhtOffset)) {
            throw new IllegalArgumentException("rhtOffset must be finite");
        }
        double resolved = rhtOffset * offsetMultiplier;
        return resolved == 0.0 ? 0.0 : resolved;
    }
}
