package com.cookiecraftmods.roadbuilder.preset;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public enum RoadCategory {
    ALL("all"),
    SMALL("small"),
    MEDIUM("medium"),
    LARGE("large"),
    HIGHWAY("highway"),
    BUS("bus"),
    PEDESTRIAN("pedestrian"),
    OTHER("other");

    private final String serializedName;

    RoadCategory(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static List<RoadCategory> editableValues() {
        return List.of(SMALL, MEDIUM, LARGE, HIGHWAY, BUS, PEDESTRIAN, OTHER);
    }

    public static RoadCategory nextEditable(String category) {
        List<RoadCategory> editable = editableValues();
        int index = editable.indexOf(fromDisplayCategory(category));
        return editable.get((index + 1) % editable.size());
    }

    public static RoadCategory fromDisplayCategory(String category) {
        String normalized = category == null
                ? ""
                : category.strip().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "roads" -> MEDIUM;
            case "streets" -> SMALL;
            case "highways" -> HIGHWAY;
            case "custom" -> OTHER;
            default -> Arrays.stream(values())
                    .filter(value -> value.serializedName.equals(normalized))
                    .findFirst()
                    .orElse(OTHER);
        };
    }

    public boolean matches(RoadPresetCatalogEntry entry) {
        return this == ALL || fromDisplayCategory(
                entry.display().category()
        ) == this;
    }
}
