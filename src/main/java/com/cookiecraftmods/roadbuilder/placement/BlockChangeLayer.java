package com.cookiecraftmods.roadbuilder.placement;

import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;

public record BlockChangeLayer(
        PlacementOperationId operationId,
        BlockState beforeState,
        BlockState afterState
) {
    public BlockChangeLayer {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(beforeState, "beforeState");
        Objects.requireNonNull(afterState, "afterState");
    }

    BlockChangeLayer withBeforeState(BlockState replacement) {
        return new BlockChangeLayer(operationId, replacement, afterState);
    }
}
