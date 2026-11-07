package com.cookiecraftmods.roadbuilder.client.tool;

import com.cookiecraftmods.roadbuilder.geometry.PathSample;
import com.cookiecraftmods.roadbuilder.geometry.ReferenceGeometry;
import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.RoadVector;
import com.cookiecraftmods.roadbuilder.geometry.StraightGeometry;
import com.cookiecraftmods.roadbuilder.network.RoadNode;
import com.cookiecraftmods.roadbuilder.network.RoadNodeId;
import com.cookiecraftmods.roadbuilder.network.RoadSegment;
import com.cookiecraftmods.roadbuilder.network.RoadSegmentId;
import com.cookiecraftmods.roadbuilder.preset.LaneComponent;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class RoadSnapResolver {
    static final double NODE_SNAP_RADIUS = 2.5;
    static final double NODE_SNAP_MAXIMUM_VERTICAL_GAP = 6.0;
    static final double ANGLE_SNAP_INCREMENT_RADIANS = Math.toRadians(45.0);

    private static final double[] ANGLE_OFFSETS = {0, 45, -45, 90, -90, 135, -135, 180};

    private static final double GEOMETRY_CONTACT_TOLERANCE = 0.075;
    private static final double PROJECTION_CHORD_LENGTH = 0.5;
    private static final int MAXIMUM_PROJECTION_CHORDS = 8_192;
    private static final int PROJECTION_REFINEMENT_STEPS = 18;
    private static final double MINIMUM_GUIDE_PROJECTION = 0.25;
    private static final double MINIMUM_VISIBLE_GUIDE_LENGTH = 12.0;
    private static final double GUIDE_EXTENSION = 6.0;
    private static final double DIRECTION_DUPLICATE_DOT = 1.0 - 1.0e-9;
    private static final double STRAIGHT_NODE_DOT =
            -Math.cos(Math.toRadians(10.0));
    private static final double MINIMUM_JUNCTION_ANGLE_FACTOR = 0.25;
    private static final double MAXIMUM_JUNCTION_FOOTPRINT_RADIUS = 32.0;
    private static final double JUNCTION_FOOTPRINT_MARGIN = 1.0;

    private RoadSnapResolver() {
    }

    public static CursorTarget snap(
            CursorTarget raw,
            CursorTarget start,
            Collection<RoadNode> nodes,
            RoadSnappingMode mode
    ) {
        Objects.requireNonNull(raw, "raw");
        Objects.requireNonNull(nodes, "nodes");
        Objects.requireNonNull(mode, "mode");
        return resolve(
                raw,
                start,
                nodes,
                List.of(),
                mode,
                new RoadSnapSettings(
                        NODE_SNAP_RADIUS,
                        1.0,
                        RoadSnapPriority.ANGLE_TANGENT_GRID,
                        false,
                        false
                ),
                false
        ).target();
    }

    public static CursorTarget snap(
            CursorTarget raw,
            CursorTarget start,
            Collection<RoadNode> nodes,
            Collection<RoadSegment> segments,
            RoadSnappingMode mode,
            RoadSnapSettings settings,
            boolean temporaryOverride
    ) {
        return resolve(
                raw,
                start,
                nodes,
                segments,
                mode,
                settings,
                temporaryOverride
        ).target();
    }

    public static RoadSnapResult resolve(
            CursorTarget raw,
            CursorTarget start,
            Collection<RoadNode> nodes,
            Collection<RoadSegment> segments,
            RoadSnappingMode mode,
            RoadSnapSettings settings,
            boolean temporaryOverride
    ) {
        return resolve(
                raw,
                start,
                nodes,
                segments,
                mode,
                settings,
                temporaryOverride,
                null
        );
    }

    public static RoadSnapResult resolve(
            CursorTarget raw,
            CursorTarget start,
            Collection<RoadNode> nodes,
            Collection<RoadSegment> segments,
            RoadSnappingMode mode,
            RoadSnapSettings settings,
            boolean temporaryOverride,
            CurveAngleContext curveAngleContext
    ) {
        Objects.requireNonNull(raw, "raw");
        Objects.requireNonNull(nodes, "nodes");
        Objects.requireNonNull(segments, "segments");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(settings, "settings");

        Map<RoadNodeId, List<RoadSegment>> incidentByNode =
                incidentSegmentsByNode(segments);
        if (mode.snapsNodes()) {
            NodeCandidate nearest = nodes.stream()
                    .map(node -> Objects.requireNonNull(
                            node,
                            "nodes contains null"
                    ))
                    .filter(node -> start == null
                            || !node.position().equals(start.roadPoint()))
                    .filter(node -> Math.abs(
                            node.position().y() - raw.roadPoint().y()
                    ) <= NODE_SNAP_MAXIMUM_VERTICAL_GAP)
                    .map(node -> new NodeCandidate(
                            node,
                            settings.range()
                    ))
                    .filter(candidate -> horizontalDistance(
                            candidate.node().position(),
                            raw.roadPoint()
                    ) <= candidate.radius())
                    .min(Comparator
                            .comparingDouble((NodeCandidate candidate) ->
                                    horizontalDistance(
                                            candidate.node().position(),
                                            raw.roadPoint()
                                    ))
                            .thenComparing(candidate ->
                                    candidate.node().id()))
                    .orElse(null);
            if (nearest != null) {
                return RoadSnapResult.unsnapped(
                        target(nearest.node().position())
                );
            }
            SegmentProjection nearestCenterline = nearestRoadSurface(
                    raw.roadPoint(),
                    segments,
                    settings.range()
            );
            if (nearestCenterline != null) {
                RoadPoint snapped = nearestCenterline.projection()
                        .position();
                RoadVector direction = horizontalUnit(
                        nearestCenterline.projection().sample().tangent()
                );
                double halfGuideLength = MINIMUM_VISIBLE_GUIDE_LENGTH / 2.0;
                return new RoadSnapResult(
                        target(snapped),
                        List.of(new RoadSnapGuide(
                                snapped.subtract(direction.scale(
                                        halfGuideLength
                                )),
                                snapped.add(direction.scale(
                                        halfGuideLength
                                )),
                                RoadSnapGuide.Kind.LANE_CONTINUATION
                        ))
                );
            }
        }

        if (temporaryOverride) {
            return RoadSnapResult.unsnapped(raw);
        }
        if (mode.snapsGuidelines()) {
            SegmentProjection nearestSurface = nearestRoadSurface(
                    raw.roadPoint(),
                    segments,
                    settings.range()
            );
            if (nearestSurface != null) {
                RoadPoint snapped = nearestSurface.guidePosition();
                RoadVector direction = horizontalUnit(
                        nearestSurface.projection().sample().tangent()
                );
                double halfGuideLength = MINIMUM_VISIBLE_GUIDE_LENGTH / 2.0;
                return new RoadSnapResult(
                        target(snapped),
                        List.of(new RoadSnapGuide(
                                snapped.subtract(direction.scale(halfGuideLength)),
                                snapped.add(direction.scale(halfGuideLength)),
                                RoadSnapGuide.Kind.LANE_CONTINUATION
                        ))
                );
            }
        }
        for (RoadSnapPriority.Target priority : settings.priority().targets()) {
            SnapCandidate candidate = switch (priority) {
                case TANGENT -> mode.snapsGuidelines()
                        && settings.tangentEnabled()
                        ? tangentTarget(
                                raw,
                                start,
                                segments,
                                settings.range(),
                                settings.guidelineRange()
                        )
                        : null;
                case GRID -> settings.gridEnabled()
                        ? gridTarget(
                                raw,
                                settings.gridSpacing(),
                                settings.range()
                        )
                        : null;
                case ANGLE -> mode.snapsAngles()
                        ? curveAngleContext == null
                                ? angleTarget(
                                        raw,
                                        start,
                                        nodes,
                                        incidentByNode,
                                        segments,
                                        settings.range(),
                                        settings.guidelineRange()
                                )
                                : curveAngleTarget(
                                        raw,
                                        curveAngleContext, settings.range()
                                )
                        : null;
            };
            if (candidate != null) {
                return new RoadSnapResult(
                        candidate.target(),
                        candidate.guide() == null
                                ? List.of()
                                : List.of(candidate.guide())
                );
            }
        }
        return RoadSnapResult.unsnapped(raw);
    }

    public static boolean hasJunctionFootprint(
            RoadNode node,
            Collection<RoadSegment> segments
    ) {
        Objects.requireNonNull(node, "node");
        List<RoadVector> directions = incidentDirections(node, segments);
        if (directions.size() >= 3) {
            return true;
        }
        return directions.size() == 2
                && directions.get(0).dot(directions.get(1))
                        > STRAIGHT_NODE_DOT;
    }

    public static double junctionFootprintRadius(
            RoadNode node,
            Collection<RoadSegment> segments
    ) {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(segments, "segments");
        List<RoadSegment> incident = incidentSegments(node, segments);
        if (!hasJunctionFootprint(node, incident)) {
            return 0.48;
        }
        List<RoadVector> directions = incidentDirections(node, incident);
        double angleFactor = 1.0;
        for (int first = 0; first < directions.size(); first++) {
            for (int second = first + 1;
                    second < directions.size();
                    second++) {
                double dot = clamp(
                        directions.get(first).dot(directions.get(second)),
                        -1.0,
                        1.0
                );
                double sineHalf = Math.sqrt(Math.max(
                        0.0,
                        (1.0 - dot) / 2.0
                ));
                angleFactor = Math.min(
                        angleFactor,
                        Math.max(MINIMUM_JUNCTION_ANGLE_FACTOR, sineHalf)
                );
            }
        }
        double maximumHalfWidth = incident.stream()
                .mapToDouble(segment -> segment.presetSnapshot()
                        .surfaceBlockWidth() / 2.0)
                .max()
                .orElse(0.5);
        return Math.min(
                MAXIMUM_JUNCTION_FOOTPRINT_RADIUS,
                maximumHalfWidth / angleFactor + JUNCTION_FOOTPRINT_MARGIN
        );
    }

    private static List<RoadSegment> incidentSegments(
            RoadNode node,
            Collection<RoadSegment> segments
    ) {
        return segments.stream()
                .map(segment -> Objects.requireNonNull(
                        segment,
                        "segments contains null"
                ))
                .filter(segment -> segment.startNodeId().equals(node.id())
                        || segment.endNodeId().equals(node.id()))
                .toList();
    }

    private static Map<RoadNodeId, List<RoadSegment>> incidentSegmentsByNode(
            Collection<RoadSegment> segments
    ) {
        Map<RoadNodeId, List<RoadSegment>> result = new HashMap<>();
        for (RoadSegment segment : segments) {
            RoadSegment nonNull = Objects.requireNonNull(
                    segment,
                    "segments contains null"
            );
            result.computeIfAbsent(
                    nonNull.startNodeId(),
                    ignored -> new ArrayList<>()
            ).add(nonNull);
            result.computeIfAbsent(
                    nonNull.endNodeId(),
                    ignored -> new ArrayList<>()
            ).add(nonNull);
        }
        result.replaceAll((nodeId, incident) -> List.copyOf(incident));
        return Map.copyOf(result);
    }

    private static List<RoadVector> incidentDirections(
            RoadNode node,
            Collection<RoadSegment> segments
    ) {
        List<RoadVector> directions = new ArrayList<>();
        for (RoadSegment segment : incidentSegments(node, segments)) {
            boolean atStart = segment.startNodeId().equals(node.id());
            PathSample sample = segment.referenceGeometry().sample(
                    atStart ? 0.0 : segment.referenceGeometry().length()
            );
            RoadVector outward = atStart
                    ? sample.tangent()
                    : sample.tangent().scale(-1.0);
            addDistinctDirection(directions, outward);
        }
        return List.copyOf(directions);
    }

    private static SegmentProjection nearestRoadSurface(
            RoadPoint raw,
            Collection<RoadSegment> segments,
            double snapRange
    ) {
        return segments.stream()
                .map(segment -> Objects.requireNonNull(
                        segment,
                        "segments contains null"
                ))
                .map(segment -> new SegmentProjection(
                        segment,
                        project(segment.referenceGeometry(), raw),
                        raw
                ))
                .filter(candidate -> candidate.projection().verticalGap()
                        <= NODE_SNAP_MAXIMUM_VERTICAL_GAP)
                .filter(candidate -> candidate.withinSurface(snapRange))
                .min(Comparator
                        .<SegmentProjection>comparingDouble(candidate ->
                                candidate.snapDistance(snapRange))
                        .thenComparingDouble(candidate -> candidate
                                .projection().horizontalDistance())
                        .thenComparing(candidate -> candidate.segment().id()))
                .orElse(null);
    }

    private static SnapCandidate tangentTarget(
            CursorTarget raw,
            CursorTarget start,
            Collection<RoadSegment> segments,
            double range,
            double guidelineRange
    ) {
        List<SnapCandidate> candidates = new ArrayList<>();
        if (start != null) {
            List<DirectionCandidate> startDirections = directionsAt(
                    start.roadPoint(),
                    segments
            );
            for (DirectionCandidate direction : startDirections) {
                SnapCandidate candidate = orthogonalGuideCandidate(
                        raw,
                        start.roadPoint(),
                        direction.direction(),
                        range,
                        guidelineRange,
                        RoadSnapGuide.Kind.LANE_CONTINUATION,
                        0,
                        direction.segmentId(),
                        0L
                );
                if (candidate != null) {
                    candidates.add(candidate);
                }
            }
        }

        for (RoadSegment segment : segments) {
            RoadSegment nonNull = Objects.requireNonNull(
                    segment,
                    "segments contains null"
            );
            List<Double> offsets = laneGuideOffsets(nonNull);
            ReferenceGeometry geometry = nonNull.referenceGeometry();
            PathSample first = geometry.sample(0.0);
            PathSample last = geometry.sample(geometry.length());
            for (int endIndex = 0; endIndex < 2; endIndex++) {
                PathSample endpoint = endIndex == 0 ? first : last;
                RoadVector outward = endIndex == 0
                        ? endpoint.tangent().scale(-1.0)
                        : endpoint.tangent();
                for (double offset : offsets) {
                    RoadPoint anchor = endpoint.offsetLaterally(offset)
                            .position();
                    SnapCandidate candidate = orthogonalGuideCandidate(
                            raw,
                            anchor,
                            outward,
                            range,
                            guidelineRange,
                            RoadSnapGuide.Kind.LANE_CONTINUATION,
                            1,
                            nonNull.id(),
                            Math.round(offset * 1_000_000.0)
                                    * 2L + endIndex
                    );
                    if (candidate != null) {
                        candidates.add(candidate);
                    }
                }
            }
        }
        return bestCandidate(candidates);
    }

    private static List<Double> laneGuideOffsets(RoadSegment segment) {
        List<Double> centers = segment.presetSnapshot()
                .lanes(segment.trafficHandedness()).stream()
                .map(LaneComponent::lateralOffset)
                .map(offset -> -offset)
                .sorted()
                .toList();
        List<Double> offsets = new ArrayList<>();
        addDistinctOffset(offsets, 0.0);
        double surfaceHalfWidth = segment.presetSnapshot()
                .surfaceBlockWidth() / 2.0;
        addDistinctOffset(offsets, -surfaceHalfWidth);
        addDistinctOffset(offsets, surfaceHalfWidth);
        centers.forEach(offset -> addDistinctOffset(offsets, offset));
        for (int index = 1; index < centers.size(); index++) {
            addDistinctOffset(
                    offsets,
                    (centers.get(index - 1) + centers.get(index)) / 2.0
            );
        }
        offsets.sort(Double::compare);
        return List.copyOf(offsets);
    }

    private static void addDistinctOffset(List<Double> offsets, double value) {
        if (offsets.stream().noneMatch(existing ->
                Math.abs(existing - value) <= 1.0e-6)) {
            offsets.add(value == 0.0 ? 0.0 : value);
        }
    }

    private static SnapCandidate gridTarget(
            CursorTarget raw,
            double spacing,
            double range
    ) {
        RoadPoint point = raw.roadPoint();
        RoadPoint snapped = new RoadPoint(
                Math.rint((point.x() - 0.5) / spacing) * spacing + 0.5,
                point.y(),
                Math.rint((point.z() - 0.5) / spacing) * spacing + 0.5
        );
        double distance = horizontalDistance(point, snapped);
        return distance <= range
                ? new SnapCandidate(
                        target(snapped),
                        null,
                        distance,
                        0,
                        null,
                        0L
                )
                : null;
    }

    private static SnapCandidate angleTarget(
            CursorTarget raw,
            CursorTarget start,
            Collection<RoadNode> nodes,
            Map<RoadNodeId, List<RoadSegment>> incidentByNode,
            Collection<RoadSegment> segments,
            double range,
            double guidelineRange
    ) {
        List<SnapCandidate> candidates = new ArrayList<>();
        if (start != null) {
            SnapCandidate selectedStart = angleTargetFromStart(
                    raw,
                    start,
                    segments,
                    range
            );
            if (selectedStart != null) {
                candidates.add(selectedStart);
            }
        }

        double[] offsets = ANGLE_OFFSETS;
        for (RoadNode suppliedNode : nodes) {
            RoadNode node = Objects.requireNonNull(
                    suppliedNode,
                    "nodes contains null"
            );
            if (start != null
                    && node.position().equals(start.roadPoint())) {
                continue;
            }
            if (Math.abs(node.position().y() - raw.roadPoint().y())
                    > NODE_SNAP_MAXIMUM_VERTICAL_GAP) {
                continue;
            }

            List<DirectionCandidate> guideDirections = new ArrayList<>();
            for (DirectionCandidate frame : directionsAtNode(
                    node,
                    incidentByNode.getOrDefault(node.id(), List.of())
            )) {
                RoadVector horizontal = horizontalUnit(frame.direction());
                for (double degrees : offsets) {
                    addDistinctDirectionCandidate(
                            guideDirections,
                            new DirectionCandidate(
                                    frame.segmentId(),
                                    rotateHorizontal(
                                            horizontal,
                                            Math.toRadians(degrees)
                                    )
                            )
                    );
                }
            }

            long nodeTieBreaker = Integer.toUnsignedLong(
                    node.id().hashCode()
            ) << 32;
            for (int index = 0;
                    index < guideDirections.size();
                    index++) {
                DirectionCandidate direction = guideDirections.get(index);
                SnapCandidate candidate = rayCandidate(
                        raw,
                        node.position(),
                        direction.direction(),
                        range,
                        guidelineRange,
                        RoadSnapGuide.Kind.NODE_ANGLE,
                        1,
                        direction.segmentId(),
                        nodeTieBreaker | Integer.toUnsignedLong(index)
                );
                if (candidate != null) {
                    candidates.add(candidate);
                }
            }
        }
        return bestCandidate(candidates);
    }

    private static SnapCandidate angleTargetFromStart(
            CursorTarget raw,
            CursorTarget start,
            Collection<RoadSegment> segments,
            double range
    ) {
        RoadPoint startPoint = start.roadPoint();
        RoadPoint end = raw.roadPoint();
        double deltaX = end.x() - startPoint.x();
        double deltaZ = end.z() - startPoint.z();
        double horizontalLength = Math.hypot(deltaX, deltaZ);
        if (horizontalLength == 0.0) {
            return null;
        }

        List<DirectionCandidate> frames = directionsAt(startPoint, segments);
        List<RoadVector> directions = new ArrayList<>();
        if (frames.isEmpty()) {
            double angle = Math.atan2(deltaZ, deltaX);
            double snappedAngle = Math.rint(
                    angle / ANGLE_SNAP_INCREMENT_RADIANS
            ) * ANGLE_SNAP_INCREMENT_RADIANS;
            directions.add(new RoadVector(
                    Math.cos(snappedAngle),
                    0.0,
                    Math.sin(snappedAngle)
            ));
        } else {
            double[] offsets = ANGLE_OFFSETS;
            for (DirectionCandidate frame : frames) {
                RoadVector horizontal = horizontalUnit(frame.direction());
                for (double degrees : offsets) {
                    addDistinctDirection(
                            directions,
                            rotateHorizontal(horizontal, Math.toRadians(degrees))
                    );
                }
            }
        }

        List<SnapCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < directions.size(); index++) {
            RoadVector direction = directions.get(index);
            RoadPoint snapped = new RoadPoint(
                    startPoint.x() + direction.x() * horizontalLength,
                    end.y(),
                    startPoint.z() + direction.z() * horizontalLength
            );
            double distance = horizontalDistance(end, snapped);
            if (distance > range) {
                continue;
            }
            RoadPoint guideStart = new RoadPoint(
                    startPoint.x(),
                    end.y(),
                    startPoint.z()
            );
            RoadPoint guideEnd = guideStart.add(direction.scale(
                    Math.max(
                            MINIMUM_VISIBLE_GUIDE_LENGTH,
                            horizontalLength + GUIDE_EXTENSION
                    )
            ));
            candidates.add(new SnapCandidate(
                    target(snapped),
                    new RoadSnapGuide(
                            guideStart,
                            guideEnd,
                            RoadSnapGuide.Kind.NODE_ANGLE
                    ),
                    distance,
                    0,
                    null,
                    index
            ));
        }
        return bestCandidate(candidates);
    }

    private static SnapCandidate curveAngleTarget(
            CursorTarget raw,
            CurveAngleContext context, double range
    ) {
        RoadPoint start = context.start();
        RoadPoint control = context.control();
        double startDeltaX = start.x() - control.x();
        double startDeltaZ = start.z() - control.z();
        double tangentLength = Math.hypot(startDeltaX, startDeltaZ);
        if (tangentLength <= GEOMETRY_CONTACT_TOLERANCE) {
            return null;
        }

        RoadVector startDirection = new RoadVector(
                startDeltaX / tangentLength,
                0.0,
                startDeltaZ / tangentLength
        );
        double legLength = context.equalLegs() ? tangentLength
                : horizontalDistance(control, raw.roadPoint());
        if (legLength <= GEOMETRY_CONTACT_TOLERANCE) {
            return null;
        }
        double[] angles = context.equalLegs()
                ? new double[]{45.0, -45.0, 90.0, -90.0, 180.0}
                : ANGLE_OFFSETS;
        List<SnapCandidate> candidates = new ArrayList<>(angles.length);
        for (int index = 0; index < angles.length; index++) {
            RoadVector endDirection = rotateHorizontal(
                    startDirection,
                    Math.toRadians(angles[index])
            );
            RoadPoint snapped = new RoadPoint(
                    control.x() + endDirection.x() * legLength,
                    raw.roadPoint().y(),
                    control.z() + endDirection.z() * legLength
            );
            double distance = horizontalDistance(raw.roadPoint(), snapped);
            if (!context.equalLegs() && distance > range) {
                continue;
            }
            RoadPoint guideStart = new RoadPoint(
                    control.x(),
                    raw.roadPoint().y(),
                    control.z()
            );
            candidates.add(new SnapCandidate(
                    target(snapped),
                    new RoadSnapGuide(
                            guideStart,
                            guideStart.add(endDirection.scale(Math.max(
                                    MINIMUM_VISIBLE_GUIDE_LENGTH,
                                    legLength + GUIDE_EXTENSION
                            ))),
                            RoadSnapGuide.Kind.NODE_ANGLE
                    ),
                    distance,
                    0,
                    null,
                    index
            ));
        }
        return bestCandidate(candidates);
    }

    /** Rays pointing from a contact point into each existing road arm. */
    static List<RoadVector> attachmentDirections(RoadPoint point, Collection<RoadSegment> segments) {
        return directionsAt(point, segments).stream()
                .map(candidate -> candidate.direction().scale(-1.0)).toList();
    }

    /** One guide per attachment: choose the arm with the smallest horizontal angle. */
    static java.util.Optional<RoadVector> attachmentDirection(RoadPoint point,
            Collection<RoadSegment> segments, RoadVector outgoing) {
        return attachmentDirections(point, segments).stream()
                .filter(direction -> direction.horizontalLength() > 1.0e-9)
                .max(java.util.Comparator.comparingDouble(direction ->
                        (direction.x() * outgoing.x() + direction.z() * outgoing.z())
                                / direction.horizontalLength()));
    }

    private static List<DirectionCandidate> directionsAt(
            RoadPoint point,
            Collection<RoadSegment> segments
    ) {
        List<DirectionCandidate> candidates = new ArrayList<>();
        for (RoadSegment segment : segments) {
            RoadSegment nonNull = Objects.requireNonNull(
                    segment,
                    "segments contains null"
            );
            GeometryProjection projection = project(
                    nonNull.referenceGeometry(),
                    point
            );
            if (projection.horizontalDistance() > GEOMETRY_CONTACT_TOLERANCE
                    || projection.verticalGap()
                            > NODE_SNAP_MAXIMUM_VERTICAL_GAP) {
                continue;
            }
            double length = nonNull.referenceGeometry().length();
            if (projection.distance() <= GEOMETRY_CONTACT_TOLERANCE) {
                addDistinctDirectionCandidate(
                        candidates,
                        new DirectionCandidate(
                                nonNull.id(),
                                projection.sample().tangent().scale(-1.0)
                        )
                );
            } else if (length - projection.distance()
                    <= GEOMETRY_CONTACT_TOLERANCE) {
                addDistinctDirectionCandidate(
                        candidates,
                        new DirectionCandidate(
                                nonNull.id(),
                                projection.sample().tangent()
                        )
                );
            } else {
                addDistinctDirectionCandidate(
                        candidates,
                        new DirectionCandidate(
                                nonNull.id(),
                                projection.sample().tangent()
                        )
                );
                addDistinctDirectionCandidate(
                        candidates,
                        new DirectionCandidate(
                                nonNull.id(),
                                projection.sample().tangent().scale(-1.0)
                        )
                );
            }
        }
        return List.copyOf(candidates);
    }

    private static List<DirectionCandidate> directionsAtNode(
            RoadNode node,
            Collection<RoadSegment> incidentSegments
    ) {
        List<DirectionCandidate> candidates = new ArrayList<>();
        for (RoadSegment segment : incidentSegments) {
            RoadSegment nonNull = Objects.requireNonNull(
                    segment,
                    "incidentSegments contains null"
            );
            boolean atStart = nonNull.startNodeId().equals(node.id());
            if (!atStart && !nonNull.endNodeId().equals(node.id())) {
                continue;
            }
            PathSample endpoint = nonNull.referenceGeometry().sample(
                    atStart ? 0.0 : nonNull.referenceGeometry().length()
            );
            addDistinctDirectionCandidate(
                    candidates,
                    new DirectionCandidate(
                            nonNull.id(),
                            atStart
                                    ? endpoint.tangent().scale(-1.0)
                                    : endpoint.tangent()
                    )
            );
        }
        return List.copyOf(candidates);
    }

    private static void addDistinctDirectionCandidate(
            List<DirectionCandidate> directions,
            DirectionCandidate candidate
    ) {
        RoadVector unit = horizontalUnit(candidate.direction());
        boolean duplicate = directions.stream().anyMatch(existing ->
                horizontalUnit(existing.direction()).dot(unit)
                        >= DIRECTION_DUPLICATE_DOT);
        if (!duplicate) {
            directions.add(new DirectionCandidate(
                    candidate.segmentId(),
                    unit
            ));
        }
    }

    private static void addDistinctDirection(
            List<RoadVector> directions,
            RoadVector candidate
    ) {
        RoadVector unit = horizontalUnit(candidate);
        if (directions.stream().noneMatch(existing ->
                existing.dot(unit) >= DIRECTION_DUPLICATE_DOT)) {
            directions.add(unit);
        }
    }

    /** Guidelines extend along both road axes, on either side of the anchor. */
    private static SnapCandidate orthogonalGuideCandidate(
            CursorTarget raw,
            RoadPoint anchor,
            RoadVector suppliedDirection,
            double range,
            double maximumProjection,
            RoadSnapGuide.Kind kind,
            int priority,
            RoadSegmentId segmentId,
            long tieBreaker
    ) {
        List<SnapCandidate> candidates = new ArrayList<>(4);
        RoadVector forward = horizontalUnit(suppliedDirection);
        RoadVector side = new RoadVector(-forward.z(), 0, forward.x());
        for (RoadVector direction : List.of(forward, forward.scale(-1), side, side.scale(-1))) {
            SnapCandidate candidate = rayCandidate(raw, anchor, direction, range,
                    maximumProjection, kind, priority, segmentId, tieBreaker);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }
        return bestCandidate(candidates);
    }

    private static SnapCandidate rayCandidate(
            CursorTarget raw,
            RoadPoint anchor,
            RoadVector suppliedDirection,
            double range,
            double maximumProjection,
            RoadSnapGuide.Kind kind,
            int priority,
            RoadSegmentId segmentId,
            long tieBreaker
    ) {
        RoadVector direction = horizontalUnit(suppliedDirection);
        RoadVector rawDelta = anchor.vectorTo(raw.roadPoint());
        double projection = rawDelta.x() * direction.x()
                + rawDelta.z() * direction.z();
        if (projection <= MINIMUM_GUIDE_PROJECTION
                || projection > maximumProjection) {
            return null;
        }
        RoadPoint snapped = new RoadPoint(
                anchor.x() + direction.x() * projection,
                raw.roadPoint().y(),
                anchor.z() + direction.z() * projection
        );
        double distance = horizontalDistance(raw.roadPoint(), snapped);
        if (distance > range) {
            return null;
        }
        RoadPoint guideStart = new RoadPoint(
                anchor.x(),
                raw.roadPoint().y(),
                anchor.z()
        );
        RoadPoint guideEnd = guideStart.add(direction.scale(Math.min(
                maximumProjection,
                Math.max(
                        MINIMUM_VISIBLE_GUIDE_LENGTH,
                        projection + GUIDE_EXTENSION
                )
        )));
        return new SnapCandidate(
                target(snapped),
                new RoadSnapGuide(guideStart, guideEnd, kind),
                distance,
                priority,
                segmentId,
                tieBreaker
        );
    }

    private static SnapCandidate bestCandidate(
            Collection<SnapCandidate> candidates
    ) {
        return candidates.stream().min(Comparator
                .comparingDouble(SnapCandidate::distance)
                .thenComparingInt(SnapCandidate::priority)
                .thenComparing(
                        SnapCandidate::segmentId,
                        Comparator.nullsLast(Comparator.naturalOrder())
                )
                .thenComparingLong(SnapCandidate::tieBreaker))
                .orElse(null);
    }

    private static GeometryProjection project(
            ReferenceGeometry geometry,
            RoadPoint point
    ) {
        if (geometry instanceof StraightGeometry straight) {
            RoadPoint start = straight.start();
            RoadPoint end = straight.end();
            double deltaX = end.x() - start.x();
            double deltaZ = end.z() - start.z();
            double horizontalLengthSquared = deltaX * deltaX
                    + deltaZ * deltaZ;
            double fraction = ((point.x() - start.x()) * deltaX
                    + (point.z() - start.z()) * deltaZ)
                    / horizontalLengthSquared;
            fraction = clamp(fraction, 0.0, 1.0);
            return projection(
                    geometry.sample(geometry.length() * fraction),
                    point
            );
        }

        double length = geometry.length();
        int chords = Math.max(
                1,
                Math.min(
                        MAXIMUM_PROJECTION_CHORDS,
                        (int) Math.ceil(length / PROJECTION_CHORD_LENGTH)
                )
        );
        double bestDistance = 0.0;
        double bestSquared = horizontalDistanceSquared(
                geometry.sample(0.0).position(),
                point
        );
        double bracketStart = 0.0;
        double bracketEnd = Math.min(length, length / chords);
        PathSample previous = geometry.sample(0.0);
        for (int index = 1; index <= chords; index++) {
            double segmentStart = length * (index - 1) / chords;
            double endDistance = index == chords
                    ? length
                    : clamp(length * index / chords, 0.0, length);
            PathSample next = geometry.sample(endDistance);
            double local = horizontalLineProjection(
                    point,
                    previous.position(),
                    next.position()
            );
            double candidateDistance = clamp(
                    segmentStart + (endDistance - segmentStart) * local,
                    0.0,
                    length
            );
            PathSample candidate = geometry.sample(candidateDistance);
            double squared = horizontalDistanceSquared(
                    candidate.position(),
                    point
            );
            if (squared < bestSquared) {
                bestSquared = squared;
                bestDistance = candidateDistance;
                bracketStart = segmentStart;
                bracketEnd = endDistance;
            }
            previous = next;
        }

        double left = bracketStart;
        double right = bracketEnd;
        for (int iteration = 0;
                iteration < PROJECTION_REFINEMENT_STEPS;
                iteration++) {
            double first = left + (right - left) / 3.0;
            double second = right - (right - left) / 3.0;
            double firstSquared = horizontalDistanceSquared(
                    geometry.sample(first).position(),
                    point
            );
            double secondSquared = horizontalDistanceSquared(
                    geometry.sample(second).position(),
                    point
            );
            if (firstSquared <= secondSquared) {
                right = second;
            } else {
                left = first;
            }
        }
        double refined = (left + right) / 2.0;
        PathSample best = geometry.sample(bestDistance);
        PathSample refinedSample = geometry.sample(refined);
        if (horizontalDistanceSquared(refinedSample.position(), point)
                < horizontalDistanceSquared(best.position(), point)) {
            best = refinedSample;
        }
        PathSample atLeft = geometry.sample(bracketStart);
        if (horizontalDistanceSquared(atLeft.position(), point)
                < horizontalDistanceSquared(best.position(), point)) {
            best = atLeft;
        }
        PathSample atRight = geometry.sample(bracketEnd);
        if (horizontalDistanceSquared(atRight.position(), point)
                < horizontalDistanceSquared(best.position(), point)) {
            best = atRight;
        }
        return projection(best, point);
    }

    private static GeometryProjection projection(
            PathSample sample,
            RoadPoint point
    ) {
        return new GeometryProjection(
                sample.distance(),
                sample,
                horizontalDistance(sample.position(), point),
                Math.abs(sample.position().y() - point.y())
        );
    }

    private static double horizontalLineProjection(
            RoadPoint point,
            RoadPoint start,
            RoadPoint end
    ) {
        double deltaX = end.x() - start.x();
        double deltaZ = end.z() - start.z();
        double lengthSquared = deltaX * deltaX + deltaZ * deltaZ;
        if (lengthSquared == 0.0) {
            return 0.0;
        }
        return clamp(
                ((point.x() - start.x()) * deltaX
                        + (point.z() - start.z()) * deltaZ)
                        / lengthSquared,
                0.0,
                1.0
        );
    }

    private static RoadVector rotateHorizontal(
            RoadVector vector,
            double angle
    ) {
        double cosine = Math.cos(angle);
        double sine = Math.sin(angle);
        return new RoadVector(
                vector.x() * cosine - vector.z() * sine,
                0.0,
                vector.x() * sine + vector.z() * cosine
        );
    }

    private static RoadVector horizontalUnit(RoadVector vector) {
        double horizontalLength = vector.horizontalLength();
        if (horizontalLength == 0.0) {
            throw new IllegalArgumentException(
                    "a snap direction must have a horizontal component"
            );
        }
        return new RoadVector(
                vector.x() / horizontalLength,
                0.0,
                vector.z() / horizontalLength
        );
    }

    private static double horizontalDistance(RoadPoint first, RoadPoint second) {
        return Math.sqrt(horizontalDistanceSquared(first, second));
    }

    private static double horizontalDistanceSquared(
            RoadPoint first,
            RoadPoint second
    ) {
        double deltaX = first.x() - second.x();
        double deltaZ = first.z() - second.z();
        return deltaX * deltaX + deltaZ * deltaZ;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static CursorTarget target(RoadPoint point) {
        return new CursorTarget(
                BlockPos.containing(point.x(), point.y(), point.z()),
                point
        );
    }

    private record GeometryProjection(
            double distance,
            PathSample sample,
            double horizontalDistance,
            double verticalGap
    ) {
        RoadPoint position() {
            return sample.position();
        }
    }

    public record CurveAngleContext(RoadPoint start, RoadPoint control, boolean equalLegs) {
        public CurveAngleContext(RoadPoint start, RoadPoint control) {
            this(start, control, true);
        }

        public CurveAngleContext {
            Objects.requireNonNull(start, "start");
            Objects.requireNonNull(control, "control");
        }
    }

    private record NodeCandidate(RoadNode node, double radius) {
    }

    private record SegmentProjection(
            RoadSegment segment,
            GeometryProjection projection,
            RoadPoint raw
    ) {
        boolean withinSurface(double range) {
            double halfWidth = segment.presetSnapshot()
                    .surfaceBlockWidth() / 2.0;
            return lateralDistance() <= halfWidth + range
                    && endpointOvershoot() <= range;
        }

        double snapDistance(double range) {
            return horizontalDistance(raw, guidePosition());
        }

        RoadPoint guidePosition() {
            double lateral = signedLateralDistance();
            double offset = laneGuideOffsets(segment).stream()
                    .min(Comparator
                            .comparingDouble((Double candidate) ->
                                    Math.abs(candidate - lateral))
                            .thenComparingDouble(Double::doubleValue))
                    .orElse(0.0);
            return projection.sample().offsetLaterally(offset).position();
        }

        private double lateralDistance() {
            return Math.abs(signedLateralDistance());
        }

        private double signedLateralDistance() {
            RoadVector tangent = horizontalUnit(
                    projection.sample().tangent()
            );
            double deltaX = raw.x() - projection.position().x();
            double deltaZ = raw.z() - projection.position().z();
            return deltaX * tangent.z() - deltaZ * tangent.x();
        }

        private double endpointOvershoot() {
            RoadVector tangent = horizontalUnit(
                    projection.sample().tangent()
            );
            double deltaX = raw.x() - projection.position().x();
            double deltaZ = raw.z() - projection.position().z();
            double longitudinal = deltaX * tangent.x()
                    + deltaZ * tangent.z();
            if (projection.distance() <= GEOMETRY_CONTACT_TOLERANCE) {
                return Math.max(0.0, -longitudinal);
            }
            if (segment.referenceGeometry().length() - projection.distance()
                    <= GEOMETRY_CONTACT_TOLERANCE) {
                return Math.max(0.0, longitudinal);
            }
            return 0.0;
        }
    }

    private record DirectionCandidate(
            RoadSegmentId segmentId,
            RoadVector direction
    ) {
    }

    private record SnapCandidate(
            CursorTarget target,
            RoadSnapGuide guide,
            double distance,
            int priority,
            RoadSegmentId segmentId,
            long tieBreaker
    ) {
    }
}
