package com.cookiecraftmods.roadbuilder.client.tool;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.RoadVector;
import com.cookiecraftmods.roadbuilder.network.RoadGraphSnapshot;
import com.cookiecraftmods.roadbuilder.network.RoadSegment;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

public final class RoadSegmentHoverSelector {
    private static final double EXTRA_PICK_RADIUS = 0.75;
    private static final double PARALLEL_EPSILON = 1.0E-9;

    private RoadSegmentHoverSelector() {
    }

    public static Optional<RoadSegment> select(
            RoadGraphSnapshot snapshot,
            CursorRay ray
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(ray, "ray");
        return snapshot.segments().values().stream()
                .map(segment -> candidate(segment, ray))
                .flatMap(Optional::stream)
                .min(Comparator
                        .comparingDouble(Candidate::distance)
                        .thenComparingDouble(Candidate::rayDistance)
                        .thenComparing(candidate -> candidate.segment().id()))
                .map(Candidate::segment);
    }

    private static Optional<Candidate> candidate(RoadSegment segment, CursorRay ray) {
        double length = segment.referenceGeometry().length();
        int sampleCount = Math.max(
                1,
                Math.min(2_048, (int) Math.ceil(length / 0.5))
        );
        Candidate best = null;
        RoadPoint start = segment.referenceGeometry().sample(0.0).position();
        for (int index = 1; index <= sampleCount; index++) {
            double distance = index == sampleCount
                    ? length
                    : length * index / sampleCount;
            RoadPoint end = segment.referenceGeometry()
                    .sample(distance)
                    .position();
            Candidate candidate = lineCandidate(segment, ray, start, end)
                    .orElse(null);
            if (candidate != null && (best == null
                    || candidate.distance() < best.distance()
                    || candidate.distance() == best.distance()
                    && candidate.rayDistance() < best.rayDistance())) {
                best = candidate;
            }
            start = end;
        }
        return Optional.ofNullable(best);
    }

    private static Optional<Candidate> lineCandidate(
            RoadSegment segment,
            CursorRay ray,
            RoadPoint start,
            RoadPoint end
    ) {
        RoadVector segmentVector = start.vectorTo(end);
        RoadVector originToStart = ray.origin().vectorTo(start);
        RoadVector direction = ray.direction();

        double segmentLengthSquared = segmentVector.dot(segmentVector);
        if (segmentLengthSquared == 0.0) {
            return Optional.empty();
        }
        double directionDotSegment = direction.dot(segmentVector);
        double directionDotOffset = direction.dot(originToStart);
        double segmentDotOffset = segmentVector.dot(originToStart);
        double denominator = segmentLengthSquared
                - directionDotSegment * directionDotSegment;

        double segmentParameter;
        if (denominator <= PARALLEL_EPSILON) {
            segmentParameter = clamp(
                    -segmentDotOffset / segmentLengthSquared,
                    0.0,
                    1.0
            );
        } else {
            segmentParameter = clamp(
                    (directionDotSegment * directionDotOffset - segmentDotOffset)
                            / denominator,
                    0.0,
                    1.0
            );
        }

        RoadPoint segmentPoint = start.add(segmentVector.scale(segmentParameter));
        double rayDistance = direction.dot(ray.origin().vectorTo(segmentPoint));
        rayDistance = clamp(rayDistance, 0.0, ray.maximumDistance());
        RoadPoint rayPoint = ray.origin().add(direction.scale(rayDistance));
        double distance = rayPoint.distanceTo(segmentPoint);
        double pickRadius = segment.presetSnapshot().surfaceBlockWidth() / 2.0
                + EXTRA_PICK_RADIUS;
        if (distance > pickRadius) {
            return Optional.empty();
        }
        return Optional.of(new Candidate(segment, distance, rayDistance));
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private record Candidate(RoadSegment segment, double distance, double rayDistance) {
    }
}
