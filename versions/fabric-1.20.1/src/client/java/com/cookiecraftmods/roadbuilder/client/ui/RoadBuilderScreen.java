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
        renderBackground(graphics);
    }

    static void tickEditBox(EditBox box) {
        box.tick();
    }

    protected boolean scrollRoadPanel(double mouseX, double mouseY, double vertical) {
        return false;
    }

    @Override
    public final boolean mouseScrolled(double mouseX, double mouseY, double vertical) {
        return scrollRoadPanel(mouseX, mouseY, vertical)
                || super.mouseScrolled(mouseX, mouseY, vertical);
    }
}
