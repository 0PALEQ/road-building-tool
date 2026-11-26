package com.cookiecraftmods.roadbuilder.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.network.chat.Component;

abstract class RoadButton extends AbstractButton {
    RoadButton(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    protected abstract void roadPress();

    protected abstract void renderRoadButton(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

    @Override
    public final void onPress() {
        roadPress();
    }

    @Override
    protected final void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderRoadButton(graphics, mouseX, mouseY, partialTick);
    }
}
