package com.cookiecraftmods.roadbuilder.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

abstract class RoadBuilderScreen extends Screen {
    RoadBuilderScreen(Component title) {
        this(Minecraft.getInstance(), title);
    }

    private RoadBuilderScreen(Minecraft client, Component title) {
        super(client, client == null ? null : client.font, title);
    }

    final void renderRoadBackground(GuiGraphics graphics) {
        graphics.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
    }

    @Override
    public final void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Each Road Builder screen draws its background before its own panel.
    }

    final void roadTooltip(GuiGraphics graphics, Component tooltip, int x, int y) {
        graphics.setTooltipForNextFrame(font, Tooltip.splitTooltip(minecraft, tooltip), x, y);
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
    public final boolean keyPressed(KeyEvent event) {
        return roadKeyPressed(event.key(), event.hasControlDown()) || super.keyPressed(event);
    }

    @Override
    public final boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return roadMouseClicked(event.x(), event.y(), event.button(), event.hasShiftDown())
                || super.mouseClicked(event, doubleClick);
    }

    @Override
    public final boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return roadMouseDragged(event.x(), event.y(), event.button()) || super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public final boolean mouseReleased(MouseButtonEvent event) {
        return roadMouseReleased(event.x(), event.y(), event.button()) || super.mouseReleased(event);
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
