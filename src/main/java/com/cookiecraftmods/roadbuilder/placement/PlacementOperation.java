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
import java.util.Optional;
import java.util.Set;

public final class PlacementOperation implements ConstructionFootprint {
    private static final Comparator<BlockPos> POSITION_ORDER = Comparator
            .comparingInt((BlockPos position) -> position.getX())
            .thenComparingInt(BlockPos::getY)
            .thenComparingInt(BlockPos::getZ);
    private static final Comparator<ChunkPos> CHUNK_ORDER = Comparator
            .comparingInt((ChunkPos chunk) -> chunk.x)
            .thenComparingInt(chunk -> chunk.z);

    private final PlacementOperationId id;
    private final ResourceKey<Level> dimension;
    private final Optional<RoadMaterializationOwner> materializationOwner;
    private final List<BlockPos> positions;
    private final ConstructionBounds bounds;
    private final List<ChunkPos> touchedChunks;

    private PlacementOperation(
            PlacementOperationId id,
            ResourceKey<Level> dimension,
            Optional<RoadMaterializationOwner> materializationOwner,
            List<BlockPos> positions,
            ConstructionBounds bounds,
            List<ChunkPos> touchedChunks
    ) {
        this.id = id;
        this.dimension = dimension;
        this.materializationOwner = materializationOwner;
        this.positions = positions;
        this.bounds = bounds;
        this.touchedChunks = touchedChunks;
    }

    public static PlacementOperation create(
            PlacementOperationId id,
            ResourceKey<Level> dimension,
            Collection<BlockPos> positions
    ) {
        return create(id, dimension, Optional.empty(), positions);
    }

    public static PlacementOperation create(
            PlacementOperationId id,
            ResourceKey<Level> dimension,
            RoadMaterializationOwner materializationOwner,
            Collection<BlockPos> positions
    ) {
        return create(
                id,
                dimension,
                Optional.of(Objects.requireNonNull(materializationOwner, "materializationOwner")),
                positions
        );
    }

    static PlacementOperation create(
            PlacementOperationId id,
            ResourceKey<Level> dimension,
            Optional<RoadMaterializationOwner> materializationOwner,
            Collection<BlockPos> positions
    ) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(materializationOwner, "materializationOwner");
        Objects.requireNonNull(positions, "positions");
        if (positions.isEmpty()) {
            throw new IllegalArgumentException("An operation must own at least one position");
        }

        List<BlockPos> ordered = new ArrayList<>(positions.size());
        HashSet<BlockPos> unique = new HashSet<>(positions.size());
        Set<ChunkPos> chunks = new HashSet<>();
        int minimumX = Integer.MAX_VALUE;
        int minimumY = Integer.MAX_VALUE;
        int minimumZ = Integer.MAX_VALUE;
        int maximumX = Integer.MIN_VALUE;
        int maximumY = Integer.MIN_VALUE;
        int maximumZ = Integer.MIN_VALUE;

        for (BlockPos suppliedPosition : positions) {
            BlockPos position = Objects.requireNonNull(suppliedPosition, "positions contains null").immutable();
            if (!unique.add(position)) {
                throw new IllegalArgumentException("Duplicate operation position: " + position.toShortString());
            }
            ordered.add(position);
            chunks.add(new ChunkPos(position));
            minimumX = Math.min(minimumX, position.getX());
            minimumY = Math.min(minimumY, position.getY());
            minimumZ = Math.min(minimumZ, position.getZ());
            maximumX = Math.max(maximumX, position.getX());
            maximumY = Math.max(maximumY, position.getY());
            maximumZ = Math.max(maximumZ, position.getZ());
        }

        ordered.sort(POSITION_ORDER);
        List<ChunkPos> touchedChunks = new ArrayList<>(chunks);
        touchedChunks.sort(CHUNK_ORDER);
        return new PlacementOperation(
                id,
                dimension,
                materializationOwner,
                List.copyOf(ordered),
                new ConstructionBounds(
                        new BlockPos(minimumX, minimumY, minimumZ),
                        new BlockPos(maximumX, maximumY, maximumZ)
                ),
                List.copyOf(touchedChunks)
        );
    }

    public PlacementOperationId id() {
        return id;
    }

    public Optional<RoadMaterializationOwner> materializationOwner() {
        return materializationOwner;
    }

    @Override
    public ResourceKey<Level> dimension() {
        return dimension;
    }

    @Override
    public List<BlockPos> positions() {
        return positions;
    }

    @Override
    public int affectedBlockCount() {
        return positions.size();
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
