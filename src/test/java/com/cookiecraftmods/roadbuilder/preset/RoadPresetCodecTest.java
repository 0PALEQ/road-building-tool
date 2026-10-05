package com.cookiecraftmods.roadbuilder.preset;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadPresetCodecTest {
    private static final String VALID_JSON = """
            {
              "schema_version": 1,
              "revision": 3,
              "surface_width": 7.0,
              "surface_block": "minecraft:gray_concrete",
              "lanes": [
                {
                  "type": "vehicle",
                  "width": 3.0,
                  "offset": -1.75,
                  "direction": "forward"
                },
                {
                  "type": "vehicle",
                  "width": 3.0,
                  "offset": 1.75,
                  "direction": "backward"
                }
              ]
            }
            """;

    @Test
    void parsesAndRoundTripsVersionedPresetContentWithoutAnIdField() {
        RoadPreset parsed = parse(VALID_JSON).result().orElseThrow();
        JsonObject encoded = RoadPreset.CODEC.encodeStart(JsonOps.INSTANCE, parsed)
                .result()
                .orElseThrow()
                .getAsJsonObject();
        RoadPreset roundTripped = RoadPreset.CODEC.parse(JsonOps.INSTANCE, encoded)
                .result()
                .orElseThrow();

        assertAll(
                () -> assertEquals(RoadPreset.CURRENT_SCHEMA_VERSION, parsed.schemaVersion()),
                () -> assertEquals(3, parsed.revision()),
                () -> assertEquals(7.0, parsed.surfaceWidth()),
                () -> assertEquals(7, parsed.surfaceBlockWidth()),
                () -> assertEquals(new ResourceLocation("minecraft", "gray_concrete"),
                        parsed.surfaceBlock()),
                () -> assertEquals(2, parsed.lanes().size()),
                () -> assertEquals(parsed, roundTripped),
                () -> assertFalse(encoded.has("id")),
                () -> assertTrue(encoded.has("schema_version")),
                () -> assertTrue(encoded.has("materials"))
        );
    }

    @Test
    void keepsItsLaneListImmutable() {
        List<LaneComponent> source = new java.util.ArrayList<>();
        source.add(lane(0.0, 2.0));
        RoadPreset preset = new RoadPreset(
                1,
                1,
                4.0,
                new ResourceLocation("minecraft", "stone"),
                source
        );

        source.clear();

        assertAll(
                () -> assertEquals(1, preset.lanes().size()),
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> preset.lanes().add(lane(0.0, 1.0)))
        );
    }

    @Test
    void roundTripsSurfaceBlockSetsAndKeepsThePrimaryBlockFirst() {
        JsonObject json = JsonParser.parseString(VALID_JSON)
                .getAsJsonObject();
        json.addProperty("schema_version", RoadPreset.CURRENT_SCHEMA_VERSION);
        com.google.gson.JsonArray bands = new com.google.gson.JsonArray();
        JsonObject band = new JsonObject();
        band.addProperty("role", "pavement");
        band.addProperty("width", 7);
        band.addProperty("block", "minecraft:stone_slab");
        com.google.gson.JsonArray set = new com.google.gson.JsonArray();
        set.add("minecraft:andesite_slab");
        set.add("minecraft:stone_slab");
        set.add("minecraft:cobblestone_slab");
        band.add("block_set", set);
        bands.add(band);
        json.add("surface_bands", bands);

        RoadPreset preset = RoadPreset.CODEC.parse(JsonOps.INSTANCE, json)
                .result()
                .orElseThrow();
        JsonObject encoded = RoadPreset.CODEC
                .encodeStart(JsonOps.INSTANCE, preset)
                .result()
                .orElseThrow()
                .getAsJsonObject();

        assertEquals(List.of(
                new ResourceLocation("minecraft", "stone_slab"),
                new ResourceLocation("minecraft", "andesite_slab"),
                new ResourceLocation("minecraft", "cobblestone_slab")
        ), preset.surfaceBands().get(0).blockSet());
        assertTrue(encoded.getAsJsonArray("surface_bands")
                .get(0).getAsJsonObject().has("block_set"));
    }

    @Test
    void rejectsUnsupportedVersionsAndInvalidRevisionOrWidth() {
        assertAll(
                () -> assertCodecError(VALID_JSON.replace("\"schema_version\": 1",
                        "\"schema_version\": "
                                + (RoadPreset.CURRENT_SCHEMA_VERSION + 1)),
                        "unsupported road preset schema version"),
                () -> assertCodecError(VALID_JSON.replace("\"revision\": 3",
                        "\"revision\": 0"), "revision must be at least 1"),
                () -> assertCodecError(VALID_JSON.replace("\"surface_width\": 7.0",
                        "\"surface_width\": 0.0"), "surface width"),
                () -> assertCodecError(VALID_JSON.replace("\"surface_width\": 7.0",
                        "\"surface_width\": 7.5"), "whole block count"),
                () -> assertCodecError(VALID_JSON.replace("\"surface_width\": 7.0",
                        "\"surface_width\": 65.0"), "whole block count")
        );
    }

    @Test
    void rejectsEmptyOutOfBoundsAndOverlappingLaneLayouts() {
        String empty = VALID_JSON.replaceFirst("(?s)\"lanes\": \\[.*]", "\"lanes\": []");
        String outOfBounds = VALID_JSON.replaceFirst("\"offset\": -1.75", "\"offset\": -3.0");
        String overlapping = VALID_JSON
                .replaceFirst("\"offset\": -1.75", "\"offset\": -1.0")
                .replaceFirst("\"offset\": 1.75", "\"offset\": 1.0");

        assertAll(
                () -> assertCodecError(empty, "at least one lane"),
                () -> assertCodecError(outOfBounds, "outside the road surface"),
                () -> assertCodecError(overlapping, "overlap")
        );
    }

    @Test
    void mirrorsLanePositionsWithoutChangingEitherDirectedMeaning() {
        RoadPreset preset = parse(VALID_JSON).result().orElseThrow();
        List<LaneComponent> rht = preset.lanes(TrafficHandedness.RHT);
        List<LaneComponent> lht = preset.lanes(TrafficHandedness.LHT);

        assertAll(
                () -> assertEquals(-1.75, rht.get(0).lateralOffset()),
                () -> assertEquals(1.75, rht.get(1).lateralOffset()),
                () -> assertEquals(1.75, lht.get(0).lateralOffset()),
                () -> assertEquals(-1.75, lht.get(1).lateralOffset()),
                () -> assertEquals(LaneTravelDirection.FORWARD, lht.get(0).direction()),
                () -> assertEquals(LaneTravelDirection.BACKWARD, lht.get(1).direction()),
                () -> assertEquals(rht.stream().map(LaneComponent::direction).toList(),
                        lht.stream().map(LaneComponent::direction).toList())
        );
    }

    @Test
    void parsesCustomPavementMarkingAndSidewalkBandsInRasterOrder() {
        String styled = VALID_JSON.replace(
                "\"lanes\": [",
                """
                "surface_bands": [
                  {"role": "sidewalk", "width": 1, "block": "minecraft:smooth_stone"},
                  {"role": "pavement", "width": 5, "block": "minecraft:gray_concrete"},
                  {"role": "marking", "width": 1, "block": "minecraft:white_concrete"}
                ],
                "lanes": ["""
        );

        RoadPreset preset = parse(styled).result().orElseThrow();

        assertEquals(
                List.of(
                        new ResourceLocation("minecraft", "smooth_stone"),
                        new ResourceLocation("minecraft", "gray_concrete"),
                        new ResourceLocation("minecraft", "gray_concrete"),
                        new ResourceLocation("minecraft", "gray_concrete"),
                        new ResourceLocation("minecraft", "gray_concrete"),
                        new ResourceLocation("minecraft", "gray_concrete"),
                        new ResourceLocation("minecraft", "white_concrete")
                ),
                preset.surfaceColumnBlocks()
        );
        assertEquals(preset, RoadPreset.CODEC
                .parse(JsonOps.INSTANCE, RoadPreset.CODEC
                        .encodeStart(JsonOps.INSTANCE, preset)
                        .result()
                        .orElseThrow())
                .result()
                .orElseThrow());
    }

    @Test
    void rejectsSurfaceBandsThatDoNotFillTheDeclaredWidth() {
        String incomplete = VALID_JSON.replace(
                "\"lanes\": [",
                """
                "surface_bands": [
                  {"role": "pavement", "width": 6, "block": "minecraft:stone"}
                ],
                "lanes": ["""
        );

        assertCodecError(incomplete, "must total surface_width");
    }

    @Test
    void parsesPatternsAndExpandsDoubleContinuousMarkingsOverPavement() {
        String patterned = VALID_JSON.replace(
                "\"lanes\": [",
                """
                "surface_bands": [
                  {"role": "pavement", "width": 1, "block": "minecraft:stone"},
                  {"role": "marking", "width": 1, "block": "minecraft:white_concrete", "pattern": "dashed"},
                  {"role": "marking", "width": 3, "block": "minecraft:yellow_concrete", "pattern": "double_continuous"},
                  {"role": "pavement", "width": 2, "block": "minecraft:stone"}
                ],
                "lanes": ["""
        );

        RoadPreset preset = parse(patterned).result().orElseThrow();

        assertAll(
                () -> assertEquals(
                        RoadMarkingPattern.DASHED,
                        preset.surfaceBands().get(1).pattern()
                ),
                () -> assertEquals(
                        RoadMarkingPattern.DOUBLE_CONTINUOUS,
                        preset.surfaceBands().get(2).pattern()
                ),
                () -> assertEquals(
                        List.of(
                                new ResourceLocation("minecraft", "stone"),
                                new ResourceLocation("minecraft", "white_concrete"),
                                new ResourceLocation("minecraft", "yellow_concrete"),
                                new ResourceLocation("minecraft", "gray_concrete"),
                                new ResourceLocation("minecraft", "yellow_concrete"),
                                new ResourceLocation("minecraft", "stone"),
                                new ResourceLocation("minecraft", "stone")
                        ),
                        preset.surfaceColumnBlocks()
                ),
                () -> assertEquals(
                        new ResourceLocation("minecraft", "gray_concrete"),
                        preset.surfaceColumns().get(1).underlayBlock()
                ),
                () -> assertEquals(preset, RoadPreset.CODEC.parse(
                        JsonOps.INSTANCE,
                        RoadPreset.CODEC.encodeStart(JsonOps.INSTANCE, preset)
                                .result()
                                .orElseThrow()
                ).result().orElseThrow())
        );
    }

    @Test
    void defaultsLegacyMarkingsAndRejectsInvalidDoubleWidths() {
        RoadPreset legacy = parse(VALID_JSON.replace(
                "\"lanes\": [",
                """
                "surface_bands": [
                  {"role": "marking", "width": 7, "block": "minecraft:white_concrete"}
                ],
                "lanes": ["""
        )).result().orElseThrow();
        assertEquals(
                RoadMarkingPattern.CONTINUOUS,
                legacy.surfaceBands().get(0).pattern()
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new RoadSurfaceBand(
                        RoadSurfaceRole.MARKING,
                        7,
                        new ResourceLocation(
                                "minecraft",
                                "white_concrete"
                        ),
                        RoadMarkingPattern.DOUBLE_CONTINUOUS
                )
        );
    }

    private static LaneComponent lane(double offset, double width) {
        return new LaneComponent(LaneType.VEHICLE, width, offset, LaneTravelDirection.FORWARD);
    }

    private static DataResult<RoadPreset> parse(String json) {
        return RoadPreset.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    private static void assertCodecError(String json, String expectedMessage) {
        DataResult<RoadPreset> result = parse(json);
        assertTrue(result.error().isPresent(), () -> "Expected an error for " + json);
        assertTrue(
                result.error().orElseThrow().message().contains(expectedMessage),
                () -> "Expected error containing '" + expectedMessage + "' but was '"
                        + result.error().orElseThrow().message() + "'"
        );
    }
}
