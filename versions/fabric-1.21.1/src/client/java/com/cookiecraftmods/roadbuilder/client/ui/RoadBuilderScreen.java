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

    final void roadTooltip(GuiGraphics graphics, Component tooltip, int x, int y) {
        setTooltipForNextRenderPass(tooltip);
    }

    static void tickEditBox(EditBox box) {
        // EditBox updates its cursor from elapsed time in 1.21.1.
    }

    protected boolean roadKeyPressed(int key, boolean controlDown) {
        return false;
    }

    protected boolean roadMouseClicked(double x, double y, int button, boolean shiftDown) {
        return false;
    }

    protected boolean roadMouseDragged(double x, double y, int button) {
        return false;
    }

    protected boolean roadMouseReleased(double x, double y, int button) {
        return false;
    }

    @Override
    public final boolean keyPressed(int key, int scanCode, int modifiers) {
        return roadKeyPressed(key, hasControlDown()) || super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public final boolean mouseClicked(double x, double y, int button) {
        return roadMouseClicked(x, y, button, hasShiftDown()) || super.mouseClicked(x, y, button);
    }

    @Override
    public final boolean mouseDragged(double x, double y, int button, double dragX, double dragY) {
        return roadMouseDragged(x, y, button) || super.mouseDragged(x, y, button, dragX, dragY);
    }

    @Override
    public final boolean mouseReleased(double x, double y, int button) {
        return roadMouseReleased(x, y, button) || super.mouseReleased(x, y, button);
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
