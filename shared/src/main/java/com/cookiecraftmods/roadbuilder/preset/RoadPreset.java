package com.cookiecraftmods.roadbuilder.preset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record RoadPreset(
        int schemaVersion,
        int revision,
        double surfaceWidth,
        ResourceLocation surfaceBlock,
        List<LaneComponent> lanes,
        List<RoadSurfaceBand> surfaceBands,
        RoadPresetDisplay display,
        RoadClassification classification,
        RoadIntersectionStyle intersectionStyle,
        RoadMaterialPalette materialPalette
) {
    public static final int CURRENT_SCHEMA_VERSION = 4;
    public static final int EARLIEST_SUPPORTED_SCHEMA_VERSION = 1;
    public static final int MAXIMUM_SURFACE_BLOCK_WIDTH = 64;

    private static final double EDGE_TOLERANCE = 1.0e-9;
    private static final Codec<Serialized> SERIALIZED_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("schema_version").forGetter(Serialized::schemaVersion),
            Codec.INT.fieldOf("revision").forGetter(Serialized::revision),
            Codec.DOUBLE.fieldOf("surface_width").forGetter(Serialized::surfaceWidth),
            ResourceLocation.CODEC.fieldOf("surface_block").forGetter(Serialized::surfaceBlock),
            LaneComponent.CODEC.listOf().fieldOf("lanes").forGetter(Serialized::lanes),
            RoadSurfaceBand.CODEC.listOf()
                    .optionalFieldOf("surface_bands", List.of())
                    .forGetter(Serialized::surfaceBands),
            RoadPresetDisplay.CODEC.optionalFieldOf("display", RoadPresetDisplay.DEFAULT)
                    .forGetter(Serialized::display),
            RoadClassification.CODEC
                    .optionalFieldOf("classification", RoadClassification.DEFAULT)
                    .forGetter(Serialized::classification),
            RoadIntersectionStyle.CODEC.optionalFieldOf("intersection_style")
                    .forGetter(Serialized::intersectionStyle),
            RoadMaterialPalette.CODEC.optionalFieldOf("materials")
                    .forGetter(Serialized::materialPalette)
    ).apply(instance, Serialized::new));

    public static final Codec<RoadPreset> CODEC = SERIALIZED_CODEC.comapFlatMap(
            RoadPreset::decode,
            preset -> new Serialized(
                    preset.schemaVersion,
                    preset.revision,
                    preset.surfaceWidth,
                    preset.surfaceBlock,
                    preset.lanes,
                    preset.surfaceBands,
                    preset.display,
                    preset.classification,
                    Optional.of(preset.intersectionStyle),
                    Optional.of(preset.materialPalette)
            )
    );

    public RoadPreset(
            int schemaVersion,
            int revision,
            double surfaceWidth,
            ResourceLocation surfaceBlock,
            List<LaneComponent> lanes
    ) {
        this(
                schemaVersion,
                revision,
                surfaceWidth,
                surfaceBlock,
                lanes,
                List.of(),
                RoadPresetDisplay.DEFAULT,
                RoadClassification.DEFAULT,
                RoadIntersectionStyle.defaults(surfaceBlock),
                RoadMaterialPalette.defaults(surfaceBlock)
        );
    }

    public RoadPreset(
            int schemaVersion,
            int revision,
            double surfaceWidth,
            ResourceLocation surfaceBlock,
            List<LaneComponent> lanes,
            List<RoadSurfaceBand> surfaceBands
    ) {
        this(
                schemaVersion,
                revision,
                surfaceWidth,
                surfaceBlock,
                lanes,
                surfaceBands,
                RoadPresetDisplay.DEFAULT,
                RoadClassification.DEFAULT,
                RoadIntersectionStyle.defaults(surfaceBlock),
                RoadMaterialPalette.migrate(surfaceBlock, surfaceBands)
        );
    }

    public RoadPreset(
            int schemaVersion,
            int revision,
            double surfaceWidth,
            ResourceLocation surfaceBlock,
            List<LaneComponent> lanes,
            List<RoadSurfaceBand> surfaceBands,
            RoadPresetDisplay display,
            RoadClassification classification,
            RoadIntersectionStyle intersectionStyle
    ) {
        this(
                schemaVersion,
                revision,
                surfaceWidth,
                surfaceBlock,
                lanes,
                surfaceBands,
                display,
                classification,
                intersectionStyle,
                RoadMaterialPalette.migrate(surfaceBlock, surfaceBands)
        );
    }

    public RoadPreset {
        if (schemaVersion < EARLIEST_SUPPORTED_SCHEMA_VERSION
                || schemaVersion > CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported road preset schema version " + schemaVersion
                            + "; expected " + EARLIEST_SUPPORTED_SCHEMA_VERSION
                            + ".." + CURRENT_SCHEMA_VERSION);
        }
        // Older schemas are decoded into the complete current representation.
        schemaVersion = CURRENT_SCHEMA_VERSION;
        if (revision < 1) {
            throw new IllegalArgumentException("road preset revision must be at least 1");
        }
        if (!Double.isFinite(surfaceWidth)
                || surfaceWidth < 1.0
                || surfaceWidth > MAXIMUM_SURFACE_BLOCK_WIDTH
                || surfaceWidth != Math.rint(surfaceWidth)) {
            throw new IllegalArgumentException(
                    "road preset surface width must be a whole block count between 1 and "
                            + MAXIMUM_SURFACE_BLOCK_WIDTH);
        }
        surfaceBlock = Objects.requireNonNull(surfaceBlock, "surfaceBlock");
        materialPalette = Objects.requireNonNull(
                materialPalette,
                "materialPalette"
        );
        surfaceBlock = materialPalette.pavementBlock();
        lanes = List.copyOf(Objects.requireNonNull(lanes, "lanes"));
        if (lanes.isEmpty()) {
            throw new IllegalArgumentException("road preset must contain at least one lane");
        }
        surfaceBands = normalizeSurfaceBands(surfaceBlockWidth(surfaceWidth), surfaceBlock,
                surfaceBands);
        display = Objects.requireNonNull(display, "display");
        classification = Objects.requireNonNull(classification, "classification");
        // Category-only presets must carry highway semantics into the network
        // snapshot, where display metadata is no longer available.
        if (RoadCategory.fromDisplayCategory(display.category()) == RoadCategory.HIGHWAY
                && classification.roadClass() != RoadClass.HIGHWAY) {
            classification = new RoadClassification(
                    RoadClass.HIGHWAY,
                    classification.priorityRank(),
                    classification.nominalSpeed()
            );
        }
        intersectionStyle = Objects.requireNonNull(
                intersectionStyle,
                "intersectionStyle"
        );
        validateLaneLayout(surfaceWidth, lanes);
    }

        public List<LaneComponent> lanes(TrafficHandedness handedness) {
        Objects.requireNonNull(handedness, "handedness");
        if (handedness == TrafficHandedness.RHT) {
            return lanes;
        }
        return lanes.stream()
                .map(lane -> lane.forTrafficHandedness(handedness))
                .toList();
    }

        public int surfaceBlockWidth() {
        return (int) surfaceWidth;
    }

        public List<ResourceLocation> surfaceColumnBlocks() {
        return surfaceColumns().stream()
                .map(RoadSurfaceColumn::block)
                .toList();
    }

        public List<RoadSurfaceColumn> surfaceColumns() {
        return RoadSurfaceColumn.expand(materialPalette.pavementBlocks(), surfaceBands);
    }

    private static List<RoadSurfaceBand> normalizeSurfaceBands(
            int surfaceBlockWidth,
            ResourceLocation surfaceBlock,
            List<RoadSurfaceBand> suppliedBands
    ) {
        Objects.requireNonNull(suppliedBands, "surfaceBands");
        List<RoadSurfaceBand> bands = suppliedBands.isEmpty()
                ? List.of(new RoadSurfaceBand(
                        RoadSurfaceRole.PAVEMENT,
                        surfaceBlockWidth,
                        surfaceBlock
                ))
                : List.copyOf(suppliedBands);
        int totalWidth = 0;
        for (int index = 0; index < bands.size(); index++) {
            RoadSurfaceBand band = Objects.requireNonNull(
                    bands.get(index),
                    "surfaceBands[" + index + "]"
            );
            totalWidth = Math.addExact(totalWidth, band.width());
        }
        if (totalWidth != surfaceBlockWidth) {
            throw new IllegalArgumentException(
                    "road surface band widths must total surface_width "
                            + surfaceBlockWidth + " but total " + totalWidth
            );
        }
        return bands;
    }

    private static int surfaceBlockWidth(double surfaceWidth) {
        return (int) surfaceWidth;
    }

    private static void validateLaneLayout(double surfaceWidth, List<LaneComponent> lanes) {
        double halfSurfaceWidth = surfaceWidth / 2.0;
        for (int index = 0; index < lanes.size(); index++) {
            LaneComponent lane = Objects.requireNonNull(lanes.get(index), "lanes[" + index + "]");
            if (lane.rightEdge() < -halfSurfaceWidth - EDGE_TOLERANCE
                    || lane.leftEdge() > halfSurfaceWidth + EDGE_TOLERANCE) {
                throw new IllegalArgumentException(
                        "lane " + index + " extends outside the road surface");
            }
        }

        List<IndexedLane> orderedLanes = new ArrayList<>(lanes.size());
        for (int index = 0; index < lanes.size(); index++) {
            orderedLanes.add(new IndexedLane(index, lanes.get(index)));
        }
        orderedLanes.sort(Comparator.comparingDouble(value -> value.lane.rightEdge()));
        for (int index = 1; index < orderedLanes.size(); index++) {
            IndexedLane previous = orderedLanes.get(index - 1);
            IndexedLane current = orderedLanes.get(index);
            if (current.lane.rightEdge() < previous.lane.leftEdge() - EDGE_TOLERANCE) {
                throw new IllegalArgumentException(
                        "lanes " + previous.index + " and " + current.index + " overlap");
            }
        }
    }

    private static DataResult<RoadPreset> decode(Serialized serialized) {
        try {
            RoadMaterialPalette materials = serialized.materialPalette
                    .orElseGet(() -> RoadMaterialPalette.migrate(
                            serialized.surfaceBlock,
                            serialized.surfaceBands
                    ));
            return DataResult.success(new RoadPreset(
                    serialized.schemaVersion,
                    serialized.revision,
                    serialized.surfaceWidth,
                    serialized.surfaceBlock,
                    serialized.lanes,
                    serialized.surfaceBands,
                    serialized.display,
                    serialized.classification,
                    serialized.intersectionStyle.orElseGet(() ->
                            RoadIntersectionStyle.defaults(
                                    serialized.surfaceBlock
                            )),
                    materials
            ));
        } catch (IllegalArgumentException | NullPointerException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private record Serialized(
            int schemaVersion,
            int revision,
            double surfaceWidth,
            ResourceLocation surfaceBlock,
            List<LaneComponent> lanes,
            List<RoadSurfaceBand> surfaceBands,
            RoadPresetDisplay display,
            RoadClassification classification,
            Optional<RoadIntersectionStyle> intersectionStyle,
            Optional<RoadMaterialPalette> materialPalette
    ) {
    }

    private record IndexedLane(int index, LaneComponent lane) {
    }
}
