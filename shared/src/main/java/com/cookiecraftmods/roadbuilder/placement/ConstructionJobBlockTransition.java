package com.cookiecraftmods.roadbuilder.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;

public record ConstructionJobBlockTransition(
        BlockPos position,
        BlockState beforeState,
        BlockState afterState
) {
    public ConstructionJobBlockTransition {
        position = Objects.requireNonNull(position, "position").immutable();
        Objects.requireNonNull(beforeState, "beforeState");
        Objects.requireNonNull(afterState, "afterState");
    }
}
