package com.cookiecraftmods.roadbuilder.client.ui;

import com.cookiecraftmods.roadbuilder.preset.LaneComponent;
import com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection;
import com.cookiecraftmods.roadbuilder.preset.LaneType;
import com.cookiecraftmods.roadbuilder.preset.RoadClassification;
import com.cookiecraftmods.roadbuilder.preset.RoadIntersectionStyle;
import com.cookiecraftmods.roadbuilder.preset.RoadMaterialPalette;
import com.cookiecraftmods.roadbuilder.preset.RoadPreset;
import com.cookiecraftmods.roadbuilder.preset.RoadPresetDisplay;
import com.cookiecraftmods.roadbuilder.preset.RoadSurfaceBand;
import com.cookiecraftmods.roadbuilder.preset.RoadSurfaceRole;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class RoadPresetEditorDraft {
    private final List<RoadPresetEditorComponent> components;
    private final List<RoadPresetEditorComponent> componentView;
    private final RoadPresetDisplay display;
    private RoadClassification classification;
    private RoadIntersectionStyle intersectionStyle;
    private RoadMaterialPalette materialPalette;

    public RoadPresetEditorDraft(RoadPreset source) {
        Objects.requireNonNull(source, "source");
        materialPalette = source.materialPalette();
        components = new ArrayList<>(projectComponents(source));
        componentView = Collections.unmodifiableList(components);
        display = source.display();
        classification = source.classification();
        intersectionStyle = source.intersectionStyle();
    }

    public int surfaceBlockWidth() {
        return components.stream()
                .mapToInt(RoadPresetEditorComponent::width)
                .sum();
    }

    public double surfaceWidth() {
        return surfaceBlockWidth();
    }

    public List<RoadPresetEditorComponent> components() {
        return componentView;
    }

    ResourceLocation fallbackSurfaceBlock() {
        return materialPalette.pavementBlock();
    }

    public RoadMaterialPalette materialPalette() {
        return materialPalette;
    }

    public void setRoadMaterial(
            RoadSurfaceRole role,
            ResourceLocation block
    ) {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(block, "block");
        setRoadMaterialSet(role, List.of(block));
    }

    public void setRoadMaterialSet(
            RoadSurfaceRole role,
            List<ResourceLocation> blocks
    ) {
        Objects.requireNonNull(role, "role");
        blocks = List.copyOf(Objects.requireNonNull(blocks, "blocks"));
        materialPalette = materialPalette.withBlockSet(role, blocks);
        for (int index = 0; index < components.size(); index++) {
            RoadPresetEditorComponent component = components.get(index);
            if (component.inheritsRoadMaterial()
                    && component.kind().surfaceRole() == role) {
                components.set(index, component.withInheritedBlockSet(blocks));
            }
        }
    }

    public void setComponentMaterialOverride(
            int index,
            ResourceLocation block
    ) {
        replaceComponent(index, components.get(index).withBlock(block));
    }

    public void clearComponentMaterialOverride(int index) {
        RoadPresetEditorComponent component = components.get(index);
        replaceComponent(index, component.withInheritedBlockSet(
                materialPalette.blockSetFor(component.kind().surfaceRole())
        ));
    }

    public int laneCount() {
        return (int) components.stream()
                .filter(RoadPresetEditorComponent::isVehicleLane)
                .count();
    }

    public RoadPresetDisplay display() {
        return display;
    }

    public RoadIntersectionStyle intersectionStyle() {
        return intersectionStyle;
    }

    public void setIntersectionStyle(RoadIntersectionStyle replacement) {
        intersectionStyle = Objects.requireNonNull(replacement, "replacement");
    }

    public RoadClassification classification() {
        return classification;
    }

    public void setClassification(RoadClassification replacement) {
        classification = Objects.requireNonNull(replacement, "replacement");
    }

    public void mirror() {
        Collections.reverse(components);
    }

    public int insertComponent(
            int index,
            RoadPresetEditorComponent component
    ) {
        Objects.requireNonNull(component, "component");
        if (index < 0 || index > components.size()) {
            throw new IndexOutOfBoundsException("component index is invalid");
        }
        requireSurfaceWidth(surfaceBlockWidth() + component.width());
        components.add(index, component);
        return index;
    }

    public int addComponent(RoadPresetEditorComponent component) {
        return insertComponent(components.size(), component);
    }

    public void replaceComponent(
            int index,
            RoadPresetEditorComponent replacement
    ) {
        Objects.requireNonNull(replacement, "replacement");
        RoadPresetEditorComponent previous = components.get(index);
        requireSurfaceWidth(
                surfaceBlockWidth() - previous.width() + replacement.width()
        );
        if (previous.isVehicleLane()
                && !replacement.isVehicleLane()
                && laneCount() == 1) {
            throw new IllegalArgumentException(
                    "a road must keep at least one vehicle lane"
            );
        }
        components.set(index, replacement);
    }

        public int moveComponent(int fromIndex, int insertionIndex) {
        if (fromIndex < 0 || fromIndex >= components.size()
                || insertionIndex < 0
                || insertionIndex > components.size()) {
            throw new IndexOutOfBoundsException("component index is invalid");
        }
        RoadPresetEditorComponent moved = components.remove(fromIndex);
        int adjusted = insertionIndex > fromIndex
                ? insertionIndex - 1
                : insertionIndex;
        adjusted = Math.max(0, Math.min(adjusted, components.size()));
        components.add(adjusted, moved);
        return adjusted;
    }

    public void removeComponent(int index) {
        RoadPresetEditorComponent component = components.get(index);
        if (component.isVehicleLane() && laneCount() == 1) {
            throw new IllegalArgumentException(
                    "a road must keep at least one vehicle lane"
            );
        }
        if (components.size() == 1) {
            throw new IllegalArgumentException(
                    "a road must keep at least one component"
            );
        }
        components.remove(index);
    }

    public Optional<String> validationError(int revision) {
        try {
            build(revision);
            return Optional.empty();
        } catch (IllegalArgumentException | NullPointerException invalid) {
            return Optional.ofNullable(invalid.getMessage());
        }
    }

    public Optional<String> validationError(
            int revision,
            String displayName,
            String category
    ) {
        try {
            build(revision, displayName, category);
            return Optional.empty();
        } catch (IllegalArgumentException | NullPointerException invalid) {
            return Optional.ofNullable(invalid.getMessage());
        }
    }

    public RoadPreset build(int revision) {
        return build(revision, display);
    }

    public RoadPreset build(
            int revision,
            String displayName,
            String category
    ) {
        return build(revision, new RoadPresetDisplay(
                display.translationKey(),
                displayName,
                category,
                display.thumbnailKey()
        ));
    }

    private RoadPreset build(int revision, RoadPresetDisplay candidateDisplay) {
        int totalWidth = surfaceBlockWidth();
        requireSurfaceWidth(totalWidth);
        List<LaneComponent> lanes = new ArrayList<>();
        List<RoadSurfaceBand> bands = new ArrayList<>(components.size());
        int cursor = 0;
        for (RoadPresetEditorComponent component : components) {
            bands.add(component.toSurfaceBand());
            if (component.isVehicleLane()) {
                double lateralOffset = -totalWidth / 2.0
                        + cursor
                        + component.width() / 2.0;
                lanes.add(new LaneComponent(
                        component.kind().laneType(),
                        component.width(),
                        lateralOffset,
                        component.direction()
                ));
            }
            cursor += component.width();
        }
        if (lanes.isEmpty()) {
            throw new IllegalArgumentException(
                    "a road must contain at least one vehicle lane"
            );
        }
        return new RoadPreset(
                RoadPreset.CURRENT_SCHEMA_VERSION,
                revision,
                totalWidth,
                materialPalette.pavementBlock(),
                List.copyOf(lanes),
                List.copyOf(bands),
                candidateDisplay,
                classification,
                intersectionStyle,
                materialPalette
        );
    }

    private static List<RoadPresetEditorComponent> projectComponents(
            RoadPreset source
    ) {
        int totalWidth = source.surfaceBlockWidth();
        List<Cell> cells = expandBands(source.surfaceBands());
        if (cells.size() != totalWidth) {
            throw new IllegalArgumentException(
                    "surface bands do not fill the road cross-section"
            );
        }

        List<LaneComponent> lanes = source.lanes().stream()
                .sorted(Comparator.comparingDouble(
                        LaneComponent::lateralOffset
                ))
                .toList();
        int[] laneAt = new int[totalWidth];
        Arrays.fill(laneAt, -1);
        for (int laneIndex = 0; laneIndex < lanes.size(); laneIndex++) {
            LaneComponent lane = lanes.get(laneIndex);
            int laneWidth = clamp(
                    (int) Math.round(lane.width()),
                    1,
                    totalWidth
            );
            double firstColumnCenter = -(totalWidth - 1) / 2.0;
            int preferredStart = (int) Math.round(
                    lane.lateralOffset()
                            - firstColumnCenter
                            - (laneWidth - 1) / 2.0
            );
            int start = clamp(preferredStart, 0, totalWidth - laneWidth);
            for (int column = start;
                 column < start + laneWidth;
                 column++) {
                if (laneAt[column] < 0
                        && cells.get(column).band().role()
                                == RoadSurfaceRole.PAVEMENT) {
                    laneAt[column] = laneIndex;
                }
            }
        }

        List<RoadPresetEditorComponent> result = new ArrayList<>();
        int cursor = 0;
        while (cursor < totalWidth) {
            int laneIndex = laneAt[cursor];
            if (laneIndex >= 0) {
                int end = cursor + 1;
                while (end < totalWidth && laneAt[end] == laneIndex) {
                    end++;
                }
                LaneComponent lane = lanes.get(laneIndex);
                RoadSurfaceBand band = cells.get(cursor).band();
                result.add(component(
                        lane.type() == LaneType.BUS
                                ? RoadPresetEditorComponent.Kind.BUS_LANE
                                : RoadPresetEditorComponent.Kind.VEHICLE_LANE,
                        end - cursor,
                        band.block(),
                        band.blockSet(),
                        lane.direction(),
                        source.materialPalette()
                ));
                cursor = end;
                continue;
            }

            Cell cell = cells.get(cursor);
            int end = cursor + 1;
            while (end < totalWidth
                    && laneAt[end] < 0
                    && cells.get(end).bandIndex() == cell.bandIndex()) {
                end++;
            }
            RoadSurfaceBand band = cell.band();
            result.add(component(
                    RoadPresetEditorComponent.Kind.fromBand(band),
                    end - cursor,
                    band.block(),
                    band.blockSet(),
                    null,
                    source.materialPalette()
            ));
            cursor = end;
        }

        if (result.stream().noneMatch(
                RoadPresetEditorComponent::isVehicleLane
        )) {
            // Valid legacy presets always have a lane. If unusual fractional
            // authoring prevented a pavement-column match, retain editability
            // by promoting the pavement component nearest the first lane.
            int fallback = nearestPavementComponent(result);
            if (fallback < 0) {
                throw new IllegalArgumentException(
                        "a vehicle lane needs at least one pavement block"
                );
            }
            RoadPresetEditorComponent pavement = result.get(fallback);
            result.set(fallback, component(
                    lanes.get(0).type() == LaneType.BUS
                            ? RoadPresetEditorComponent.Kind.BUS_LANE
                            : RoadPresetEditorComponent.Kind.VEHICLE_LANE,
                    pavement.width(),
                    pavement.block(),
                    pavement.blockSet(),
                    lanes.get(0).direction(),
                    source.materialPalette()
            ));
        }
        return List.copyOf(result);
    }

    private static RoadPresetEditorComponent component(
            RoadPresetEditorComponent.Kind kind,
            int width,
            ResourceLocation block,
            List<ResourceLocation> blockSet,
            LaneTravelDirection direction,
            RoadMaterialPalette materials
    ) {
        boolean inherited = blockSet.equals(materials.blockSetFor(
                kind.surfaceRole()
        ));
        return new RoadPresetEditorComponent(
                kind,
                width,
                block,
                direction,
                inherited,
                blockSet
        );
    }

    private static List<Cell> expandBands(List<RoadSurfaceBand> bands) {
        List<Cell> cells = new ArrayList<>();
        for (int bandIndex = 0; bandIndex < bands.size(); bandIndex++) {
            RoadSurfaceBand band = bands.get(bandIndex);
            for (int column = 0; column < band.width(); column++) {
                cells.add(new Cell(bandIndex, band));
            }
        }
        return cells;
    }

    private static int nearestPavementComponent(
            List<RoadPresetEditorComponent> components
    ) {
        for (int index = 0; index < components.size(); index++) {
            if (components.get(index).kind()
                    == RoadPresetEditorComponent.Kind.PAVEMENT) {
                return index;
            }
        }
        return -1;
    }

    private static void requireSurfaceWidth(int width) {
        if (width < 1 || width > RoadPreset.MAXIMUM_SURFACE_BLOCK_WIDTH) {
            throw new IllegalArgumentException(
                    "road surface width must remain between 1 and "
                            + RoadPreset.MAXIMUM_SURFACE_BLOCK_WIDTH
            );
        }
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private record Cell(int bandIndex, RoadSurfaceBand band) {
    }
}
