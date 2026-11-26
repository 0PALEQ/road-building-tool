package com.cookiecraftmods.roadbuilder.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.input.MouseButtonEvent;

abstract class RoadListEntry<E extends RoadListEntry<E>> extends ObjectSelectionList.Entry<E> {
    protected abstract void renderRoadRow(
            GuiGraphics graphics, int top, int left, int width, int height,
            int mouseX, int mouseY, boolean hovered
    );

    protected abstract boolean roadRowClicked(int button);

    @Override
    public final void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
        renderRoadRow(graphics, getY(), getX(), getWidth(), getHeight(), mouseX, mouseY, hovered);
    }

    @Override
    public final boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return roadRowClicked(event.button());
    }
}
