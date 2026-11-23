package com.cookiecraftmods.roadbuilder.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

abstract class RoadBuilderScreen extends Screen {
    RoadBuilderScreen(Component title) {
        super(title);
    }

    final void renderRoadBackground(GuiGraphics graphics) {
        graphics.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
    }

    @Override
    public final void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Each Road Builder screen draws its background before its own panel.
    }

    static void tickEditBox(EditBox box) {
        // EditBox updates its cursor from elapsed time in 1.21.1.
    }

    protected boolean scrollRoadPanel(double mouseX, double mouseY, double vertical) {
        return false;
    }

    @Override
    public final boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        return scrollRoadPanel(mouseX, mouseY, vertical)
                || super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }
}
