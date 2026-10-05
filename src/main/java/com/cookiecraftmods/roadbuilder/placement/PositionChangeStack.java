package com.cookiecraftmods.roadbuilder.placement;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class PositionChangeStack {
    private final List<BlockChangeLayer> layers;

    private PositionChangeStack(List<BlockChangeLayer> layers) {
        this.layers = layers;
    }

    public static PositionChangeStack of(BlockChangeLayer layer) {
        return new PositionChangeStack(List.of(Objects.requireNonNull(layer, "layer")));
    }

    public static PositionChangeStack restore(List<BlockChangeLayer> suppliedLayers) {
        Objects.requireNonNull(suppliedLayers, "suppliedLayers");
        if (suppliedLayers.isEmpty()) {
            throw new IllegalArgumentException("A position stack must not be empty");
        }

        Set<PlacementOperationId> operationIds = new HashSet<>();
        List<BlockChangeLayer> copied = new ArrayList<>(suppliedLayers.size());
        for (BlockChangeLayer layer : suppliedLayers) {
            BlockChangeLayer nonNullLayer = Objects.requireNonNull(layer, "suppliedLayers contains null");
            if (!operationIds.add(nonNullLayer.operationId())) {
                throw new IllegalArgumentException(
                        "Operation appears more than once in a position stack: " + nonNullLayer.operationId()
                );
            }
            copied.add(nonNullLayer);
        }
        return new PositionChangeStack(List.copyOf(copied));
    }

    public List<BlockChangeLayer> layers() {
        return layers;
    }

    public BlockChangeLayer top() {
        return layers.get(layers.size() - 1);
    }

    public Optional<BlockChangeLayer> layer(PlacementOperationId operationId) {
        int index = indexOf(Objects.requireNonNull(operationId, "operationId"));
        return index < 0 ? Optional.empty() : Optional.of(layers.get(index));
    }

    public boolean isTop(PlacementOperationId operationId) {
        return top().operationId().equals(Objects.requireNonNull(operationId, "operationId"));
    }

    public PositionChangeStack push(BlockChangeLayer layer) {
        Objects.requireNonNull(layer, "layer");
        if (indexOf(layer.operationId()) >= 0) {
            throw new IllegalArgumentException(
                    "Operation already appears in this position stack: " + layer.operationId()
            );
        }
        List<BlockChangeLayer> updated = new ArrayList<>(layers.size() + 1);
        updated.addAll(layers);
        updated.add(layer);
        return new PositionChangeStack(List.copyOf(updated));
    }

        public StackRemoval remove(PlacementOperationId operationId) {
        Objects.requireNonNull(operationId, "operationId");
        int index = indexOf(operationId);
        if (index < 0) {
            throw new IllegalArgumentException("Operation is absent from position stack: " + operationId);
        }

        List<BlockChangeLayer> updated = new ArrayList<>(layers);
        BlockChangeLayer removed = updated.get(index);
        boolean wasTop = index == updated.size() - 1;
        boolean successorRebased = false;
        if (!wasTop) {
            BlockChangeLayer successor = updated.get(index + 1);
            if (BlockTransitionStateMatcher.matchesRecordedSource(
                    successor.beforeState(),
                    removed.afterState()
            )) {
                updated.set(index + 1, successor.withBeforeState(removed.beforeState()));
                successorRebased = true;
            }
        }
        updated.remove(index);

        Optional<PositionChangeStack> resultingStack = updated.isEmpty()
                ? Optional.empty()
                : Optional.of(new PositionChangeStack(List.copyOf(updated)));
        return new StackRemoval(resultingStack, removed, wasTop, successorRebased);
    }

    PositionChangeStack replace(
            PlacementOperationId operationId,
            List<BlockChangeLayer> replacementLayers
    ) {
        Objects.requireNonNull(operationId, "operationId");
        replacementLayers = List.copyOf(Objects.requireNonNull(
                replacementLayers,
                "replacementLayers"
        ));
        if (replacementLayers.isEmpty()) {
            throw new IllegalArgumentException("replacementLayers must not be empty");
        }
        int index = indexOf(operationId);
        if (index < 0) {
            throw new IllegalArgumentException("Operation is absent from position stack");
        }

        Set<PlacementOperationId> ids = new HashSet<>();
        for (int current = 0; current < layers.size(); current++) {
            if (current != index) {
                ids.add(layers.get(current).operationId());
            }
        }
        for (BlockChangeLayer layer : replacementLayers) {
            if (!ids.add(Objects.requireNonNull(layer, "replacementLayers contains null")
                    .operationId())) {
                throw new IllegalArgumentException(
                        "Replacement operation already appears in the position stack"
                );
            }
        }

        List<BlockChangeLayer> updated =
                new ArrayList<>(layers.size() - 1 + replacementLayers.size());
        updated.addAll(layers.subList(0, index));
        updated.addAll(replacementLayers);
        updated.addAll(layers.subList(index + 1, layers.size()));
        return new PositionChangeStack(List.copyOf(updated));
    }

    private int indexOf(PlacementOperationId operationId) {
        for (int index = 0; index < layers.size(); index++) {
            if (layers.get(index).operationId().equals(operationId)) {
                return index;
            }
        }
        return -1;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof PositionChangeStack stack && layers.equals(stack.layers);
    }

    @Override
    public int hashCode() {
        return layers.hashCode();
    }

    @Override
    public String toString() {
        return "PositionChangeStack" + layers;
    }
}
