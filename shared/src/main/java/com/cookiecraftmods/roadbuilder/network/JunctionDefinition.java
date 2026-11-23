package com.cookiecraftmods.roadbuilder.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record JunctionDefinition(
        RoadNodeId nodeId,
        JunctionStrategy strategy,
        JunctionPriorityPolicy priorityPolicy,
        List<ApproachKey> primaryApproachKeys,
        JunctionCrossingPolicy crossingPolicy,
        Map<ApproachKey, JunctionApproachOverride> approachOverrides,
        long revision
) {
    public static final int MAXIMUM_APPROACH_COUNT = 16;

    public JunctionDefinition {
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(strategy, "strategy");
        Objects.requireNonNull(priorityPolicy, "priorityPolicy");
        Objects.requireNonNull(crossingPolicy, "crossingPolicy");
        if (revision < 1) {
            throw new IllegalArgumentException(
                    "junction definition revision must be at least 1"
            );
        }
        primaryApproachKeys = List.copyOf(Objects.requireNonNull(
                primaryApproachKeys,
                "primaryApproachKeys"
        ));
        if (primaryApproachKeys.stream().distinct().count()
                != primaryApproachKeys.size()) {
            throw new IllegalArgumentException(
                    "primaryApproachKeys must be unique"
            );
        }
        if (primaryApproachKeys.size() > MAXIMUM_APPROACH_COUNT) {
            throw new IllegalArgumentException(
                    "a junction may have at most "
                            + MAXIMUM_APPROACH_COUNT + " primary approaches"
            );
        }
        TreeMap<ApproachKey, JunctionApproachOverride> ordered =
                new TreeMap<>();
        Objects.requireNonNull(approachOverrides, "approachOverrides")
                .forEach((key, value) -> ordered.put(
                        Objects.requireNonNull(key, "approach override key"),
                        Objects.requireNonNull(value, "approach override")
                ));
        approachOverrides = Collections.unmodifiableMap(
                new LinkedHashMap<>(ordered)
        );
        if (approachOverrides.size() > MAXIMUM_APPROACH_COUNT) {
            throw new IllegalArgumentException(
                    "a junction may have at most "
                            + MAXIMUM_APPROACH_COUNT + " approach overrides"
            );
        }
    }

    public static JunctionDefinition automatic(RoadNodeId nodeId) {
        return new JunctionDefinition(
                nodeId,
                JunctionStrategy.AUTO,
                JunctionPriorityPolicy.RIGHT_OF_WAY,
                List.of(),
                JunctionCrossingPolicy.AUTO,
                Map.of(),
                1
        );
    }

        public JunctionDefinition migrateApproach(
            ApproachKey removed,
            ApproachKey replacement
    ) {
        Objects.requireNonNull(removed, "removed");
        Objects.requireNonNull(replacement, "replacement");
        List<ApproachKey> primary = new ArrayList<>(primaryApproachKeys);
        for (int index = 0; index < primary.size(); index++) {
            if (primary.get(index).equals(removed)) {
                primary.set(index, replacement);
            }
        }
        Map<ApproachKey, JunctionApproachOverride> overrides =
                new LinkedHashMap<>(approachOverrides);
        JunctionApproachOverride override = overrides.remove(removed);
        if (override != null) {
            overrides.put(replacement, override);
        }
        return new JunctionDefinition(
                nodeId,
                strategy,
                priorityPolicy,
                primary,
                crossingPolicy,
                overrides,
                revision + 1
        );
    }

    public JunctionDefinition removeApproach(ApproachKey removed) {
        Objects.requireNonNull(removed, "removed");
        Map<ApproachKey, JunctionApproachOverride> overrides =
                new LinkedHashMap<>(approachOverrides);
        overrides.remove(removed);
        return new JunctionDefinition(
                nodeId,
                strategy,
                priorityPolicy,
                primaryApproachKeys.stream()
                        .filter(key -> !key.equals(removed))
                        .toList(),
                crossingPolicy,
                overrides,
                revision + 1
        );
    }
}
