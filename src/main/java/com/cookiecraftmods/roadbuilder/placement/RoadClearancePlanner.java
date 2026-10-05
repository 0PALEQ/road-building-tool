package com.cookiecraftmods.roadbuilder.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

public final class RoadClearancePlanner {
    public static final int DEFAULT_CLEARANCE_HEIGHT = 12;

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
        return new BlockPlanResult.Success(
                BlockPlacementPlan.create(surfacePlan.dimension(), targets.values())
        );
    }
}
