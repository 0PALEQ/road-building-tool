package com.cookiecraftmods.roadbuilder.client.tool;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.placement.BlockPlanFailureCode;

import java.util.Objects;
import java.util.Optional;

public record StraightRoadPreviewProblem(
        Kind kind,
        Optional<BlockPlanFailureCode> planningFailure
) {
    public enum Kind {
        NONE,
        NO_TARGET,
        SAME_POINT,
        NO_HORIZONTAL_DIRECTION,
        PLANNING_FAILED
    }

    public StraightRoadPreviewProblem {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(planningFailure, "planningFailure");
        if ((kind == Kind.PLANNING_FAILED) != planningFailure.isPresent()) {
            throw new IllegalArgumentException(
                    "planningFailure must be present exactly for PLANNING_FAILED"
            );
        }
    }

    public static StraightRoadPreviewProblem none() {
        return new StraightRoadPreviewProblem(Kind.NONE, Optional.empty());
    }

    public static StraightRoadPreviewProblem of(Kind kind) {
        if (kind == Kind.PLANNING_FAILED) {
            throw new IllegalArgumentException("use planningFailure(...) for PLANNING_FAILED");
        }
        return new StraightRoadPreviewProblem(kind, Optional.empty());
    }

    public static StraightRoadPreviewProblem planningFailure(BlockPlanFailureCode code) {
        return new StraightRoadPreviewProblem(
                Kind.PLANNING_FAILED,
                Optional.of(Objects.requireNonNull(code, "code"))
        );
    }

    public static boolean lacksHorizontalDirection(
            RoadPoint start,
            RoadPoint end
    ) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        return start.x() == end.x() && start.z() == end.z();
    }
}
