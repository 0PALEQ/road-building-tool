package com.cookiecraftmods.roadbuilder.network;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;

import java.util.Objects;

public record RoadCrossingCandidate(
        RoadSegmentId crossedSegmentId,
        RoadPoint nodePosition,
        double proposedPathFraction,
        double crossedPathFraction,
        double verticalGap
) {
    public RoadCrossingCandidate {
        Objects.requireNonNull(crossedSegmentId, "crossedSegmentId");
        Objects.requireNonNull(nodePosition, "nodePosition");
        requireClosedFraction(
                proposedPathFraction,
                "proposedPathFraction"
        );
        requireInteriorFraction(
                crossedPathFraction,
                "crossedPathFraction"
        );
        if (!Double.isFinite(verticalGap) || verticalGap < 0.0) {
            throw new IllegalArgumentException("verticalGap must be finite and non-negative");
        }
    }

    private static void requireClosedFraction(double fraction, String name) {
        if (!Double.isFinite(fraction) || fraction < 0.0 || fraction > 1.0) {
            throw new IllegalArgumentException(
                    name + " must be between 0 and 1"
            );
        }
    }

    private static void requireInteriorFraction(double fraction, String name) {
        if (!Double.isFinite(fraction) || fraction <= 0.0 || fraction >= 1.0) {
            throw new IllegalArgumentException(name + " must be strictly between 0 and 1");
        }
    }
}
