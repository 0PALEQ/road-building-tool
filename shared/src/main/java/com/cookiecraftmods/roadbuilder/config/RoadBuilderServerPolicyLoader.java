package com.cookiecraftmods.roadbuilder.config;

import com.cookiecraftmods.roadbuilder.RoadBuilder;
import com.cookiecraftmods.roadbuilder.placement.ConstructionLimits;
import com.cookiecraftmods.roadbuilder.preset.TrafficHandedness;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static com.cookiecraftmods.roadbuilder.config.JsonConfigIO.bool;
import static com.cookiecraftmods.roadbuilder.config.JsonConfigIO.enumValue;
import static com.cookiecraftmods.roadbuilder.config.JsonConfigIO.integer;
import static com.cookiecraftmods.roadbuilder.config.JsonConfigIO.number;

public final class RoadBuilderServerPolicyLoader {
    public static final String FILE_NAME = "roadbuilder-server.json";
    private static final Set<String> KEYS = Set.of(
            "schema_version",
            "maximum_distance",
            "maximum_affected_blocks",
            "maximum_touched_chunks",
            "maximum_planning_candidates",
            "maximum_grade",
            "transitions_per_tick",
            "force_restore_permission_level",
            "traffic_handedness",
            "durable_journal_required"
    );
    private RoadBuilderServerPolicyLoader() {
    }

    public static RoadBuilderServerPolicy load() {
        return load(FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME));
    }

    public static RoadBuilderServerPolicy load(Path path) {
        if (!Files.exists(path)) {
            try {
                write(path, RoadBuilderServerPolicy.DEFAULTS);
            } catch (IOException failure) {
                RoadBuilder.LOGGER.warn(
                        "Could not write default Road Builder server policy to {}",
                        path,
                        failure
                );
            }
            return RoadBuilderServerPolicy.DEFAULTS;
        }
        try {
            return decode(JsonConfigIO.readObject(path));
        } catch (IOException | RuntimeException invalid) {
            RoadBuilder.LOGGER.error(
                    "Invalid Road Builder server policy at {}; using safe defaults: {}",
                    path,
                    invalid.getMessage()
            );
            return RoadBuilderServerPolicy.DEFAULTS;
        }
    }

    static RoadBuilderServerPolicy decode(JsonObject root) {
        JsonConfigIO.requireExactKeys(root, KEYS);
        return new RoadBuilderServerPolicy(
                integer(root, "schema_version"),
                new ConstructionLimits(
                        number(root, "maximum_distance"),
                        integer(root, "maximum_affected_blocks"),
                        integer(root, "maximum_touched_chunks"),
                        integer(root, "maximum_planning_candidates"),
                        number(root, "maximum_grade")
                ),
                integer(root, "transitions_per_tick"),
                integer(root, "force_restore_permission_level"),
                enumValue(
                        root,
                        "traffic_handedness",
                        TrafficHandedness.class
                ),
                bool(root, "durable_journal_required")
        );
    }

    private static void write(
            Path path,
            RoadBuilderServerPolicy policy
    ) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", policy.schemaVersion());
        root.addProperty(
                "maximum_distance",
                policy.constructionLimits().maximumDistance()
        );
        root.addProperty(
                "maximum_affected_blocks",
                policy.constructionLimits().maximumAffectedBlocks()
        );
        root.addProperty(
                "maximum_touched_chunks",
                policy.constructionLimits().maximumTouchedChunks()
        );
        root.addProperty(
                "maximum_planning_candidates",
                policy.constructionLimits().maximumPlanningCandidates()
        );
        root.addProperty(
                "maximum_grade",
                policy.constructionLimits().maximumGrade()
        );
        root.addProperty(
                "transitions_per_tick",
                policy.transitionsPerTick()
        );
        root.addProperty(
                "force_restore_permission_level",
                policy.forceRestorePermissionLevel()
        );
        root.addProperty(
                "traffic_handedness",
                policy.trafficHandedness().name()
        );
        root.addProperty(
                "durable_journal_required",
                policy.durableJournalRequired()
        );
        JsonConfigIO.writeAtomic(path, FILE_NAME, root);
    }
}
