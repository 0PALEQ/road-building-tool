package com.cookiecraftmods.roadbuilder.preset;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadCategoryTest {
    @Test
    void canonicalizesLegacyCategoriesAndFiltersCatalogRows() {
        RoadPresetCatalogEntry highway = new RoadPresetCatalogEntry(
                new ResourceLocation("example", "highway"),
                1,
                6,
                2,
                "0".repeat(64),
                true,
                new RoadPresetDisplay("", "Highway", "highways", "generated"),
                RoadClass.HIGHWAY,
                10
        );

        assertEquals(RoadCategory.HIGHWAY,
                RoadCategory.fromDisplayCategory("highways"));
        assertTrue(RoadCategory.HIGHWAY.matches(highway));
        assertTrue(RoadCategory.ALL.matches(highway));
    }

    @Test
    void cyclesOnlyThroughEditableCategories() {
        assertEquals(RoadCategory.SMALL,
                RoadCategory.nextEditable(RoadCategory.OTHER.serializedName()));
        assertEquals(RoadCategory.LARGE,
                RoadCategory.nextEditable("medium"));
        assertEquals(RoadCategory.SMALL,
                RoadCategory.nextEditable("all"));
    }
}
