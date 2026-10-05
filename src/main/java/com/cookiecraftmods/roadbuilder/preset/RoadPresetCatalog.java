package com.cookiecraftmods.roadbuilder.preset;

import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record RoadPresetCatalog(
        long revision,
        List<RoadPresetCatalogEntry> entries
) {
    public RoadPresetCatalog {
        if (revision < 0) {
            throw new IllegalArgumentException(
                    "catalog revision must be non-negative"
            );
        }
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        for (int index = 1; index < entries.size(); index++) {
            if (entries.get(index - 1).compareTo(entries.get(index)) >= 0) {
                throw new IllegalArgumentException(
                        "catalog entries must be unique and sorted"
                );
            }
        }
    }

    public static RoadPresetCatalog from(
            long revision,
            Map<ResourceLocation, RoadPreset> presets,
            Set<ResourceLocation> editableIds
    ) {
        Objects.requireNonNull(presets, "presets");
        Objects.requireNonNull(editableIds, "editableIds");
        List<RoadPresetCatalogEntry> entries = new ArrayList<>(presets.size());
        presets.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> entries.add(new RoadPresetCatalogEntry(
                        entry.getKey(),
                        entry.getValue().revision(),
                        entry.getValue().surfaceBlockWidth(),
                        entry.getValue().lanes().size(),
                        fingerprint(entry.getValue()),
                        editableIds.contains(entry.getKey()),
                        entry.getValue().display(),
                        entry.getValue().classification().roadClass(),
                        entry.getValue().classification().priorityRank()
                )));
        return new RoadPresetCatalog(revision, entries);
    }

    static String fingerprint(RoadPreset preset) {
        StringBuilder canonical = new StringBuilder()
                .append(preset.schemaVersion()).append('|')
                .append(preset.revision()).append('|')
                .append(preset.surfaceWidth()).append('|')
                .append(preset.surfaceBlock()).append('|');
        canonical.append(preset.display()).append('|')
                .append(preset.classification()).append('|')
                .append(preset.intersectionStyle()).append('|')
                .append(preset.materialPalette()).append('|');
        preset.surfaceBands().forEach(band -> canonical
                .append(band.role()).append(':')
                .append(band.width()).append(':')
                .append(band.block()).append(':')
                .append(band.blockSet()).append(':')
                .append(band.pattern()).append(';'));
        canonical.append('|');
        preset.lanes().forEach(lane -> canonical
                .append(lane.type()).append(':')
                .append(lane.direction()).append(':')
                .append(lane.width()).append(':')
                .append(lane.lateralOffset()).append(';'));
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            canonical.toString().getBytes(StandardCharsets.UTF_8)
                    )
            );
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
