package com.cookiecraftmods.roadbuilder.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class BlockPlacementPlan implements ConstructionFootprint {
    private static final Comparator<PlannedBlock> PLACEMENT_ORDER = Comparator
            .comparingInt((PlannedBlock block) -> Math.floorDiv(block.position().getX(), 16))
            .thenComparingInt(block -> Math.floorDiv(block.position().getZ(), 16))
            .thenComparingInt(block -> block.position().getY())
            .thenComparingInt(block -> block.position().getX())
            .thenComparingInt(block -> block.position().getZ());
    private static final Comparator<ChunkPos> CHUNK_ORDER = Comparator
            .comparingInt((ChunkPos chunk) -> chunk.x)
            .thenComparingInt(chunk -> chunk.z);

    private final ResourceKey<Level> dimension;
    private final List<PlannedBlock> blocks;
    private final ConstructionBounds bounds;
    private final List<ChunkPos> touchedChunks;

    private BlockPlacementPlan(
            ResourceKey<Level> dimension,
            List<PlannedBlock> blocks,
            ConstructionBounds bounds,
            List<ChunkPos> touchedChunks
    ) {
        this.dimension = dimension;
        this.blocks = blocks;
        this.bounds = bounds;
        this.touchedChunks = touchedChunks;
    }

    public static BlockPlacementPlan create(
            ResourceKey<Level> dimension,
            Collection<PlannedBlock> plannedBlocks
    ) {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(plannedBlocks, "plannedBlocks");
        if (plannedBlocks.isEmpty()) {
            throw new IllegalArgumentException("A placement plan must contain at least one block");
        }

        List<PlannedBlock> blocks = new ArrayList<>(plannedBlocks.size());
        Set<BlockPos> positions = new HashSet<>(plannedBlocks.size());
        Set<ChunkPos> chunks = new HashSet<>();

        int minimumX = Integer.MAX_VALUE;
        int minimumY = Integer.MAX_VALUE;
        int minimumZ = Integer.MAX_VALUE;
        int maximumX = Integer.MIN_VALUE;
        int maximumY = Integer.MIN_VALUE;
        int maximumZ = Integer.MIN_VALUE;

        for (PlannedBlock plannedBlock : plannedBlocks) {
            PlannedBlock block = Objects.requireNonNull(plannedBlock, "plannedBlocks contains null");
            BlockPos position = block.position();
            if (!positions.add(position)) {
                throw new IllegalArgumentException("Duplicate planned block position: " + position.toShortString());
            }

            blocks.add(block);
            chunks.add(new ChunkPos(position));
            minimumX = Math.min(minimumX, position.getX());
            minimumY = Math.min(minimumY, position.getY());
            minimumZ = Math.min(minimumZ, position.getZ());
            maximumX = Math.max(maximumX, position.getX());
            maximumY = Math.max(maximumY, position.getY());
            maximumZ = Math.max(maximumZ, position.getZ());
        }

        blocks.sort(PLACEMENT_ORDER);
        List<ChunkPos> touchedChunks = new ArrayList<>(chunks);
        touchedChunks.sort(CHUNK_ORDER);

        ConstructionBounds bounds = new ConstructionBounds(
                new BlockPos(minimumX, minimumY, minimumZ),
                new BlockPos(maximumX, maximumY, maximumZ)
        );
        return new BlockPlacementPlan(
                dimension,
                List.copyOf(blocks),
                bounds,
                List.copyOf(touchedChunks)
        );
    }

    @Override
    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public List<PlannedBlock> blocks() {
        return blocks;
    }

    @Override
    public List<BlockPos> positions() {
        return blocks.stream().map(PlannedBlock::position).toList();
    }

    @Override
    public int affectedBlockCount() {
        return blocks.size();
    }

    @Override
    public ConstructionBounds bounds() {
        return bounds;
    }

    @Override
    public List<ChunkPos> touchedChunks() {
        return touchedChunks;
    }
}
