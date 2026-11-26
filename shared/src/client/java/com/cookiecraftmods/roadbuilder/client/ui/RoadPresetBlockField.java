package com.cookiecraftmods.roadbuilder.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.function.Consumer;

import static com.cookiecraftmods.roadbuilder.client.ui.UIStyle.*;

final class RoadPresetBlockField extends RoadButton {
    private static final String KEY = "screen.roadbuilder.block_selector.";
    private final Screen parent;
    private final Consumer<ResourceLocation> onSelected;
    private ResourceLocation blockId;
    private Component blockName;
    private ItemStack icon;
    private int previewColor;
    private final int blockCount;

    RoadPresetBlockField(
            UiRectangle bounds,
            Screen parent,
            ResourceLocation blockId,
            Consumer<ResourceLocation> onSelected
    ) {
        this(bounds, parent, blockId, 1, onSelected);
    }

    RoadPresetBlockField(
            UiRectangle bounds,
            Screen parent,
            ResourceLocation blockId,
            int blockCount,
            Consumer<ResourceLocation> onSelected
    ) {
        super(
                Objects.requireNonNull(bounds, "bounds").x(),
                bounds.y(),
                bounds.width(),
                bounds.height(),
                Component.empty()
        );
        this.parent = Objects.requireNonNull(parent, "parent");
        this.onSelected = Objects.requireNonNull(onSelected, "onSelected");
        this.blockCount = Math.max(1, blockCount);
        setBlockId(blockId);
    }

    ResourceLocation blockId() {
        return blockId;
    }

    void setBlockId(ResourceLocation replacement) {
        blockId = Objects.requireNonNull(replacement, "replacement");
        var block = BuiltInRegistries.BLOCK.getOptional(blockId);
        blockName = block.map(value -> (Component) value.getName())
                .orElseGet(() -> Component.literal(blockId.toString()));
        icon = block.map(ItemStack::new).orElse(ItemStack.EMPTY);
        previewColor = RoadPresetBlockAppearance.opaquePreviewColor(blockId);
        setMessage(Component.translatable(
                KEY + "field_narration",
                blockName,
                blockId.toString()
        ));
    }

    @Override
    protected void roadPress() {
        if (!active) {
            return;
        }
        RoadPresetBlockSelectorScreen.open(
                parent,
                blockId,
                replacement -> {
                    setBlockId(replacement);
                    onSelected.accept(replacement);
                }
        );
    }

    @Override
    protected void renderRoadButton(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        int left = getX();
        int top = getY();
        int right = left + getWidth();
        int bottom = top + getHeight();
        graphics.fill(
                left,
                top,
                right,
                bottom,
                isHoveredOrFocused() ? CONTROL_HOVERED : CONTROL_BACKGROUND
        );
        graphics.renderOutline(
                left,
                top,
                getWidth(),
                getHeight(),
                isHoveredOrFocused() ? HIGHLIGHT : BORDER_CONTROL
        );

        int iconX = left + 4;
        int iconY = top + Math.max(1, (getHeight() - 16) / 2);
        if (!icon.isEmpty() && getHeight() >= 18) {
            graphics.renderItem(icon, iconX, iconY);
        } else {
            int size = Math.max(4, Math.min(16, getHeight() - 4));
            int swatchY = top + (getHeight() - size) / 2;
            graphics.fill(
                    iconX,
                    swatchY,
                    iconX + size,
                    swatchY + size,
                    previewColor
            );
            graphics.renderOutline(
                    iconX,
                    swatchY,
                    size,
                    size,
                    BORDER_HOVERED
            );
        }

        var font = Minecraft.getInstance().font;
        int textX = left + 25;
        int arrowSpace = 14;
        int availableWidth = Math.max(
                1,
                right - textX - arrowSpace
        );
        if (getHeight() >= 27) {
            graphics.drawString(
                    font,
                    font.plainSubstrByWidth(
                            blockName.getString(),
                            availableWidth
                    ),
                    textX,
                    top + Math.max(3, (getHeight() - 22) / 2),
                    active ? TEXT_PRIMARY : TEXT_SECONDARY,
                    false
            );
            graphics.drawString(
                    font,
                    font.plainSubstrByWidth(
                            blockId.toString(),
                            availableWidth
                    ),
                    textX,
                    top + Math.max(14, (getHeight() - 22) / 2 + 11),
                    TEXT_SECONDARY,
                    false
            );
        } else {
            graphics.drawString(
                    font,
                    font.plainSubstrByWidth(
                            blockName.getString(),
                            availableWidth
                    ),
                    textX,
                    top + Math.max(1, (getHeight() - 8) / 2),
                    active ? TEXT_PRIMARY : TEXT_SECONDARY,
                    false
            );
        }
        graphics.drawCenteredString(
                font,
                ">",
                right - (blockCount > 1 ? 15 : 7),
                top + Math.max(1, (getHeight() - 8) / 2),
                active ? TEXT_PRIMARY : TEXT_SECONDARY
        );
        if (blockCount > 1) {
            graphics.drawString(
                    font,
                    "+" + (blockCount - 1),
                    right - 12,
                    top + 2,
                    active ? HIGHLIGHT : TEXT_SECONDARY,
                    false
            );
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
