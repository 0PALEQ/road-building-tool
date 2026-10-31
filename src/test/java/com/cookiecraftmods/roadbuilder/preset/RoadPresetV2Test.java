package com.cookiecraftmods.roadbuilder.preset;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadPresetV2Test {
    @Test
    void highwayCategoriesCarryTheirClassIntoNetworkSnapshots() {
        for (String category : java.util.List.of("highway", "highways")) {
            RoadPreset preset = RoadPreset.CODEC.parse(JsonOps.INSTANCE,
                    JsonParser.parseString("""
                            {
                              "schema_version": 4,
                              "revision": 1,
                              "surface_width": 3,
                              "surface_block": "minecraft:stone",
                              "display": {"category": "%s"},
                              "lanes": [{
                                "type": "vehicle", "width": 2,
                                "offset": 0, "direction": "forward"
                              }]
                            }
                            """.formatted(category))).result().orElseThrow();
            var snapshot = com.cookiecraftmods.roadbuilder.network.RoadPresetSnapshot.from(preset);
            assertEquals(RoadClass.HIGHWAY, snapshot.classification().roadClass());
            assertEquals(RoadClassification.DEFAULT.priorityRank(),
                    snapshot.classification().priorityRank());
            assertEquals(RoadClassification.DEFAULT.nominalSpeed(),
                    snapshot.classification().nominalSpeed());
            assertEquals(preset, RoadPreset.CODEC.parse(JsonOps.INSTANCE,
                    RoadPreset.CODEC.encodeStart(JsonOps.INSTANCE, preset)
                            .result().orElseThrow()).result().orElseThrow());
        }
    }

    @Test
    void migratesV1DefaultsAndRoundTripsV2JunctionFields() {
        RoadPreset migrated = RoadPreset.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {
                          "schema_version": 1,
                          "revision": 1,
                          "surface_width": 3,
                          "surface_block": "minecraft:stone",
                          "lanes": [{
                            "type": "vehicle",
                            "width": 2,
                            "offset": 0,
                            "direction": "forward"
                          }]
                        }
                        """)
        ).result().orElseThrow();
        assertEquals(RoadPreset.CURRENT_SCHEMA_VERSION, migrated.schemaVersion());
        assertEquals(RoadClass.LOCAL, migrated.classification().roadClass());

        RoadPreset v2 = RoadPreset.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {
                          "schema_version": 2,
                          "revision": 7,
                          "surface_width": 3,
                          "surface_block": "minecraft:stone",
                          "display": {
                            "fallback": "Collector",
                            "category": "roads",
                            "thumbnail": "generated"
                          },
                          "classification": {
                            "road_class": "collector",
                            "priority_rank": 9,
                            "nominal_speed": 70
                          },
                          "intersection_style": {
                            "pavement_block": "minecraft:stone",
                            "crosswalk_block": "minecraft:white_concrete",
                            "default_crosswalks": true,
                            "default_control": "stop"
                          },
                          "lanes": [{
                            "type": "vehicle",
                            "width": 2,
                            "offset": 0,
                            "direction": "forward"
                          }]
                        }
                        """)
        ).result().orElseThrow();

        assertEquals(RoadClass.COLLECTOR, v2.classification().roadClass());
        assertEquals(9, v2.classification().priorityRank());
        assertTrue(v2.intersectionStyle().defaultCrosswalks());
        assertEquals(
                new ResourceLocation("minecraft", "white_concrete"),
                v2.intersectionStyle().crosswalkBlock().orElseThrow()
        );
        assertEquals(v2, RoadPreset.CODEC.parse(
                JsonOps.INSTANCE,
                RoadPreset.CODEC.encodeStart(JsonOps.INSTANCE, v2)
                        .result().orElseThrow()
        ).result().orElseThrow());
    }
}
