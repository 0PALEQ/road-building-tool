package com.cookiecraftmods.roadbuilder.network;

import com.cookiecraftmods.roadbuilder.preset.LaneType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

final class LaneConnectorLaneMatcher {
    private LaneConnectorLaneMatcher() {
    }

    static List<Match> match(
            Collection<RoadLane> incoming,
            Collection<RoadLane> outgoing,
            PreferredSide preferredSide
    ) {
        Objects.requireNonNull(incoming, "incoming");
        Objects.requireNonNull(outgoing, "outgoing");
        Objects.requireNonNull(preferredSide, "preferredSide");
        List<Match> result = new ArrayList<>();
        for (LaneType type : LaneType.values()) {
            List<RoadLane> matchingIncoming = ordered(
                    incoming,
                    type,
                    preferredSide
            );
            List<RoadLane> matchingOutgoing = ordered(
                    outgoing,
                    type,
                    preferredSide
            );
            int pairCount = Math.min(
                    matchingIncoming.size(),
                    matchingOutgoing.size()
            );
            for (int index = 0; index < pairCount; index++) {
                result.add(new Match(
                        matchingIncoming.get(index),
                        matchingOutgoing.get(index)
                ));
            }
        }
        return List.copyOf(result);
    }

    private static List<RoadLane> ordered(
            Collection<RoadLane> lanes,
            LaneType type,
            PreferredSide preferredSide
    ) {
        double sign = preferredSide == PreferredSide.RIGHT ? -1.0 : 1.0;
        return lanes.stream()
                .filter(lane -> lane.component().type() == type)
                .sorted(Comparator
                        .comparingDouble((RoadLane lane) -> sign
                                * travelRightOffset(lane))
                        .thenComparing(RoadLane::id))
                .toList();
    }

    static double travelRightOffset(RoadLane lane) {
        Objects.requireNonNull(lane, "lane");
        double direction = lane.component().direction()
                .followsReferenceDirection() ? 1.0 : -1.0;
        return lane.component().lateralOffset() * direction;
    }

    enum PreferredSide {
        LEFT,
        RIGHT
    }

    record Match(RoadLane from, RoadLane to) {
        Match {
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
        }
    }
}
