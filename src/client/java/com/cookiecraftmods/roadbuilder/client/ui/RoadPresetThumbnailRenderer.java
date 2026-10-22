package com.cookiecraftmods.roadbuilder.client.ui;

import com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection;
import com.cookiecraftmods.roadbuilder.preset.RoadPreset;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

import static com.cookiecraftmods.roadbuilder.client.ui.UIStyle.*;

final class RoadPresetThumbnailRenderer {
    private RoadPresetThumbnailRenderer() {
    }

    static void render(
            GuiGraphics graphics,
            UiRectangle bounds,
            RoadPreset preset
    ) {
        if (bounds.width() < 8 || bounds.height() < 8) {
            return;
        }
        RoadPresetEditorDraft draft = new RoadPresetEditorDraft(preset);
        List<RoadPresetEditorComponent> components = draft.components();
        RoadGeometry road = RoadGeometry.create(bounds);
        drawShadow(graphics, road);

        RoadPresetEditorCrossSectionGeometry geometry =
                RoadPresetEditorCrossSectionGeometry.create(
                        new UiRectangle(0, 0, road.height(), 1),
                        components.stream()
                                .map(RoadPresetEditorComponent::width)
                                .toList()
                );
        for (int index = 0; index < components.size(); index++) {
            UiRectangle slice = geometry.componentRectangle(index);
            if (slice.width() == 0) {
                continue;
            }
            int top = road.top() + slice.x();
            int bottom = top + slice.width();
            drawComponent(
                    graphics,
                    road,
                    top,
                    bottom,
                    components.get(index),
                    draft.fallbackSurfaceBlock()
            );
        }
        int topEdgeColor = components.isEmpty()
                ? PREVIEW_SIDEWALK
                : materialColor(components.get(0).block());
        int bottomEdgeColor = components.isEmpty()
                ? PREVIEW_SIDEWALK
                : materialColor(components.get(components.size() - 1).block());
        drawEdges(graphics, road, topEdgeColor, bottomEdgeColor);

        for (int index = 0; index < components.size(); index++) {
            RoadPresetEditorComponent component = components.get(index);
            if (!component.isVehicleLane()) {
                continue;
            }
            UiRectangle slice = geometry.componentRectangle(index);
            if (slice.width() > 0) {
                drawLaneDirection(
                        graphics,
                        road,
                        road.top() + slice.x(),
                        road.top() + slice.right(),
                        component.direction(),
                        materialColor(component.block())
                );
            }
        }
    }

    static void renderFallback(
            GuiGraphics graphics,
            UiRectangle bounds,
            int laneCount
    ) {
        if (bounds.width() < 8 || bounds.height() < 8) {
            return;
        }
        RoadGeometry road = RoadGeometry.create(bounds);
        drawShadow(graphics, road);
        int edgeHeight = Math.max(1, road.height() / 7);
        fillRows(
                graphics,
                road,
                road.top(),
                road.top() + edgeHeight,
                PREVIEW_SIDEWALK
        );
        fillRows(
                graphics,
                road,
                road.top() + edgeHeight,
                road.bottom() - edgeHeight,
                PREVIEW_ASPHALT
        );
        fillRows(
                graphics,
                road,
                road.bottom() - edgeHeight,
                road.bottom(),
                PREVIEW_SIDEWALK
        );
        int lanes = Math.max(1, laneCount);
        int pavementTop = road.top() + edgeHeight;
        int pavementHeight = Math.max(1, road.height() - edgeHeight * 2);
        for (int lane = 1; lane < lanes; lane++) {
            int y = pavementTop + pavementHeight * lane / lanes;
            drawDashes(graphics, road, y, Math.min(y + 1, road.bottom()), HIGHLIGHT);
        }
        drawEdges(
                graphics,
                road,
                PREVIEW_SIDEWALK,
                PREVIEW_SIDEWALK
        );
    }

    private static void drawComponent(
            GuiGraphics graphics,
            RoadGeometry road,
            int top,
            int bottom,
            RoadPresetEditorComponent component,
            ResourceLocation fallbackSurfaceBlock
    ) {
        switch (component.kind()) {
            case VEHICLE_LANE, BUS_LANE, PAVEMENT -> fillRows(
                    graphics,
                    road,
                    top,
                    bottom,
                    materialColor(component.block())
            );
            case RAILING -> {
                fillRows(graphics, road, top, bottom, materialColor(component.block()));
                int middle = (top + bottom) / 2;
                fillRows(graphics, road, middle, Math.min(middle + 1, bottom), 0xFFD0D0D0);
                for (int x = road.left() + 3; x < road.right(); x += 7) {
                    for (int y = top; y < bottom; y++) {
                        int shifted = x + road.shiftAt(y);
                        graphics.fill(shifted, y, shifted + 1, y + 1, 0xFF777777);
                    }
                }
            }
            case SIDEWALK -> drawSidewalk(
                    graphics,
                    road,
                    top,
                    bottom,
                    materialColor(component.block())
            );
            case GRASS_MEDIAN, CONCRETE_MEDIAN -> fillRows(
                    graphics,
                    road,
                    top,
                    bottom,
                    materialColor(component.block())
            );
            case SOLID_MARKING -> fillRows(
                    graphics,
                    road,
                    top,
                    bottom,
                    materialColor(component.block())
            );
            case DASHED_MARKING -> {
                fillRows(
                        graphics,
                        road,
                        top,
                        bottom,
                        materialColor(fallbackSurfaceBlock)
                );
                drawDashes(
                        graphics,
                        road,
                        top,
                        bottom,
                        materialColor(component.block())
                );
            }
            case DOUBLE_CONTINUOUS_MARKING -> drawDoubleMarking(
                    graphics,
                    road,
                    top,
                    bottom,
                    materialColor(fallbackSurfaceBlock),
                    materialColor(component.block())
            );
        }
        if (top > road.top()) {
            fillRows(
                    graphics,
                    road,
                    top,
                    Math.min(top + 1, bottom),
                    OVERLAY_COMPONENT_DIVIDER
            );
        }
    }

    private static void drawSidewalk(
            GuiGraphics graphics,
            RoadGeometry road,
            int top,
            int bottom,
            int color
    ) {
        fillRows(graphics, road, top, bottom, color);
        fillRows(
                graphics,
                road,
                top,
                Math.min(top + 1, bottom),
                lighten(color, 28)
        );
        for (int x = road.left() + 7; x < road.right(); x += 8) {
            for (int y = top; y < bottom; y++) {
                int shiftedX = x + road.shiftAt(y);
                graphics.fill(
                        shiftedX,
                        y,
                        shiftedX + 1,
                        y + 1,
                        darken(color, 24)
                );
            }
        }
    }

    private static void drawDoubleMarking(
            GuiGraphics graphics,
            RoadGeometry road,
            int top,
            int bottom,
            int surface,
            int marking
    ) {
        fillRows(graphics, road, top, bottom, surface);
        if (bottom - top <= 1) {
            fillRows(graphics, road, top, bottom, marking);
            return;
        }
        fillRows(graphics, road, top, top + 1, marking);
        fillRows(graphics, road, bottom - 1, bottom, marking);
    }

    private static void drawDashes(
            GuiGraphics graphics,
            RoadGeometry road,
            int top,
            int bottom,
            int color
    ) {
        for (int x = road.left() + 2; x < road.right(); x += 9) {
            int end = Math.min(road.right(), x + 5);
            for (int y = top; y < bottom; y++) {
                int shift = road.shiftAt(y);
                graphics.fill(x + shift, y, end + shift, y + 1, color);
            }
        }
    }

    private static void drawLaneDirection(
            GuiGraphics graphics,
            RoadGeometry road,
            int top,
            int bottom,
            LaneTravelDirection direction,
            int surfaceColor
    ) {
        int y = Math.max(top, Math.min(bottom - 1, (top + bottom - 1) / 2));
        int centerX = (road.left() + road.right()) / 2 + road.shiftAt(y);
        int color = contrastingColor(surfaceColor);
        int directionSign = direction == LaneTravelDirection.FORWARD ? 1 : -1;
        int tailX = centerX - directionSign * 4;
        int headX = centerX + directionSign * 4;
        line(graphics, tailX, y, headX, y, color);
        line(graphics, headX, y, headX - directionSign * 2, y - 2, color);
        line(graphics, headX, y, headX - directionSign * 2, y + 2, color);
    }

    private static void drawShadow(
            GuiGraphics graphics,
            RoadGeometry road
    ) {
        int shadowBottom = Math.min(
                road.bounds().bottom() - 1,
                road.bottom() + 2
        );
        for (int y = road.top() + 1; y < shadowBottom; y++) {
            int sourceY = Math.min(road.bottom() - 1, y);
            int shift = road.shiftAt(sourceY);
            graphics.fill(
                    road.left() + shift + 1,
                    y + 1,
                    road.right() + shift + 1,
                    y + 2,
                    SHADOW_DARK
            );
        }
    }

    private static void drawEdges(
            GuiGraphics graphics,
            RoadGeometry road,
            int topMaterial,
            int bottomMaterial
    ) {
        int topShift = road.shiftAt(road.top());
        int bottomShift = road.shiftAt(road.bottom() - 1);
        graphics.fill(
                road.left() + topShift,
                road.top(),
                road.right() + topShift,
                road.top() + 1,
                lighten(topMaterial, 26)
        );
        graphics.fill(
                road.left() + bottomShift,
                road.bottom() - 1,
                road.right() + bottomShift,
                road.bottom(),
                darken(bottomMaterial, 26)
        );
    }

    private static void fillRows(
            GuiGraphics graphics,
            RoadGeometry road,
            int top,
            int bottom,
            int color
    ) {
        int clippedTop = Math.max(road.top(), top);
        int clippedBottom = Math.min(road.bottom(), bottom);
        for (int y = clippedTop; y < clippedBottom; y++) {
            int shift = road.shiftAt(y);
            graphics.fill(
                    road.left() + shift,
                    y,
                    road.right() + shift,
                    y + 1,
                    color
            );
        }
    }

    static int materialColor(ResourceLocation block) {
        return RoadPresetBlockAppearance.opaquePreviewColor(block);
    }

    private static int contrastingColor(int color) {
        int red = color >> 16 & 0xFF;
        int green = color >> 8 & 0xFF;
        int blue = color & 0xFF;
        int luminance = (red * 299 + green * 587 + blue * 114) / 1000;
        return luminance >= 150 ? TEXT_INVERSE : TEXT_PRIMARY;
    }

    private static void line(
            GuiGraphics graphics,
            int x0,
            int y0,
            int x1,
            int y1,
            int color
    ) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int error = dx - dy;
        int x = x0;
        int y = y0;
        while (true) {
            graphics.fill(x, y, x + 1, y + 1, color);
            if (x == x1 && y == y1) {
                return;
            }
            int twiceError = error * 2;
            if (twiceError > -dy) {
                error -= dy;
                x += sx;
            }
            if (twiceError < dx) {
                error += dx;
                y += sy;
            }
        }
    }

    private record RoadGeometry(
            UiRectangle bounds,
            int left,
            int right,
            int top,
            int bottom,
            int shear
    ) {
        static RoadGeometry create(UiRectangle bounds) {
            int verticalInset = Math.max(2, bounds.height() / 8);
            int top = bounds.y() + verticalInset;
            int bottom = Math.max(top + 1, bounds.bottom() - verticalInset - 1);
            int shear = Math.min(5, Math.max(1, bounds.width() / 10));
            int left = bounds.x() + 2;
            int right = Math.max(left + 1, bounds.right() - shear - 2);
            return new RoadGeometry(
                    bounds,
                    left,
                    right,
                    top,
                    bottom,
                    shear
            );
        }

        int height() {
            return bottom - top;
        }

        int shiftAt(int y) {
            if (height() <= 1) {
                return 0;
            }
            return (bottom - 1 - y) * shear / (height() - 1);
        }
    }
}
