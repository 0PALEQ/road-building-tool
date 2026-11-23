package com.cookiecraftmods.roadbuilder.network;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.StraightGeometry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class StraightRoadCrossingAnalyzer {
    public static final double DEFAULT_MAXIMUM_VERTICAL_GAP =
            RoadPathCrossingAnalyzer.DEFAULT_MAXIMUM_VERTICAL_GAP;

    private static final double PARALLEL_TOLERANCE = 1.0e-10;
    private static final double ENDPOINT_TOLERANCE = 1.0e-9;

    public List<RoadCrossingCandidate> analyze(
            StraightGeometry proposed,
            Collection<RoadSegment> existingSegments
    ) {
        return analyze(proposed, existingSegments, DEFAULT_MAXIMUM_VERTICAL_GAP);
    }

    public List<RoadCrossingCandidate> analyze(
            StraightGeometry proposed,
            Collection<RoadSegment> existingSegments,
            double maximumVerticalGap
    ) {
        Objects.requireNonNull(proposed, "proposed");
        Objects.requireNonNull(existingSegments, "existingSegments");
        if (!Double.isFinite(maximumVerticalGap) || maximumVerticalGap < 0.0) {
            throw new IllegalArgumentException(
                    "maximumVerticalGap must be finite and non-negative"
            );
        }

        List<RoadCrossingCandidate> candidates = new ArrayList<>();
        for (RoadSegment segment : existingSegments) {
            properCrossing(proposed, Objects.requireNonNull(segment, "existingSegments contains null"),
                    maximumVerticalGap).ifPresent(candidates::add);
        }
        candidates.sort(Comparator
                .comparingDouble(RoadCrossingCandidate::proposedPathFraction)
                .thenComparing(RoadCrossingCandidate::crossedSegmentId));
        return List.copyOf(candidates);
    }

    private static Optional<RoadCrossingCandidate> properCrossing(
            StraightGeometry proposed,
            RoadSegment existing,
            double maximumVerticalGap
    ) {
        RoadPoint p = proposed.start();
        RoadPoint p2 = proposed.end();
        if (!(existing.referenceGeometry()
                instanceof StraightGeometry crossed)) {
            return Optional.empty();
        }
        RoadPoint q = crossed.start();
        RoadPoint q2 = crossed.end();

        double rx = p2.x() - p.x();
        double rz = p2.z() - p.z();
        double sx = q2.x() - q.x();
        double sz = q2.z() - q.z();
        double denominator = cross(rx, rz, sx, sz);
        double scale = Math.hypot(rx, rz) * Math.hypot(sx, sz);
        if (Math.abs(denominator) <= PARALLEL_TOLERANCE * scale) {
            return Optional.empty();
        }

        double qpx = q.x() - p.x();
        double qpz = q.z() - p.z();
        double proposedFraction = cross(qpx, qpz, sx, sz) / denominator;
        double crossedFraction = cross(qpx, qpz, rx, rz) / denominator;
        if (!isStrictInterior(proposedFraction) || !isStrictInterior(crossedFraction)) {
            return Optional.empty();
        }

        double proposedY = interpolate(p.y(), p2.y(), proposedFraction);
        double crossedY = interpolate(q.y(), q2.y(), crossedFraction);
        double verticalGap = Math.abs(proposedY - crossedY);
        if (verticalGap > maximumVerticalGap) {
            return Optional.empty();
        }

        return Optional.of(new RoadCrossingCandidate(
                existing.id(),
                new RoadPoint(
                        interpolate(p.x(), p2.x(), proposedFraction),
                        crossedY,
                        interpolate(p.z(), p2.z(), proposedFraction)
                ),
                proposedFraction,
                crossedFraction,
                verticalGap
        ));
    }

    private static boolean isStrictInterior(double fraction) {
        return fraction > ENDPOINT_TOLERANCE && fraction < 1.0 - ENDPOINT_TOLERANCE;
    }

    private static double interpolate(double start, double end, double fraction) {
        return start + (end - start) * fraction;
    }

    private static double cross(double ax, double az, double bx, double bz) {
        return ax * bz - az * bx;
    }
}
