package com.cookiecraftmods.roadbuilder.client.ui;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

final class RoadPresetBlockSearch {
    private RoadPresetBlockSearch() {
    }

    static boolean matches(
            String query,
            String displayName,
            ResourceLocation id
    ) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(id, "id");

        String normalizedQuery = normalize(query);
        if (normalizedQuery.isEmpty()) {
            return true;
        }
        String searchable = normalize(
                displayName + " "
                        + id + " "
                        + id.getNamespace() + " "
                        + id.getPath().replace('_', ' ')
        );
        for (String token : normalizedQuery.split("\\s+")) {
            if (!searchable.contains(token)) {
                return false;
            }
        }
        return true;
    }

    static int preferredIndex(
            List<ResourceLocation> optionIds,
            ResourceLocation preferredId,
            boolean preferredIdIsAvailable
    ) {
        Objects.requireNonNull(optionIds, "optionIds");
        Objects.requireNonNull(preferredId, "preferredId");
        int exactMatch = optionIds.indexOf(preferredId);
        if (exactMatch >= 0) {
            return exactMatch;
        }
        return preferredIdIsAvailable && !optionIds.isEmpty() ? 0 : -1;
    }

    private static String normalize(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }
}
