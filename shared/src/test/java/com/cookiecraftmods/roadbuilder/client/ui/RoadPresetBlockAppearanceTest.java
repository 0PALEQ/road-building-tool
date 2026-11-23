package com.cookiecraftmods.roadbuilder.client.ui;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import com.cookiecraftmods.roadbuilder.test.MinecraftBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoadPresetBlockAppearanceTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftBootstrap.bootstrap();
    }

    @Test
    void usesTheRegisteredBlocksOpaqueMapColor() {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(
                Blocks.RED_CONCRETE
        );
        int expected = 0xFF000000 | Blocks.RED_CONCRETE
                .defaultBlockState()
                .getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
                .col;

        assertEquals(
                expected,
                RoadPresetBlockAppearance.opaquePreviewColor(id)
        );
    }

    @Test
    void fallsBackForUnknownAndColorlessBlocks() {
        assertEquals(
                UIStyle.PREVIEW_BLOCK_FALLBACK,
                RoadPresetBlockAppearance.opaquePreviewColor(
                        MinecraftIds.id("missing_mod", "missing_block")
                )
        );
        assertEquals(
                UIStyle.PREVIEW_BLOCK_FALLBACK,
                RoadPresetBlockAppearance.opaquePreviewColor(
                        BuiltInRegistries.BLOCK.getKey(Blocks.GLASS)
                )
        );
    }

    @Test
    void selectorThumbnailPreservesTheResolvedBlockColor() {
        ResourceLocation grass = BuiltInRegistries.BLOCK.getKey(
                Blocks.GRASS_BLOCK
        );
        int expected = RoadPresetBlockAppearance.opaquePreviewColor(grass);
        int rendered = RoadPresetThumbnailRenderer.materialColor(grass);

        assertEquals(expected, rendered);
        int red = rendered >> 16 & 0xFF;
        int green = rendered >> 8 & 0xFF;
        int blue = rendered & 0xFF;
        org.junit.jupiter.api.Assertions.assertTrue(
                green > red && green > blue,
                "grass should remain green instead of being converted to gray"
        );
    }
}
