package com.cookiecraftmods.roadbuilder.client.ui;

import com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection;
import com.cookiecraftmods.roadbuilder.preset.LaneType;
import com.cookiecraftmods.roadbuilder.preset.RoadMarkingPattern;
import com.cookiecraftmods.roadbuilder.preset.RoadPreset;
import com.cookiecraftmods.roadbuilder.preset.RoadSurfaceBand;
import com.cookiecraftmods.roadbuilder.preset.RoadSurfaceRole;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.List;

public record RoadPresetEditorComponent(
        Kind kind,
        int width,
        ResourceLocation block,
        LaneTravelDirection direction,
        boolean inheritsRoadMaterial,
        List<ResourceLocation> blockSet
) {
    public static final int DEFAULT_LANE_WIDTH = 3;

    public RoadPresetEditorComponent {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(block, "block");
        blockSet = normalizeBlockSet(block, blockSet);
        if (width < 1 || width > RoadPreset.MAXIMUM_SURFACE_BLOCK_WIDTH) {
            throw new IllegalArgumentException(
                    "component width must be between 1 and "
                            + RoadPreset.MAXIMUM_SURFACE_BLOCK_WIDTH
            );
        }
        if (kind.isLane()) {
            Objects.requireNonNull(direction, "direction");
        } else if (direction != null) {
            throw new IllegalArgumentException(
                    "only vehicle lanes can have a travel direction"
            );
        }
        if (kind == Kind.DOUBLE_CONTINUOUS_MARKING && width != 3) {
            throw new IllegalArgumentException(
                    "a double continuous marking is exactly 3 blocks wide"
            );
        }
    }

    public RoadPresetEditorComponent(
            Kind kind,
            int width,
            ResourceLocation block,
            LaneTravelDirection direction,
            boolean inheritsRoadMaterial
    ) {
        this(kind, width, block, direction, inheritsRoadMaterial,
                List.of(block));
    }

    public RoadPresetEditorComponent(
            Kind kind,
            int width,
            ResourceLocation block,
            LaneTravelDirection direction
    ) {
        this(kind, width, block, direction, false);
    }

    public static RoadPresetEditorComponent vehicleLane(
            int width,
            ResourceLocation block,
            LaneTravelDirection direction
    ) {
        return new RoadPresetEditorComponent(
                Kind.VEHICLE_LANE,
                width,
                block,
                direction,
                false
        );
    }

    public static RoadPresetEditorComponent inheritedVehicleLane(
            int width,
            ResourceLocation block,
            LaneTravelDirection direction
    ) {
        return new RoadPresetEditorComponent(
                Kind.VEHICLE_LANE,
                width,
                block,
                direction,
                true
        );
    }

    public static RoadPresetEditorComponent inheritedBusLane(
            int width,
            ResourceLocation block,
            LaneTravelDirection direction
    ) {
        return new RoadPresetEditorComponent(
                Kind.BUS_LANE,
                width,
                block,
                direction,
                true
        );
    }

    public static RoadPresetEditorComponent surface(
            Kind kind,
            int width,
            ResourceLocation block
    ) {
        return new RoadPresetEditorComponent(kind, width, block, null, false);
    }

    public static RoadPresetEditorComponent inheritedSurface(
            Kind kind,
            int width,
            ResourceLocation block
    ) {
        return new RoadPresetEditorComponent(kind, width, block, null, true);
    }

    public boolean isVehicleLane() {
        return kind.isLane();
    }

    public boolean hasAdjustableWidth() {
        return kind != Kind.DOUBLE_CONTINUOUS_MARKING;
    }

    public RoadPresetEditorComponent withWidth(int replacementWidth) {
        return new RoadPresetEditorComponent(
                kind,
                replacementWidth,
                block,
                direction,
                inheritsRoadMaterial,
                blockSet
        );
    }

    public RoadPresetEditorComponent withBlock(
            ResourceLocation replacementBlock
    ) {
        return new RoadPresetEditorComponent(
                kind,
                width,
                replacementBlock,
                direction,
                false,
                List.of(replacementBlock)
        );
    }

    public RoadPresetEditorComponent withBlockSet(
            List<ResourceLocation> replacementBlocks
    ) {
        replacementBlocks = List.copyOf(replacementBlocks);
        if (replacementBlocks.isEmpty()) {
            throw new IllegalArgumentException("block set must not be empty");
        }
        return new RoadPresetEditorComponent(
                kind,
                width,
                replacementBlocks.get(0),
                direction,
                false,
                replacementBlocks
        );
    }

    public RoadPresetEditorComponent withInheritedBlock(
            ResourceLocation roadBlock
    ) {
        return new RoadPresetEditorComponent(
                kind,
                width,
                roadBlock,
                direction,
                true,
                List.of(roadBlock)
        );
    }

    public RoadPresetEditorComponent withInheritedBlockSet(
            List<ResourceLocation> roadBlocks
    ) {
        roadBlocks = List.copyOf(roadBlocks);
        if (roadBlocks.isEmpty()) {
            throw new IllegalArgumentException("road block set must not be empty");
        }
        return new RoadPresetEditorComponent(
                kind,
                width,
                roadBlocks.get(0),
                direction,
                true,
                roadBlocks
        );
    }

    public RoadPresetEditorComponent withDirection(
            LaneTravelDirection replacementDirection
    ) {
        if (!isVehicleLane()) {
            throw new IllegalStateException(
                    "only a vehicle lane has a travel direction"
            );
        }
        return new RoadPresetEditorComponent(
                kind,
                width,
                block,
                replacementDirection,
                inheritsRoadMaterial,
                blockSet
        );
    }

    public RoadSurfaceBand toSurfaceBand() {
        return new RoadSurfaceBand(
                kind.surfaceRole(),
                width,
                block,
                kind.markingPattern(),
                blockSet
        );
    }

    private static List<ResourceLocation> normalizeBlockSet(
            ResourceLocation primary,
            List<ResourceLocation> supplied
    ) {
        java.util.LinkedHashSet<ResourceLocation> unique =
                new java.util.LinkedHashSet<>();
        unique.add(primary);
        unique.addAll(Objects.requireNonNull(supplied, "blockSet"));
        if (unique.size() > 16) {
            throw new IllegalArgumentException(
                    "a component block set may contain at most 16 blocks"
            );
        }
        return List.copyOf(unique);
    }

    public enum Kind {
        VEHICLE_LANE(
                RoadSurfaceRole.PAVEMENT,
                RoadMarkingPattern.CONTINUOUS,
                LaneType.VEHICLE
        ),
        BUS_LANE(
                RoadSurfaceRole.PAVEMENT,
                RoadMarkingPattern.CONTINUOUS,
                LaneType.BUS
        ),
        PAVEMENT(
                RoadSurfaceRole.PAVEMENT,
                RoadMarkingPattern.CONTINUOUS,
                null
        ),
        SOLID_MARKING(
                RoadSurfaceRole.MARKING,
                RoadMarkingPattern.CONTINUOUS,
                null
        ),
        DASHED_MARKING(
                RoadSurfaceRole.MARKING,
                RoadMarkingPattern.DASHED,
                null
        ),
        DOUBLE_CONTINUOUS_MARKING(
                RoadSurfaceRole.MARKING,
                RoadMarkingPattern.DOUBLE_CONTINUOUS,
                null
        ),
        SIDEWALK(
                RoadSurfaceRole.SIDEWALK,
                RoadMarkingPattern.CONTINUOUS,
                null
        ),
        GRASS_MEDIAN(
                RoadSurfaceRole.GRASS_MEDIAN,
                RoadMarkingPattern.CONTINUOUS,
                null
        ),
        CONCRETE_MEDIAN(
                RoadSurfaceRole.CONCRETE_MEDIAN,
                RoadMarkingPattern.CONTINUOUS,
                null
        );

        private final RoadSurfaceRole surfaceRole;
        private final RoadMarkingPattern markingPattern;
        private final LaneType laneType;

        Kind(
                RoadSurfaceRole surfaceRole,
                RoadMarkingPattern markingPattern,
                LaneType laneType
        ) {
            this.surfaceRole = surfaceRole;
            this.markingPattern = markingPattern;
            this.laneType = laneType;
        }

        public RoadSurfaceRole surfaceRole() {
            return surfaceRole;
        }

        public RoadMarkingPattern markingPattern() {
            return markingPattern;
        }

        public boolean isLane() {
            return laneType != null;
        }

        public LaneType laneType() {
            if (laneType == null) {
                throw new IllegalStateException("component is not a lane");
            }
            return laneType;
        }

        public static Kind fromBand(RoadSurfaceBand band) {
            Objects.requireNonNull(band, "band");
            return switch (band.role()) {
                case PAVEMENT -> PAVEMENT;
                case SIDEWALK -> SIDEWALK;
                case GRASS_MEDIAN -> GRASS_MEDIAN;
                case CONCRETE_MEDIAN -> CONCRETE_MEDIAN;
                case MARKING -> switch (band.pattern()) {
                    case CONTINUOUS -> SOLID_MARKING;
                    case DASHED -> DASHED_MARKING;
                    case DOUBLE_CONTINUOUS ->
                            DOUBLE_CONTINUOUS_MARKING;
                };
            };
        }
    }
}
