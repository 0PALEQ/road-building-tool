package com.cookiecraftmods.roadbuilder.placement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConstructionLimitsTest {
    @Test
    void suppliesConservativeDefaults() {
        ConstructionLimits limits = ConstructionLimits.defaults();

        assertEquals(ConstructionLimits.DEFAULT_MAXIMUM_DISTANCE, limits.maximumDistance());
        assertEquals(ConstructionLimits.DEFAULT_MAXIMUM_AFFECTED_BLOCKS, limits.maximumAffectedBlocks());
        assertEquals(ConstructionLimits.DEFAULT_MAXIMUM_TOUCHED_CHUNKS, limits.maximumTouchedChunks());
        assertEquals(ConstructionLimits.DEFAULT_MAXIMUM_PLANNING_CANDIDATES, limits.maximumPlanningCandidates());
        assertEquals(ConstructionLimits.DEFAULT_MAXIMUM_GRADE, limits.maximumGrade());
    }

    @Test
    void validatesPolicyCaps() {
        assertThrows(IllegalArgumentException.class, () -> new ConstructionLimits(Double.NaN, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new ConstructionLimits(0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new ConstructionLimits(
                ConstructionLimits.MAXIMUM_CONFIGURABLE_DISTANCE + 1,
                1,
                1
        ));
        assertThrows(IllegalArgumentException.class, () -> new ConstructionLimits(1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new ConstructionLimits(1, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new ConstructionLimits(
                1,
                1,
                ConstructionLimits.MAXIMUM_CONFIGURABLE_TOUCHED_CHUNKS + 1
        ));
        assertThrows(IllegalArgumentException.class, () -> new ConstructionLimits(1, 1, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new ConstructionLimits(
                1,
                1,
                1,
                ConstructionLimits.MAXIMUM_CONFIGURABLE_PLANNING_CANDIDATES + 1
        ));
        assertThrows(IllegalArgumentException.class, () -> new ConstructionLimits(
                1,
                1,
                1,
                1,
                Double.NaN
        ));
        assertThrows(IllegalArgumentException.class, () -> new ConstructionLimits(
                1,
                1,
                1,
                1,
                -0.1
        ));
        assertThrows(IllegalArgumentException.class, () -> new ConstructionLimits(
                1,
                1,
                1,
                1,
                ConstructionLimits.MAXIMUM_CONFIGURABLE_GRADE + 0.1
        ));
    }
}
