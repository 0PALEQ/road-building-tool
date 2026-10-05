package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.network.RoadNodeId;
import com.cookiecraftmods.roadbuilder.network.RoadSegmentId;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record RoadMaterializationOwner(Type type, UUID ownerId) {
    public RoadMaterializationOwner {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(ownerId, "ownerId");
    }

    public static RoadMaterializationOwner segment(RoadSegmentId segmentId) {
        return new RoadMaterializationOwner(
                Type.SEGMENT,
                Objects.requireNonNull(segmentId, "segmentId").value()
        );
    }

    public static RoadMaterializationOwner node(RoadNodeId nodeId) {
        return new RoadMaterializationOwner(
                Type.NODE,
                Objects.requireNonNull(nodeId, "nodeId").value()
        );
    }

    public RoadSegmentId segmentId() {
        if (type != Type.SEGMENT) {
            throw new IllegalStateException("materialization owner is not a segment");
        }
        return new RoadSegmentId(ownerId);
    }

    public RoadNodeId nodeId() {
        if (type != Type.NODE) {
            throw new IllegalStateException("materialization owner is not a node");
        }
        return new RoadNodeId(ownerId);
    }

    public enum Type {
        SEGMENT("segment"),
        NODE("node");

        private final String serializedName;

        Type(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return serializedName;
        }

        public static Optional<Type> fromSerializedName(String serializedName) {
            return Arrays.stream(values())
                    .filter(value -> value.serializedName.equals(serializedName))
                    .findFirst();
        }
    }
}
