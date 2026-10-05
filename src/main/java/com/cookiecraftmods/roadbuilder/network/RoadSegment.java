package com.cookiecraftmods.roadbuilder.network;

import com.cookiecraftmods.roadbuilder.geometry.OffsetGeometry;
import com.cookiecraftmods.roadbuilder.geometry.ReferenceGeometry;
import com.cookiecraftmods.roadbuilder.geometry.StraightGeometry;
import com.cookiecraftmods.roadbuilder.preset.LaneComponent;
import com.cookiecraftmods.roadbuilder.preset.TrafficHandedness;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class RoadSegment {
    private final RoadSegmentId id;
    private final RoadId roadId;
    private final RoadNodeId startNodeId;
    private final RoadNodeId endNodeId;
    private final ReferenceGeometry referenceGeometry;
    private final ResourceLocation presetId;
    private final int presetRevision;
    private final RoadPresetSnapshot presetSnapshot;
    private final TrafficHandedness trafficHandedness;
    private final List<RoadLane> lanes;

    private RoadSegment(
            RoadSegmentId id,
            RoadId roadId,
            RoadNodeId startNodeId,
            RoadNodeId endNodeId,
            ReferenceGeometry referenceGeometry,
            ResourceLocation presetId,
            int presetRevision,
            RoadPresetSnapshot presetSnapshot,
            TrafficHandedness trafficHandedness,
            List<RoadLane> lanes
    ) {
        this.id = id;
        this.roadId = roadId;
        this.startNodeId = startNodeId;
        this.endNodeId = endNodeId;
        this.referenceGeometry = referenceGeometry;
        this.presetId = presetId;
        this.presetRevision = presetRevision;
        this.presetSnapshot = presetSnapshot;
        this.trafficHandedness = trafficHandedness;
        this.lanes = lanes;
    }

    public static RoadSegment straight(
            RoadSegmentId id,
            RoadId roadId,
            RoadNodeId startNodeId,
            RoadNodeId endNodeId,
            StraightGeometry referenceGeometry,
            ResourceLocation presetId,
            int presetRevision,
            RoadPresetSnapshot presetSnapshot,
            TrafficHandedness trafficHandedness,
            List<RoadLaneId> laneIds
    ) {
        return path(
                id,
                roadId,
                startNodeId,
                endNodeId,
                referenceGeometry,
                presetId,
                presetRevision,
                presetSnapshot,
                trafficHandedness,
                laneIds
        );
    }

    public static RoadSegment path(
            RoadSegmentId id,
            RoadId roadId,
            RoadNodeId startNodeId,
            RoadNodeId endNodeId,
            ReferenceGeometry referenceGeometry,
            ResourceLocation presetId,
            int presetRevision,
            RoadPresetSnapshot presetSnapshot,
            TrafficHandedness trafficHandedness,
            List<RoadLaneId> laneIds
    ) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(roadId, "roadId");
        Objects.requireNonNull(startNodeId, "startNodeId");
        Objects.requireNonNull(endNodeId, "endNodeId");
        if (startNodeId.equals(endNodeId)) {
            throw new IllegalArgumentException("a segment must have two distinct boundary nodes");
        }
        Objects.requireNonNull(referenceGeometry, "referenceGeometry");
        Objects.requireNonNull(presetId, "presetId");
        if (presetRevision < 1) {
            throw new IllegalArgumentException("presetRevision must be at least 1");
        }
        Objects.requireNonNull(presetSnapshot, "presetSnapshot");
        Objects.requireNonNull(trafficHandedness, "trafficHandedness");
        laneIds = List.copyOf(Objects.requireNonNull(laneIds, "laneIds"));

        List<LaneComponent> components = presetSnapshot.lanes(trafficHandedness);
        if (laneIds.size() != components.size()) {
            throw new IllegalArgumentException(
                    "lane ID count must match the preset snapshot lane count"
            );
        }

        Set<RoadLaneId> uniqueLaneIds = new HashSet<>();
        List<RoadLane> derivedLanes = new ArrayList<>(laneIds.size());
        for (int index = 0; index < laneIds.size(); index++) {
            RoadLaneId laneId = Objects.requireNonNull(laneIds.get(index), "laneIds contains null");
            if (!uniqueLaneIds.add(laneId)) {
                throw new IllegalArgumentException("duplicate lane ID " + laneId);
            }
            LaneComponent component = components.get(index);
            derivedLanes.add(new RoadLane(
                    laneId,
                    id,
                    index,
                    component,
                    deriveLaneGeometry(referenceGeometry, component)
            ));
        }

        return new RoadSegment(
                id,
                roadId,
                startNodeId,
                endNodeId,
                referenceGeometry,
                presetId,
                presetRevision,
                presetSnapshot,
                trafficHandedness,
                List.copyOf(derivedLanes)
        );
    }

    private static ReferenceGeometry deriveLaneGeometry(
            ReferenceGeometry referenceGeometry,
            LaneComponent component
    ) {
        return new OffsetGeometry(
                referenceGeometry,
                -component.lateralOffset(),
                !component.direction().followsReferenceDirection()
        );
    }

    public RoadSegmentId id() {
        return id;
    }

    public RoadId roadId() {
        return roadId;
    }

    public RoadNodeId startNodeId() {
        return startNodeId;
    }

    public RoadNodeId endNodeId() {
        return endNodeId;
    }

    public ReferenceGeometry referenceGeometry() {
        return referenceGeometry;
    }

    public ResourceLocation presetId() {
        return presetId;
    }

    public int presetRevision() {
        return presetRevision;
    }

    public RoadPresetSnapshot presetSnapshot() {
        return presetSnapshot;
    }

    public TrafficHandedness trafficHandedness() {
        return trafficHandedness;
    }

    public List<RoadLane> lanes() {
        return lanes;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RoadSegment segment)) {
            return false;
        }
        return presetRevision == segment.presetRevision
                && id.equals(segment.id)
                && roadId.equals(segment.roadId)
                && startNodeId.equals(segment.startNodeId)
                && endNodeId.equals(segment.endNodeId)
                && referenceGeometry.equals(segment.referenceGeometry)
                && presetId.equals(segment.presetId)
                && presetSnapshot.equals(segment.presetSnapshot)
                && trafficHandedness == segment.trafficHandedness
                && lanes.equals(segment.lanes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id,
                roadId,
                startNodeId,
                endNodeId,
                referenceGeometry,
                presetId,
                presetRevision,
                presetSnapshot,
                trafficHandedness,
                lanes
        );
    }

    @Override
    public String toString() {
        return "RoadSegment[id=" + id
                + ", roadId=" + roadId
                + ", startNodeId=" + startNodeId
                + ", endNodeId=" + endNodeId
                + ", presetId=" + presetId
                + ", presetRevision=" + presetRevision
                + "]";
    }
}
