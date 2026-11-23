package com.cookiecraftmods.roadbuilder.preset;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record RoadPresetCatalogEntry(
        ResourceLocation id,
        int revision,
        int surfaceWidth,
        int laneCount,
        String fingerprint,
        boolean editable,
        RoadPresetDisplay display,
        RoadClass roadClass,
        int priorityRank
) implements Comparable<RoadPresetCatalogEntry> {
    public RoadPresetCatalogEntry(
            ResourceLocation id,
            int revision,
            int surfaceWidth,
            int laneCount,
            String fingerprint,
            boolean editable
    ) {
        this(
                id,
                revision,
                surfaceWidth,
                laneCount,
                fingerprint,
                editable,
                RoadPresetDisplay.DEFAULT,
                RoadClass.LOCAL,
                4
        );
    }

    public RoadPresetCatalogEntry {
        Objects.requireNonNull(id, "id");
        if (revision < 1 || surfaceWidth < 1 || laneCount < 1) {
            throw new IllegalArgumentException(
                    "catalog counts and revision must be positive"
            );
        }
        Objects.requireNonNull(fingerprint, "fingerprint");
        if (!fingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "fingerprint must be lowercase SHA-256"
            );
        }
        Objects.requireNonNull(display, "display");
        Objects.requireNonNull(roadClass, "roadClass");
        if (priorityRank < 0 || priorityRank > 15) {
            throw new IllegalArgumentException(
                    "priorityRank must be between 0 and 15"
            );
        }
    }

    @Override
    public int compareTo(RoadPresetCatalogEntry other) {
        return id.compareTo(other.id);
    }
}
