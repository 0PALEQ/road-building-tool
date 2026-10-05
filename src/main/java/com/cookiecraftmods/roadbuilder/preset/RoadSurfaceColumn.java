package com.cookiecraftmods.roadbuilder.preset;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record RoadSurfaceColumn(
        List<ResourceLocation> blocks,
        List<ResourceLocation> underlayBlocks,
        RoadMarkingPattern pattern
) {
    public RoadSurfaceColumn {
        blocks = requireBlocks(blocks, "blocks");
        underlayBlocks = requireBlocks(underlayBlocks, "underlayBlocks");
        Objects.requireNonNull(pattern, "pattern");
        if (pattern == RoadMarkingPattern.DOUBLE_CONTINUOUS) {
            throw new IllegalArgumentException(
                    "double continuous markings must be expanded into three columns"
            );
        }
    }

    public ResourceLocation block() {
        return blocks.get(0);
    }

    public ResourceLocation underlayBlock() {
        return underlayBlocks.get(0);
    }

    public static List<RoadSurfaceColumn> expand(
            ResourceLocation defaultUnderlay,
            List<RoadSurfaceBand> bands
    ) {
        Objects.requireNonNull(defaultUnderlay, "defaultUnderlay");
        bands = List.copyOf(Objects.requireNonNull(bands, "bands"));
        List<RoadSurfaceColumn> columns = new ArrayList<>();
        for (RoadSurfaceBand band : bands) {
            Objects.requireNonNull(band, "bands contains null");
            if (band.pattern() == RoadMarkingPattern.DOUBLE_CONTINUOUS) {
                columns.add(markingColumn(
                        band.blockSet(),
                        List.of(defaultUnderlay),
                        RoadMarkingPattern.CONTINUOUS
                ));
                columns.add(baseColumn(List.of(defaultUnderlay)));
                columns.add(markingColumn(
                        band.blockSet(),
                        List.of(defaultUnderlay),
                        RoadMarkingPattern.CONTINUOUS
                ));
                continue;
            }
            for (int index = 0; index < band.width(); index++) {
                columns.add(band.role() == RoadSurfaceRole.MARKING
                        ? markingColumn(
                                band.blockSet(),
                                List.of(defaultUnderlay),
                                band.pattern()
                        )
                        : baseColumn(band.blockSet()));
            }
        }
        return List.copyOf(columns);
    }

    private static RoadSurfaceColumn markingColumn(
            List<ResourceLocation> blocks,
            List<ResourceLocation> underlays,
            RoadMarkingPattern pattern
    ) {
        return new RoadSurfaceColumn(blocks, underlays, pattern);
    }

    private static RoadSurfaceColumn baseColumn(List<ResourceLocation> blocks) {
        return new RoadSurfaceColumn(
                blocks,
                blocks,
                RoadMarkingPattern.CONTINUOUS
        );
    }


    private static List<ResourceLocation> requireBlocks(
            List<ResourceLocation> blocks,
            String name
    ) {
        blocks = List.copyOf(Objects.requireNonNull(blocks, name));
        if (blocks.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return blocks;
    }
}
