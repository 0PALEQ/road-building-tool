package com.cookiecraftmods.roadbuilder.network;

import com.cookiecraftmods.roadbuilder.geometry.PathSample;
import com.cookiecraftmods.roadbuilder.geometry.RoadVector;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Recognizes a free-flow fork or merge: one wider trunk continues into two
 * narrower arms on opposite sides of its reference direction.
 */
public final class RoadForkJunction {
    private static final double MAXIMUM_BRANCH_DEVIATION =
            Math.toRadians(60.0);
    private static final double MINIMUM_TRUNK_TO_BRANCH_WIDTH_RATIO = 1.20;
    private static final double MINIMUM_COMBINED_WIDTH_COVERAGE = 0.65;
    private static final double SIDE_TOLERANCE = 1.0e-6;

    private RoadForkJunction() {
    }

    public static boolean isFork(
            RoadNode node,
            List<RoadSegment> incidentSegments
    ) {
        Objects.requireNonNull(node, "node");
        List<RoadSegment> incident = List.copyOf(Objects.requireNonNull(
                incidentSegments,
                "incidentSegments"
        ));
        if (!AuxiliaryLanePorts.attachments(node.id(), incident).isEmpty()) {
            return true;
        }
        if (incident.size() != 3) {
            return false;
        }

        List<Arm> arms = incident.stream()
                .map(segment -> arm(node, Objects.requireNonNull(
                        segment,
                        "incidentSegments contains null"
                )))
                .filter(Objects::nonNull)
                .toList();
        if (arms.size() != 3) {
            return false;
        }

        return arms.stream()
                .sorted(Comparator
                        .comparingDouble(Arm::width)
                        .reversed()
                        .thenComparing(arm -> arm.segment().id()))
                .anyMatch(trunk -> isForkTrunk(trunk, arms));
    }

    private static boolean isForkTrunk(Arm trunk, List<Arm> arms) {
        List<Arm> branches = arms.stream()
                .filter(arm -> arm != trunk)
                .toList();
        double largestBranch = branches.stream()
                .mapToDouble(Arm::width)
                .max()
                .orElseThrow();
        double combinedBranches = branches.stream()
                .mapToDouble(Arm::width)
                .sum();
        if (trunk.width()
                    < largestBranch * MINIMUM_TRUNK_TO_BRANCH_WIDTH_RATIO
                || trunk.width()
                    < combinedBranches * MINIMUM_COMBINED_WIDTH_COVERAGE) {
            return false;
        }

        double minimumOpposition = -Math.cos(MAXIMUM_BRANCH_DEVIATION);
        if (branches.stream().anyMatch(branch ->
                dot(trunk.outward(), branch.outward())
                        > minimumOpposition + SIDE_TOLERANCE)) {
            return false;
        }

        double firstSide = cross(trunk.outward(), branches.get(0).outward());
        double secondSide = cross(trunk.outward(), branches.get(1).outward());
        return firstSide * secondSide <= SIDE_TOLERANCE;
    }

    private static Arm arm(RoadNode node, RoadSegment segment) {
        boolean atStart = segment.startNodeId().equals(node.id());
        if (!atStart && !segment.endNodeId().equals(node.id())) {
            return null;
        }
        PathSample sample = segment.referenceGeometry().sample(
                atStart ? 0.0 : segment.referenceGeometry().length()
        );
        RoadVector tangent = atStart
                ? sample.tangent()
                : sample.tangent().scale(-1.0);
        double horizontal = tangent.horizontalLength();
        if (horizontal == 0.0) {
            return null;
        }
        return new Arm(
                segment,
                new RoadVector(
                        tangent.x() / horizontal,
                        0.0,
                        tangent.z() / horizontal
                ),
                segment.presetSnapshot().surfaceBlockWidth()
        );
    }

    private static double dot(RoadVector first, RoadVector second) {
        return first.x() * second.x() + first.z() * second.z();
    }

    private static double cross(RoadVector first, RoadVector second) {
        return first.x() * second.z() - first.z() * second.x();
    }

    private record Arm(
            RoadSegment segment,
            RoadVector outward,
            double width
    ) {
    }
}
