package com.cookiecraftmods.roadbuilder.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** Plain form labels and wrapped helper text, without button decoration. */
final class RoadPresetEditorText extends AbstractWidget {
    private final int color;

    RoadPresetEditorText(UiRectangle bounds, Component message, int color) {
        super(bounds.x(), bounds.y(), bounds.width(), bounds.height(), message);
        this.color = color;
        active = false;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float tick) {
        var font = Minecraft.getInstance().font;
        int y = getY();
        for (var line : font.split(getMessage(), Math.max(1, getWidth()))) {
            graphics.drawString(font, line, getX(), y, color, false);
            y += font.lineHeight;
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
    }
}
