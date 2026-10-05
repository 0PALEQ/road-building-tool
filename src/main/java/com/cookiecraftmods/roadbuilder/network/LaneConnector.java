package com.cookiecraftmods.roadbuilder.network;

import java.util.Objects;

public record LaneConnector(
        LaneConnectorId id,
        RoadLaneId fromLaneId,
        RoadLaneId toLaneId,
        LaneConnectorMovement movement
) {
    public LaneConnector(
            LaneConnectorId id,
            RoadLaneId fromLaneId,
            RoadLaneId toLaneId
    ) {
        this(
                id,
                fromLaneId,
                toLaneId,
                LaneConnectorMovement.AUTOMATIC
        );
    }

    public LaneConnector {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(fromLaneId, "fromLaneId");
        Objects.requireNonNull(toLaneId, "toLaneId");
        Objects.requireNonNull(movement, "movement");
        if (fromLaneId.equals(toLaneId)) {
            throw new IllegalArgumentException("a lane connector cannot connect a lane to itself");
        }
    }
}
