package com.cookiecraftmods.roadbuilder.network;

import com.cookiecraftmods.roadbuilder.preset.LaneComponent;
import com.cookiecraftmods.roadbuilder.preset.RoadPreset;
import com.cookiecraftmods.roadbuilder.preset.RoadMaterialPalette;
import com.cookiecraftmods.roadbuilder.preset.RoadClassification;
import com.cookiecraftmods.roadbuilder.preset.RoadIntersectionStyle;
import com.cookiecraftmods.roadbuilder.preset.RoadSurfaceBand;
import com.cookiecraftmods.roadbuilder.preset.RoadSurfaceColumn;
import com.cookiecraftmods.roadbuilder.preset.RoadSurfaceRole;
import com.cookiecraftmods.roadbuilder.preset.TrafficHandedness;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

public record RoadPresetSnapshot(
        int surfaceBlockWidth,
        ResourceLocation surfaceBlock,
        List<LaneComponent> lanes,
        List<RoadSurfaceBand> surfaceBands,
        RoadClassification classification,
        RoadIntersectionStyle intersectionStyle,
        boolean sunkenRoad
) {
    public RoadPresetSnapshot(
            int surfaceBlockWidth,
            ResourceLocation surfaceBlock,
            List<LaneComponent> lanes
    ) {
        this(
                surfaceBlockWidth,
                surfaceBlock,
                lanes,
                List.of(),
                RoadClassification.DEFAULT,
                RoadIntersectionStyle.defaults(surfaceBlock)
        );
    }

    public RoadPresetSnapshot(
            int surfaceBlockWidth,
            ResourceLocation surfaceBlock,
            List<LaneComponent> lanes,
            List<RoadSurfaceBand> surfaceBands
    ) {
        this(
                surfaceBlockWidth,
                surfaceBlock,
                lanes,
                surfaceBands,
                RoadClassification.DEFAULT,
                RoadIntersectionStyle.defaults(surfaceBlock)
        );
    }

    public RoadPresetSnapshot(int surfaceBlockWidth, ResourceLocation surfaceBlock,
            List<LaneComponent> lanes, List<RoadSurfaceBand> surfaceBands,
            RoadClassification classification, RoadIntersectionStyle intersectionStyle) {
        this(surfaceBlockWidth, surfaceBlock, lanes, surfaceBands, classification, intersectionStyle, false);
    }

    public RoadPresetSnapshot withSunkenRoad(boolean enabled) {
        return new RoadPresetSnapshot(surfaceBlockWidth, surfaceBlock, lanes, surfaceBands,
                classification, intersectionStyle, enabled);
    }

    public RoadPresetSnapshot {
        if (surfaceBlockWidth < 1
                || surfaceBlockWidth > RoadPreset.MAXIMUM_SURFACE_BLOCK_WIDTH) {
            throw new IllegalArgumentException(
                    "surfaceBlockWidth must be between 1 and "
                            + RoadPreset.MAXIMUM_SURFACE_BLOCK_WIDTH
            );
        }
        Objects.requireNonNull(surfaceBlock, "surfaceBlock");
        lanes = List.copyOf(Objects.requireNonNull(lanes, "lanes"));
        surfaceBands = Objects.requireNonNull(surfaceBands, "surfaceBands");
        if (surfaceBands.isEmpty()) {
            surfaceBands = List.of(new RoadSurfaceBand(
                    RoadSurfaceRole.PAVEMENT,
                    surfaceBlockWidth,
                    surfaceBlock
            ));
        } else {
            surfaceBands = List.copyOf(surfaceBands);
        }
        classification = Objects.requireNonNull(
                classification,
                "classification"
        );
        intersectionStyle = Objects.requireNonNull(
                intersectionStyle,
                "intersectionStyle"
        );

        // Reuse the preset's complete cross-section validation without making
        // the snapshot depend on the source preset's revision.
        new RoadPreset(
                RoadPreset.CURRENT_SCHEMA_VERSION,
                1,
                surfaceBlockWidth,
                surfaceBlock,
                lanes,
                surfaceBands,
                com.cookiecraftmods.roadbuilder.preset.RoadPresetDisplay.DEFAULT,
                classification,
                intersectionStyle
        );
    }

    public static RoadPresetSnapshot from(RoadPreset preset) {
        Objects.requireNonNull(preset, "preset");
        var pavement = preset.materialPalette().pavementBlocks();
        var bands = preset.surfaceBands();
        if (!pavement.equals(RoadMaterialPalette.pavementUnderlay(preset.surfaceBlock(), bands))) {
            // Snapshots carry bands independently of the source preset palette.
            bands = bands.stream().map(band -> RoadSurfaceBand.hasUnderlay(band.pattern())
                            && band.underlayBlockSet().isEmpty()
                            ? band.withUnderlayBlockSet(pavement) : band)
                    .toList();
        }
        return new RoadPresetSnapshot(
                preset.surfaceBlockWidth(),
                preset.surfaceBlock(),
                preset.lanes(),
                bands,
                preset.classification(),
                preset.intersectionStyle()
        );
    }

    public List<ResourceLocation> surfaceColumnBlocks() {
        return surfaceColumns().stream()
                .map(RoadSurfaceColumn::block)
                .toList();
    }

    public List<RoadSurfaceColumn> surfaceColumns() {
        return RoadSurfaceColumn.expand(
                RoadMaterialPalette.pavementUnderlay(surfaceBlock, surfaceBands),
                surfaceBands
        );
    }

    public List<RoadSurfaceRole> surfaceColumnRoles() {
        return surfaceBands.stream()
                .flatMap(band -> java.util.stream.IntStream.range(0, band.width())
                        .mapToObj(index -> band.role()))
                .toList();
    }

    public List<LaneComponent> lanes(TrafficHandedness handedness) {
        Objects.requireNonNull(handedness, "handedness");
        return handedness == TrafficHandedness.RHT
                ? lanes
                : lanes.stream().map(lane -> lane.forTrafficHandedness(handedness)).toList();
    }
}
