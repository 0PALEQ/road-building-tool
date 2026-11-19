package com.cookiecraftmods.roadbuilder.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public final class SunkenRoadPlanner {
    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    static Set<BlockPos> surfacePositions(BlockPlacementPlan surface,
            List<RoadStructurePlanner.StructureSpan> spans) {
        if (spans.stream().noneMatch(RoadStructurePlanner.StructureSpan::sunkenRoad)) return Set.of();
        Map<Column, List<BlockPos>> columns = new HashMap<>();
        for (PlannedBlock block : surface.blocks()) {
            if (!block.targetState().isAir()) {
                columns.computeIfAbsent(Column.of(block.position()), key -> new java.util.ArrayList<>())
                        .add(block.position());
            }
        }
        Set<BlockPos> result = new HashSet<>();
        for (var span : spans) {
            if (!span.sunkenRoad()) continue;
            var geometry = span.geometry();
            double length = geometry.length();
            for (double distance = 0; distance <= length + 0.5; distance += 0.5) {
                var sample = geometry.sample(Math.min(distance, length));
                for (double lateral = -span.roadWidth() / 2.0 - 0.5;
                        lateral <= span.roadWidth() / 2.0 + 0.5; lateral += 0.5) {
                    var point = sample.offsetLaterally(lateral).position();
                    var column = new Column((int) Math.floor(point.x()), (int) Math.floor(point.z()));
                    for (BlockPos position : columns.getOrDefault(column, List.of())) {
                        if (Math.abs(position.getY() - point.y()) <= 2) result.add(position);
                    }
                }
            }
        }
        return result;
    }

    public BlockPlanResult excavate(BlockPlacementPlan structures, Set<BlockPos> surfaces,
            PlacementBlockWorld world, Predicate<BlockPos> existingRoad, int maximumBlocks) {
        if (surfaces.isEmpty()) return new BlockPlanResult.Success(structures);
        Map<BlockPos, PlannedBlock> targets = new LinkedHashMap<>();
        structures.blocks().forEach(block -> targets.put(block.position(), block));
        Map<Column, Integer> floors = new HashMap<>();
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        surfaces.forEach(position -> offer(position, floors, pending));

        while (!pending.isEmpty()) {
            BlockPos floor = pending.removeFirst();
            if (floor.getY() != floors.get(Column.of(floor))) continue;
            boolean cutTerrain = false;
            for (int y = world.highestBlockY(floor.getX(), floor.getZ()); y > floor.getY(); y--) {
                BlockPos position = new BlockPos(floor.getX(), y, floor.getZ());
                var state = world.blockState(position);
                if (state.isAir()) continue;
                PlannedBlock planned = targets.get(position);
                // Junction rebuilding already plans which old surface blocks to
                // keep or clear, including slab companions above the new floor.
                // Protect other roads without rejecting those explicit changes.
                if ((existingRoad.test(position) && planned == null) || world.hasBlockEntity(position)
                        || !state.getFluidState().isEmpty()) {
                    com.cookiecraftmods.roadbuilder.RoadBuilder.LOGGER.warn(
                            "Rejected sunken-road excavation: position={}, state={}, protectedRoad={}, blockEntity={}, fluid={}",
                            position.toShortString(), state, existingRoad.test(position) && planned == null,
                            world.hasBlockEntity(position), !state.getFluidState().isEmpty());
                    return new BlockPlanResult.Failure(BlockPlanFailureCode.INVALID_GEOMETRY);
                }
                if (planned != null && !planned.targetState().isAir()) continue;
                cutTerrain |= RoadTunnelPlanner.isTerrain(state);
                targets.put(position, new PlannedBlock(position, Blocks.AIR.defaultBlockState()));
                if (targets.size() > maximumBlocks) {
                    return new BlockPlanResult.Failure(BlockPlanFailureCode.BLOCK_LIMIT_EXCEEDED);
                }
            }
            if (!cutTerrain) continue;
            for (int[] direction : DIRECTIONS) {
                BlockPos next = floor.offset(direction[0], 1, direction[1]);
                if (existingRoad.test(next) || world.hasBlockEntity(next)) continue;
                offer(next, floors, pending);
            }
            if (floors.size() > maximumBlocks) {
                return new BlockPlanResult.Failure(BlockPlanFailureCode.BLOCK_LIMIT_EXCEEDED);
            }
        }
        return new BlockPlanResult.Success(BlockPlacementPlan.create(structures.dimension(), targets.values()));
    }

    private static void offer(BlockPos floor, Map<Column, Integer> floors, ArrayDeque<BlockPos> pending) {
        Column column = Column.of(floor);
        Integer previous = floors.get(column);
        if (previous == null || floor.getY() < previous) {
            floors.put(column, floor.getY());
            pending.addLast(floor);
        }
    }

    private record Column(int x, int z) {
        static Column of(BlockPos position) { return new Column(position.getX(), position.getZ()); }
    }
}
