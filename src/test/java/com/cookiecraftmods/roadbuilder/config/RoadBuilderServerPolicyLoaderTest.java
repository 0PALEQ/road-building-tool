package com.cookiecraftmods.roadbuilder.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoadBuilderServerPolicyLoaderTest {
    @TempDir
    Path directory;

    @Test
    void createsValidatedDefaultsAndLoadsExplicitPolicy() throws Exception {
        Path path = directory.resolve("nested").resolve("server.json");
        assertEquals(
                RoadBuilderServerPolicy.DEFAULTS,
                RoadBuilderServerPolicyLoader.load(path)
        );
        Files.writeString(path, """
                {
                  "schema_version": 1,
                  "maximum_distance": 256.0,
                  "maximum_affected_blocks": 65536,
                  "maximum_touched_chunks": 128,
                  "maximum_planning_candidates": 500000,
                  "maximum_grade": 0.75,
                  "transitions_per_tick": 512,
                  "force_restore_permission_level": 3,
                  "traffic_handedness": "LHT",
                  "durable_journal_required": true
                }
                """);

        RoadBuilderServerPolicy loaded =
                RoadBuilderServerPolicyLoader.load(path);
        assertEquals(256.0, loaded.constructionLimits().maximumDistance());
        assertEquals(512, loaded.transitionsPerTick());
        assertEquals(3, loaded.forceRestorePermissionLevel());
    }

    @Test
    void invalidPolicyIsPreservedAndFallsBackSafely() throws Exception {
        Path path = directory.resolve("server.json");
        String invalid = "{\"schema_version\":99}";
        Files.writeString(path, invalid);

        assertEquals(
                RoadBuilderServerPolicy.DEFAULTS,
                RoadBuilderServerPolicyLoader.load(path)
        );
        assertEquals(invalid, Files.readString(path));
    }
}
