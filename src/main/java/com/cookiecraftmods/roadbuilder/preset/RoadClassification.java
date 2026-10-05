package com.cookiecraftmods.roadbuilder.preset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Objects;

public record RoadClassification(
        RoadClass roadClass,
        int priorityRank,
        double nominalSpeed
) {
    public static final RoadClassification DEFAULT =
            new RoadClassification(RoadClass.LOCAL, 4, 50.0);

    public static final Codec<RoadClassification> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    RoadClass.CODEC.optionalFieldOf("road_class", RoadClass.LOCAL)
                            .forGetter(RoadClassification::roadClass),
                    Codec.INT.optionalFieldOf("priority_rank", 4)
                            .forGetter(RoadClassification::priorityRank),
                    Codec.DOUBLE.optionalFieldOf("nominal_speed", 50.0)
                            .forGetter(RoadClassification::nominalSpeed)
            ).apply(instance, RoadClassification::new));

    public RoadClassification {
        Objects.requireNonNull(roadClass, "roadClass");
        if (priorityRank < 0 || priorityRank > 15) {
            throw new IllegalArgumentException(
                    "priorityRank must be between 0 and 15"
            );
        }
        if (!Double.isFinite(nominalSpeed)
                || nominalSpeed < 0.0
                || nominalSpeed > 500.0) {
            throw new IllegalArgumentException(
                    "nominalSpeed must be finite and between 0 and 500"
            );
        }
    }
}
