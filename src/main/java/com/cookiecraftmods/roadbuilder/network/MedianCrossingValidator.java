package com.cookiecraftmods.roadbuilder.network;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.RoadVector;
import com.cookiecraftmods.roadbuilder.preset.RoadSurfaceRole;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Rejects lane connections whose path would cut across a road median.
 *
 * <p>A median is a non-drivable surface band (grass or concrete) in the
 * middle of a road. A connector that joins a lane on one side of a median
 * to a lane on the other side of the same median would have cars drive
 * straight through the median, which is unrealistic. The check is purely
 * lateral: for each of the two segments, the lateral position of both lanes
 * is measured against that segment's median bands, and the connection is
 * rejected when the lanes sit on opposite sides of any median band.
 * Turns between non-aligned arms and U-turns are exempt: they go around
 * the median ends inside the junction's trimmed area.</p>
 */
public final class MedianCrossingValidator {
    private static final double LATERAL_TOLERANCE = 0.5;

    private MedianCrossingValidator() {
    }

    public static boolean crossesMedian(
            RoadSegment fromSegment,
            RoadLane fromLane,
            RoadSegment toSegment,
            RoadLane toLane,
            RoadNodeId nodeId
    ) {
        Objects.requireNonNull(fromSegment, "fromSegment");
        Objects.requireNonNull(fromLane, "fromLane");
        Objects.requireNonNull(toSegment, "toSegment");
        Objects.requireNonNull(toLane, "toLane");
        Objects.requireNonNull(nodeId, "nodeId");
        // Medians end at the junction mouth. Turning around their ends is
        // legal; the lateral-only check applies only to aligned road arms.
        RoadVector fromLeft = leftUnit(fromSegment, nodeId);
        RoadVector toLeft = leftUnit(toSegment, nodeId);
        double alignment = Math.abs(fromLeft.x() * toLeft.x()
                + fromLeft.z() * toLeft.z());
        if (fromSegment.id().equals(toSegment.id())
                || alignment < Math.cos(Math.toRadians(20.0))) {
            return false;
        }
        return crossesMedianOf(
                fromSegment,
                fromSegment,
                fromLane,
                toSegment,
                toLane,
                nodeId
        ) || crossesMedianOf(
                toSegment,
                fromSegment,
                fromLane,
                toSegment,
                toLane,
                nodeId
        );
    }

    private static boolean crossesMedianOf(
            RoadSegment segment,
            RoadSegment fromSegment,
            RoadLane fromLane,
            RoadSegment toSegment,
            RoadLane toLane,
            RoadNodeId nodeId
    ) {
        List<double[]> intervals = medianIntervals(segment);
        if (intervals.isEmpty()) {
            return false;
        }
        double fromLateral = lateralPosition(
                segment,
                fromSegment,
                fromLane,
                nodeId
        );
        double toLateral = lateralPosition(
                segment,
                toSegment,
                toLane,
                nodeId
        );
        for (double[] interval : intervals) {
            Side fromSide = side(fromLateral, interval);
            Side toSide = side(toLateral, interval);
            if (fromSide == Side.LEFT && toSide == Side.RIGHT
                    || fromSide == Side.RIGHT && toSide == Side.LEFT) {
                return true;
            }
        }
        return false;
    }

    private static double lateralPosition(
            RoadSegment segment,
            RoadSegment laneSegment,
            RoadLane lane,
            RoadNodeId nodeId
    ) {
        if (laneSegment.id().equals(segment.id())) {
            return -lane.component().lateralOffset();
        }
        RoadVector left = leftUnit(segment, nodeId);
        RoadPoint endpoint = laneEndpoint(laneSegment, lane, nodeId);
        RoadPoint nodePosition = nodePosition(segment, nodeId);
        return (endpoint.x() - nodePosition.x()) * left.x()
                + (endpoint.z() - nodePosition.z()) * left.z();
    }

    private static RoadPoint laneEndpoint(
            RoadSegment segment,
            RoadLane lane,
            RoadNodeId nodeId
    ) {
        boolean atStart = segment.startNodeId().equals(nodeId);
        return atStart
                ? lane.geometry().start()
                : lane.geometry().end();
    }

    private static RoadPoint nodePosition(
            RoadSegment segment,
            RoadNodeId nodeId
    ) {
        boolean atStart = segment.startNodeId().equals(nodeId);
        return atStart
                ? segment.referenceGeometry().start()
                : segment.referenceGeometry().end();
    }

    private static RoadVector leftUnit(
            RoadSegment segment,
            RoadNodeId nodeId
    ) {
        boolean atStart = segment.startNodeId().equals(nodeId);
        RoadVector tangent = segment.referenceGeometry().sample(
                atStart ? 0.0 : segment.referenceGeometry().length()
        ).tangent();
        return tangent.horizontalLeftUnit();
    }

    private static List<double[]> medianIntervals(RoadSegment segment) {
        List<RoadSurfaceRole> roles = segment.presetSnapshot()
                .surfaceColumnRoles();
        int width = segment.presetSnapshot().surfaceBlockWidth();
        List<double[]> intervals = new ArrayList<>();
        double intervalStart = Double.NaN;
        double intervalEnd = Double.NaN;
        for (int index = 0; index < roles.size(); index++) {
            RoadSurfaceRole role = roles.get(index);
            boolean median = role == RoadSurfaceRole.GRASS_MEDIAN
                    || role == RoadSurfaceRole.CONCRETE_MEDIAN;
            double lateral = (width - 1) / 2.0 - index;
            if (median) {
                if (Double.isNaN(intervalStart)) {
                    intervalStart = lateral;
                    intervalEnd = lateral;
                } else {
                    intervalStart = Math.min(intervalStart, lateral);
                    intervalEnd = Math.max(intervalEnd, lateral);
                }
            } else if (!Double.isNaN(intervalStart)) {
                intervals.add(new double[]{intervalStart, intervalEnd});
                intervalStart = Double.NaN;
                intervalEnd = Double.NaN;
            }
        }
        if (!Double.isNaN(intervalStart)) {
            intervals.add(new double[]{intervalStart, intervalEnd});
        }
        return intervals;
    }

    private static Side side(double lateral, double[] interval) {
        if (lateral < interval[0] - LATERAL_TOLERANCE) {
            return Side.RIGHT;
        }
        if (lateral > interval[1] + LATERAL_TOLERANCE) {
            return Side.LEFT;
        }
        return Side.ON;
    }

    private enum Side {
        LEFT,
        RIGHT,
        ON
    }
}
