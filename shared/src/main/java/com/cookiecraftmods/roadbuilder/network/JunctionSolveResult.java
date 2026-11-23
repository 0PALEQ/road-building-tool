package com.cookiecraftmods.roadbuilder.network;

import java.util.List;
import java.util.Objects;

public sealed interface JunctionSolveResult {
    record Success(List<SolvedJunctionApproach> approaches)
            implements JunctionSolveResult {
        public Success {
            approaches = List.copyOf(Objects.requireNonNull(
                    approaches,
                    "approaches"
            ));
        }
    }

    record Failure(String diagnostic) implements JunctionSolveResult {
        public Failure {
            Objects.requireNonNull(diagnostic, "diagnostic");
        }
    }
}
