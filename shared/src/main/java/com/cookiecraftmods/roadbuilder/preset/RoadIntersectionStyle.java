package com.cookiecraftmods.roadbuilder.preset;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;
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
        IntersectionControl defaultControl,
        Optional<Boolean> pavementOverride,
        Optional<Boolean> crosswalkOverride
) {
    private static final ResourceLocation LEGACY_DEFAULT_PAVEMENT = MinecraftIds.id("minecraft", "gray_concrete");

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
                            .forGetter(RoadIntersectionStyle::defaultControl),
                    Codec.BOOL.optionalFieldOf("pavement_override")
                            .forGetter(RoadIntersectionStyle::pavementOverride),
                    Codec.BOOL.optionalFieldOf("crosswalk_override")
                            .forGetter(RoadIntersectionStyle::crosswalkOverride)
            ).apply(instance, RoadIntersectionStyle::new));

    public RoadIntersectionStyle(ResourceLocation pavementBlock, Optional<ResourceLocation> markingBlock,
            Optional<ResourceLocation> crosswalkBlock, Optional<ResourceLocation> stopLineBlock,
            boolean defaultCrosswalks, IntersectionControl defaultControl) {
        this(pavementBlock, markingBlock, crosswalkBlock, stopLineBlock, defaultCrosswalks,
                defaultControl, Optional.empty());
    }

    public RoadIntersectionStyle(ResourceLocation pavementBlock, Optional<ResourceLocation> markingBlock,
            Optional<ResourceLocation> crosswalkBlock, Optional<ResourceLocation> stopLineBlock,
            boolean defaultCrosswalks, IntersectionControl defaultControl, Optional<Boolean> pavementOverride) {
        this(pavementBlock, markingBlock, crosswalkBlock, stopLineBlock, defaultCrosswalks,
                defaultControl, pavementOverride, Optional.empty());
    }

    public RoadIntersectionStyle {
        Objects.requireNonNull(pavementBlock, "pavementBlock");
        markingBlock = immutable(markingBlock, "markingBlock");
        crosswalkBlock = immutable(crosswalkBlock, "crosswalkBlock");
        stopLineBlock = immutable(stopLineBlock, "stopLineBlock");
        Objects.requireNonNull(defaultControl, "defaultControl");
        Objects.requireNonNull(pavementOverride, "pavementOverride");
        Objects.requireNonNull(crosswalkOverride, "crosswalkOverride");
    }

    public boolean usesPavementOverride(ResourceLocation roadPavement) {
        // Older presets stored their original default even after changes to the road palette.
        return pavementOverride.orElseGet(() -> !pavementBlock.equals(roadPavement)
                && !pavementBlock.equals(LEGACY_DEFAULT_PAVEMENT));
    }

    public Optional<ResourceLocation> crosswalkMaterialOverride() {
        // Earlier editor presets copied the lane marking into the crosswalk field.
        return crosswalkOverride.orElseGet(() -> !crosswalkBlock.equals(markingBlock))
                ? crosswalkBlock : Optional.empty();
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
