package com.cookiecraftmods.roadbuilder.client.ui;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class RoadGuiDrawing {
    private RoadGuiDrawing() {
    }

    static void texture(GuiGraphics graphics, Identifier texture, int x, int y,
                        int width, int height, int textureWidth, int textureHeight) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0,
                width, height, textureWidth, textureHeight, textureWidth, textureHeight);
    }

    static void icon(GuiGraphics graphics, Identifier texture, int x, int y, int size, int textureSize, int tint) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0,
                size, size, textureSize, textureSize, textureSize, textureSize, tint | 0xFF000000);
    }

    public static void line(GuiGraphics graphics, float x, float y, float radians, int length, int color) {
        var pose = graphics.pose();
        pose.pushMatrix();
        try {
            pose.translate(x, y).rotate(radians);
            graphics.fill(0, 0, length, 1, color);
        } finally {
            pose.popMatrix();
        }
    }

    static void tooltip(GuiGraphics graphics, Font font, Component message, int x, int y) {
        if (Minecraft.getInstance().screen == null) {
            graphics.setTooltipForNextFrame(font, message, x, y);
        }
    }

    static void tooltip(GuiGraphics graphics, Font font, List<FormattedCharSequence> lines, int x, int y) {
        graphics.setTooltipForNextFrame(font, lines, x, y);
    }
}
