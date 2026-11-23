package com.cookiecraftmods.roadbuilder.client.tool;

import java.util.Objects;
import java.util.Optional;

public record CursorTargetResult(
        CursorTargetProblem problem,
        Optional<CursorTarget> target
) {
    public CursorTargetResult {
        Objects.requireNonNull(problem, "problem");
        Objects.requireNonNull(target, "target");
        if ((problem == CursorTargetProblem.NONE) != target.isPresent()) {
            throw new IllegalArgumentException("target must be present exactly when problem is NONE");
        }
    }

    public static CursorTargetResult success(CursorTarget target) {
        return new CursorTargetResult(
                CursorTargetProblem.NONE,
                Optional.of(Objects.requireNonNull(target, "target"))
        );
    }

    public static CursorTargetResult failure(CursorTargetProblem problem) {
        Objects.requireNonNull(problem, "problem");
        if (problem == CursorTargetProblem.NONE) {
            throw new IllegalArgumentException("NONE is not a cursor-target failure");
        }
        return new CursorTargetResult(problem, Optional.empty());
    }
}
