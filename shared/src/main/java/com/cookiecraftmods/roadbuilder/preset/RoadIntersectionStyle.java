package com.cookiecraftmods.roadbuilder.preset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;

public record RoadIntersectionStyle(
        ResourceLocation pavementBlock,
        Optional<ResourceLocation> markingBlock,
        Optional<ResourceLocation> crosswalkBlock,
        Optional<ResourceLocation> stopLineBlock,
        boolean defaultCrosswalks,
        IntersectionControl defaultControl
) {
    public static final Codec<RoadIntersectionStyle> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    ResourceLocation.CODEC.fieldOf("pavement_block")
                            .forGetter(RoadIntersectionStyle::pavementBlock),
                    ResourceLocation.CODEC.optionalFieldOf("marking_block")
                            .forGetter(RoadIntersectionStyle::markingBlock),
                    ResourceLocation.CODEC.optionalFieldOf("crosswalk_block")
                            .forGetter(RoadIntersectionStyle::crosswalkBlock),
                    ResourceLocation.CODEC.optionalFieldOf("stop_line_block")
                            .forGetter(RoadIntersectionStyle::stopLineBlock),
                    Codec.BOOL.optionalFieldOf("default_crosswalks", true)
                            .forGetter(RoadIntersectionStyle::defaultCrosswalks),
                    IntersectionControl.CODEC
                            .optionalFieldOf("default_control", IntersectionControl.FREE)
                            .forGetter(RoadIntersectionStyle::defaultControl)
            ).apply(instance, RoadIntersectionStyle::new));

    public RoadIntersectionStyle {
        Objects.requireNonNull(pavementBlock, "pavementBlock");
        markingBlock = immutable(markingBlock, "markingBlock");
        crosswalkBlock = immutable(crosswalkBlock, "crosswalkBlock");
        stopLineBlock = immutable(stopLineBlock, "stopLineBlock");
        Objects.requireNonNull(defaultControl, "defaultControl");
    }

    public static RoadIntersectionStyle defaults(ResourceLocation pavementBlock) {
        return new RoadIntersectionStyle(
                pavementBlock,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                true,
                IntersectionControl.FREE
        );
    }

    private static <T> Optional<T> immutable(Optional<T> value, String name) {
        Objects.requireNonNull(value, name);
        value.ifPresent(item -> Objects.requireNonNull(item, name + " value"));
        return value;
    }
}
