package com.cookiecraftmods.roadbuilder.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import static com.cookiecraftmods.roadbuilder.client.ui.UIStyle.*;

final class RoadPresetEditorLabel extends AbstractWidget {
    private final boolean selected;

    RoadPresetEditorLabel(
            UiRectangle bounds,
            Component message,
            boolean selected
    ) {
        super(bounds.x(), bounds.y(), bounds.width(), bounds.height(), message);
        this.selected = selected;
        active = false;
    }

    @Override
    protected void renderWidget(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        int background = selected ? CONTROL_SELECTED : CONTROL_BACKGROUND;
        graphics.fill(
                getX(), getY(), getX() + getWidth(), getY() + getHeight(),
                background
        );
        graphics.fill(
                getX(), getY(), getX() + getWidth(), getY() + 1,
                selected ? HIGHLIGHT : BORDER_CONTROL
        );
        var font = Minecraft.getInstance().font;
        graphics.drawCenteredString(
                font,
                font.plainSubstrByWidth(
                        getMessage().getString(),
                        Math.max(1, getWidth() - 6)
                ),
                getX() + getWidth() / 2,
                getY() + Math.max(1, (getHeight() - 8) / 2),
                TEXT_PRIMARY
        );
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
    }
}
