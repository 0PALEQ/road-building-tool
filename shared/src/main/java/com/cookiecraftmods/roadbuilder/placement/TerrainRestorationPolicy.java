package com.cookiecraftmods.roadbuilder.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Stable terrain to restore after demolition, distinct from transaction rollback. */
public final class TerrainRestorationPolicy {
    private TerrainRestorationPolicy() {
    }

    public static boolean isDisposable(BlockState state) {
        // Shape alone must never authorize destroying inventories or other block data.
        return !state.hasBlockEntity()
                && !state.isAir()
                && !state.is(Blocks.FARMLAND)
                && !state.is(Blocks.DIRT_PATH)
                && !state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    public static BlockState restorationState(BlockState state) {
        if (state.is(Blocks.FARMLAND) || state.is(Blocks.DIRT_PATH)) {
            return Blocks.DIRT.defaultBlockState();
        }
        return isDisposable(state) ? state.getFluidState().createLegacyBlock() : state;
    }
}
