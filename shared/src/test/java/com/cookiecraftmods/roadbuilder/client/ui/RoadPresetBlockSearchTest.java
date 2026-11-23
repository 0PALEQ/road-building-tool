package com.cookiecraftmods.roadbuilder.client.ui;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadPresetBlockSearchTest {
    private static final ResourceLocation POLISHED_STONE =
            MinecraftIds.id("examplemod", "polished_road_stone");

    @Test
    void emptyQueryMatchesEveryOption() {
        assertTrue(RoadPresetBlockSearch.matches(
                "  ",
                "Polished Road Stone",
                POLISHED_STONE
        ));
    }

    @Test
    void matchesLocalizedNameRegistryIdAndUnderscoreSeparatedPath() {
        assertTrue(RoadPresetBlockSearch.matches(
                "polished stone",
                "Polished Road Stone",
                POLISHED_STONE
        ));
        assertTrue(RoadPresetBlockSearch.matches(
                "examplemod:polished",
                "Localized name",
                POLISHED_STONE
        ));
        assertTrue(RoadPresetBlockSearch.matches(
                "road stone",
                "Localized name",
                POLISHED_STONE
        ));
    }

    @Test
    void everySearchTokenMustMatch() {
        assertTrue(RoadPresetBlockSearch.matches(
                "examplemod stone",
                "Polished Road Stone",
                POLISHED_STONE
        ));
        assertFalse(RoadPresetBlockSearch.matches(
                "examplemod concrete",
                "Polished Road Stone",
                POLISHED_STONE
        ));
    }

    @Test
    void unavailableCurrentBlockIsNeverImplicitlyReplaced() {
        List<ResourceLocation> options = List.of(
                MinecraftIds.id("minecraft", "stone"),
                MinecraftIds.id("minecraft", "gray_concrete")
        );

        assertEquals(-1, RoadPresetBlockSearch.preferredIndex(
                options,
                MinecraftIds.id("removed_mod", "road_surface"),
                false
        ));
        assertEquals(1, RoadPresetBlockSearch.preferredIndex(
                options,
                options.get(1),
                true
        ));
        assertEquals(0, RoadPresetBlockSearch.preferredIndex(
                options,
                MinecraftIds.id("minecraft", "white_concrete"),
                true
        ));
    }
}
