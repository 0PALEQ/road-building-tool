package com.cookiecraftmods.roadbuilder.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;

public final class PlacementOwnershipLedger {
    private static final Comparator<BlockPos> POSITION_ORDER = Comparator
            .comparingInt((BlockPos position) -> position.getX())
            .thenComparingInt(BlockPos::getY)
            .thenComparingInt(BlockPos::getZ);

    private final ResourceKey<Level> dimension;
    private final Runnable changeListener;
    private final PlacementOwnershipLimits limits;
    private final Map<BlockPos, PositionChangeStack> positions = new HashMap<>();
    private final Map<PlacementOperationId, PlacementOperation> operations = new HashMap<>();
    private final Map<RoadMaterializationOwner, PlacementOperationId> materializations =
            new HashMap<>();
    private long revision;
    private int totalLayerCount;

    public PlacementOwnershipLedger(ResourceKey<Level> dimension) {
        this(dimension, PlacementOwnershipLimits.defaults(), () -> { });
    }

    public PlacementOwnershipLedger(ResourceKey<Level> dimension, Runnable changeListener) {
        this(dimension, PlacementOwnershipLimits.defaults(), changeListener);
    }

    public PlacementOwnershipLedger(
            ResourceKey<Level> dimension,
            PlacementOwnershipLimits limits,
            Runnable changeListener
    ) {
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.changeListener = Objects.requireNonNull(changeListener, "changeListener");
    }

    public static PlacementOwnershipLedger restore(
            ResourceKey<Level> dimension,
            Map<BlockPos, PositionChangeStack> suppliedStacks,
            Runnable changeListener
    ) {
        return restore(dimension, suppliedStacks, Map.of(), changeListener);
    }

    public static PlacementOwnershipLedger restore(
            ResourceKey<Level> dimension,
            Map<BlockPos, PositionChangeStack> suppliedStacks,
            Map<PlacementOperationId, RoadMaterializationOwner> suppliedOwners,
            Runnable changeListener
    ) {
        Objects.requireNonNull(suppliedStacks, "suppliedStacks");
        Objects.requireNonNull(suppliedOwners, "suppliedOwners");
        PlacementOwnershipLedger ledger = new PlacementOwnershipLedger(dimension, changeListener);
        if (suppliedStacks.size() > ledger.limits.maximumPositionCount()) {
            throw new IllegalArgumentException("Restored ownership position count exceeds its limit");
        }
        Map<PlacementOperationId, List<BlockPos>> operationPositions = new HashMap<>();

        for (Map.Entry<BlockPos, PositionChangeStack> entry : suppliedStacks.entrySet()) {
            BlockPos position = Objects.requireNonNull(entry.getKey(), "suppliedStacks contains null position").immutable();
            PositionChangeStack stack = PositionChangeStack.restore(
                    Objects.requireNonNull(entry.getValue(), "suppliedStacks contains null stack").layers()
            );
            if (stack.layers().size() > ledger.limits.maximumStackDepth()) {
                throw new IllegalArgumentException("Restored ownership stack depth exceeds its limit");
            }
            if ((long) ledger.totalLayerCount + stack.layers().size()
                    > ledger.limits.maximumTotalLayerCount()) {
                throw new IllegalArgumentException("Restored ownership layer count exceeds its limit");
            }
            if (ledger.positions.put(position, stack) != null) {
                throw new IllegalArgumentException("Duplicate restored position: " + position.toShortString());
            }
            ledger.totalLayerCount += stack.layers().size();
            for (BlockChangeLayer layer : stack.layers()) {
                operationPositions.computeIfAbsent(layer.operationId(), ignored -> new ArrayList<>()).add(position);
            }
        }

        for (Map.Entry<PlacementOperationId, List<BlockPos>> entry : operationPositions.entrySet()) {
            RoadMaterializationOwner owner = suppliedOwners.get(entry.getKey());
            ledger.operations.put(
                    entry.getKey(),
                    PlacementOperation.create(
                            entry.getKey(),
                            dimension,
                            Optional.ofNullable(owner),
                            entry.getValue()
                    )
            );
            if (owner != null && ledger.materializations.put(owner, entry.getKey()) != null) {
                throw new IllegalArgumentException(
                        "Multiple operations materialize the same graph owner: " + owner
                );
            }
        }
        for (PlacementOperationId operationId : suppliedOwners.keySet()) {
            if (!operationPositions.containsKey(operationId)) {
                throw new IllegalArgumentException(
                        "Materialization owner references unknown operation: " + operationId
                );
            }
        }
        return ledger;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public Optional<PlacementOperation> operation(PlacementOperationId id) {
        return Optional.ofNullable(operations.get(Objects.requireNonNull(id, "id")));
    }

    public Optional<PlacementOperation> operation(RoadMaterializationOwner owner) {
        PlacementOperationId operationId = materializations.get(
                Objects.requireNonNull(owner, "owner")
        );
        return operationId == null ? Optional.empty() : operation(operationId);
    }

    public PlacementOperation assignMaterializationOwner(
            PlacementOperationId operationId,
            RoadMaterializationOwner owner
    ) {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(owner, "owner");
        PlacementOperation operation = operations.get(operationId);
        if (operation == null) {
            throw new IllegalArgumentException("Unknown operation: " + operationId);
        }
        if (operation.materializationOwner().isPresent()) {
            if (operation.materializationOwner().orElseThrow().equals(owner)) {
                return operation;
            }
            throw new IllegalStateException(
                    "Operation already materializes another graph object"
            );
        }
        if (materializations.containsKey(owner)) {
            throw new IllegalStateException(
                    "Graph object already has a materialization"
            );
        }
        PlacementOperation replacement = PlacementOperation.create(
                operation.id(),
                dimension,
                owner,
                operation.positions()
        );
        changeListener.run();
        operations.put(operationId, replacement);
        materializations.put(owner, operationId);
        revision++;
        return replacement;
    }

    public long revision() {
        return revision;
    }

    public int positionCount() {
        return positions.size();
    }

    public int totalLayerCount() {
        return totalLayerCount;
    }

    public Collection<PlacementOperation> operations() {
        return operations.values().stream()
                .sorted(Comparator.comparing(operation -> operation.id().toString()))
                .toList();
    }

    public Map<BlockPos, PositionChangeStack> positionStacks() {
        List<BlockPos> orderedPositions = positions.keySet().stream().sorted(POSITION_ORDER).toList();
        Map<BlockPos, PositionChangeStack> copy = new LinkedHashMap<>(orderedPositions.size());
        for (BlockPos position : orderedPositions) {
            copy.put(position, positions.get(position));
        }
        return Map.copyOf(copy);
    }

    public Optional<PositionChangeStack> stack(BlockPos position) {
        return Optional.ofNullable(positions.get(Objects.requireNonNull(position, "position")));
    }

    public Optional<BlockState> expectedVisibleState(BlockPos position) {
        return stack(position).map(value -> value.top().afterState());
    }

    public boolean isMaterializedRoadPosition(BlockPos position) {
        return stack(position).isPresent();
    }

        public void repartitionMaterializations(
            List<OperationMaterializationRepartition> repartitions
    ) {
        repartitions = List.copyOf(Objects.requireNonNull(
                repartitions,
                "repartitions"
        ));
        if (repartitions.isEmpty()) {
            return;
        }

        Map<BlockPos, PositionChangeStack> candidatePositions =
                new HashMap<>(positions);
        Map<PlacementOperationId, PlacementOperation> candidateOperations =
                new HashMap<>(operations);
        Map<RoadMaterializationOwner, PlacementOperationId>
                candidateMaterializations = new HashMap<>(materializations);
        int candidateLayerCount = totalLayerCount;
        Set<PlacementOperationId> sources = new HashSet<>();

        for (OperationMaterializationRepartition repartition : repartitions) {
            if (!sources.add(repartition.sourceOperationId())) {
                throw new IllegalArgumentException("an operation is repartitioned twice");
            }
            PlacementOperation source =
                    candidateOperations.remove(repartition.sourceOperationId());
            if (source == null) {
                throw new IllegalArgumentException(
                        "unknown repartition source " + repartition.sourceOperationId()
                );
            }
            source.materializationOwner().ifPresent(candidateMaterializations::remove);

            Set<BlockPos> sourcePositions = Set.copyOf(source.positions());
            Set<BlockPos> covered = new HashSet<>();
            Set<PlacementOperationId> partitionIds = new HashSet<>();
            Set<RoadMaterializationOwner> partitionOwners = new HashSet<>();
            int replacementLayerCount = 0;
            for (MaterializationPartition partition : repartition.partitions()) {
                if (!partitionIds.add(partition.operationId())
                        || candidateOperations.containsKey(partition.operationId())) {
                    throw new IllegalArgumentException(
                            "duplicate materialization partition operation ID"
                    );
                }
                if (!partitionOwners.add(partition.owner())
                        || candidateMaterializations.containsKey(partition.owner())) {
                    throw new IllegalArgumentException(
                            "duplicate materialization partition owner"
                    );
                }
                Set<BlockPos> uniquePositions = new HashSet<>(partition.positions());
                if (uniquePositions.size() != partition.positions().size()
                        || !sourcePositions.containsAll(uniquePositions)) {
                    throw new IllegalArgumentException(
                            "partition positions must be unique members of the source operation"
                    );
                }
                covered.addAll(uniquePositions);
                replacementLayerCount = Math.addExact(
                        replacementLayerCount,
                        uniquePositions.size()
                );
            }
            if (!covered.equals(sourcePositions)) {
                throw new IllegalArgumentException(
                        "materialization partitions must cover the source operation"
                );
            }

            for (BlockPos position : source.positions()) {
                PositionChangeStack stack = candidatePositions.get(position);
                BlockChangeLayer replaced = stack == null
                        ? null
                        : stack.layer(source.id()).orElse(null);
                if (replaced == null) {
                    throw new IllegalStateException(
                            "source operation is absent at " + position.toShortString()
                    );
                }
                List<BlockChangeLayer> replacementLayers = new ArrayList<>();
                for (MaterializationPartition partition : repartition.partitions()) {
                    if (!partition.positions().contains(position)) {
                        continue;
                    }
                    BlockState before = replacementLayers.isEmpty()
                            ? replaced.beforeState()
                            : replaced.afterState();
                    replacementLayers.add(new BlockChangeLayer(
                            partition.operationId(),
                            before,
                            replaced.afterState()
                    ));
                }
                PositionChangeStack replacement = stack.replace(
                        source.id(),
                        replacementLayers
                );
                if (replacement.layers().size() > limits.maximumStackDepth()) {
                    throw new IllegalStateException(
                            "repartition would exceed the ownership stack-depth limit"
                    );
                }
                candidatePositions.put(position, replacement);
            }

            for (MaterializationPartition partition : repartition.partitions()) {
                PlacementOperation operation = PlacementOperation.create(
                        partition.operationId(),
                        dimension,
                        partition.owner(),
                        partition.positions()
                );
                candidateOperations.put(operation.id(), operation);
                candidateMaterializations.put(partition.owner(), operation.id());
            }
            candidateLayerCount = Math.addExact(
                    candidateLayerCount - source.positions().size(),
                    replacementLayerCount
            );
            if (candidateLayerCount > limits.maximumTotalLayerCount()) {
                throw new IllegalStateException(
                        "repartition would exceed the ownership layer limit"
                );
            }
        }

        changeListener.run();
        positions.clear();
        positions.putAll(candidatePositions);
        operations.clear();
        operations.putAll(candidateOperations);
        materializations.clear();
        materializations.putAll(candidateMaterializations);
        totalLayerCount = candidateLayerCount;
        revision++;
    }

        public void publishMaterializationRebuild(
            Collection<PlacementOperationId> replacedOperationIds,
            PlacementOperationId overlayOperationId,
            List<MaterializationPartition> retainedPartitions
    ) {
        replacedOperationIds = List.copyOf(Objects.requireNonNull(
                replacedOperationIds,
                "replacedOperationIds"
        ));
        Objects.requireNonNull(overlayOperationId, "overlayOperationId");
        retainedPartitions = List.copyOf(Objects.requireNonNull(
                retainedPartitions,
                "retainedPartitions"
        ));
        if (replacedOperationIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "a materialization rebuild must replace an operation"
            );
        }
        Map<BlockPos, PositionChangeStack> candidatePositions =
                new HashMap<>(positions);
        Map<PlacementOperationId, PlacementOperation> candidateOperations =
                new HashMap<>(operations);
        Map<RoadMaterializationOwner, PlacementOperationId>
                candidateMaterializations = new HashMap<>(materializations);
        int candidateLayerCount = totalLayerCount;

        PlacementOperation overlay = candidateOperations.get(overlayOperationId);
        if (overlay == null) {
            throw new IllegalArgumentException(
                    "unknown rebuild overlay " + overlayOperationId
            );
        }
        if (overlay.materializationOwner().isPresent()) {
            throw new IllegalArgumentException(
                    "a rebuild overlay must not already own graph materialization"
            );
        }

        Set<PlacementOperationId> replacedIds = new HashSet<>();
        for (PlacementOperationId operationId : replacedOperationIds) {
            Objects.requireNonNull(
                    operationId,
                    "replacedOperationIds contains null"
            );
            if (operationId.equals(overlayOperationId)
                    || !replacedIds.add(operationId)) {
                throw new IllegalArgumentException(
                        "rebuild operation IDs must be distinct from the overlay"
                );
            }
            PlacementOperation replaced = candidateOperations.remove(operationId);
            if (replaced == null) {
                throw new IllegalArgumentException(
                        "unknown replaced operation " + operationId
                );
            }
            replaced.materializationOwner().ifPresent(
                    candidateMaterializations::remove
            );
            for (BlockPos position : replaced.positions()) {
                PositionChangeStack stack = candidatePositions.get(position);
                if (stack == null || stack.layer(operationId).isEmpty()) {
                    throw new IllegalStateException(
                            "replaced operation is absent at "
                                    + position.toShortString()
                    );
                }
                Optional<PositionChangeStack> updated = stack.remove(operationId)
                        .updatedStack();
                if (updated.isPresent()) {
                    candidatePositions.put(position, updated.orElseThrow());
                } else {
                    candidatePositions.remove(position);
                }
                candidateLayerCount--;
            }
        }

        candidateOperations.remove(overlayOperationId);
        Set<BlockPos> overlayPositions = Set.copyOf(overlay.positions());
        Set<PlacementOperationId> partitionIds = new HashSet<>();
        Set<RoadMaterializationOwner> partitionOwners = new HashSet<>();
        Map<BlockPos, List<MaterializationPartition>> partitionsByPosition =
                new HashMap<>();
        int replacementLayerCount = 0;
        for (MaterializationPartition partition : retainedPartitions) {
            if (!partitionIds.add(partition.operationId())
                    || candidateOperations.containsKey(partition.operationId())) {
                throw new IllegalArgumentException(
                        "duplicate materialization partition operation ID"
                );
            }
            if (!partitionOwners.add(partition.owner())
                    || candidateMaterializations.containsKey(partition.owner())) {
                throw new IllegalArgumentException(
                        "duplicate materialization partition owner"
                );
            }
            Set<BlockPos> uniquePositions = new HashSet<>(partition.positions());
            if (uniquePositions.size() != partition.positions().size()
                    || !overlayPositions.containsAll(uniquePositions)) {
                throw new IllegalArgumentException(
                        "retained partition positions must be unique overlay members"
                );
            }
            replacementLayerCount = Math.addExact(
                    replacementLayerCount,
                    uniquePositions.size()
            );
            for (BlockPos position : partition.positions()) {
                partitionsByPosition.computeIfAbsent(
                        position,
                        ignored -> new ArrayList<>()
                ).add(partition);
            }
        }

        for (BlockPos position : overlay.positions()) {
            PositionChangeStack stack = candidatePositions.get(position);
            BlockChangeLayer overlayLayer = stack == null
                    ? null
                    : stack.layer(overlayOperationId).orElse(null);
            if (overlayLayer == null) {
                throw new IllegalStateException(
                        "rebuild overlay is absent at " + position.toShortString()
                );
            }
            List<MaterializationPartition> covering = partitionsByPosition
                    .getOrDefault(position, List.of());
            if (covering.isEmpty()) {
                StackRemoval removal = stack.remove(overlayOperationId);
                if (removal.updatedStack().isPresent()) {
                    candidatePositions.put(
                            position,
                            removal.updatedStack().orElseThrow()
                    );
                } else {
                    candidatePositions.remove(position);
                }
                continue;
            }

            List<BlockChangeLayer> replacementLayers = new ArrayList<>();
            for (MaterializationPartition partition : covering) {
                BlockState before = replacementLayers.isEmpty()
                        ? overlayLayer.beforeState()
                        : overlayLayer.afterState();
                replacementLayers.add(new BlockChangeLayer(
                        partition.operationId(),
                        before,
                        overlayLayer.afterState()
                ));
            }
            PositionChangeStack replacement = stack.replace(
                    overlayOperationId,
                    replacementLayers
            );
            if (replacement.layers().size() > limits.maximumStackDepth()) {
                throw new IllegalStateException(
                        "rebuild would exceed the ownership stack-depth limit"
                );
            }
            candidatePositions.put(position, replacement);
        }

        for (MaterializationPartition partition : retainedPartitions) {
            PlacementOperation operation = PlacementOperation.create(
                    partition.operationId(),
                    dimension,
                    partition.owner(),
                    partition.positions()
            );
            candidateOperations.put(operation.id(), operation);
            candidateMaterializations.put(partition.owner(), operation.id());
        }
        candidateLayerCount = Math.addExact(
                candidateLayerCount - overlay.positions().size(),
                replacementLayerCount
        );
        if (candidateLayerCount < 0
                || candidateLayerCount > limits.maximumTotalLayerCount()) {
            throw new IllegalStateException(
                    "rebuild would exceed the ownership layer limit"
            );
        }

        changeListener.run();
        positions.clear();
        positions.putAll(candidatePositions);
        operations.clear();
        operations.putAll(candidateOperations);
        materializations.clear();
        materializations.putAll(candidateMaterializations);
        totalLayerCount = candidateLayerCount;
        revision++;
    }

    boolean containsOperation(PlacementOperationId id) {
        return operations.containsKey(id);
    }

    boolean canRecord(BlockPlacementPlan plan) {
        Objects.requireNonNull(plan, "plan");
        if (!dimension.equals(plan.dimension())) {
            return false;
        }
        int addedPositions = 0;
        for (PlannedBlock block : plan.blocks()) {
            PositionChangeStack existing = positions.get(block.position());
            if (existing == null) {
                addedPositions++;
            } else if (existing.layers().size() >= limits.maximumStackDepth()) {
                return false;
            }
        }
        return (long) positions.size() + addedPositions <= limits.maximumPositionCount()
                && (long) totalLayerCount + plan.affectedBlockCount()
                <= limits.maximumTotalLayerCount();
    }

    void recordPlacement(
            PlacementOperationId operationId,
            BlockPlacementPlan plan,
            Map<BlockPos, BlockState> statesBeforePlacement,
            long expectedRevision
    ) {
        recordPlacement(
                operationId,
                Optional.empty(),
                plan,
                statesBeforePlacement,
                expectedRevision
        );
    }

    void recordPlacement(
            PlacementOperationId operationId,
            RoadMaterializationOwner owner,
            BlockPlacementPlan plan,
            Map<BlockPos, BlockState> statesBeforePlacement,
            long expectedRevision
    ) {
        recordPlacement(
                operationId,
                Optional.of(Objects.requireNonNull(owner, "owner")),
                plan,
                statesBeforePlacement,
                expectedRevision
        );
    }

    private void recordPlacement(
            PlacementOperationId operationId,
            Optional<RoadMaterializationOwner> owner,
            BlockPlacementPlan plan,
            Map<BlockPos, BlockState> statesBeforePlacement,
            long expectedRevision
    ) {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(statesBeforePlacement, "statesBeforePlacement");
        if (revision != expectedRevision) {
            throw new IllegalStateException("Ownership ledger changed during placement");
        }
        if (!dimension.equals(plan.dimension())) {
            throw new IllegalArgumentException("Plan dimension does not match ledger dimension");
        }
        if (operations.containsKey(operationId)) {
            throw new IllegalArgumentException("Operation already exists: " + operationId);
        }
        if (owner.isPresent() && materializations.containsKey(owner.orElseThrow())) {
            throw new IllegalArgumentException(
                    "Graph owner already has a materialization: " + owner.orElseThrow()
            );
        }
        if (!canRecord(plan)) {
            throw new IllegalStateException("Placement would exceed ownership ledger limits");
        }

        Map<BlockPos, PositionChangeStack> replacements = new HashMap<>(plan.affectedBlockCount());
        for (PlannedBlock block : plan.blocks()) {
            BlockPos position = block.position();
            BlockState priorState = Objects.requireNonNull(
                    statesBeforePlacement.get(position),
                    "Missing prior state for " + position.toShortString()
            );
            BlockChangeLayer layer = new BlockChangeLayer(operationId, priorState, block.targetState());
            PositionChangeStack existing = positions.get(position);
            replacements.put(position, existing == null ? PositionChangeStack.of(layer) : existing.push(layer));
        }

        PlacementOperation operation = PlacementOperation.create(
                operationId,
                dimension,
                owner,
                replacements.keySet()
        );
        changeListener.run();
        positions.putAll(replacements);
        operations.put(operationId, operation);
        owner.ifPresent(value -> materializations.put(value, operationId));
        totalLayerCount += plan.affectedBlockCount();
        revision++;
    }

    void removeOperation(PlacementOperationId operationId, long expectedRevision) {
        if (revision != expectedRevision) {
            throw new IllegalStateException("Ownership ledger changed during removal");
        }
        PlacementOperation operation = operations.get(Objects.requireNonNull(operationId, "operationId"));
        if (operation == null) {
            throw new IllegalArgumentException("Unknown operation: " + operationId);
        }

        Map<BlockPos, Optional<PositionChangeStack>> replacements = new HashMap<>(operation.positions().size());
        for (BlockPos position : operation.positions()) {
            PositionChangeStack stack = positions.get(position);
            if (stack == null || stack.layer(operationId).isEmpty()) {
                throw new IllegalStateException(
                        "Operation index disagrees with stack at " + position.toShortString()
                );
            }
            replacements.put(position, stack.remove(operationId).updatedStack());
        }

        changeListener.run();
        for (Map.Entry<BlockPos, Optional<PositionChangeStack>> entry : replacements.entrySet()) {
            if (entry.getValue().isPresent()) {
                positions.put(entry.getKey(), entry.getValue().orElseThrow());
            } else {
                positions.remove(entry.getKey());
            }
        }
        operations.remove(operationId);
        operation.materializationOwner().ifPresent(materializations::remove);
        totalLayerCount -= operation.positions().size();
        revision++;
    }

        void discardOperationBestEffort(PlacementOperationId operationId) {
        Objects.requireNonNull(operationId, "operationId");
        PlacementOperation operation = operations.get(operationId);
        Map<BlockPos, Optional<PositionChangeStack>> replacements =
                new HashMap<>();
        int removedLayers = 0;
        for (Map.Entry<BlockPos, PositionChangeStack> entry
                : positions.entrySet()) {
            if (entry.getValue().layer(operationId).isEmpty()) {
                continue;
            }
            replacements.put(
                    entry.getKey(),
                    entry.getValue().remove(operationId).updatedStack()
            );
            removedLayers++;
        }
        if (operation == null && removedLayers == 0) {
            return;
        }

        changeListener.run();
        replacements.forEach((position, replacement) -> {
            if (replacement.isPresent()) {
                positions.put(position, replacement.orElseThrow());
            } else {
                positions.remove(position);
            }
        });
        operations.remove(operationId);
        if (operation != null) {
            operation.materializationOwner().ifPresent(owner ->
                    materializations.remove(owner, operationId));
        }
        materializations.entrySet().removeIf(entry ->
                entry.getValue().equals(operationId));
        totalLayerCount = Math.max(0, totalLayerCount - removedLayers);
        revision++;
    }
}
