package com.cookiecraftmods.roadbuilder.network;

import com.cookiecraftmods.roadbuilder.geometry.ReferenceGeometry;
import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.RoadVector;
import com.cookiecraftmods.roadbuilder.preset.LaneComponent;
import com.cookiecraftmods.roadbuilder.preset.LaneType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Travel-oriented attachment points, separate from a road's reference-line node. */
public final class AuxiliaryLanePorts {
    private static final double CONTACT_TOLERANCE = 0.05;
    private static final double MINIMUM_TANGENT_DOT = Math.cos(Math.toRadians(5));

    private AuxiliaryLanePorts() { }

    public record Port(RoadNodeId nodeId, RoadSegmentId segmentId, RoadLaneId laneId,
                       RoadPoint position, RoadVector direction, boolean incoming, int roadWidth) { }

    public record Attachment(RoadSegment branch, Port port, boolean atStart) { }

    private static boolean supports(List<LaneComponent> lanes) {
        return lanes.size() == 1 && lanes.get(0).type() == LaneType.VEHICLE
                && lanes.get(0).direction().followsReferenceDirection()
                && Math.abs(lanes.get(0).lateralOffset()) < 1.0e-9;
    }

    public static List<Port> ports(Collection<RoadSegment> segments) {
        List<Port> result = new ArrayList<>();
        for (RoadSegment segment : segments) {
            if (segment.lanes().stream().filter(lane -> lane.component().type() == LaneType.VEHICLE).count() < 2) {
                continue;
            }
            for (RoadLane lane : segment.lanes()) {
                if (lane.component().type() != LaneType.VEHICLE) {
                    continue;
                }
                boolean forward = lane.component().direction().followsReferenceDirection();
                var first = lane.geometry().sample(0);
                var last = lane.geometry().sample(lane.geometry().length());
                result.add(new Port(forward ? segment.startNodeId() : segment.endNodeId(),
                        segment.id(), lane.id(), first.position(), first.tangent(), false,
                        segment.presetSnapshot().surfaceBlockWidth()));
                result.add(new Port(forward ? segment.endNodeId() : segment.startNodeId(),
                        segment.id(), lane.id(), last.position(), last.tangent(), true,
                        segment.presetSnapshot().surfaceBlockWidth()));
            }
        }
        result.sort(Comparator.comparing(Port::segmentId).thenComparing(Port::laneId)
                .thenComparing(Port::incoming));
        return List.copyOf(result);
    }

    public static Optional<Port> matching(ReferenceGeometry geometry, boolean atStart,
                                          int branchWidth, Collection<Port> ports) {
        var boundary = geometry.sample(atStart ? 0 : geometry.length());
        return ports.stream().filter(port -> port.incoming() == atStart && port.roadWidth() > branchWidth)
                .filter(port -> port.position().distanceTo(boundary.position()) <= CONTACT_TOLERANCE)
                .filter(port -> port.direction().horizontalLeftUnit()
                        .dot(boundary.tangent().horizontalLeftUnit()) >= MINIMUM_TANGENT_DOT)
                .min(Comparator.comparing(Port::segmentId).thenComparing(Port::laneId));
    }

    public static List<Attachment> attachments(RoadNodeId nodeId, Collection<RoadSegment> incident) {
        List<Port> ports = ports(incident).stream().filter(port -> port.nodeId().equals(nodeId)).toList();
        List<Attachment> result = new ArrayList<>();
        for (RoadSegment branch : incident) {
            if (!supports(branch.presetSnapshot().lanes())) {
                continue;
            }
            boolean atStart = branch.startNodeId().equals(nodeId);
            if (!atStart && !branch.endNodeId().equals(nodeId)) {
                continue;
            }
            matching(branch.referenceGeometry(), atStart, branch.presetSnapshot().surfaceBlockWidth(),
                    ports.stream().filter(port -> !port.segmentId().equals(branch.id())).toList())
                    .ifPresent(port -> result.add(new Attachment(branch, port, atStart)));
        }
        return List.copyOf(result);
    }

}
