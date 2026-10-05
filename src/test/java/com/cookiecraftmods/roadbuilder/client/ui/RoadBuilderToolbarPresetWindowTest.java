package com.cookiecraftmods.roadbuilder.client.ui;

import com.cookiecraftmods.roadbuilder.preset.RoadPresetCatalogEntry;
import com.cookiecraftmods.roadbuilder.preset.RoadCategory;
import com.cookiecraftmods.roadbuilder.preset.RoadClass;
import com.cookiecraftmods.roadbuilder.preset.RoadPresetDisplay;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadBuilderToolbarPresetWindowTest {
    @Test
    void exposesOneCompletePageAtATime() {
        List<RoadPresetCatalogEntry> entries = entries(8);

        RoadBuilderToolbarPresetWindow window =
                RoadBuilderToolbarPresetWindow.create(
                        entries,
                        1
                );

        assertEquals(5, window.firstCatalogIndex());
        assertEquals(entries.subList(5, 8), window.entries());
    }

    @Test
    void clampsTheWindowAtBothCatalogEnds() {
        List<RoadPresetCatalogEntry> entries = entries(8);

        assertEquals(
                entries.subList(0, 5),
                RoadBuilderToolbarPresetWindow.create(
                        entries,
                        -1
                ).entries()
        );
        assertEquals(
                entries.subList(5, 8),
                RoadBuilderToolbarPresetWindow.create(
                        entries,
                        99
                ).entries()
        );
    }

    @Test
    void exposesEveryPresetWhenTheCatalogFits() {
        List<RoadPresetCatalogEntry> entries = entries(3);

        RoadBuilderToolbarPresetWindow window =
                RoadBuilderToolbarPresetWindow.create(entries, 0);

        assertEquals(0, window.firstCatalogIndex());
        assertEquals(entries, window.entries());
        assertEquals(entries.get(2), window.entryAt(2).orElseThrow());
        assertTrue(window.entryAt(3).isEmpty());
    }

    @Test
    void filtersTheSelectorWindowByCategory() {
        List<RoadPresetCatalogEntry> catalog = List.of(
                categorized("small", "local"),
                categorized("highway", "motorway"),
                categorized("bus", "transit")
        );

        RoadBuilderToolbarPresetWindow window =
                RoadBuilderToolbarPresetWindow.create(
                        catalog,
                        0,
                        RoadCategory.HIGHWAY
                );

        assertEquals(List.of(catalog.get(1)), window.entries());
    }

    private static RoadPresetCatalogEntry categorized(
            String category,
            String path
    ) {
        return new RoadPresetCatalogEntry(
                new ResourceLocation("example", path),
                1,
                6,
                2,
                "0".repeat(64),
                true,
                new RoadPresetDisplay("", path, category, "generated"),
                RoadClass.LOCAL,
                4
        );
    }

    private static List<RoadPresetCatalogEntry> entries(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> new RoadPresetCatalogEntry(
                        new ResourceLocation(
                                "roadbuilder",
                                "preset_" + index
                        ),
                        1,
                        9,
                        2,
                        "0".repeat(64),
                        false
                ))
                .toList();
    }
}
