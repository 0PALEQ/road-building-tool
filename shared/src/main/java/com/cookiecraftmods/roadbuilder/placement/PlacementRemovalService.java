package com.cookiecraftmods.roadbuilder.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class PlacementRemovalService {
        public IncrementalRemovalPlanningResult planIncremental(
            PlacementOperationId operationId,
            RemovalMode mode,
            PlacementBlockWorld world,
            PlacementOwnershipLedger ledger
    ) {
        PreparationResult preparation = prepare(operationId, mode, world, ledger);
        if (preparation instanceof PreparationFailure failure) {
            return new IncrementalRemovalPlanningResult.Failure(
                    failure.code(),
                    failure.positions()
            );
        }
        PreparedRemoval prepared = ((PreparationSuccess) preparation).prepared();
        List<ConstructionJobBlockTransition> transitions = prepared.visibleActions()
                .stream()
                .filter(action -> action.desiredState() != null)
                .filter(action -> !action.observedState().equals(action.desiredState()))
                .map(action -> new ConstructionJobBlockTransition(
                        action.position(),
                        action.observedState(),
                        action.desiredState()
                ))
                .toList();
        return new IncrementalRemovalPlanningResult.Success(
                new IncrementalRemovalPlan(transitions, prepared.summary())
        );
    }

    public PlacementRemovalPreviewResult preview(
            PlacementOperationId operationId,
            RemovalMode mode,
            PlacementBlockWorld world,
            PlacementOwnershipLedger ledger
    ) {
        PreparationResult preparation = prepare(operationId, mode, world, ledger);
        if (preparation instanceof PreparationFailure failure) {
            return new PlacementRemovalPreviewResult.Failure(failure.code(), failure.positions());
        }
        PreparedRemoval prepared = ((PreparationSuccess) preparation).prepared();
        return new PlacementRemovalPreviewResult.Success(operationId, mode, prepared.summary());
    }

    public PlacementRemovalResult remove(
            PlacementOperationId operationId,
            RemovalMode mode,
            PlacementBlockWorld world,
            PlacementOwnershipLedger ledger
    ) {
        PreparationResult preparation = prepare(operationId, mode, world, ledger);
        if (preparation instanceof PreparationFailure failure) {
            return new PlacementRemovalResult.Failure(failure.code(), failure.positions());
        }
        PreparedRemoval prepared = ((PreparationSuccess) preparation).prepared();
        List<AppliedWrite> appliedWrites = new ArrayList<>();

        for (VisibleAction action : prepared.visibleActions()) {
            BlockState current = world.blockState(action.position());
            boolean stillObserved =
                    BlockTransitionStateMatcher.matchesUpdateDrivenTarget(
                            current,
                            action.observedState()
                    );
            if (!stillObserved && action.desiredState() != null
                    && BlockTransitionStateMatcher.matchesRecordedSource(current, action.desiredState())) {
                appliedWrites.add(new AppliedWrite(
                        action.position(),
                        action.observedState()
                ));
                continue;
            }
            if (!stillObserved) {
                return failureAfterRollback(
                        PlacementRemovalFailureCode.WORLD_CHANGED_DURING_REMOVAL,
                        action.position(),
                        world,
                        appliedWrites
                );
            }
            if (action.desiredState() == null || current.equals(action.desiredState())) {
                continue;
            }

            boolean accepted;
            try {
                accepted = world.setBlockStateForRestoration(
                        action.position(),
                        action.desiredState()
                );
            } catch (RuntimeException exception) {
                accepted = false;
            }
            BlockState observedAfter = world.blockState(action.position());
            boolean desiredStateReached =
                    BlockTransitionStateMatcher.matchesRecordedSource(
                            observedAfter,
                            action.desiredState()
                    );
            boolean removedStateGone =
                    !BlockTransitionStateMatcher.matchesUpdateDrivenTarget(
                            observedAfter,
                            action.observedState()
                    );
            if (desiredStateReached || removedStateGone) {
                appliedWrites.add(new AppliedWrite(
                        action.position(),
                        action.observedState()
                ));
                continue;
            }

            if (!observedAfter.equals(action.observedState())) {
                appliedWrites.add(new AppliedWrite(
                    action.position(),
                    action.observedState()
                ));
            }
            PlacementRemovalFailureCode code = accepted
                    ? PlacementRemovalFailureCode.WORLD_WRITE_DID_NOT_STICK
                    : PlacementRemovalFailureCode.WORLD_WRITE_REJECTED;
            return failureAfterRollback(code, action.position(), world, appliedWrites);
        }

        for (VisibleAction action : prepared.visibleActions()) {
            BlockState expectedFinalState = action.desiredState() == null
                    ? action.observedState()
                    : action.desiredState();
            boolean retainedConflict = action.desiredState() == null;
            boolean matches = retainedConflict
                    ? BlockTransitionStateMatcher.matchesPlannedTarget(
                            world.blockState(action.position()),
                            expectedFinalState
                    )
                    : BlockTransitionStateMatcher.matchesRecordedSource(
                            world.blockState(action.position()),
                            expectedFinalState
                    ) || !BlockTransitionStateMatcher.matchesUpdateDrivenTarget(
                            world.blockState(action.position()),
                            action.observedState()
                    );
            if (!matches) {
                return failureAfterRollback(
                        PlacementRemovalFailureCode.WORLD_WRITE_DID_NOT_STICK,
                        action.position(),
                        world,
                        appliedWrites
                );
            }
        }

        try {
            ledger.removeOperation(operationId, prepared.ledgerRevision());
        } catch (RuntimeException exception) {
            return failureAfterRollback(
                    PlacementRemovalFailureCode.LEDGER_REJECTED,
                    null,
                    world,
                    appliedWrites
            );
        }

        return new PlacementRemovalResult.Success(operationId, mode, prepared.summary());
    }

        public PlacementRemovalResult removeBestEffort(
            PlacementOperationId operationId,
            PlacementBlockWorld world,
            PlacementOwnershipLedger ledger
    ) {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(ledger, "ledger");
        if (!world.dimension().equals(ledger.dimension())) {
            return PlacementRemovalResult.Failure.withoutPosition(
                    PlacementRemovalFailureCode.DIMENSION_MISMATCH
            );
        }
        PlacementOperation operation = ledger.operation(operationId)
                .orElse(null);
        if (operation == null) {
            return PlacementRemovalResult.Failure.withoutPosition(
                    PlacementRemovalFailureCode.UNKNOWN_OPERATION
            );
        }

        int restored = 0;
        int unchanged = 0;
        int shadowed = 0;
        for (BlockPos position : operation.positions()) {
            PositionChangeStack stack = ledger.stack(position).orElse(null);
            BlockChangeLayer layer = stack == null
                    ? null
                    : stack.layer(operationId).orElse(null);
            if (layer == null) {
                unchanged++;
                continue;
            }
            if (!stack.isTop(operationId)) {
                shadowed++;
                continue;
            }
            BlockState current = world.blockState(position);
            BlockState target = layer.beforeState();
            if (current.equals(target)) {
                unchanged++;
                continue;
            }
            if (world.hasBlockEntity(position)
                    || current.hasBlockEntity()
                    || target.hasBlockEntity()
                    || !current.getFluidState().isEmpty()) {
                unchanged++;
                continue;
            }
            try {
                world.setBlockStateForRestoration(position, target);
            } catch (RuntimeException ignored) {
                // Logical cleanup below must still make the road removable.
            }
            BlockState observed = world.blockState(position);
            if (BlockTransitionStateMatcher.matchesRecordedSource(
                    observed,
                    target
            ) || !BlockTransitionStateMatcher.matchesPlannedTarget(
                    observed,
                    layer.afterState()
            )) {
                restored++;
            } else {
                unchanged++;
            }
        }
        ledger.discardOperationBestEffort(operationId);
        RemovalSummary summary = new RemovalSummary(
                operation.positions().size(),
                restored,
                unchanged,
                shadowed,
                0,
                0,
                List.of()
        );
        return new PlacementRemovalResult.Success(
                operationId,
                RemovalMode.FORCE,
                summary
        );
    }

    private static PreparationResult prepare(
            PlacementOperationId operationId,
            RemovalMode mode,
            PlacementBlockWorld world,
            PlacementOwnershipLedger ledger
    ) {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(ledger, "ledger");

        if (!world.dimension().equals(ledger.dimension())) {
            return PreparationFailure.withoutPosition(PlacementRemovalFailureCode.DIMENSION_MISMATCH);
        }
        PlacementOperation operation = ledger.operation(operationId).orElse(null);
        if (operation == null) {
            return PreparationFailure.withoutPosition(PlacementRemovalFailureCode.UNKNOWN_OPERATION);
        }

        long ledgerRevision = ledger.revision();
        List<VisibleAction> actions = new ArrayList<>();
        List<BlockPos> conflicts = new ArrayList<>();
        int restored = 0;
        int unchanged = 0;
        int shadowed = 0;
        int forcedConflicts = 0;

        for (BlockPos position : operation.positions()) {
            PositionChangeStack stack = ledger.stack(position).orElse(null);
            BlockChangeLayer layer = stack == null ? null : stack.layer(operationId).orElse(null);
            if (layer == null) {
                return PreparationFailure.at(PlacementRemovalFailureCode.CORRUPT_OWNERSHIP, position);
            }
            if (!stack.isTop(operationId)) {
                shadowed++;
                continue;
            }

            BlockState current = world.blockState(position);
            boolean expectedStatePresent =
                    BlockTransitionStateMatcher.matchesUpdateDrivenTarget(
                            current,
                            layer.afterState()
                    );
            if (!expectedStatePresent && mode == RemovalMode.SAFE) {
                conflicts.add(position);
                actions.add(new VisibleAction(position, current, null));
                continue;
            }
            if (!expectedStatePresent) {
                if (world.hasBlockEntity(position) || current.hasBlockEntity()) {
                    return PreparationFailure.at(
                            PlacementRemovalFailureCode.FORCE_CURRENT_BLOCK_ENTITY_UNSUPPORTED,
                            position
                    );
                }
                if (!current.getFluidState().isEmpty()) {
                    return PreparationFailure.at(
                            PlacementRemovalFailureCode.FORCE_CURRENT_FLUID_UNSUPPORTED,
                            position
                    );
                }
                forcedConflicts++;
            }

            BlockState desiredState = layer.beforeState();
            actions.add(new VisibleAction(position, current, desiredState));
            if (current.equals(desiredState)) {
                unchanged++;
            } else {
                restored++;
            }
        }

        RemovalSummary summary = new RemovalSummary(
                operation.positions().size(),
                restored,
                unchanged,
                shadowed,
                conflicts.size(),
                forcedConflicts,
                conflicts
        );
        return new PreparationSuccess(new PreparedRemoval(ledgerRevision, List.copyOf(actions), summary));
    }

    private static PlacementRemovalResult.Failure failureAfterRollback(
            PlacementRemovalFailureCode originalCode,
            BlockPos originalPosition,
            PlacementBlockWorld world,
            List<AppliedWrite> appliedWrites
    ) {
        List<BlockPos> rollbackFailures = rollback(world, appliedWrites);
        if (!rollbackFailures.isEmpty()) {
            return new PlacementRemovalResult.Failure(
                    PlacementRemovalFailureCode.ROLLBACK_INCOMPLETE,
                    rollbackFailures
            );
        }
        return originalPosition == null
                ? PlacementRemovalResult.Failure.withoutPosition(originalCode)
                : PlacementRemovalResult.Failure.at(originalCode, originalPosition);
    }

    private static List<BlockPos> rollback(
            PlacementBlockWorld world,
            List<AppliedWrite> appliedWrites
    ) {
        List<BlockPos> failures = new ArrayList<>();
        for (int index = appliedWrites.size() - 1; index >= 0; index--) {
            AppliedWrite write = appliedWrites.get(index);
            BlockState current = world.blockState(write.position());
            if (current.equals(write.observedBefore())) {
                continue;
            }

            boolean accepted;
            try {
                accepted = world.setBlockStateForRestoration(
                        write.position(),
                        write.observedBefore()
                );
            } catch (RuntimeException exception) {
                accepted = false;
            }
            if (!accepted || !BlockTransitionStateMatcher.matchesPlannedTarget(
                    world.blockState(write.position()),
                    write.observedBefore()
            )) {
                failures.add(write.position());
            }
        }
        Collections.reverse(failures);
        return List.copyOf(failures);
    }

    private sealed interface PreparationResult {
    }

    private record PreparationSuccess(PreparedRemoval prepared) implements PreparationResult {
    }

    private record PreparationFailure(
            PlacementRemovalFailureCode code,
            List<BlockPos> positions
    ) implements PreparationResult {
        private static PreparationFailure at(PlacementRemovalFailureCode code, BlockPos position) {
            return new PreparationFailure(code, List.of(position.immutable()));
        }

        private static PreparationFailure withoutPosition(PlacementRemovalFailureCode code) {
            return new PreparationFailure(code, List.of());
        }
    }

    private record PreparedRemoval(
            long ledgerRevision,
            List<VisibleAction> visibleActions,
            RemovalSummary summary
    ) {
    }

    private record VisibleAction(BlockPos position, BlockState observedState, BlockState desiredState) {
    }

    private record AppliedWrite(BlockPos position, BlockState observedBefore) {
    }
}
