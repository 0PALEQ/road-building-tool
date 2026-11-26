package com.cookiecraftmods.roadbuilder.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.Objects;

import static com.cookiecraftmods.roadbuilder.client.ui.UIStyle.*;

final class RoadPresetEditorButton extends RoadButton {
    enum Style {
        NORMAL,
        PRIMARY,
        DANGER,
        SELECTED,
        GHOST
    }

    private final Runnable onPress;
    private final Style style;
    private final Component subtitle;

    RoadPresetEditorButton(
            UiRectangle bounds,
            Component message,
            Style style,
            Runnable onPress
    ) {
        this(bounds, message, null, style, onPress);
    }

    RoadPresetEditorButton(
            UiRectangle bounds,
            Component message,
            Component subtitle,
            Style style,
            Runnable onPress
    ) {
        super(bounds.x(), bounds.y(), bounds.width(), bounds.height(), message);
        this.style = Objects.requireNonNull(style, "style");
        this.subtitle = subtitle;
        this.onPress = Objects.requireNonNull(onPress, "onPress");
    }

    @Override
    protected void roadPress() {
        if (active) {
            onPress.run();
        }
    }

    @Override
    protected void renderRoadButton(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        int left = getX();
        int top = getY();
        int right = left + getWidth();
        int bottom = top + getHeight();
        int background = background();
        graphics.fill(left, top, right, bottom, background);
        if (style != Style.GHOST || isHoveredOrFocused()) {
            graphics.fill(
                    left,
                    top,
                    right,
                    top + 1,
                    style == Style.PRIMARY || style == Style.SELECTED
                            ? HIGHLIGHT_STRONG
                            : BORDER_CONTROL
            );
        }
        if (isFocused()) {
            graphics.renderOutline(left, top, getWidth(), getHeight(), HIGHLIGHT_STRONG);
        }
        var font = Minecraft.getInstance().font;
        if (subtitle == null || getHeight() < 27) {
            graphics.drawCenteredString(
                    font,
                    font.plainSubstrByWidth(
                            getMessage().getString(),
                            Math.max(1, getWidth() - 6)
                    ),
                    left + getWidth() / 2,
                    top + Math.max(1, (getHeight() - 8) / 2),
                    active ? TEXT_PRIMARY : TEXT_DISABLED
            );
        } else {
            graphics.drawCenteredString(
                    font,
                    font.plainSubstrByWidth(
                            getMessage().getString(),
                            Math.max(1, getWidth() - 8)
                    ),
                    left + getWidth() / 2,
                    top + 5,
                    active ? TEXT_PRIMARY : TEXT_DISABLED
            );
            graphics.drawCenteredString(
                    font,
                    font.plainSubstrByWidth(
                            subtitle.getString(),
                            Math.max(1, getWidth() - 8)
                    ),
                    left + getWidth() / 2,
                    top + 16,
                    active ? TEXT_SUBTITLE : TEXT_DISABLED
            );
        }
    }

    private int background() {
        if (!active) {
            return CONTROL_DISABLED;
        }
        boolean highlighted = isHoveredOrFocused();
        return switch (style) {
            case PRIMARY, SELECTED -> highlighted
                    ? lighten(CONTROL_SELECTED, 8) : CONTROL_SELECTED;
            case DANGER -> highlighted
                    ? lighten(CONTROL_DANGER, 21) : CONTROL_DANGER;
            case GHOST -> highlighted ? CONTROL_HOVERED : CONTROL_GHOST;
            case NORMAL -> highlighted
                    ? CONTROL_HOVERED : CONTROL_BACKGROUND;
        };
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
