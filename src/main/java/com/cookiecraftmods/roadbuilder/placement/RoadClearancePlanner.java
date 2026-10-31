package com.cookiecraftmods.roadbuilder.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

public final class RoadClearancePlanner {
    public static final int DEFAULT_CLEARANCE_HEIGHT = 12;
    public static final int VEGETATION_MARGIN = 3;
    public static final int TREE_CLEARANCE_HEIGHT = 32;
    private static final int CANOPY_RADIUS = 6;

    public BlockPlanResult addClearance(
            BlockPlacementPlan surfacePlan,
            PlacementBlockWorld world,
            int maximumAffectedBlocks
    ) {
        return addClearance(
                surfacePlan,
                world,
                DEFAULT_CLEARANCE_HEIGHT,
                position -> false,
                maximumAffectedBlocks
        );
    }

    public BlockPlanResult addClearance(
            BlockPlacementPlan surfacePlan,
            PlacementBlockWorld world,
            int clearanceHeight,
            int maximumAffectedBlocks
    ) {
        return addClearance(
                surfacePlan,
                world,
                clearanceHeight,
                position -> false,
                maximumAffectedBlocks
        );
    }

    public BlockPlanResult addClearance(
            BlockPlacementPlan surfacePlan,
            PlacementBlockWorld world,
            Predicate<BlockPos> preservedPosition,
            int maximumAffectedBlocks
    ) {
        return addClearance(
                surfacePlan,
                world,
                DEFAULT_CLEARANCE_HEIGHT,
                preservedPosition,
                maximumAffectedBlocks
        );
    }

    public BlockPlanResult addClearance(
            BlockPlacementPlan surfacePlan,
            PlacementBlockWorld world,
            int clearanceHeight,
            Predicate<BlockPos> preservedPosition,
            int maximumAffectedBlocks
    ) {
        Objects.requireNonNull(surfacePlan, "surfacePlan");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(preservedPosition, "preservedPosition");
        if (!surfacePlan.dimension().equals(world.dimension())) {
            throw new IllegalArgumentException("plan and world dimensions differ");
        }
        if (clearanceHeight < 1) {
            throw new IllegalArgumentException("clearanceHeight must be positive");
        }
        if (maximumAffectedBlocks < 1) {
            throw new IllegalArgumentException(
                    "maximumAffectedBlocks must be positive"
            );
        }

        Map<BlockPos, PlannedBlock> targets = new LinkedHashMap<>();
        for (PlannedBlock block : surfacePlan.blocks()) {
            targets.put(block.position(), block);
        }
        for (PlannedBlock surface : surfacePlan.blocks()) {
            if (surface.targetState().isAir()) {
                continue;
            }
            for (int offset = 1; offset <= clearanceHeight; offset++) {
                BlockPos position = surface.position().above(offset);
                if (targets.containsKey(position)
                        || preservedPosition.test(position)
                        || world.blockState(position).isAir()) {
                    continue;
                }
                if (targets.size() >= maximumAffectedBlocks) {
                    return new BlockPlanResult.Failure(
                            BlockPlanFailureCode.BLOCK_LIMIT_EXCEEDED
                    );
                }
                targets.put(
                        position,
                        new PlannedBlock(position, Blocks.AIR.defaultBlockState())
                );
            }
        }
        if (!clearEdgeVegetation(surfacePlan, world, clearanceHeight, preservedPosition, targets, maximumAffectedBlocks)) {
            return new BlockPlanResult.Failure(BlockPlanFailureCode.BLOCK_LIMIT_EXCEEDED);
        }
        return new BlockPlanResult.Success(
                BlockPlacementPlan.create(surfacePlan.dimension(), targets.values())
        );
    }
    private static boolean clearEdgeVegetation(
            BlockPlacementPlan surfacePlan, PlacementBlockWorld world, int clearanceHeight,
            Predicate<BlockPos> preserved, Map<BlockPos, PlannedBlock> targets, int limit
    ) {
        java.util.Set<BlockPos> columns = new java.util.HashSet<>();
        for (PlannedBlock surface : surfacePlan.blocks()) {
            if (surface.targetState().isAir()) continue;
            for (int x = -VEGETATION_MARGIN; x <= VEGETATION_MARGIN; x++) {
                for (int z = -VEGETATION_MARGIN; z <= VEGETATION_MARGIN; z++) {
                    columns.add(surface.position().offset(x, 0, z));
                }
            }
        }
        java.util.Set<BlockPos> trunks = new java.util.HashSet<>();
        for (BlockPos column : columns) {
            for (int height = 0; height <= Math.max(clearanceHeight, TREE_CLEARANCE_HEIGHT); height++) {
                BlockPos pos = column.above(height);
                if (preserved.test(pos) || world.hasBlockEntity(pos)) {
                    if (height == 0) continue;
                    break;
                }
                var state = world.blockState(pos);
                PlannedBlock planned = targets.get(pos);
                if (planned != null && !planned.targetState().isAir()) {
                    if (height == 0) continue;
                    break;
                }
                if (state.isAir()) continue;
                if (!isVegetation(state)) {
                    // Only excavations already authorized by the road may be crossed.
                    if (height == 0 || (planned != null && planned.targetState().isAir())) continue;
                    break;
                }
                if (isLog(state)) trunks.add(pos);
                if (!clear(targets, pos, limit)) return false;
            }
        }
        // Follow leaves only, within a fixed distance of a removed trunk. Never
        // follow neighboring trunks into another tree or propagate through a forest.
        java.util.Set<BlockPos> visited = new java.util.HashSet<>();
        for (BlockPos trunk : trunks) {
            java.util.ArrayDeque<BlockPos> pending = new java.util.ArrayDeque<>();
            pending.add(trunk);
            java.util.Set<BlockPos> treeVisited = new java.util.HashSet<>();
            while (!pending.isEmpty()) {
                BlockPos pos = pending.removeFirst();
                for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
                    BlockPos leaf = pos.relative(direction);
                    if (Math.abs(leaf.getX() - trunk.getX()) > CANOPY_RADIUS
                            || Math.abs(leaf.getZ() - trunk.getZ()) > CANOPY_RADIUS
                            || Math.abs(leaf.getY() - trunk.getY()) > CANOPY_RADIUS
                            || !treeVisited.add(leaf) || preserved.test(leaf) || world.hasBlockEntity(leaf)) continue;
                    var state = world.blockState(leaf);
                    if (!(state.getBlock() instanceof net.minecraft.world.level.block.LeavesBlock)
                            && !state.is(net.minecraft.tags.BlockTags.LEAVES)) continue;
                    PlannedBlock planned = targets.get(leaf);
                    if (planned != null && !planned.targetState().isAir()) continue;
                    pending.addLast(leaf);
                    if (visited.add(leaf) && !clear(targets, leaf, limit)) return false;
                }
            }
        }
        return true;
    }

    private static boolean isLog(net.minecraft.world.level.block.state.BlockState state) {
        String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return state.is(net.minecraft.tags.BlockTags.LOGS) || path.endsWith("_log") || path.endsWith("_wood")
                || path.endsWith("_stem") || path.endsWith("_hyphae");
    }

    private static boolean isVegetation(net.minecraft.world.level.block.state.BlockState state) {
        return isLog(state) || state.getBlock() instanceof net.minecraft.world.level.block.LeavesBlock
                || state.is(net.minecraft.tags.BlockTags.LEAVES)
                || (state.canBeReplaced() && state.getFluidState().isEmpty());
    }

    private static boolean clear(Map<BlockPos, PlannedBlock> targets, BlockPos pos, int limit) {
        if (!targets.containsKey(pos) && targets.size() >= limit) return false;
        targets.put(pos, new PlannedBlock(pos, Blocks.AIR.defaultBlockState()));
        return true;
    }

}
