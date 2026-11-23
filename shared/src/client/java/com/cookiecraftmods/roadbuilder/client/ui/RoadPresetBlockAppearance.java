package com.cookiecraftmods.roadbuilder.client.ui;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.material.MapColor;

import java.util.Objects;

final class RoadPresetBlockAppearance {
    private RoadPresetBlockAppearance() {
    }

    static int opaquePreviewColor(ResourceLocation blockId) {
        Objects.requireNonNull(blockId, "blockId");
        return BuiltInRegistries.BLOCK.getOptional(blockId)
                .map(block -> {
                    try {
                        MapColor color = block.defaultBlockState().getMapColor(
                                EmptyBlockGetter.INSTANCE,
                                BlockPos.ZERO
                        );
                        if (color == null || color == MapColor.NONE) {
                            return UIStyle.PREVIEW_BLOCK_FALLBACK;
                        }
                        return UIStyle.withAlpha(color.col, 0xFF);
                    } catch (RuntimeException exception) {
                        return UIStyle.PREVIEW_BLOCK_FALLBACK;
                    }
                })
                .orElse(UIStyle.PREVIEW_BLOCK_FALLBACK);
    }
}
