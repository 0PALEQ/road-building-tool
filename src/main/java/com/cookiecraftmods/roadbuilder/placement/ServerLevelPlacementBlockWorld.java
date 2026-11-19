package com.cookiecraftmods.roadbuilder.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Objects;

public final class ServerLevelPlacementBlockWorld implements PlacementBlockWorld {
    private static final int UPDATE_FLAGS = Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS;
    private static final int RESTORATION_UPDATE_FLAGS = Block.UPDATE_CLIENTS
            | Block.UPDATE_KNOWN_SHAPE
            | Block.UPDATE_SUPPRESS_DROPS;
    private static final int CONSTRUCTION_UPDATE_FLAGS = Block.UPDATE_CLIENTS
            | Block.UPDATE_KNOWN_SHAPE
            | Block.UPDATE_SUPPRESS_DROPS;

    private final ServerLevel level;

    public ServerLevelPlacementBlockWorld(ServerLevel level) {
        this.level = Objects.requireNonNull(level, "level");
    }

    public ServerLevel level() {
        return level;
    }

    @Override
    public ResourceKey<Level> dimension() {
        return level.dimension();
    }

    @Override
    public int minimumBuildHeight() {
        return level.getMinBuildHeight();
    }

    @Override
    public int maximumBuildHeight() { return level.getMaxBuildHeight(); }

    @Override
    public int highestBlockY(int x, int z) {
        // Level#getHeight skips unloaded chunks. Read the same chunk that
        // blockState() would query so excavation also works beyond the loaded ring.
        var chunk = level.getChunk(x >> 4, z >> 4);
        return chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
    }

    @Override
    public BlockState blockState(BlockPos position) {
        return level.getBlockState(position);
    }

    @Override
    public boolean hasBlockEntity(BlockPos position) {
        return level.getBlockEntity(position) != null;
    }

    @Override
    public boolean setBlockState(BlockPos position, BlockState state) {
        return level.setBlock(position, state, UPDATE_FLAGS);
    }

    @Override
    public boolean setBlockStateForConstruction(
            BlockPos position,
            BlockState state
    ) {
        return level.setBlock(position, state, CONSTRUCTION_UPDATE_FLAGS);
    }

    @Override
    public boolean setBlockStateForRestoration(
            BlockPos position,
            BlockState state
    ) {
        return level.setBlock(position, state, RESTORATION_UPDATE_FLAGS);
    }
}
