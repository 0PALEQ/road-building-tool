package com.cookiecraftmods.roadbuilder.preset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Objects;

public record RoadPresetDisplay(
        String translationKey,
        String fallbackName,
        String category,
        String thumbnailKey
) {
    public static final RoadPresetDisplay DEFAULT = new RoadPresetDisplay(
            "",
            "Road",
            "medium",
            "generated"
    );

    public static final Codec<RoadPresetDisplay> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.optionalFieldOf("translation", "")
                            .forGetter(RoadPresetDisplay::translationKey),
                    Codec.STRING.optionalFieldOf("fallback", "Road")
                            .forGetter(RoadPresetDisplay::fallbackName),
                    Codec.STRING.optionalFieldOf("category", "medium")
                            .forGetter(RoadPresetDisplay::category),
                    Codec.STRING.optionalFieldOf("thumbnail", "generated")
                            .forGetter(RoadPresetDisplay::thumbnailKey)
            ).apply(instance, RoadPresetDisplay::new));

    public RoadPresetDisplay {
        translationKey = bounded(translationKey, "translationKey", 128, true);
        fallbackName = bounded(fallbackName, "fallbackName", 64, false);
        category = bounded(category, "category", 32, false);
        thumbnailKey = bounded(thumbnailKey, "thumbnailKey", 128, false);
    }

    private static String bounded(
            String value,
            String name,
            int maximumLength,
            boolean mayBeEmpty
    ) {
        Objects.requireNonNull(value, name);
        String normalized = value.strip();
        if ((!mayBeEmpty && normalized.isEmpty())
                || normalized.length() > maximumLength) {
            throw new IllegalArgumentException(
                    name + " must contain between "
                            + (mayBeEmpty ? 0 : 1) + " and "
                            + maximumLength + " characters"
            );
        }
        return normalized;
    }
}
