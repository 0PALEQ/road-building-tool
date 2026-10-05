package com.cookiecraftmods.roadbuilder.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public interface PlacementBlockWorld {
    ResourceKey<Level> dimension();

    default int minimumBuildHeight() {
        return -64;
    }

    BlockState blockState(BlockPos position);

    boolean hasBlockEntity(BlockPos position);

    boolean setBlockState(BlockPos position, BlockState state);

        default boolean setBlockStateForConstruction(
            BlockPos position,
            BlockState state
    ) {
        return setBlockState(position, state);
    }

        default boolean setBlockStateForRestoration(
            BlockPos position,
            BlockState state
    ) {
        return setBlockState(position, state);
    }
}
