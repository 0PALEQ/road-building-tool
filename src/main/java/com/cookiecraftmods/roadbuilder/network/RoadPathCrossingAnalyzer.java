package com.cookiecraftmods.roadbuilder.network;

import com.cookiecraftmods.roadbuilder.geometry.PathSample;
import com.cookiecraftmods.roadbuilder.geometry.ReferenceGeometry;
import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class RoadPathCrossingAnalyzer {
    public static final double DEFAULT_MAXIMUM_VERTICAL_GAP = 1.0;
    public static final double DEFAULT_MAXIMUM_CHORD_LENGTH = 0.25;
    public static final int MAXIMUM_CHORDS_PER_PATH = 8_192;
    public static final double ENDPOINT_ON_PATH_TOLERANCE = 0.05;
    public static final double ENDPOINT_SURFACE_MARGIN = 0.5;

    private static final double ENDPOINT_TOLERANCE = 1.0e-6;
    private static final double PARALLEL_TOLERANCE = 1.0e-12;
    private static final double DUPLICATE_FRACTION_TOLERANCE = 1.0e-5;

    public List<RoadCrossingCandidate> analyze(
            ReferenceGeometry proposed,
            Collection<RoadSegment> existingSegments
    ) {
        return analyze(
                proposed,
                existingSegments,
                DEFAULT_MAXIMUM_VERTICAL_GAP,
                DEFAULT_MAXIMUM_CHORD_LENGTH
        );
    }

    public List<RoadCrossingCandidate> analyze(
            ReferenceGeometry proposed,
            Collection<RoadSegment> existingSegments,
            double maximumVerticalGap,
            double maximumChordLength
    ) {
        Objects.requireNonNull(proposed, "proposed");
        Objects.requireNonNull(existingSegments, "existingSegments");
        requirePositiveFinite(maximumChordLength, "maximumChordLength");
        if (!Double.isFinite(maximumVerticalGap) || maximumVerticalGap < 0.0) {
            throw new IllegalArgumentException(
                    "maximumVerticalGap must be finite and non-negative"
            );
        }

        List<Chord> proposedChords = flatten(proposed, maximumChordLength);
        List<RoadCrossingCandidate> result = new ArrayList<>();
        Set<CandidateKey> seen = new HashSet<>();
        for (RoadSegment segment : existingSegments) {
            RoadSegment existing = Objects.requireNonNull(
                    segment,
                    "existingSegments contains null"
            );
            List<Chord> existingChords = flatten(
                    existing.referenceGeometry(),
                    maximumChordLength
            );
            for (Chord first : proposedChords) {
                for (Chord second : existingChords) {
                    Intersection intersection = intersect(first, second);
                    if (intersection == null
                            || !strictInterior(intersection.firstFraction())
                            || !strictInterior(intersection.secondFraction())) {
                        continue;
                    }
                    PathSample proposedSample = proposed.sample(
                            intersection.firstFraction() * proposed.length()
                    );
                    PathSample existingSample = existing.referenceGeometry().sample(
                            intersection.secondFraction()
                                    * existing.referenceGeometry().length()
                    );
                    double verticalGap = Math.abs(
                            proposedSample.position().y()
                                    - existingSample.position().y()
                    );
                    if (verticalGap > maximumVerticalGap) {
                        continue;
                    }
                    CandidateKey key = new CandidateKey(
                            existing.id(),
                            quantize(intersection.firstFraction()),
                            quantize(intersection.secondFraction())
                    );
                    if (!seen.add(key)) {
                        continue;
                    }
                    result.add(new RoadCrossingCandidate(
                            existing.id(),
                            existingSample.position(),
                            intersection.firstFraction(),
                            intersection.secondFraction(),
                            verticalGap
                    ));
                }
            }
            addEndpointJunction(
                    proposed.start(),
                    0.0,
                    existing,
                    existingChords,
                    maximumVerticalGap,
                    seen,
                    result
            );
            addEndpointJunction(
                    proposed.end(),
                    1.0,
                    existing,
                    existingChords,
                    maximumVerticalGap,
                    seen,
                    result
            );
        }
        return normalizeCrossings(proposed, result);
    }

    private static List<RoadCrossingCandidate> normalizeCrossings(
            ReferenceGeometry proposed,
            Collection<RoadCrossingCandidate> crossings
    ) {
        List<RoadCrossingCandidate> prioritized = new ArrayList<>(crossings);
        prioritized.sort(Comparator
                .comparingDouble((RoadCrossingCandidate candidate) ->
                        contactDistance(proposed, candidate))
                .thenComparingDouble(RoadCrossingCandidate::verticalGap)
                .thenComparing(RoadCrossingCandidate::crossedSegmentId)
                .thenComparingDouble(
                        RoadCrossingCandidate::proposedPathFraction
                )
                .thenComparingDouble(
                        RoadCrossingCandidate::crossedPathFraction
                ));

        List<RoadCrossingCandidate> distinct = new ArrayList<>();
        for (RoadCrossingCandidate candidate : prioritized) {
            boolean duplicatesProposedSplit = distinct.stream().anyMatch(
                    accepted -> Math.abs(
                            accepted.proposedPathFraction()
                                    - candidate.proposedPathFraction()
                    ) <= DUPLICATE_FRACTION_TOLERANCE
            );
            boolean duplicatesExistingSplit = distinct.stream().anyMatch(
                    accepted -> accepted.crossedSegmentId().equals(
                            candidate.crossedSegmentId()
                    ) && Math.abs(
                            accepted.crossedPathFraction()
                                    - candidate.crossedPathFraction()
                    ) <= DUPLICATE_FRACTION_TOLERANCE
            );
            if (!duplicatesProposedSplit && !duplicatesExistingSplit) {
                distinct.add(candidate);
            }
        }

        distinct.sort(Comparator
                .comparingDouble(RoadCrossingCandidate::proposedPathFraction)
                .thenComparing(RoadCrossingCandidate::crossedSegmentId)
                .thenComparingDouble(RoadCrossingCandidate::crossedPathFraction));
        return List.copyOf(distinct);
    }

    private static double contactDistance(
            ReferenceGeometry proposed,
            RoadCrossingCandidate candidate
    ) {
        RoadPoint proposedPosition = proposed.sample(
                proposed.length() * candidate.proposedPathFraction()
        ).position();
        return Math.hypot(
                proposedPosition.x() - candidate.nodePosition().x(),
                proposedPosition.z() - candidate.nodePosition().z()
        );
    }

    private static void addEndpointJunction(
            RoadPoint endpoint,
            double proposedFraction,
            RoadSegment existing,
            List<Chord> chords,
            double maximumVerticalGap,
            Set<CandidateKey> seen,
            List<RoadCrossingCandidate> result
    ) {
        EndpointProjection best = null;
        for (Chord chord : chords) {
            double deltaX = chord.end().x() - chord.start().x();
            double deltaZ = chord.end().z() - chord.start().z();
            double lengthSquared = deltaX * deltaX + deltaZ * deltaZ;
            if (lengthSquared == 0.0) {
                continue;
            }
            double local = ((endpoint.x() - chord.start().x()) * deltaX
                    + (endpoint.z() - chord.start().z()) * deltaZ)
                    / lengthSquared;
            if (local < 0.0 || local > 1.0) {
                continue;
            }
            double fraction = interpolate(
                    chord.startFraction(),
                    chord.endFraction(),
                    local
            );
            if (!strictInterior(fraction)) {
                continue;
            }
            PathSample sample = existing.referenceGeometry().sample(
                    existing.referenceGeometry().length() * fraction
            );
            double horizontal = Math.hypot(
                    endpoint.x() - sample.position().x(),
                    endpoint.z() - sample.position().z()
            );
            double vertical = Math.abs(
                    endpoint.y() - sample.position().y()
            );
            double surfaceRadius = existing.presetSnapshot()
                    .surfaceBlockWidth() / 2.0
                    + ENDPOINT_SURFACE_MARGIN;
            if (horizontal > surfaceRadius
                    || vertical > maximumVerticalGap) {
                continue;
            }
            EndpointProjection candidate = new EndpointProjection(
                    fraction,
                    horizontal,
                    vertical,
                    sample.position()
            );
            if (best == null
                    || candidate.horizontalDistance()
                            < best.horizontalDistance()) {
                best = candidate;
            }
        }
        if (best == null) {
            return;
        }
        CandidateKey key = new CandidateKey(
                existing.id(),
                quantize(proposedFraction),
                quantize(best.fraction())
        );
        if (!seen.add(key)) {
            return;
        }
        result.add(new RoadCrossingCandidate(
                existing.id(),
                best.position(),
                proposedFraction,
                best.fraction(),
                best.verticalGap()
        ));
    }

    private static List<Chord> flatten(
            ReferenceGeometry geometry,
            double maximumChordLength
    ) {
        int chords = Math.max(
                1,
                (int) Math.ceil(geometry.length() / maximumChordLength)
        );
        if (chords > MAXIMUM_CHORDS_PER_PATH) {
            throw new IllegalArgumentException(
                    "path exceeds curve-intersection chord limit"
            );
        }
        List<Chord> result = new ArrayList<>(chords);
        PathSample previous = geometry.sample(0.0);
        for (int index = 1; index <= chords; index++) {
            double endFraction = (double) index / chords;
            PathSample next = geometry.sample(geometry.length() * endFraction);
            result.add(new Chord(
                    previous.position(),
                    next.position(),
                    (double) (index - 1) / chords,
                    endFraction
            ));
            previous = next;
        }
        return result;
    }

    private static Intersection intersect(Chord first, Chord second) {
        double rx = first.end().x() - first.start().x();
        double rz = first.end().z() - first.start().z();
        double sx = second.end().x() - second.start().x();
        double sz = second.end().z() - second.start().z();
        double denominator = cross(rx, rz, sx, sz);
        double scale = Math.hypot(rx, rz) * Math.hypot(sx, sz);
        if (scale == 0.0
                || Math.abs(denominator) <= PARALLEL_TOLERANCE * scale) {
            return null;
        }
        double qpx = second.start().x() - first.start().x();
        double qpz = second.start().z() - first.start().z();
        double firstLocal = cross(qpx, qpz, sx, sz) / denominator;
        double secondLocal = cross(qpx, qpz, rx, rz) / denominator;
        if (firstLocal < 0.0 || firstLocal > 1.0
                || secondLocal < 0.0 || secondLocal > 1.0) {
            return null;
        }
        return new Intersection(
                interpolate(
                        first.startFraction(),
                        first.endFraction(),
                        firstLocal
                ),
                interpolate(
                        second.startFraction(),
                        second.endFraction(),
                        secondLocal
                )
        );
    }

    private static boolean strictInterior(double fraction) {
        return fraction > ENDPOINT_TOLERANCE
                && fraction < 1.0 - ENDPOINT_TOLERANCE;
    }

    private static long quantize(double fraction) {
        return Math.round(fraction / DUPLICATE_FRACTION_TOLERANCE);
    }

    private static double interpolate(double start, double end, double amount) {
        return start + (end - start) * amount;
    }

    private static double cross(double ax, double az, double bx, double bz) {
        return ax * bz - az * bx;
    }

    private static void requirePositiveFinite(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }

    private record Chord(
            RoadPoint start,
            RoadPoint end,
            double startFraction,
            double endFraction
    ) {
    }

    private record Intersection(double firstFraction, double secondFraction) {
    }

    private record CandidateKey(
            RoadSegmentId segmentId,
            long proposedBucket,
            long crossedBucket
    ) {
    }

    private record EndpointProjection(
            double fraction,
            double horizontalDistance,
            double verticalGap,
            RoadPoint position
    ) {
    }
}
