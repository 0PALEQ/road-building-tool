package com.cookiecraftmods.roadbuilder.preset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.List;

public record RoadSurfaceBand(
        RoadSurfaceRole role,
        int width,
        ResourceLocation block,
        RoadMarkingPattern pattern,
        List<ResourceLocation> blockSet,
        List<ResourceLocation> underlayBlockSet
) {
    private static final Codec<Serialized> SERIALIZED_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    RoadSurfaceRole.CODEC.fieldOf("role").forGetter(Serialized::role),
                    Codec.INT.fieldOf("width").forGetter(Serialized::width),
                    ResourceLocation.CODEC.fieldOf("block").forGetter(Serialized::block),
                    RoadMarkingPattern.CODEC
                            .optionalFieldOf(
                                    "pattern",
                                    RoadMarkingPattern.CONTINUOUS
                            )
                            .forGetter(Serialized::pattern),
                    ResourceLocation.CODEC.listOf()
                            .optionalFieldOf("block_set", List.of())
                            .forGetter(Serialized::blockSet),
                    ResourceLocation.CODEC.listOf()
                            .optionalFieldOf("underlay_block_set", List.of())
                            .forGetter(Serialized::underlayBlockSet)
            ).apply(instance, Serialized::new));

    public static final Codec<RoadSurfaceBand> CODEC = SERIALIZED_CODEC.comapFlatMap(
            RoadSurfaceBand::decode,
            band -> new Serialized(
                    band.role,
                    band.width,
                    band.block,
                    band.pattern,
                    band.blockSet,
                    band.underlayBlockSet
            )
    );

    public RoadSurfaceBand(
            RoadSurfaceRole role, int width, ResourceLocation block,
            RoadMarkingPattern pattern, List<ResourceLocation> blockSet
    ) {
        this(role, width, block, pattern, blockSet, List.of());
    }

    public RoadSurfaceBand(
            RoadSurfaceRole role,
            int width,
            ResourceLocation block,
            RoadMarkingPattern pattern
    ) {
        this(role, width, block, pattern, List.of(block));
    }

    public RoadSurfaceBand(
            RoadSurfaceRole role,
            int width,
            ResourceLocation block
    ) {
        this(role, width, block, RoadMarkingPattern.CONTINUOUS);
    }

    public RoadSurfaceBand {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(pattern, "pattern");
        blockSet = normalizeBlockSet(block, blockSet);
        underlayBlockSet = List.copyOf(underlayBlockSet);
        if (!underlayBlockSet.isEmpty()) {
            if (!hasUnderlay(pattern)) {
                throw new IllegalArgumentException("only dashed and double markings have pavement gaps");
            }
            underlayBlockSet = normalizeBlockSet(underlayBlockSet.get(0), underlayBlockSet);
        }
        if (width < 1 || width > RoadPreset.MAXIMUM_SURFACE_BLOCK_WIDTH) {
            throw new IllegalArgumentException(
                    "road surface band width must be between 1 and "
                            + RoadPreset.MAXIMUM_SURFACE_BLOCK_WIDTH
            );
        }
        if (role != RoadSurfaceRole.MARKING
                && pattern != RoadMarkingPattern.CONTINUOUS) {
            throw new IllegalArgumentException(
                    "only marking bands may use a non-continuous pattern"
            );
        }
        if (pattern == RoadMarkingPattern.DOUBLE_CONTINUOUS && width != 3) {
            throw new IllegalArgumentException(
                    "double continuous road markings must be exactly 3 blocks wide"
            );
        }
    }

    public static boolean hasUnderlay(RoadMarkingPattern pattern) {
        return pattern == RoadMarkingPattern.DASHED
                || pattern == RoadMarkingPattern.DOUBLE_CONTINUOUS;
    }

    public RoadSurfaceBand withUnderlayBlockSet(List<ResourceLocation> blocks) {
        return new RoadSurfaceBand(role, width, block, pattern, blockSet, blocks);
    }

    private static DataResult<RoadSurfaceBand> decode(Serialized serialized) {
        try {
            return DataResult.success(new RoadSurfaceBand(
                    serialized.role,
                    serialized.width,
                    serialized.block,
                    serialized.pattern,
                    serialized.blockSet,
                    serialized.underlayBlockSet
            ));
        } catch (IllegalArgumentException | NullPointerException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private record Serialized(
            RoadSurfaceRole role,
            int width,
            ResourceLocation block,
            RoadMarkingPattern pattern,
            List<ResourceLocation> blockSet,
            List<ResourceLocation> underlayBlockSet
    ) {
    }

    private static List<ResourceLocation> normalizeBlockSet(
            ResourceLocation primary,
            List<ResourceLocation> supplied
    ) {
        Objects.requireNonNull(supplied, "blockSet");
        java.util.LinkedHashSet<ResourceLocation> unique =
                new java.util.LinkedHashSet<>();
        unique.add(primary);
        for (int index = 0; index < supplied.size(); index++) {
            unique.add(Objects.requireNonNull(
                    supplied.get(index),
                    "blockSet[" + index + "]"
            ));
        }
        if (unique.size() > 16) {
            throw new IllegalArgumentException(
                    "a road surface block set may contain at most 16 blocks"
            );
        }
        return List.copyOf(unique);
    }
}
