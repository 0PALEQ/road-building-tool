package com.cookiecraftmods.roadbuilder.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;

public record PlannedBlock(BlockPos position, BlockState targetState) {
    public PlannedBlock {
        position = Objects.requireNonNull(position, "position").immutable();
        Objects.requireNonNull(targetState, "targetState");
    }
}
