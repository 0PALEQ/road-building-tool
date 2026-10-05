package com.cookiecraftmods.roadbuilder.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public final class BlockPlanCommitter {
        private static final Comparator<PlannedBlock> SAFE_WRITE_ORDER =
            Comparator.comparingInt(
                    (PlannedBlock block) -> block.position().getY()
            ).reversed();

    private final Supplier<PlacementOperationId> operationIds;

    public BlockPlanCommitter() {
        this(PlacementOperationId::create);
    }

    public BlockPlanCommitter(Supplier<PlacementOperationId> operationIds) {
        this.operationIds = Objects.requireNonNull(operationIds, "operationIds");
    }

        public PlacementOperationId nextOperationId() {
        return Objects.requireNonNull(
                operationIds.get(),
                "operationIds returned null"
        );
    }

    public PlacementCommitResult commit(
            BlockPlacementPlan plan,
            PlacementBlockWorld world,
            PlacementOwnershipLedger ledger
    ) {
        return commit(
                nextOperationId(),
                null,
                plan,
                world,
                ledger,
                false
        );
    }

    public PlacementCommitResult commitOwned(
            RoadMaterializationOwner owner,
            BlockPlacementPlan plan,
            PlacementBlockWorld world,
            PlacementOwnershipLedger ledger
    ) {
        return commit(
                nextOperationId(),
                Objects.requireNonNull(owner, "owner"),
                plan,
                world,
                ledger,
                false
        );
    }

    public PlacementCommitResult commit(
            PlacementOperationId operationId,
            BlockPlacementPlan plan,
            PlacementBlockWorld world,
            PlacementOwnershipLedger ledger
    ) {
        return commit(operationId, null, plan, world, ledger, false);
    }

    public PlacementCommitResult commit(
            PlacementOperationId operationId,
            RoadMaterializationOwner owner,
            BlockPlacementPlan plan,
            PlacementBlockWorld world,
            PlacementOwnershipLedger ledger
    ) {
        return commit(operationId, owner, plan, world, ledger, false);
    }

        public PlacementCommitResult commitRestorationOverlay(
            PlacementOperationId operationId,
            BlockPlacementPlan plan,
            PlacementBlockWorld world,
            PlacementOwnershipLedger ledger
    ) {
        return commit(operationId, null, plan, world, ledger, true);
    }

    private PlacementCommitResult commit(
            PlacementOperationId operationId,
            RoadMaterializationOwner owner,
            BlockPlacementPlan plan,
            PlacementBlockWorld world,
            PlacementOwnershipLedger ledger,
            boolean restorationOverlay
    ) {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(ledger, "ledger");

        if (!plan.dimension().equals(world.dimension()) || !plan.dimension().equals(ledger.dimension())) {
            return PlacementCommitResult.Failure.withoutPosition(
                    PlacementCommitFailureCode.DIMENSION_MISMATCH
            );
        }
        if (ledger.containsOperation(operationId)) {
            return PlacementCommitResult.Failure.withoutPosition(
                    PlacementCommitFailureCode.DUPLICATE_OPERATION_ID
            );
        }
        if (!ledger.canRecord(plan)) {
            return PlacementCommitResult.Failure.withoutPosition(
                    PlacementCommitFailureCode.OWNERSHIP_LIMIT_EXCEEDED
            );
        }
        long ledgerRevision = ledger.revision();

        Map<BlockPos, BlockState> beforeStates = new LinkedHashMap<>(plan.affectedBlockCount());
        for (PlannedBlock block : plan.blocks()) {
            BlockPos position = block.position();
            BlockState beforeState = world.blockState(position);
            PlacementCommitFailureCode unsupported = unsupportedTransition(world, position, beforeState, block.targetState());
            if (unsupported != null) {
                return PlacementCommitResult.Failure.at(unsupported, position);
            }
            beforeStates.put(position, beforeState);
        }

        List<PlannedBlock> writeOrder = new ArrayList<>(plan.blocks());
        writeOrder.sort(SAFE_WRITE_ORDER);
        List<AppliedWrite> appliedWrites = new ArrayList<>();
        for (PlannedBlock block : writeOrder) {
            BlockPos position = block.position();
            BlockState beforeState = beforeStates.get(position);
            BlockState currentState = world.blockState(position);
            if (!BlockTransitionStateMatcher.matchesRecordedSource(
                    currentState,
                    beforeState
            )) {
                /*
                 * ServerLevel writes synchronously run neighbor callbacks.
                 * An earlier write in this transaction may therefore have
                 * already produced this later target. Adopt that result while
                 * retaining the preflight state for exact restoration.
                 */
                if (BlockTransitionStateMatcher.matchesPlannedTarget(
                        currentState,
                        block.targetState()
                ) || restorationOverlay) {
                    appliedWrites.add(new AppliedWrite(
                            position,
                            beforeState,
                            currentState
                    ));
                    continue;
                }
                return failureAfterRollback(
                        PlacementCommitFailureCode.WORLD_CHANGED_DURING_COMMIT,
                        position,
                        world,
                        appliedWrites,
                        restorationOverlay
                );
            }
            if (BlockTransitionStateMatcher.matchesPlannedTarget(
                    currentState,
                    block.targetState()
            )) {
                if (!beforeState.equals(block.targetState())) {
                    appliedWrites.add(new AppliedWrite(
                            position,
                            beforeState,
                            currentState
                    ));
                }
                continue;
            }

            boolean accepted;
            try {
                accepted = restorationOverlay
                        ? world.setBlockStateForRestoration(
                                position,
                                block.targetState()
                        )
                        : world.setBlockState(position, block.targetState());
            } catch (RuntimeException exception) {
                accepted = false;
            }
            BlockState observed = world.blockState(position);
            if (BlockTransitionStateMatcher.matchesPlannedTarget(
                    observed,
                    block.targetState()
            ) || restorationOverlay && (
                    BlockTransitionStateMatcher.matchesRecordedSource(
                            observed,
                            block.targetState()
                    ) || !BlockTransitionStateMatcher.matchesPlannedTarget(
                            observed,
                            beforeState
                    )
            )) {
                appliedWrites.add(new AppliedWrite(position, beforeState, observed));
                continue;
            }

            if (!observed.equals(beforeState)) {
                appliedWrites.add(new AppliedWrite(position, beforeState, observed));
            }
            PlacementCommitFailureCode code = accepted
                    ? PlacementCommitFailureCode.WORLD_WRITE_DID_NOT_STICK
                    : PlacementCommitFailureCode.WORLD_WRITE_REJECTED;
            return failureAfterRollback(
                    code,
                    position,
                    world,
                    appliedWrites,
                    restorationOverlay
            );
        }

        Map<BlockPos, BlockState> committedStates = new LinkedHashMap<>(
                plan.affectedBlockCount()
        );
        for (PlannedBlock block : plan.blocks()) {
            BlockState committedState = world.blockState(block.position());
            boolean targetPresent =
                    BlockTransitionStateMatcher.matchesPlannedTarget(
                            committedState,
                            block.targetState()
                    );
            boolean sourceStillPresent =
                    BlockTransitionStateMatcher.matchesPlannedTarget(
                            committedState,
                            beforeStates.get(block.position())
                    );
            boolean updateDrivenTargetPresent = restorationOverlay
                    && BlockTransitionStateMatcher.matchesRecordedSource(
                            committedState,
                            block.targetState()
                    );
            if (!targetPresent
                    && !updateDrivenTargetPresent
                    && (!restorationOverlay || sourceStillPresent)) {
                return failureAfterRollback(
                        PlacementCommitFailureCode.WORLD_WRITE_DID_NOT_STICK,
                        block.position(),
                        world,
                        appliedWrites,
                        restorationOverlay
                );
            }
            committedStates.put(block.position(), committedState);
        }

        BlockPlacementPlan committedPlan = BlockPlacementPlan.create(
                plan.dimension(),
                plan.blocks().stream()
                        .map(block -> new PlannedBlock(
                                block.position(),
                                committedStates.get(block.position())
                        ))
                        .toList()
        );

        try {
            if (owner == null) {
                ledger.recordPlacement(
                        operationId,
                        committedPlan,
                        beforeStates,
                        ledgerRevision
                );
            } else {
                ledger.recordPlacement(
                        operationId,
                        owner,
                        committedPlan,
                        beforeStates,
                        ledgerRevision
                );
            }
        } catch (RuntimeException exception) {
            return failureAfterRollback(
                    PlacementCommitFailureCode.LEDGER_REJECTED,
                    null,
                    world,
                    appliedWrites,
                    restorationOverlay
            );
        }

        PlacementOperation operation = ledger.operation(operationId).orElseThrow();
        return new PlacementCommitResult.Success(operation, appliedWrites.size());
    }

    private static PlacementCommitFailureCode unsupportedTransition(
            PlacementBlockWorld world,
            BlockPos position,
            BlockState beforeState,
            BlockState targetState
    ) {
        if (world.hasBlockEntity(position) || beforeState.hasBlockEntity()) {
            return PlacementCommitFailureCode.CURRENT_BLOCK_ENTITY_UNSUPPORTED;
        }
        if (targetState.hasBlockEntity()) {
            return PlacementCommitFailureCode.TARGET_BLOCK_ENTITY_UNSUPPORTED;
        }
        return null;
    }

    private static PlacementCommitResult.Failure failureAfterRollback(
            PlacementCommitFailureCode originalCode,
            BlockPos originalPosition,
            PlacementBlockWorld world,
            List<AppliedWrite> appliedWrites,
            boolean restorationOverlay
    ) {
        List<BlockPos> rollbackFailures = rollback(
                world,
                appliedWrites,
                restorationOverlay
        );
        if (!rollbackFailures.isEmpty()) {
            return new PlacementCommitResult.Failure(
                    PlacementCommitFailureCode.ROLLBACK_INCOMPLETE,
                    rollbackFailures
            );
        }
        return originalPosition == null
                ? PlacementCommitResult.Failure.withoutPosition(originalCode)
                : PlacementCommitResult.Failure.at(originalCode, originalPosition);
    }

    private static List<BlockPos> rollback(
            PlacementBlockWorld world,
            List<AppliedWrite> appliedWrites,
            boolean restorationOverlay
    ) {
        List<BlockPos> failures = new ArrayList<>();
        for (int index = appliedWrites.size() - 1; index >= 0; index--) {
            AppliedWrite write = appliedWrites.get(index);
            BlockState current = world.blockState(write.position());
            if (current.equals(write.beforeState())) {
                continue;
            }
            if (!restorationOverlay
                    && !BlockTransitionStateMatcher.matchesPlannedTarget(
                            current,
                            write.afterState()
                    )) {
                failures.add(write.position());
                continue;
            }

            boolean accepted;
            try {
                accepted = world.setBlockState(write.position(), write.beforeState());
            } catch (RuntimeException exception) {
                accepted = false;
            }
            if (!accepted || !BlockTransitionStateMatcher.matchesRecordedSource(
                    world.blockState(write.position()),
                    write.beforeState()
            )) {
                failures.add(write.position());
            }
        }
        return List.copyOf(failures);
    }

    private record AppliedWrite(BlockPos position, BlockState beforeState, BlockState afterState) {
    }
}
