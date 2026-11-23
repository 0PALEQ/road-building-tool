package com.cookiecraftmods.roadbuilder.placement;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Objects;

public record RemovalSummary(
        int positionCount,
        int restoredCount,
        int unchangedCount,
        int shadowedCount,
        int conflictCount,
        int forcedConflictCount,
        List<BlockPos> conflictPositions
) {
    public RemovalSummary {
        if (positionCount < 0 || restoredCount < 0 || unchangedCount < 0
                || shadowedCount < 0 || conflictCount < 0 || forcedConflictCount < 0) {
            throw new IllegalArgumentException("Removal counts must be non-negative");
        }
        if (positionCount != restoredCount + unchangedCount + shadowedCount + conflictCount) {
            throw new IllegalArgumentException("Removal outcome counts do not cover the operation footprint");
        }
        if (forcedConflictCount > restoredCount + unchangedCount) {
            throw new IllegalArgumentException("Forced conflicts must refer to a visible removal outcome");
        }
        conflictPositions = List.copyOf(Objects.requireNonNull(conflictPositions, "conflictPositions"));
        if (conflictPositions.size() != conflictCount) {
            throw new IllegalArgumentException("conflictPositions must contain every safe conflict");
        }
    }
}
