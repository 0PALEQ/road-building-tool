package com.cookiecraftmods.roadbuilder.preset;

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
        ResourceLocation firstId = new ResourceLocation("roadbuilder", "first");
        ResourceLocation secondId = new ResourceLocation("example", "nested/second");
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
                new ResourceLocation("example", "roads");
        ResourceLocation presetId =
                new ResourceLocation("example", "boulevard");
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
    void rejectsNullIdsAndValuesBeforePublishingANewSnapshot() {
        RoadPresetRepository repository = new RoadPresetRepository();
        ResourceLocation id = new ResourceLocation("roadbuilder", "kept");
        RoadPreset kept = preset(1);
        repository.replaceAll(Map.of(id, kept));

        Map<ResourceLocation, RoadPreset> nullValue = new java.util.HashMap<>();
        nullValue.put(new ResourceLocation("roadbuilder", "invalid"), null);

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
                new ResourceLocation("minecraft", "gray_concrete"),
                List.of(new LaneComponent(
                        LaneType.VEHICLE,
                        3.0,
                        0.0,
                        LaneTravelDirection.FORWARD
                ))
        );
    }
}
