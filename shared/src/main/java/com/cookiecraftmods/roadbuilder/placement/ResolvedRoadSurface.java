package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.preset.RoadMarkingPattern;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.core.BlockPos;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record ResolvedRoadSurface(
        List<BlockState> columnStates,
        List<BlockState> underlayStates,
        List<RoadMarkingPattern> columnPatterns,
        List<List<BlockState>> columnStateSets,
        List<List<BlockState>> underlayStateSets
) {
    public static final int MAXIMUM_BLOCK_WIDTH = 64;

    public ResolvedRoadSurface(int blockWidth, BlockState targetState) {
        this(repeatedStates(blockWidth, targetState));
    }

    public ResolvedRoadSurface(List<BlockState> columnStates) {
        this(
                columnStates,
                columnStates,
                repeatedPatterns(columnStates),
                singletonSets(columnStates),
                singletonSets(columnStates)
        );
    }

    public ResolvedRoadSurface(
            List<BlockState> columnStates,
            List<BlockState> underlayStates,
            List<RoadMarkingPattern> columnPatterns
    ) {
        this(
                columnStates,
                underlayStates,
                columnPatterns,
                singletonSets(columnStates),
                singletonSets(underlayStates)
        );
    }

    public ResolvedRoadSurface {
        columnStates = List.copyOf(Objects.requireNonNull(columnStates, "columnStates"));
        underlayStates = List.copyOf(Objects.requireNonNull(
                underlayStates,
                "underlayStates"
        ));
        columnPatterns = List.copyOf(Objects.requireNonNull(
                columnPatterns,
                "columnPatterns"
        ));
        columnStateSets = copySets(columnStateSets, "columnStateSets");
        underlayStateSets = copySets(underlayStateSets, "underlayStateSets");
        int blockWidth = columnStates.size();
        if (blockWidth < 1 || blockWidth > MAXIMUM_BLOCK_WIDTH) {
            throw new IllegalArgumentException(
                    "blockWidth must be between 1 and " + MAXIMUM_BLOCK_WIDTH
            );
        }
        if (underlayStates.size() != blockWidth
                || columnPatterns.size() != blockWidth) {
            throw new IllegalArgumentException(
                    "surface column, underlay, and pattern counts must match"
            );
        }
        if (columnStateSets.size() != blockWidth
                || underlayStateSets.size() != blockWidth) {
            throw new IllegalArgumentException(
                    "surface block-set counts must match the column count"
            );
        }
        for (int index = 0; index < columnStates.size(); index++) {
            BlockState state = Objects.requireNonNull(
                    columnStates.get(index),
                    "columnStates[" + index + "]"
            );
            BlockState underlay = Objects.requireNonNull(
                    underlayStates.get(index),
                    "underlayStates[" + index + "]"
            );
            RoadMarkingPattern pattern = Objects.requireNonNull(
                    columnPatterns.get(index),
                    "columnPatterns[" + index + "]"
            );
            if (state.isAir() || underlay.isAir()) {
                throw new IllegalArgumentException("A road surface cannot target air");
            }
            if (pattern == RoadMarkingPattern.DOUBLE_CONTINUOUS) {
                throw new IllegalArgumentException(
                        "double continuous patterns must be expanded before resolution"
                );
            }
        }
    }

    public int blockWidth() {
        return columnStates.size();
    }

        public BlockState targetState() {
        return columnStates.get(0);
    }

    public BlockState targetState(int lateralIndex) {
        return columnStates.get(lateralIndex);
    }

    public BlockState targetState(
            int lateralIndex,
            double pathDistance
    ) {
        return columnPatterns.get(lateralIndex).paintsAt(pathDistance)
                ? columnStates.get(lateralIndex)
                : underlayStates.get(lateralIndex);
    }

    public SurfacePlacement targetPlacement(
            int lateralIndex,
            double pathDistance,
            int blockX,
            int blockZ,
            double verticalLevel
    ) {
        boolean paints = columnPatterns.get(lateralIndex)
                .paintsAt(pathDistance);
        List<BlockState> choices = paints
                ? columnStateSets.get(lateralIndex)
                : underlayStateSets.get(lateralIndex);
        boolean halfStep = Math.abs(verticalLevel - Math.rint(verticalLevel))
                > 0.25;
        int approximateY = (int) Math.floor(verticalLevel);
        int seedY = halfStep ? approximateY + 1 : (int) Math.rint(verticalLevel);
        BlockPos seed = new BlockPos(blockX, seedY, blockZ);
        java.util.Optional<BlockState> slab = halfStep
                ? chooseSlab(choices, seed)
                : java.util.Optional.empty();
        if (halfStep && slab.isEmpty() && paints) {
            slab = chooseSlab(underlayStateSets.get(lateralIndex), seed);
        }
        BlockState state = slab.orElseGet(() -> choose(choices, seed));
        if (halfStep && state.getBlock() instanceof SlabBlock) {
            return new SurfacePlacement(
                    seed,
                    state.setValue(SlabBlock.TYPE, SlabType.BOTTOM)
            );
        }
        int blockY = (int) Math.floor(verticalLevel + 0.5);
        if (state.getBlock() instanceof SlabBlock) {
            state = state.setValue(SlabBlock.TYPE, SlabType.DOUBLE);
        }
        return new SurfacePlacement(
                new BlockPos(blockX, blockY, blockZ),
                state
        );
    }

    private static BlockState choose(
            List<BlockState> choices,
            BlockPos position
    ) {
        int start = Math.floorMod(mix(position.asLong()), choices.size());
        return choices.get(start);
    }

    private static java.util.Optional<BlockState> chooseSlab(
            List<BlockState> choices,
            BlockPos position
    ) {
        int start = Math.floorMod(mix(position.asLong()), choices.size());
        for (int offset = 0; offset < choices.size(); offset++) {
            BlockState candidate = choices.get((start + offset) % choices.size());
            if (candidate.getBlock() instanceof SlabBlock) {
                return java.util.Optional.of(candidate);
            }
        }
        for (int offset = 0; offset < choices.size(); offset++) {
            BlockState candidate = choices.get((start + offset) % choices.size());
            java.util.Optional<BlockState> companion =
                    RoadBlockStateFactory.slabCompanion(candidate);
            if (companion.isPresent()) {
                return companion;
            }
        }
        return java.util.Optional.empty();
    }

    private static int mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdl;
        value ^= value >>> 33;
        return (int) (value ^ (value >>> 32));
    }

    private static List<BlockState> repeatedStates(int blockWidth, BlockState state) {
        if (blockWidth < 1 || blockWidth > MAXIMUM_BLOCK_WIDTH) {
            throw new IllegalArgumentException(
                    "blockWidth must be between 1 and " + MAXIMUM_BLOCK_WIDTH
            );
        }
        return Collections.nCopies(blockWidth, Objects.requireNonNull(state, "targetState"));
    }

    private static List<RoadMarkingPattern> repeatedPatterns(
            List<BlockState> states
    ) {
        Objects.requireNonNull(states, "columnStates");
        return Collections.nCopies(
                states.size(),
                RoadMarkingPattern.CONTINUOUS
        );
    }

    private static List<List<BlockState>> singletonSets(
            List<BlockState> states
    ) {
        return states.stream().map(List::of).toList();
    }

    private static List<List<BlockState>> copySets(
            List<List<BlockState>> sets,
            String name
    ) {
        return Objects.requireNonNull(sets, name).stream()
                .map(set -> {
                    List<BlockState> copy = List.copyOf(set);
                    if (copy.isEmpty()) {
                        throw new IllegalArgumentException(
                                "surface block sets must not be empty"
                        );
                    }
                    return copy;
                })
                .toList();
    }

    public record SurfacePlacement(BlockPos position, BlockState state) {
        public SurfacePlacement {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(state, "state");
        }
    }
}
