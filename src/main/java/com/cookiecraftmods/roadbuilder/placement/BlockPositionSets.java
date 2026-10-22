package com.cookiecraftmods.roadbuilder.placement;

import net.minecraft.core.BlockPos;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Detached footprint snapshots without the dense-coordinate probing cost of Set.copyOf. */
public final class BlockPositionSets {
    private BlockPositionSets() { }

    public static Set<BlockPos> copyOf(Collection<BlockPos> positions) {
        Set<BlockPos> copy = new HashSet<>();
        for (BlockPos position : Objects.requireNonNull(positions, "positions")) {
            copy.add(Objects.requireNonNull(position, "position").immutable());
        }
        return Collections.unmodifiableSet(copy);
    }
}
