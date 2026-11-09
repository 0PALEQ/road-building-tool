package com.cookiecraftmods.roadbuilder.geometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.DoubleFunction;

final class AdaptiveCurveSampler {
    static final double MAX_CHORD_ERROR = 1.0 / 64.0;
    static final double MAX_CHORD_LENGTH = 0.5;
    private static final int MAX_DEPTH = 18;
    private static final int MAX_SAMPLES = 262_145;
    private static final double DIRECTION_EPSILON = 1.0e-10;

    private AdaptiveCurveSampler() {
    }

    static ArcLengthTable sample(
            DoubleFunction<RoadPoint> positionAt,
            DoubleFunction<RoadVector> derivativeAt
    ) {
        Objects.requireNonNull(positionAt, "positionAt");
        Objects.requireNonNull(derivativeAt, "derivativeAt");

        ArrayList<Knot> knots = new ArrayList<>();
        RoadPoint start = positionAt.apply(0.0);
        RoadPoint end = positionAt.apply(1.0);
        knots.add(new Knot(0.0, start));
        subdivide(positionAt, 0.0, start, 1.0, end, 0, knots);
        return new ArcLengthTable(positionAt, derivativeAt, knots);
    }

    private static void subdivide(
            DoubleFunction<RoadPoint> positionAt,
            double startParameter,
            RoadPoint start,
            double endParameter,
            RoadPoint end,
            int depth,
            List<Knot> output
    ) {
        double interval = endParameter - startParameter;
        double quarterParameter = startParameter + interval * 0.25;
        double middleParameter = startParameter + interval * 0.5;
        double threeQuarterParameter = startParameter + interval * 0.75;
        RoadPoint quarter = positionAt.apply(quarterParameter);
        RoadPoint middle = positionAt.apply(middleParameter);
        RoadPoint threeQuarter = positionAt.apply(threeQuarterParameter);

        double chordLength = start.distanceTo(end);
        double polylineLength = start.distanceTo(quarter)
                + quarter.distanceTo(middle)
                + middle.distanceTo(threeQuarter)
                + threeQuarter.distanceTo(end);
        double chordError = polylineLength - chordLength;
        double maximumDeviation = Math.max(
                distanceToChord(quarter, start, end),
                Math.max(
                        distanceToChord(middle, start, end),
                        distanceToChord(threeQuarter, start, end)
                )
        );
        boolean flatEnough = chordLength <= MAX_CHORD_LENGTH
                && chordError <= MAX_CHORD_ERROR
                && maximumDeviation <= MAX_CHORD_ERROR;

        if (flatEnough || depth == MAX_DEPTH) {
            if (output.size() >= MAX_SAMPLES) {
                throw new IllegalArgumentException(
                        "curve exceeds the adaptive sample limit"
                );
            }
            output.add(new Knot(endParameter, end));
            return;
        }

        subdivide(
                positionAt,
                startParameter,
                start,
                middleParameter,
                middle,
                depth + 1,
                output
        );
        subdivide(
                positionAt,
                middleParameter,
                middle,
                endParameter,
                end,
                depth + 1,
                output
        );
    }

    private static double distanceToChord(
            RoadPoint point,
            RoadPoint start,
            RoadPoint end
    ) {
        RoadVector chord = start.vectorTo(end);
        double chordLengthSquared = chord.dot(chord);
        if (chordLengthSquared == 0.0) {
            return start.distanceTo(point);
        }
        double projection = start.vectorTo(point).dot(chord) / chordLengthSquared;
        double clamped = Math.max(0.0, Math.min(1.0, projection));
        RoadPoint closest = start.add(chord.scale(clamped));
        return closest.distanceTo(point);
    }

    static final class ArcLengthTable {
        private final DoubleFunction<RoadPoint> positionAt;
        private final DoubleFunction<RoadVector> derivativeAt;
        private final List<Knot> knots;
        private final double[] cumulativeLengths;
        private final double length;

        private ArcLengthTable(
                DoubleFunction<RoadPoint> positionAt,
                DoubleFunction<RoadVector> derivativeAt,
                List<Knot> knots
        ) {
            this.positionAt = positionAt;
            this.derivativeAt = derivativeAt;
            this.knots = Collections.unmodifiableList(new ArrayList<>(knots));
            this.cumulativeLengths = new double[knots.size()];

            double accumulated = 0.0;
            for (int index = 1; index < knots.size(); index++) {
                accumulated += knots.get(index - 1)
                        .position()
                        .distanceTo(knots.get(index).position());
                if (!Double.isFinite(accumulated)) {
                    throw new IllegalArgumentException(
                            "curve length must be finite"
                    );
                }
                cumulativeLengths[index] = accumulated;
            }
            if (accumulated == 0.0) {
                throw new IllegalArgumentException(
                        "curve must have a finite, non-zero length"
                );
            }
            this.length = accumulated;

            for (int index = 0; index < knots.size(); index++) {
                tangentAt(knots.get(index).parameter(), index);
            }
        }

        double length() {
            return length;
        }

        int sampleCount() {
            return knots.size();
        }

        double distanceAtParameter(double parameter) {
            if (!Double.isFinite(parameter)
                    || parameter < 0.0
                    || parameter > 1.0) {
                throw new IllegalArgumentException(
                        "parameter must be between 0 and 1"
                );
            }
            if (parameter == 0.0) {
                return 0.0;
            }
            if (parameter == 1.0) {
                return length;
            }
            int low = 1;
            int high = knots.size() - 1;
            while (low < high) {
                int middle = (low + high) >>> 1;
                if (knots.get(middle).parameter() <= parameter) {
                    low = middle + 1;
                } else {
                    high = middle;
                }
            }
            int upper = low;
            int lower = upper - 1;
            double lowerParameter = knots.get(lower).parameter();
            double fraction = (parameter - lowerParameter)
                    / (knots.get(upper).parameter() - lowerParameter);
            return cumulativeLengths[lower]
                    + (cumulativeLengths[upper] - cumulativeLengths[lower])
                    * fraction;
        }

        double parameterAtDistance(double distance) {
            requireDistance(distance);
            if (distance == 0.0) return 0.0;
            if (distance == length) return 1.0;
            int upper = upperBound(cumulativeLengths, distance);
            int lower = upper - 1;
            double fraction = (distance - cumulativeLengths[lower])
                    / (cumulativeLengths[upper] - cumulativeLengths[lower]);
            return knots.get(lower).parameter()
                    + (knots.get(upper).parameter() - knots.get(lower).parameter()) * fraction;
        }

        PathSample sample(double distance) {
            requireDistance(distance);
            if (distance == 0.0) {
                return sampleAtKnot(0, 0.0);
            }
            if (distance == length) {
                return sampleAtKnot(knots.size() - 1, length);
            }

            int upper = upperBound(cumulativeLengths, distance);
            int lower = upper - 1;
            double segmentStartDistance = cumulativeLengths[lower];
            double segmentLength = cumulativeLengths[upper]
                    - segmentStartDistance;
            double fraction = (distance - segmentStartDistance) / segmentLength;
            double parameter = knots.get(lower).parameter()
                    + (knots.get(upper).parameter()
                    - knots.get(lower).parameter()) * fraction;
            RoadPoint position = positionAt.apply(parameter);
            return new PathSample(
                    distance,
                    position,
                    tangentAt(parameter, lower)
            );
        }

        private PathSample sampleAtKnot(int index, double distance) {
            Knot knot = knots.get(index);
            return new PathSample(
                    distance,
                    knot.position(),
                    tangentAt(knot.parameter(), Math.max(0, index - 1))
            );
        }

        private RoadVector tangentAt(double parameter, int nearbySegment) {
            RoadVector derivative = derivativeAt.apply(parameter);
            if (usableDirection(derivative)) {
                return derivative.normalize();
            }

            int lower = Math.min(nearbySegment, knots.size() - 2);
            for (int radius = 0; radius < knots.size(); radius++) {
                int forward = lower + radius;
                if (forward < knots.size() - 1) {
                    RoadVector candidate = knots.get(forward)
                            .position()
                            .vectorTo(knots.get(forward + 1).position());
                    if (usableDirection(candidate)) {
                        return candidate.normalize();
                    }
                }
                int backward = lower - radius;
                if (backward >= 0) {
                    RoadVector candidate = knots.get(backward)
                            .position()
                            .vectorTo(knots.get(backward + 1).position());
                    if (usableDirection(candidate)) {
                        return candidate.normalize();
                    }
                }
            }
            throw new IllegalArgumentException(
                    "a road curve must have a horizontal direction"
            );
        }

        private static boolean usableDirection(RoadVector vector) {
            return vector.length() > DIRECTION_EPSILON
                    && vector.horizontalLength() > DIRECTION_EPSILON;
        }

        private void requireDistance(double distance) {
            if (!Double.isFinite(distance)) {
                throw new IllegalArgumentException("distance must be finite");
            }
            if (distance < 0.0 || distance > length) {
                throw new IllegalArgumentException(
                        "distance must be between 0 and " + length
                );
            }
        }

        private static int upperBound(double[] values, double target) {
            int low = 1;
            int high = values.length - 1;
            while (low < high) {
                int middle = (low + high) >>> 1;
                if (values[middle] <= target) {
                    low = middle + 1;
                } else {
                    high = middle;
                }
            }
            return low;
        }
    }

    private record Knot(double parameter, RoadPoint position) {
    }
}
