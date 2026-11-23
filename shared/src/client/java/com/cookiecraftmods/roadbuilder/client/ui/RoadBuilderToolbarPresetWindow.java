package com.cookiecraftmods.roadbuilder.client.ui;

import com.cookiecraftmods.roadbuilder.preset.RoadPresetCatalogEntry;
import com.cookiecraftmods.roadbuilder.preset.RoadCategory;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record RoadBuilderToolbarPresetWindow(
        int firstCatalogIndex,
        List<RoadPresetCatalogEntry> entries
) {
    public RoadBuilderToolbarPresetWindow {
        if (firstCatalogIndex < 0) {
            throw new IllegalArgumentException("firstCatalogIndex must be non-negative");
        }
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
    }

    public static RoadBuilderToolbarPresetWindow create(
            List<RoadPresetCatalogEntry> catalog,
            int pageIndex
    ) {
        Objects.requireNonNull(catalog, "catalog");
        int capacity = RoadBuilderToolbarLayout.PRESET_SLOT_COUNT;
        int pageCount = Math.max(1, (catalog.size() + capacity - 1) / capacity);
        int page = Math.max(0, Math.min(pageIndex, pageCount - 1));
        int first = page * capacity;
        int last = Math.min(first + capacity, catalog.size());
        return new RoadBuilderToolbarPresetWindow(
                first,
                catalog.subList(first, last)
        );
    }

    public static RoadBuilderToolbarPresetWindow create(
            List<RoadPresetCatalogEntry> catalog,
            int pageIndex,
            RoadCategory category
    ) {
        Objects.requireNonNull(category, "category");
        return create(
                catalog.stream().filter(category::matches).toList(),
                pageIndex
        );
    }

    public Optional<RoadPresetCatalogEntry> entryAt(int slot) {
        if (slot < 0 || slot >= RoadBuilderToolbarLayout.PRESET_SLOT_COUNT) {
            throw new IllegalArgumentException("preset slot is out of range");
        }
        return slot < entries.size()
                ? Optional.of(entries.get(slot))
                : Optional.empty();
    }
}
