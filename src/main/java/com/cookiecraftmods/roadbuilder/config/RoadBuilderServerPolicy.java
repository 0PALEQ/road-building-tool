package com.cookiecraftmods.roadbuilder.config;

import com.cookiecraftmods.roadbuilder.placement.ConstructionJobBatchProcessor;
import com.cookiecraftmods.roadbuilder.placement.ConstructionLimits;
import com.cookiecraftmods.roadbuilder.placement.ForceRestorePermissionPolicy;
import com.cookiecraftmods.roadbuilder.preset.TrafficHandedness;

import java.util.Objects;

public record RoadBuilderServerPolicy(
        int schemaVersion,
        ConstructionLimits constructionLimits,
        int transitionsPerTick,
        int forceRestorePermissionLevel,
        TrafficHandedness trafficHandedness,
        boolean durableJournalRequired
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final RoadBuilderServerPolicy DEFAULTS =
            new RoadBuilderServerPolicy(
                    CURRENT_SCHEMA_VERSION,
                    ConstructionLimits.defaults(),
                    ConstructionJobBatchProcessor.DEFAULT_TRANSITIONS_PER_TICK,
                    ForceRestorePermissionPolicy.DEFAULT_OPERATOR_PERMISSION_LEVEL,
                    TrafficHandedness.RHT,
                    true
            );

    public RoadBuilderServerPolicy {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported server-policy schema " + schemaVersion
            );
        }
        Objects.requireNonNull(constructionLimits, "constructionLimits");
        if (transitionsPerTick < 1 || transitionsPerTick > 16_384) {
            throw new IllegalArgumentException(
                    "transitionsPerTick must be between 1 and 16384"
            );
        }
        if (forceRestorePermissionLevel < 0
                || forceRestorePermissionLevel > 4) {
            throw new IllegalArgumentException(
                    "forceRestorePermissionLevel must be between 0 and 4"
            );
        }
        Objects.requireNonNull(trafficHandedness, "trafficHandedness");
    }
}
