package com.cookiecraftmods.roadbuilder.preset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

public record RoadMaterialPalette(
        ResourceLocation pavementBlock,
        ResourceLocation markingBlock,
        ResourceLocation sidewalkBlock,
        ResourceLocation grassMedianBlock,
        ResourceLocation concreteMedianBlock,
        List<ResourceLocation> pavementBlocks,
        List<ResourceLocation> markingBlocks,
        List<ResourceLocation> sidewalkBlocks,
        List<ResourceLocation> grassMedianBlocks,
        List<ResourceLocation> concreteMedianBlocks,
        List<ResourceLocation> railingBlocks
) {
    private static final ResourceLocation DEFAULT_MARKING = id("white_concrete");
    private static final ResourceLocation DEFAULT_SIDEWALK = id("smooth_stone");
    private static final ResourceLocation DEFAULT_GRASS_MEDIAN = id("grass_block");
    private static final ResourceLocation DEFAULT_CONCRETE_MEDIAN = id("light_gray_concrete");

    public static final Codec<RoadMaterialPalette> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    ResourceLocation.CODEC.fieldOf("pavement")
                            .forGetter(RoadMaterialPalette::pavementBlock),
                    ResourceLocation.CODEC.fieldOf("marking")
                            .forGetter(RoadMaterialPalette::markingBlock),
                    ResourceLocation.CODEC.fieldOf("sidewalk")
                            .forGetter(RoadMaterialPalette::sidewalkBlock),
                    ResourceLocation.CODEC.fieldOf("grass_median")
                            .forGetter(RoadMaterialPalette::grassMedianBlock),
                    ResourceLocation.CODEC.fieldOf("concrete_median")
                            .forGetter(RoadMaterialPalette::concreteMedianBlock),
                    ResourceLocation.CODEC.listOf()
                            .optionalFieldOf("pavement_set", List.of())
                            .forGetter(RoadMaterialPalette::pavementBlocks),
                    ResourceLocation.CODEC.listOf()
                            .optionalFieldOf("marking_set", List.of())
                            .forGetter(RoadMaterialPalette::markingBlocks),
                    ResourceLocation.CODEC.listOf()
                            .optionalFieldOf("sidewalk_set", List.of())
                            .forGetter(RoadMaterialPalette::sidewalkBlocks),
                    ResourceLocation.CODEC.listOf()
                            .optionalFieldOf("grass_median_set", List.of())
                            .forGetter(RoadMaterialPalette::grassMedianBlocks),
                    ResourceLocation.CODEC.listOf()
                            .optionalFieldOf("concrete_median_set", List.of())
                            .forGetter(RoadMaterialPalette::concreteMedianBlocks),
                    ResourceLocation.CODEC.listOf()
                            .optionalFieldOf("railing_set", List.of(DEFAULT_SIDEWALK))
                            .forGetter(RoadMaterialPalette::railingBlocks)
            ).apply(instance, RoadMaterialPalette::new));

    public RoadMaterialPalette(
            ResourceLocation pavementBlock, ResourceLocation markingBlock,
            ResourceLocation sidewalkBlock, ResourceLocation grassMedianBlock,
            ResourceLocation concreteMedianBlock,
            List<ResourceLocation> pavementBlocks, List<ResourceLocation> markingBlocks,
            List<ResourceLocation> sidewalkBlocks, List<ResourceLocation> grassMedianBlocks,
            List<ResourceLocation> concreteMedianBlocks
    ) {
        this(pavementBlock, markingBlock, sidewalkBlock, grassMedianBlock,
                concreteMedianBlock, pavementBlocks, markingBlocks, sidewalkBlocks,
                grassMedianBlocks, concreteMedianBlocks, List.of(DEFAULT_SIDEWALK));
    }

    public RoadMaterialPalette(
            ResourceLocation pavementBlock,
            ResourceLocation markingBlock,
            ResourceLocation sidewalkBlock,
            ResourceLocation grassMedianBlock,
            ResourceLocation concreteMedianBlock
    ) {
        this(
                pavementBlock,
                markingBlock,
                sidewalkBlock,
                grassMedianBlock,
                concreteMedianBlock,
                List.of(pavementBlock),
                List.of(markingBlock),
                List.of(sidewalkBlock),
                List.of(grassMedianBlock),
                List.of(concreteMedianBlock)
        );
    }

    public RoadMaterialPalette {
        railingBlocks = List.copyOf(Objects.requireNonNull(railingBlocks, "railingBlocks"));
        if (railingBlocks.isEmpty()) {
            throw new IllegalArgumentException("railing block set must not be empty");
        }
        railingBlocks = normalize(railingBlocks.get(0), railingBlocks, "railingBlocks");
        Objects.requireNonNull(pavementBlock, "pavementBlock");
        Objects.requireNonNull(markingBlock, "markingBlock");
        Objects.requireNonNull(sidewalkBlock, "sidewalkBlock");
        Objects.requireNonNull(grassMedianBlock, "grassMedianBlock");
        Objects.requireNonNull(concreteMedianBlock, "concreteMedianBlock");
        pavementBlocks = normalize(pavementBlock, pavementBlocks, "pavementBlocks");
        markingBlocks = normalize(markingBlock, markingBlocks, "markingBlocks");
        sidewalkBlocks = normalize(sidewalkBlock, sidewalkBlocks, "sidewalkBlocks");
        grassMedianBlocks = normalize(
                grassMedianBlock,
                grassMedianBlocks,
                "grassMedianBlocks"
        );
        concreteMedianBlocks = normalize(
                concreteMedianBlock,
                concreteMedianBlocks,
                "concreteMedianBlocks"
        );
    }

    public static RoadMaterialPalette defaults(ResourceLocation pavementBlock) {
        return new RoadMaterialPalette(
                pavementBlock,
                DEFAULT_MARKING,
                DEFAULT_SIDEWALK,
                DEFAULT_GRASS_MEDIAN,
                DEFAULT_CONCRETE_MEDIAN
        );
    }

    public static RoadMaterialPalette migrate(
            ResourceLocation pavementBlock,
            List<RoadSurfaceBand> bands
    ) {
        RoadMaterialPalette defaults = defaults(pavementBlock);
        List<ResourceLocation> pavement = List.of(pavementBlock);
        List<ResourceLocation> marking = firstBlockSet(
                bands, RoadSurfaceRole.MARKING, defaults.markingBlocks
        );
        List<ResourceLocation> sidewalk = firstBlockSet(
                bands, RoadSurfaceRole.SIDEWALK, defaults.sidewalkBlocks
        );
        List<ResourceLocation> grass = firstBlockSet(
                bands, RoadSurfaceRole.GRASS_MEDIAN, defaults.grassMedianBlocks
        );
        List<ResourceLocation> concrete = firstBlockSet(
                bands, RoadSurfaceRole.CONCRETE_MEDIAN,
                defaults.concreteMedianBlocks
        );
        return new RoadMaterialPalette(
                pavement.get(0), marking.get(0), sidewalk.get(0),
                grass.get(0), concrete.get(0), pavement, marking,
                sidewalk, grass, concrete,
                firstBlockSet(bands, RoadSurfaceRole.RAILING, defaults.railingBlocks)
        );
    }

    public ResourceLocation blockFor(RoadSurfaceRole role) {
        return switch (Objects.requireNonNull(role, "role")) {
            case PAVEMENT -> pavementBlock;
            case MARKING -> markingBlock;
            case SIDEWALK -> sidewalkBlock;
            case GRASS_MEDIAN -> grassMedianBlock;
            case CONCRETE_MEDIAN -> concreteMedianBlock;
            case RAILING -> railingBlocks.get(0);
        };
    }

    public List<ResourceLocation> blockSetFor(RoadSurfaceRole role) {
        return switch (Objects.requireNonNull(role, "role")) {
            case PAVEMENT -> pavementBlocks;
            case MARKING -> markingBlocks;
            case SIDEWALK -> sidewalkBlocks;
            case GRASS_MEDIAN -> grassMedianBlocks;
            case CONCRETE_MEDIAN -> concreteMedianBlocks;
            case RAILING -> railingBlocks;
        };
    }

    public RoadMaterialPalette withBlock(
            RoadSurfaceRole role,
            ResourceLocation replacement
    ) {
        return withBlockSet(role, List.of(replacement));
    }

    public RoadMaterialPalette withBlockSet(
            RoadSurfaceRole role,
            List<ResourceLocation> replacement
    ) {
        replacement = List.copyOf(Objects.requireNonNull(
                replacement,
                "replacement"
        ));
        if (replacement.isEmpty()) {
            throw new IllegalArgumentException("material block set must not be empty");
        }
        ResourceLocation primary = replacement.get(0);
        Objects.requireNonNull(replacement, "replacement");
        return switch (Objects.requireNonNull(role, "role")) {
            case PAVEMENT -> new RoadMaterialPalette(primary, markingBlock,
                    sidewalkBlock, grassMedianBlock, concreteMedianBlock,
                    replacement, markingBlocks, sidewalkBlocks,
                    grassMedianBlocks, concreteMedianBlocks, railingBlocks);
            case MARKING -> new RoadMaterialPalette(pavementBlock, primary,
                    sidewalkBlock, grassMedianBlock, concreteMedianBlock,
                    pavementBlocks, replacement, sidewalkBlocks,
                    grassMedianBlocks, concreteMedianBlocks, railingBlocks);
            case SIDEWALK -> new RoadMaterialPalette(pavementBlock, markingBlock,
                    primary, grassMedianBlock, concreteMedianBlock,
                    pavementBlocks, markingBlocks, replacement,
                    grassMedianBlocks, concreteMedianBlocks, railingBlocks);
            case GRASS_MEDIAN -> new RoadMaterialPalette(pavementBlock, markingBlock,
                    sidewalkBlock, primary, concreteMedianBlock,
                    pavementBlocks, markingBlocks, sidewalkBlocks,
                    replacement, concreteMedianBlocks, railingBlocks);
            case CONCRETE_MEDIAN -> new RoadMaterialPalette(pavementBlock, markingBlock,
                    sidewalkBlock, grassMedianBlock, primary,
                    pavementBlocks, markingBlocks, sidewalkBlocks,
                    grassMedianBlocks, replacement, railingBlocks);
            case RAILING -> new RoadMaterialPalette(pavementBlock, markingBlock,
                    sidewalkBlock, grassMedianBlock, concreteMedianBlock,
                    pavementBlocks, markingBlocks, sidewalkBlocks,
                    grassMedianBlocks, concreteMedianBlocks, replacement);
        };
    }

    private static List<ResourceLocation> normalize(
            ResourceLocation primary,
            List<ResourceLocation> supplied,
            String name
    ) {
        java.util.LinkedHashSet<ResourceLocation> unique =
                new java.util.LinkedHashSet<>();
        unique.add(primary);
        for (ResourceLocation block : Objects.requireNonNull(supplied, name)) {
            unique.add(Objects.requireNonNull(block, name + " contains null"));
        }
        if (unique.size() > 16) {
            throw new IllegalArgumentException(
                    "a global road material set may contain at most 16 blocks"
            );
        }
        return List.copyOf(unique);
    }

    private static ResourceLocation firstBlock(
            List<RoadSurfaceBand> bands,
            RoadSurfaceRole role,
            ResourceLocation fallback
    ) {
        return bands.stream()
                .filter(band -> band.role() == role)
                .map(RoadSurfaceBand::block)
                .findFirst()
                .orElse(fallback);
    }

    private static List<ResourceLocation> firstBlockSet(
            List<RoadSurfaceBand> bands,
            RoadSurfaceRole role,
            List<ResourceLocation> fallback
    ) {
        return bands.stream()
                .filter(band -> band.role() == role)
                .map(RoadSurfaceBand::blockSet)
                .findFirst()
                .orElse(fallback);
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation("minecraft", path);
    }
}
