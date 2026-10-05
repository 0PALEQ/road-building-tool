package com.cookiecraftmods.roadbuilder.client.ui;

import com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.Objects;

import static com.cookiecraftmods.roadbuilder.client.ui.UIStyle.*;

final class RoadPresetComponentButton extends AbstractButton {
    private final RoadPresetEditorComponent prototype;
    private final Component title;
    private final Component subtitle;
    private final Runnable onPress;

    RoadPresetComponentButton(
            UiRectangle bounds,
            RoadPresetEditorComponent prototype,
            Component title,
            Component subtitle,
            Runnable onPress
    ) {
        super(
                Objects.requireNonNull(bounds, "bounds").x(),
                bounds.y(),
                bounds.width(),
                bounds.height(),
                narrationMessage(title, subtitle)
        );
        this.prototype = Objects.requireNonNull(prototype, "prototype");
        this.title = Objects.requireNonNull(title, "title");
        this.subtitle = Objects.requireNonNull(subtitle, "subtitle");
        this.onPress = Objects.requireNonNull(onPress, "onPress");
    }

    RoadPresetEditorComponent prototype() {
        return prototype;
    }

    @Override
    public void onPress() {
        if (active) {
            onPress.run();
        }
    }

    @Override
    protected void renderWidget(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        UiRectangle bounds = new UiRectangle(
                getX(),
                getY(),
                getWidth(),
                getHeight()
        );
        graphics.fill(
                bounds.x(),
                bounds.y(),
                bounds.right(),
                bounds.bottom(),
                backgroundColor()
        );
        graphics.renderOutline(
                bounds.x(),
                bounds.y(),
                bounds.width(),
                bounds.height(),
                edgeColor()
        );

        UiRectangle swatch = swatchBounds(bounds);
        renderComponentSwatch(graphics, swatch);
        renderLabels(graphics, bounds, swatch);
    }

    private void renderLabels(
            GuiGraphics graphics,
            UiRectangle bounds,
            UiRectangle swatch
    ) {
        int labelX = swatch.right() + 5;
        int availableWidth = bounds.right() - labelX - 4;
        if (availableWidth < 8) {
            return;
        }

        var font = Minecraft.getInstance().font;
        int textColor = active ? TEXT_PRIMARY : TEXT_DISABLED;
        String titleText = font.plainSubstrByWidth(
                title.getString(),
                availableWidth
        );
        if (bounds.height() < 25 || subtitle.getString().isBlank()) {
            graphics.drawString(
                    font,
                    titleText,
                    labelX,
                    bounds.y() + Math.max(1, (bounds.height() - 8) / 2),
                    textColor,
                    false
            );
            return;
        }

        int titleY = bounds.y() + Math.max(3, (bounds.height() - 22) / 2);
        graphics.drawString(
                font,
                titleText,
                labelX,
                titleY,
                textColor,
                false
        );
        graphics.drawString(
                font,
                font.plainSubstrByWidth(subtitle.getString(), availableWidth),
                labelX,
                titleY + 11,
                active ? TEXT_SUBTITLE : TEXT_DISABLED,
                false
        );
    }

    private void renderComponentSwatch(
            GuiGraphics graphics,
            UiRectangle swatch
    ) {
        switch (prototype.kind()) {
            case VEHICLE_LANE, BUS_LANE -> renderLane(graphics, swatch);
            case PAVEMENT -> renderPavement(graphics, swatch);
            case SIDEWALK -> renderSidewalk(graphics, swatch);
            case GRASS_MEDIAN, CONCRETE_MEDIAN -> renderPavement(
                    graphics,
                    swatch
            );
            case SOLID_MARKING -> renderSolidMarking(graphics, swatch);
            case DASHED_MARKING -> renderDashedMarking(graphics, swatch);
            case DOUBLE_CONTINUOUS_MARKING ->
                    renderDoubleContinuousMarking(graphics, swatch);
        }
        if (!active) {
            graphics.fill(
                    swatch.x(),
                    swatch.y(),
                    swatch.right(),
                    swatch.bottom(),
                    OVERLAY_DISABLED
            );
        }
        graphics.renderOutline(
                swatch.x(),
                swatch.y(),
                swatch.width(),
                swatch.height(),
                active ? BORDER_PREVIEW : BORDER_CONTROL_DISABLED
        );
    }

    private void renderLane(
            GuiGraphics graphics,
            UiRectangle swatch
    ) {
        renderAsphalt(graphics, swatch);
        renderArrow(
                graphics,
                swatch,
                prototype.direction() == LaneTravelDirection.FORWARD
        );
    }

    private static void renderPavement(
            GuiGraphics graphics,
            UiRectangle swatch
    ) {
        renderAsphalt(graphics, swatch);
        int middle = swatch.x() + swatch.width() / 2;
        graphics.fill(
                middle,
                swatch.y() + 3,
                middle + 1,
                swatch.bottom() - 3,
                PREVIEW_ASPHALT_DARK
        );
    }

    private static void renderSidewalk(
            GuiGraphics graphics,
            UiRectangle swatch
    ) {
        graphics.fill(
                swatch.x(),
                swatch.y(),
                swatch.right(),
                swatch.bottom(),
                PREVIEW_SIDEWALK
        );
        graphics.fill(
                swatch.x(),
                swatch.y(),
                swatch.right(),
                swatch.y() + 2,
                PREVIEW_SIDEWALK_LIGHT
        );
        graphics.fill(
                swatch.x(),
                swatch.bottom() - 2,
                swatch.right(),
                swatch.bottom(),
                PREVIEW_SIDEWALK_DARK
        );
        int rowHeight = Math.max(5, swatch.height() / 3);
        for (int y = swatch.y() + rowHeight; y < swatch.bottom() - 2;
             y += rowHeight) {
            graphics.fill(
                    swatch.x() + 1,
                    y,
                    swatch.right() - 1,
                    y + 1,
                    PREVIEW_SIDEWALK_DARK
            );
        }
        int columnWidth = Math.max(6, swatch.width() / 3);
        for (int row = 0, y = swatch.y(); y < swatch.bottom();
             row++, y += rowHeight) {
            int offset = row % 2 == 0 ? 0 : columnWidth / 2;
            for (int x = swatch.x() + columnWidth + offset;
                 x < swatch.right(); x += columnWidth) {
                graphics.fill(
                        x,
                        y + 1,
                        x + 1,
                        Math.min(swatch.bottom() - 2, y + rowHeight),
                        PREVIEW_SIDEWALK_DARK
                );
            }
        }
    }

    private static void renderSolidMarking(
            GuiGraphics graphics,
            UiRectangle swatch
    ) {
        renderAsphalt(graphics, swatch);
        int center = swatch.x() + swatch.width() / 2;
        graphics.fill(
                center - 1,
                swatch.y() + 2,
                center + 1,
                swatch.bottom() - 2,
                HIGHLIGHT
        );
    }

    private static void renderDashedMarking(
            GuiGraphics graphics,
            UiRectangle swatch
    ) {
        renderAsphalt(graphics, swatch);
        int center = swatch.x() + swatch.width() / 2;
        int dashHeight = Math.max(3, swatch.height() / 5);
        int gap = Math.max(2, dashHeight / 2);
        for (int y = swatch.y() + 2; y < swatch.bottom() - 2;
             y += dashHeight + gap) {
            graphics.fill(
                    center - 1,
                    y,
                    center + 1,
                    Math.min(y + dashHeight, swatch.bottom() - 2),
                    HIGHLIGHT
            );
        }
    }

    private static void renderDoubleContinuousMarking(
            GuiGraphics graphics,
            UiRectangle swatch
    ) {
        renderAsphalt(graphics, swatch);
        int center = swatch.x() + swatch.width() / 2;
        int separation = Math.max(2, swatch.width() / 8);
        graphics.fill(
                center - separation - 1,
                swatch.y() + 2,
                center - separation + 1,
                swatch.bottom() - 2,
                HIGHLIGHT
        );
        graphics.fill(
                center + separation - 1,
                swatch.y() + 2,
                center + separation + 1,
                swatch.bottom() - 2,
                HIGHLIGHT
        );
    }

    private static void renderAsphalt(
            GuiGraphics graphics,
            UiRectangle swatch
    ) {
        graphics.fill(
                swatch.x(),
                swatch.y(),
                swatch.right(),
                swatch.bottom(),
                PREVIEW_ASPHALT
        );
        for (int y = swatch.y() + 3; y < swatch.bottom() - 2; y += 5) {
            int x = swatch.x() + 3 + Math.floorMod(y, 7);
            if (x < swatch.right() - 2) {
                graphics.fill(
                        x, y, x + 1, y + 1, PREVIEW_ASPHALT_LIGHT
                );
            }
        }
    }

    private static void renderArrow(
            GuiGraphics graphics,
            UiRectangle swatch,
            boolean forward
    ) {
        int center = swatch.x() + swatch.width() / 2;
        int top = swatch.y() + 4;
        int bottom = swatch.bottom() - 4;
        int headDepth = Math.max(4, Math.min(7, swatch.height() / 4));
        if (forward) {
            graphics.fill(
                    center - 1,
                    top + headDepth - 1,
                    center + 2,
                    bottom,
                    TEXT_PRIMARY
            );
            for (int step = 0; step < headDepth; step++) {
                int halfWidth = Math.max(1, (step + 2) / 2);
                graphics.fill(
                        center - halfWidth,
                        top + step,
                        center + halfWidth + 1,
                        top + step + 1,
                        TEXT_PRIMARY
                );
            }
        } else {
            graphics.fill(
                    center - 1,
                    top,
                    center + 2,
                    bottom - headDepth + 1,
                    TEXT_PRIMARY
            );
            for (int step = 0; step < headDepth; step++) {
                int halfWidth = Math.max(1, (headDepth - step + 1) / 2);
                graphics.fill(
                        center - halfWidth,
                        bottom - headDepth + step,
                        center + halfWidth + 1,
                        bottom - headDepth + step + 1,
                        TEXT_PRIMARY
                );
            }
        }
    }

    static UiRectangle swatchBounds(UiRectangle bounds) {
        int inset = bounds.height() >= 12 ? 3 : 1;
        int height = Math.max(0, bounds.height() - inset * 2);
        int preferredWidth = Math.min(38, Math.max(18, bounds.width() / 3));
        int width = Math.min(
                Math.max(0, bounds.width() - inset * 2),
                Math.min(preferredWidth, height + 8)
        );
        return new UiRectangle(
                bounds.x() + inset,
                bounds.y() + inset,
                width,
                height
        );
    }

    private int backgroundColor() {
        if (!active) {
            return CONTROL_DISABLED;
        }
        return isHoveredOrFocused() ? CONTROL_HOVERED : CONTROL_BACKGROUND;
    }

    private int edgeColor() {
        if (!active) {
            return BORDER_CONTROL_DISABLED;
        }
        return isHoveredOrFocused() ? HIGHLIGHT : BORDER_CONTROL;
    }

    private static Component narrationMessage(
            Component title,
            Component subtitle
    ) {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(subtitle, "subtitle");
        if (subtitle.getString().isBlank()) {
            return title;
        }
        return title.copy().append(Component.literal(". ")).append(subtitle);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
