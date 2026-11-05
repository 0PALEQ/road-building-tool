package com.cookiecraftmods.roadbuilder.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Read-only terrain view with the operations being rebuilt virtually removed. */
public final class MaterializationRebuildWorld implements PlacementBlockWorld {
    private final PlacementBlockWorld world;
    private final PlacementOwnershipLedger ledger;
    private final Set<PlacementOperationId> removedIds;
    private final Map<BlockPos, BlockState> restoredStates = new HashMap<>();

    public MaterializationRebuildWorld(PlacementBlockWorld world,
            PlacementOwnershipLedger ledger, Collection<PlacementOperationId> removedIds) {
        this.world = java.util.Objects.requireNonNull(world, "world");
        this.ledger = java.util.Objects.requireNonNull(ledger, "ledger");
        this.removedIds = Set.copyOf(removedIds);
    }

    @Override
    public ResourceKey<Level> dimension() {
        return world.dimension();
    }

    @Override
    public int minimumBuildHeight() {
        return world.minimumBuildHeight();
    }

    @Override
    public int maximumBuildHeight() { return world.maximumBuildHeight(); }

    @Override
    public BlockState blockState(BlockPos position) {
        return restoredStates.computeIfAbsent(position.immutable(), this::restoreState);
    }

    private BlockState restoreState(BlockPos position) {
        BlockState current = world.blockState(position);
        PositionChangeStack stack = ledger.stack(position).orElse(null);
        if (stack == null || !BlockTransitionStateMatcher.matchesPlannedTarget(
                current, stack.top().afterState())) {
            return current;
        }
        for (PlacementOperationId id : removedIds) {
            if (stack.layer(id).isEmpty()) continue;
            StackRemoval removal = stack.remove(id);
            if (removal.updatedStack().isEmpty()) {
                return removal.removedLayer().beforeState();
            }
            stack = removal.updatedStack().orElseThrow();
        }
        return stack.top().afterState();
    }

    public boolean isMaterializedRoadPosition(BlockPos position) {
        return ledger.stack(position).map(stack -> stack.layers().stream()
                .anyMatch(layer -> !removedIds.contains(layer.operationId()))).orElse(false);
    }

    @Override
    public boolean hasBlockEntity(BlockPos position) {
        return world.hasBlockEntity(position);
    }

    @Override
    public boolean setBlockState(BlockPos position, BlockState state) {
        throw new UnsupportedOperationException("Rebuild planning must not modify the world");
    }
}
