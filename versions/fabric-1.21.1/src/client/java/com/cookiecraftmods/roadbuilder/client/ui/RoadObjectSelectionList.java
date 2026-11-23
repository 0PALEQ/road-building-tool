package com.cookiecraftmods.roadbuilder.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;

abstract class RoadObjectSelectionList<E extends ObjectSelectionList.Entry<E>> extends ObjectSelectionList<E> {
    RoadObjectSelectionList(Minecraft minecraft, int width, int height, int top, int bottom, int itemHeight) {
        super(minecraft, width, bottom - top, top, itemHeight);
    }

    @Override
    protected void renderListBackground(GuiGraphics graphics) {
    }

    @Override
    protected void renderListSeparators(GuiGraphics graphics) {
    }
}
