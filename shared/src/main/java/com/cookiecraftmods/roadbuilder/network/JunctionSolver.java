package com.cookiecraftmods.roadbuilder.network;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.RoadVector;
import com.cookiecraftmods.roadbuilder.preset.IntersectionControl;
import com.cookiecraftmods.roadbuilder.preset.RoadClass;
import com.cookiecraftmods.roadbuilder.preset.RoadSurfaceRole;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class JunctionSolver {
    private static final double STRAIGHT_DOT = -Math.cos(Math.toRadians(20.0));
    private static final double MINIMUM_SINE = 0.25;
    private static final double MAXIMUM_TRIM = 32.0;
    private static final double MAXIMUM_FORK_TRIM = 12.0;
    private static final double END_CLEARANCE = 0.25;

    public JunctionSolveResult solve(
            RoadNode node,
            Collection<RoadSegment> incidentSegments,
            JunctionDefinition suppliedDefinition
    ) {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(incidentSegments, "incidentSegments");
        JunctionDefinition definition = suppliedDefinition == null
                ? JunctionDefinition.automatic(node.id())
                : suppliedDefinition;
        if (!definition.nodeId().equals(node.id())) {
            throw new IllegalArgumentException(
                    "junction definition belongs to another node"
            );
        }

        List<Frame> frames = incidentSegments.stream()
                .map(segment -> frame(node.id(), Objects.requireNonNull(
                        segment,
                        "incidentSegments contains null"
                )))
                .sorted(Comparator.comparing(frame -> frame.key))
                .toList();
        if (frames.isEmpty()) {
            return new JunctionSolveResult.Failure(
                    "junctions require at least one incident arm"
            );
        }

        Set<ApproachKey> keys = frames.stream()
                .map(frame -> frame.key)
                .collect(java.util.stream.Collectors.toSet());
        if (!keys.containsAll(definition.primaryApproachKeys())
                || !keys.containsAll(definition.approachOverrides().keySet())) {
            return new JunctionSolveResult.Failure(
                    "junction definition references an absent approach"
            );
        }

        boolean automaticFork = definition.strategy() == JunctionStrategy.AUTO
                && RoadForkJunction.isFork(node, incidentSegments.stream()
                        .toList());
        Set<ApproachKey> primary = automaticFork
                ? keys
                : definition.primaryApproachKeys().isEmpty()
                ? automaticPrimary(frames)
                : Set.copyOf(definition.primaryApproachKeys());
        List<SolvedJunctionApproach> solved = new ArrayList<>(frames.size());
        for (Frame frame : frames) {
            double angleFactor = smallestAngleFactor(frame, frames);
            double desiredTrim = Math.min(
                    automaticFork ? MAXIMUM_FORK_TRIM : MAXIMUM_TRIM,
                    maximumSurfaceHalfWidth(frames) / angleFactor
            );
            double length = frame.segment.referenceGeometry().length();
            // Closely spaced junctions can leave short but valid arms. Keep
            // disjoint trim intervals at both ends instead of rejecting them.
            double maximumForSegment = length / 2.0 - END_CLEARANCE;
            if (maximumForSegment < 1.0) {
                maximumForSegment = length * 0.49;
            }
            double trim = Math.min(desiredTrim, maximumForSegment);
            boolean isPrimary = primary.contains(frame.key);
            JunctionApproachOverride override =
                    definition.approachOverrides().get(frame.key);
            IntersectionControl control = control(
                    definition,
                    frame,
                    isPrimary,
                    override
            );
            boolean crosswalk = crosswalk(
                    definition,
                    frame,
                    override
            );
            solved.add(new SolvedJunctionApproach(
                    frame.key,
                    frame.segment,
                    frame.boundaryPosition,
                    frame.outward,
                    frame.left,
                    frame.drivableHalfWidth,
                    frame.surfaceHalfWidth,
                    trim,
                    automaticFork ? IntersectionControl.FREE : control,
                    automaticFork && override == null ? false : crosswalk,
                    isPrimary
            ));
        }
        return new JunctionSolveResult.Success(solved);
    }

    private static Set<ApproachKey> automaticPrimary(List<Frame> frames) {
        List<Pair> straightPairs = new ArrayList<>();
        for (int first = 0; first < frames.size(); first++) {
            for (int second = first + 1; second < frames.size(); second++) {
                Frame a = frames.get(first);
                Frame b = frames.get(second);
                double dot = horizontalDot(a.outward, b.outward);
                if (dot <= STRAIGHT_DOT) {
                    straightPairs.add(new Pair(
                            a,
                            b,
                            a.segment.roadId().equals(b.segment.roadId()),
                            dot
                    ));
                }
            }
        }
        Frame selectedFrame = frames.stream()
                .max(Comparator
                        .comparingInt(Frame::priorityRank)
                        .thenComparingInt(Frame::roadHierarchy)
                        .thenComparingDouble(Frame::drivableHalfWidth)
                        .thenComparing(
                                frame -> frame.segment.id(),
                                Comparator.reverseOrder()
                        ))
                .orElseThrow();
        if (!straightPairs.isEmpty()) {
            Pair selected = straightPairs.stream()
                    .max(Comparator
                            .comparingInt(Pair::priorityRank)
                            .thenComparingInt(Pair::roadHierarchy)
                            .thenComparingDouble(Pair::maximumDrivableHalfWidth)
                            .thenComparing(Pair::sameRoad)
                            .thenComparingDouble(pair -> -pair.dot)
                            .thenComparing(
                                    pair -> pair.first.key,
                                    Comparator.reverseOrder()
                            )
                            .thenComparing(
                                    pair -> pair.second.key,
                                    Comparator.reverseOrder()
                            ))
                    .orElseThrow();
            if (selectedFrame.priorityRank() > selected.priorityRank()
                    || selectedFrame.priorityRank() == selected.priorityRank()
                    && selectedFrame.roadHierarchy()
                    > selected.roadHierarchy()
                    || selectedFrame.priorityRank() == selected.priorityRank()
                    && selectedFrame.roadHierarchy()
                    == selected.roadHierarchy()
                    && selectedFrame.drivableHalfWidth()
                    > selected.maximumDrivableHalfWidth() * 1.25) {
                return Set.of(selectedFrame.key);
            }
            return Set.of(selected.first.key, selected.second.key);
        }
        return Set.of(selectedFrame.key);
    }

    private static IntersectionControl control(
            JunctionDefinition definition,
            Frame frame,
            boolean primary,
            JunctionApproachOverride override
    ) {
        if (override != null) {
            return override.control();
        }
        if (definition.priorityPolicy()
                == JunctionPriorityPolicy.ALL_WAY_STOP) {
            return IntersectionControl.STOP;
        }
        if (definition.priorityPolicy() == JunctionPriorityPolicy.MANUAL) {
            return frame.segment.presetSnapshot()
                    .intersectionStyle().defaultControl();
        }
        if (primary) {
            return IntersectionControl.FREE;
        }
        IntersectionControl preset = frame.segment.presetSnapshot()
                .intersectionStyle().defaultControl();
        return preset == IntersectionControl.FREE
                ? IntersectionControl.YIELD
                : preset;
    }

    private static boolean crosswalk(
            JunctionDefinition definition,
            Frame frame,
            JunctionApproachOverride override
    ) {
        if (frame.segment.presetSnapshot().classification().roadClass() == RoadClass.HIGHWAY) {
            return false;
        }
        return switch (definition.crossingPolicy()) {
            case NONE -> false;
            case ALL_APPROACHES -> true;
            case MANUAL -> override != null && override.crosswalk();
            case AUTO -> override != null
                    ? override.crosswalk()
                    : frame.segment.presetSnapshot()
                            .intersectionStyle().defaultCrosswalks();
        };
    }

    private static Frame frame(RoadNodeId nodeId, RoadSegment segment) {
        ApproachKey key = ApproachKey.at(segment, nodeId);
        boolean atStart = key.end() == ApproachEnd.START;
        var sample = segment.referenceGeometry().sample(
                atStart ? 0.0 : segment.referenceGeometry().length()
        );
        RoadVector tangent = sample.tangent();
        RoadVector outward = atStart ? tangent : tangent.scale(-1.0);
        double horizontal = outward.horizontalLength();
        if (horizontal == 0.0) {
            throw new IllegalArgumentException(
                    "junction approach has no horizontal tangent"
            );
        }
        outward = new RoadVector(
                outward.x() / horizontal,
                0.0,
                outward.z() / horizontal
        );
        double drivableWidth = segment.presetSnapshot().surfaceBands().stream()
                .filter(band -> band.role() != RoadSurfaceRole.SIDEWALK)
                .mapToInt(band -> band.width())
                .sum();
        return new Frame(
                key,
                segment,
                sample.position(),
                outward,
                outward.horizontalLeftUnit(),
                drivableWidth / 2.0,
                segment.presetSnapshot().surfaceBlockWidth() / 2.0
        );
    }

    private static double smallestAngleFactor(
            Frame frame,
            List<Frame> frames
    ) {
        double factor = 1.0;
        for (Frame other : frames) {
            if (other == frame) {
                continue;
            }
            double dot = Math.max(
                    -1.0,
                    Math.min(1.0, horizontalDot(frame.outward, other.outward))
            );
            double sineHalf = Math.sqrt(Math.max(0.0, (1.0 - dot) / 2.0));
            factor = Math.min(factor, Math.max(MINIMUM_SINE, sineHalf));
        }
        return factor;
    }

    private static double maximumSurfaceHalfWidth(List<Frame> frames) {
        return frames.stream().mapToDouble(Frame::surfaceHalfWidth).max()
                .orElseThrow();
    }

    private static double horizontalDot(RoadVector first, RoadVector second) {
        return first.x() * second.x() + first.z() * second.z();
    }

    private record Frame(
            ApproachKey key,
            RoadSegment segment,
            RoadPoint boundaryPosition,
            RoadVector outward,
            RoadVector left,
            double drivableHalfWidth,
            double surfaceHalfWidth
    ) {
        int priorityRank() {
            return segment.presetSnapshot().classification().priorityRank();
        }

        int roadHierarchy() {
            return segment.presetSnapshot().classification()
                    .roadClass().hierarchy();
        }
    }

    private record Pair(
            Frame first,
            Frame second,
            boolean sameRoad,
            double dot
    ) {
        int priorityRank() {
            return Math.max(first.priorityRank(), second.priorityRank());
        }

        int roadHierarchy() {
            return Math.max(first.roadHierarchy(), second.roadHierarchy());
        }

        double maximumDrivableHalfWidth() {
            return Math.max(
                    first.drivableHalfWidth,
                    second.drivableHalfWidth
            );
        }
    }
}
