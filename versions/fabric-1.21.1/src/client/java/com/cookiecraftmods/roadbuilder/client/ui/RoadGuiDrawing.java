package com.cookiecraftmods.roadbuilder.client.ui;

import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class RoadGuiDrawing {
    private RoadGuiDrawing() {
    }

    static void texture(GuiGraphics graphics, ResourceLocation texture, int x, int y,
                        int width, int height, int textureWidth, int textureHeight) {
        graphics.blit(texture, x, y, width, height, 0.0F, 0.0F,
                textureWidth, textureHeight, textureWidth, textureHeight);
    }

    static void icon(GuiGraphics graphics, ResourceLocation texture, int x, int y, int size, int textureSize, int tint) {
        float scale = size / (float) textureSize;
        var pose = graphics.pose();
        pose.pushPose();
        graphics.setColor(((tint >> 16) & 0xFF) / 255.0F, ((tint >> 8) & 0xFF) / 255.0F,
                (tint & 0xFF) / 255.0F, 1.0F);
        try {
            pose.translate(x, y, 0.0F);
            pose.scale(scale, scale, 1.0F);
            graphics.blit(texture, 0, 0, 0, 0, textureSize, textureSize, textureSize, textureSize);
        } finally {
            pose.popPose();
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    public static void line(GuiGraphics graphics, float x, float y, float radians, int length, int color) {
        var pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(x, y, 0);
            pose.mulPose(Axis.ZP.rotation(radians));
            graphics.fill(0, 0, length, 1, color);
        } finally {
            pose.popPose();
        }
    }

    static void tooltip(GuiGraphics graphics, Font font, Component message, int x, int y) {
        graphics.renderTooltip(font, message, x, y);
    }

    static void tooltip(GuiGraphics graphics, Font font, List<FormattedCharSequence> lines, int x, int y) {
        graphics.renderTooltip(font, lines, x, y);
    }
}
