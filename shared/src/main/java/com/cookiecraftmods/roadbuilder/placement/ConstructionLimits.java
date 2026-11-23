package com.cookiecraftmods.roadbuilder.placement;

public record ConstructionLimits(
        double maximumDistance,
        int maximumAffectedBlocks,
        int maximumTouchedChunks,
        int maximumPlanningCandidates,
        double maximumGrade
) {
    public static final double DEFAULT_MAXIMUM_DISTANCE = 512.0;
    public static final int DEFAULT_MAXIMUM_AFFECTED_BLOCKS = 131_072;
    public static final int DEFAULT_MAXIMUM_TOUCHED_CHUNKS = 256;
    public static final int DEFAULT_MAXIMUM_PLANNING_CANDIDATES = 1_000_000;
    public static final double DEFAULT_MAXIMUM_GRADE = 0.5;
    public static final double UNRESTRICTED_MAXIMUM_GRADE = Double.MAX_VALUE;
    public static final double MAXIMUM_CONFIGURABLE_DISTANCE = 4_096.0;
    public static final int MAXIMUM_CONFIGURABLE_TOUCHED_CHUNKS = 131_072;
    public static final int MAXIMUM_CONFIGURABLE_PLANNING_CANDIDATES = 8_000_000;
    public static final double MAXIMUM_CONFIGURABLE_GRADE = 1.0;

    public ConstructionLimits(double maximumDistance, int maximumAffectedBlocks) {
        this(
                maximumDistance,
                maximumAffectedBlocks,
                DEFAULT_MAXIMUM_TOUCHED_CHUNKS,
                DEFAULT_MAXIMUM_PLANNING_CANDIDATES,
                DEFAULT_MAXIMUM_GRADE
        );
    }

    public ConstructionLimits(double maximumDistance, int maximumAffectedBlocks, int maximumTouchedChunks) {
        this(
                maximumDistance,
                maximumAffectedBlocks,
                maximumTouchedChunks,
                DEFAULT_MAXIMUM_PLANNING_CANDIDATES,
                DEFAULT_MAXIMUM_GRADE
        );
    }

    public ConstructionLimits(
            double maximumDistance,
            int maximumAffectedBlocks,
            int maximumTouchedChunks,
            int maximumPlanningCandidates
    ) {
        this(
                maximumDistance,
                maximumAffectedBlocks,
                maximumTouchedChunks,
                maximumPlanningCandidates,
                DEFAULT_MAXIMUM_GRADE
        );
    }

    public ConstructionLimits(
            double maximumDistance,
            int maximumAffectedBlocks,
            int maximumTouchedChunks,
            int maximumPlanningCandidates,
            double maximumGrade
    ) {
        this.maximumDistance = maximumDistance;
        this.maximumAffectedBlocks = maximumAffectedBlocks;
        this.maximumTouchedChunks = maximumTouchedChunks;
        this.maximumPlanningCandidates = maximumPlanningCandidates;
        this.maximumGrade = maximumGrade;

        if (!Double.isFinite(maximumDistance)
                || maximumDistance <= 0.0
                || maximumDistance > MAXIMUM_CONFIGURABLE_DISTANCE) {
            throw new IllegalArgumentException(
                    "maximumDistance must be finite, greater than zero, and no more than "
                            + MAXIMUM_CONFIGURABLE_DISTANCE
            );
        }
        if (maximumAffectedBlocks <= 0) {
            throw new IllegalArgumentException("maximumAffectedBlocks must be greater than zero");
        }
        if (maximumTouchedChunks <= 0 || maximumTouchedChunks > MAXIMUM_CONFIGURABLE_TOUCHED_CHUNKS) {
            throw new IllegalArgumentException(
                    "maximumTouchedChunks must be between 1 and " + MAXIMUM_CONFIGURABLE_TOUCHED_CHUNKS
            );
        }
        if (maximumPlanningCandidates <= 0
                || maximumPlanningCandidates > MAXIMUM_CONFIGURABLE_PLANNING_CANDIDATES) {
            throw new IllegalArgumentException(
                    "maximumPlanningCandidates must be between 1 and "
                            + MAXIMUM_CONFIGURABLE_PLANNING_CANDIDATES
            );
        }
        if (!Double.isFinite(maximumGrade)
                || maximumGrade < 0.0
                || maximumGrade > MAXIMUM_CONFIGURABLE_GRADE) {
            throw new IllegalArgumentException(
                    "maximumGrade must be finite, non-negative, and no more than "
                            + MAXIMUM_CONFIGURABLE_GRADE
            );
        }
    }

    public static ConstructionLimits defaults() {
        return new ConstructionLimits(
                DEFAULT_MAXIMUM_DISTANCE,
                DEFAULT_MAXIMUM_AFFECTED_BLOCKS,
                DEFAULT_MAXIMUM_TOUCHED_CHUNKS,
                DEFAULT_MAXIMUM_PLANNING_CANDIDATES,
                DEFAULT_MAXIMUM_GRADE
        );
    }
}
