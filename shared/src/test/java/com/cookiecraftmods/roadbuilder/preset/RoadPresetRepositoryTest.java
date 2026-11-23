package com.cookiecraftmods.roadbuilder.preset;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadPresetRepositoryTest {
    @Test
    void atomicallyReplacesImmutableSnapshotsAndSupportsRevisionLookup() {
        RoadPresetRepository repository = new RoadPresetRepository();
        ResourceLocation firstId = MinecraftIds.id("roadbuilder", "first");
        ResourceLocation secondId = MinecraftIds.id("example", "nested/second");
        RoadPreset first = preset(1);
        RoadPreset second = preset(2);

        repository.replaceAll(Map.of(firstId, first));
        Map<ResourceLocation, RoadPreset> oldSnapshot = repository.snapshot();
        repository.replaceAll(Map.of(secondId, second));

        assertAll(
                () -> assertEquals(Map.of(firstId, first), oldSnapshot),
                () -> assertEquals(Map.of(secondId, second), repository.snapshot()),
                () -> assertFalse(repository.find(firstId).isPresent()),
                () -> assertEquals(second, repository.find(secondId).orElseThrow()),
                () -> assertTrue(repository.find(secondId, 2).isPresent()),
                () -> assertFalse(repository.find(secondId, 1).isPresent()),
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> repository.snapshot().clear())
        );
    }

    @Test
    void registeredProvidersAreDeterministicReadOnlyCatalogSources() {
        RoadPresetRepository repository = new RoadPresetRepository();
        ResourceLocation providerId =
                MinecraftIds.id("example", "roads");
        ResourceLocation presetId =
                MinecraftIds.id("example", "boulevard");
        RoadPreset preset = preset(2);

        repository.registerProvider(new RoadPresetProvider() {
            @Override
            public ResourceLocation providerId() {
                return providerId;
            }

            @Override
            public Map<ResourceLocation, RoadPreset> presets() {
                return Map.of(presetId, preset);
            }
        });

        assertEquals(preset, repository.find(presetId).orElseThrow());
        assertFalse(repository.isEditable(presetId));
        assertEquals(presetId, repository.catalog().entries().get(0).id());
        assertTrue(repository.unregisterProvider(providerId));
        assertTrue(repository.find(presetId).isEmpty());
    }

    @Test
    void clientPackCatalogOverridesLocallyAndDisablingRestoresServerEntries() {
        ResourceLocation shared = MinecraftIds.id("example", "shared");
        ResourceLocation exclusive = MinecraftIds.id("example", "exclusive");
        RoadPresetCatalog server = RoadPresetCatalog.from(1, Map.of(shared, preset(1)), java.util.Set.of(shared));
        RoadPresetCatalog enabled = server.withClientPacks(2, Map.of(shared, preset(2), exclusive, preset(2)));
        assertEquals(2, enabled.entries().size());
        assertTrue(enabled.entries().stream().noneMatch(RoadPresetCatalogEntry::editable));
        assertEquals(2, enabled.entries().stream().filter(entry -> entry.id().equals(shared)).findFirst().orElseThrow().revision());
        assertEquals(server.entries(), server.withClientPacks(3, Map.of()).entries());
        assertEquals(1, server.entries().get(0).revision());
    }

    @Test
    void rejectsNullIdsAndValuesBeforePublishingANewSnapshot() {
        RoadPresetRepository repository = new RoadPresetRepository();
        ResourceLocation id = MinecraftIds.id("roadbuilder", "kept");
        RoadPreset kept = preset(1);
        repository.replaceAll(Map.of(id, kept));

        Map<ResourceLocation, RoadPreset> nullValue = new java.util.HashMap<>();
        nullValue.put(MinecraftIds.id("roadbuilder", "invalid"), null);

        assertAll(
                () -> assertThrows(NullPointerException.class,
                        () -> repository.replaceAll(nullValue)),
                () -> assertThrows(NullPointerException.class, () -> repository.find(null)),
                () -> assertEquals(Map.of(id, kept), repository.snapshot())
        );
    }

    private static RoadPreset preset(int revision) {
        return new RoadPreset(
                1,
                revision,
                4.0,
                MinecraftIds.id("minecraft", "gray_concrete"),
                List.of(new LaneComponent(
                        LaneType.VEHICLE,
                        3.0,
                        0.0,
                        LaneTravelDirection.FORWARD
                ))
        );
    }
}
