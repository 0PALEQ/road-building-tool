package com.cookiecraftmods.roadbuilder.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.List;

public interface ConstructionFootprint {
    ResourceKey<Level> dimension();

    List<BlockPos> positions();

    int affectedBlockCount();

    ConstructionBounds bounds();

    List<ChunkPos> touchedChunks();
}
