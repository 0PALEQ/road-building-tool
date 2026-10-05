package com.cookiecraftmods.roadbuilder.client.ui;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadPresetBlockSearchTest {
    private static final ResourceLocation POLISHED_STONE =
            new ResourceLocation("examplemod", "polished_road_stone");

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
                new ResourceLocation("minecraft", "stone"),
                new ResourceLocation("minecraft", "gray_concrete")
        );

        assertEquals(-1, RoadPresetBlockSearch.preferredIndex(
                options,
                new ResourceLocation("removed_mod", "road_surface"),
                false
        ));
        assertEquals(1, RoadPresetBlockSearch.preferredIndex(
                options,
                options.get(1),
                true
        ));
        assertEquals(0, RoadPresetBlockSearch.preferredIndex(
                options,
                new ResourceLocation("minecraft", "white_concrete"),
                true
        ));
    }
}
