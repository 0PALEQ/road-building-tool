package com.cookiecraftmods.roadbuilder.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.LinkedHashMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class BlockPlacementPlans {
    private BlockPlacementPlans() {
    }

        public static BlockPlacementPlan merge(
            ResourceKey<Level> dimension,
            List<BlockPlacementPlan> plans
    ) {
        Objects.requireNonNull(dimension, "dimension");
        plans = List.copyOf(Objects.requireNonNull(plans, "plans"));
        if (plans.isEmpty()) {
            throw new IllegalArgumentException("at least one plan is required");
        }

        Map<BlockPos, PlannedBlock> merged = new LinkedHashMap<>();
        for (BlockPlacementPlan plan : plans) {
            if (!dimension.equals(plan.dimension())) {
                throw new IllegalArgumentException("plan dimensions differ");
            }
            for (PlannedBlock block : plan.blocks()) {
                merged.put(block.position(), block);
            }
        }
        return BlockPlacementPlan.create(dimension, merged.values());
    }

        public static Optional<BlockPlacementPlan> excludingPositions(
            BlockPlacementPlan plan,
            Collection<BlockPos> excludedPositions
    ) {
        Objects.requireNonNull(plan, "plan");
        Set<BlockPos> excluded = BlockPositionSets.copyOf(Objects.requireNonNull(
                excludedPositions,
                "excludedPositions"
        ));
        List<PlannedBlock> retained = plan.blocks().stream()
                .filter(block -> !excluded.contains(block.position()))
                .toList();
        return retained.isEmpty()
                ? Optional.empty()
                : Optional.of(BlockPlacementPlan.create(
                        plan.dimension(),
                        retained
                ));
    }

        public static BlockPlacementPlan mergeLayered(
            ResourceKey<Level> dimension,
            List<LayeredBlockPlan> plans
    ) {
        Objects.requireNonNull(dimension, "dimension");
        plans = List.copyOf(Objects.requireNonNull(plans, "plans"));
        if (plans.isEmpty()) {
            throw new IllegalArgumentException("at least one plan is required");
        }
        List<MaterialCell> cells = new java.util.ArrayList<>();
        for (LayeredBlockPlan layered : plans) {
            if (!dimension.equals(layered.plan().dimension())) {
                throw new IllegalArgumentException("plan dimensions differ");
            }
            for (PlannedBlock block : layered.plan().blocks()) {
                cells.add(new MaterialCell(
                        block.position(),
                        block.targetState(),
                        layered.layer(),
                        layered.ownerType(),
                        layered.ownerKey()
                ));
            }
        }
        List<PlannedBlock> visible = new LayeredMaterialCellResolver()
                .resolve(cells)
                .values()
                .stream()
                .map(cell -> new PlannedBlock(cell.position(), cell.state()))
                .toList();
        return BlockPlacementPlan.create(dimension, visible);
    }
}
