package com.cookiecraftmods.roadbuilder.preset;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import com.cookiecraftmods.roadbuilder.test.MinecraftBootstrap;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadPresetReloadListenerTest {
    private static final ResourceLocation SAMPLE_ID =
            MinecraftIds.id("example", "two_way");

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        MinecraftBootstrap.bootstrap();
    }

    @Test
    void resourcePackReloadPublishesReplacementWithoutChangingServerCatalog() {
        RoadPresetRepository server = new RoadPresetRepository();
        java.util.concurrent.atomic.AtomicReference<Map<ResourceLocation, RoadPreset>> client =
                new java.util.concurrent.atomic.AtomicReference<>(Map.of());
        RoadPresetReloadListener listener = new RoadPresetReloadListener(client::set);
        listener.apply(Map.of(SAMPLE_ID, sampleJson()), null, null);
        assertTrue(client.get().containsKey(SAMPLE_ID));
        assertEquals(0, server.size());
        listener.apply(Map.of(), null, null);
        assertTrue(client.get().isEmpty());
    }

    @Test
    void resourcePresetUsesItsResourcePathAsId() {
        RoadPresetRepository repository = new RoadPresetRepository();
        RoadPresetReloadListener listener = new RoadPresetReloadListener(repository);

        listener.apply(Map.of(SAMPLE_ID, sampleJson()), null, null);
        RoadPreset preset = repository.find(SAMPLE_ID).orElseThrow();

        assertAll(
                () -> assertEquals(1, repository.size()),
                () -> assertEquals(RoadPreset.CURRENT_SCHEMA_VERSION,
                        preset.schemaVersion()),
                () -> assertEquals(2, preset.revision()),
                () -> assertEquals(MinecraftIds.id("minecraft", "gray_concrete"),
                        preset.surfaceBlock()),
                () -> assertEquals(2, preset.lanes().size()),
                () -> assertEquals(LaneTravelDirection.FORWARD,
                        preset.lanes().get(0).direction()),
                () -> assertEquals(LaneTravelDirection.BACKWARD,
                        preset.lanes().get(1).direction())
        );
    }

    @Test
    void rejectsMalformedAndUnknownBlockPresetsThenReplacesTheWholeSnapshot()
            {
        RoadPresetRepository repository = new RoadPresetRepository();
        RoadPresetReloadListener listener = new RoadPresetReloadListener(repository);
        ResourceLocation malformedId = MinecraftIds.id("example", "malformed");
        ResourceLocation missingBlockId = MinecraftIds.id("example", "missing_block");
        ResourceLocation airId = MinecraftIds.id("example", "air");

        JsonElement malformed = JsonParser.parseString("""
                {
                  "schema_version": 999,
                  "revision": 1,
                  "surface_width": 3.0,
                  "surface_block": "minecraft:stone",
                  "lanes": []
                }
                """);
        JsonElement missingBlock = JsonParser.parseString("""
                {
                  "schema_version": 1,
                  "revision": 1,
                  "surface_width": 3.0,
                  "surface_block": "example:not_registered",
                  "lanes": [{
                    "type": "vehicle",
                    "width": 3.0,
                    "offset": 0.0,
                    "direction": "forward"
                  }]
                }
                """);
        JsonElement air = JsonParser.parseString("""
                {
                  "schema_version": 1,
                  "revision": 1,
                  "surface_width": 1.0,
                  "surface_block": "minecraft:air",
                  "lanes": [{
                    "type": "vehicle",
                    "width": 1.0,
                    "offset": 0.0,
                    "direction": "forward"
                  }]
                }
                """);

        listener.apply(Map.of(
                SAMPLE_ID, sampleJson(),
                malformedId, malformed,
                missingBlockId, missingBlock,
                airId, air
        ), null, null);

        assertAll(
                () -> assertEquals(1, repository.size()),
                () -> assertTrue(repository.find(SAMPLE_ID).isPresent()),
                () -> assertFalse(repository.find(malformedId).isPresent()),
                () -> assertFalse(repository.find(missingBlockId).isPresent()),
                () -> assertFalse(repository.find(airId).isPresent())
        );

        listener.apply(Map.of(), null, null);
        assertTrue(repository.snapshot().isEmpty());
    }

    private static JsonElement sampleJson() {
        return JsonParser.parseString("""
                {
                  "schema_version": 2,
                  "revision": 2,
                  "surface_width": 6,
                  "surface_block": "minecraft:gray_concrete",
                  "lanes": [
                    {"type": "vehicle", "width": 3, "offset": -1.5,
                     "direction": "forward"},
                    {"type": "vehicle", "width": 3, "offset": 1.5,
                     "direction": "backward"}
                  ]
                }
                """);
    }
}
