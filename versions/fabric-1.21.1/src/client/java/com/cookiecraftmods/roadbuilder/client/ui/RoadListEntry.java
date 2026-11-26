package com.cookiecraftmods.roadbuilder.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;

abstract class RoadListEntry<E extends RoadListEntry<E>> extends ObjectSelectionList.Entry<E> {
    protected abstract void renderRoadRow(
            GuiGraphics graphics, int top, int left, int width, int height,
            int mouseX, int mouseY, boolean hovered
    );

    protected abstract boolean roadRowClicked(int button);

    @Override
    public final void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                             int mouseX, int mouseY, boolean hovered, float partialTick) {
        renderRoadRow(graphics, top, left, width, height, mouseX, mouseY, hovered);
    }

    @Override
    public final boolean mouseClicked(double mouseX, double mouseY, int button) {
        return roadRowClicked(button);
    }
}
