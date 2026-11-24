package com.cookiecraftmods.roadbuilder.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;

public final class ConstructionJobBatchProcessor {
    public static final int DEFAULT_TRANSITIONS_PER_TICK = 256;

    private final int maximumTransitions;

    public ConstructionJobBatchProcessor() {
        this(DEFAULT_TRANSITIONS_PER_TICK);
    }

    public ConstructionJobBatchProcessor(int maximumTransitions) {
        if (maximumTransitions <= 0) {
            throw new IllegalArgumentException(
                    "maximumTransitions must be greater than zero"
            );
        }
        this.maximumTransitions = maximumTransitions;
    }

    public ConstructionJobBatchResult process(
            ConstructionJob suppliedJob,
            PlacementBlockWorld world
    ) {
        ConstructionJob job = Objects.requireNonNull(suppliedJob, "suppliedJob");
        Objects.requireNonNull(world, "world");
        if (job.phase() == ConstructionJobPhase.PREPARED) {
            job = job.start();
        }
        return switch (job.phase()) {
            case APPLYING -> apply(job, world);
            case CANCELLING -> rollback(job, world);
            case PREPARED -> throw new IllegalStateException(
                    "prepared job did not enter applying"
            );
            case COMPLETING, CANCELLED, QUARANTINED ->
                    new ConstructionJobBatchResult(job, 0, 0);
        };
    }

    private ConstructionJobBatchResult apply(
            ConstructionJob job,
            PlacementBlockWorld world
    ) {
        int remaining = job.transitions().size() - job.cursor();
        int examined = Math.min(maximumTransitions, remaining);
        int changed = 0;
        for (int offset = 0; offset < examined; offset++) {
            ConstructionJobBlockTransition transition = job.transitions().get(
                    job.cursor() + offset
            );
            TransitionResult result = applyTransition(
                    transition,
                    world,
                    job.action() == ConstructionJobAction.REMOVE
            );
            if (result.diagnostic() != null) {
                ConstructionJob advanced = offset == 0 ? job : job.advance(offset);
                return new ConstructionJobBatchResult(
                        advanced.quarantine(
                                transition.position(),
                                result.diagnostic()
                        ),
                        offset + 1,
                        changed
                );
            }
            if (result.changed()) {
                changed++;
            }
        }
        return new ConstructionJobBatchResult(
                job.advance(examined),
                examined,
                changed
        );
    }

    private ConstructionJobBatchResult rollback(
            ConstructionJob job,
            PlacementBlockWorld world
    ) {
        int examined = Math.min(maximumTransitions, job.cursor());
        int changed = 0;
        for (int offset = 0; offset < examined; offset++) {
            int index = job.cursor() - 1 - offset;
            ConstructionJobBlockTransition transition = job.transitions().get(index);
            TransitionResult result = rollbackTransition(
                    transition,
                    world,
                    job.action() == ConstructionJobAction.PLACE
            );
            if (result.diagnostic() != null) {
                ConstructionJob rolledBack = offset == 0 ? job : job.rollback(offset);
                return new ConstructionJobBatchResult(
                        rolledBack.quarantine(
                                transition.position(),
                                result.diagnostic()
                        ),
                        offset + 1,
                        changed
                );
            }
            if (result.changed()) {
                changed++;
            }
        }
        return new ConstructionJobBatchResult(
                job.rollback(examined),
                examined,
                changed
        );
    }

    private static TransitionResult applyTransition(
            ConstructionJobBlockTransition transition,
            PlacementBlockWorld world,
            boolean removal
    ) {
        BlockPos position = transition.position();
        BlockState current = world.blockState(position);
        boolean updatedTarget = BlockTransitionStateMatcher.matchesUpdateDrivenTarget(
                current, transition.afterState());
        // During removal, a refreshed state may match both the road and the original terrain.
        boolean stillOwned = removal && BlockTransitionStateMatcher.matchesUpdateDrivenTarget(
                current, transition.beforeState());
        if (current.equals(transition.afterState()) || (updatedTarget && !stillOwned)) {
            return TransitionResult.unchangedResult();
        }
        if (BlockTransitionStateMatcher.matchesRecordedSource(
                current,
                transition.beforeState()
        )) {
            String unsupported = unsupportedWorldState(
                    position,
                    current,
                    transition.afterState(),
                    world
            );
            if (unsupported != null) {
                return TransitionResult.failed(unsupported);
            }
            return write(
                    position,
                    transition.beforeState(),
                    transition.afterState(),
                    world,
                    removal
            );
        }
        if (removal
                && BlockTransitionStateMatcher.matchesRecordedSource(
                        current,
                        transition.afterState()
                )) {
            return TransitionResult.unchangedResult();
        }
        return TransitionResult.failed(
                "world state matches neither before nor after: current="
                        + blockId(current)
                        + ", before=" + blockId(transition.beforeState())
                        + ", after=" + blockId(transition.afterState())
        );
    }

    private static TransitionResult rollbackTransition(
            ConstructionJobBlockTransition transition,
            PlacementBlockWorld world,
            boolean preserveThirdState
    ) {
        BlockPos position = transition.position();
        BlockState current = world.blockState(position);
        if (BlockTransitionStateMatcher.matchesPlannedTarget(
                current,
                transition.beforeState()
        )) {
            return TransitionResult.unchangedResult();
        }
        if (BlockTransitionStateMatcher.matchesPlannedTarget(
                current,
                transition.afterState()
        ) || preserveThirdState
                && BlockTransitionStateMatcher.matchesUpdateDrivenTarget(
                        current,
                        transition.afterState()
                )) {
            return restoreTransition(transition, world, current);
        }
        if (BlockTransitionStateMatcher.matchesRecordedSource(
                current,
                transition.beforeState()
        )) {
            return TransitionResult.unchangedResult();
        }
        if (BlockTransitionStateMatcher.matchesRecordedSource(
                current,
                transition.afterState()
        )) {
            return restoreTransition(transition, world, current);
        }
        if (preserveThirdState) {
            /*
             * A failed placement has never published ownership or graph
             * state. Preserve an external edit at this position, but keep
             * walking the prefix so unrelated road target blocks cannot
             * remain as undiscoverable fragments.
             */
            return TransitionResult.unchangedResult();
        }
        return TransitionResult.failed(
                "world state matches neither applied nor restored state"
        );
    }

    private static TransitionResult restoreTransition(
            ConstructionJobBlockTransition transition,
            PlacementBlockWorld world,
            BlockState current
    ) {
        BlockPos position = transition.position();
        String unsupported = unsupportedWorldState(
                position,
                current,
                transition.beforeState(),
                world
        );
        if (unsupported != null) {
            return TransitionResult.failed(unsupported);
        }
        return writeRestoration(position, transition.beforeState(), world);
    }

    private static String unsupportedWorldState(
            BlockPos position,
            BlockState current,
            BlockState target,
            PlacementBlockWorld world
    ) {
        if (world.hasBlockEntity(position) || current.hasBlockEntity()
                || target.hasBlockEntity()) {
            return "block entity transition is unsupported";
        }
        return null;
    }

    private static TransitionResult write(
            BlockPos position,
            BlockState source,
            BlockState target,
            PlacementBlockWorld world,
            boolean removal
    ) {
        boolean accepted;
        try {
            accepted = removal
                    ? world.setBlockStateForRestoration(position, target)
                    : world.setBlockStateForConstruction(position, target);
        } catch (RuntimeException exception) {
            return TransitionResult.failed("world write threw an exception");
        }
        BlockState observed = world.blockState(position);
        if ((!removal
                ? BlockTransitionStateMatcher.matchesUpdateDrivenTarget(
                        observed,
                        target
                )
                : BlockTransitionStateMatcher.matchesPlannedTarget(
                        observed,
                        target
                ))) {
            return TransitionResult.changedResult();
        }
        if (removal && (
                BlockTransitionStateMatcher.matchesRecordedSource(observed, target)
                        || !BlockTransitionStateMatcher.matchesUpdateDrivenTarget(
                                observed,
                                source
                        )
        )) {
            /*
             * setBlock runs neighbor callbacks synchronously. Restored plants,
             * grass, farmland, and modded blocks may immediately disappear or
             * transform. A state compatible with that update-driven target,
             * or any state other than the road/source state, is a valid
             * restoration outcome rather than a failed demolition.
             */
            return TransitionResult.changedResult();
        }
        return TransitionResult.failed(
                accepted
                        ? removal
                                ? "world write left the removed state in place"
                                : "world write did not retain its target state"
                        : "world rejected the block write"
        );
    }

    private static TransitionResult writeRestoration(
            BlockPos position,
            BlockState target,
            PlacementBlockWorld world
    ) {
        boolean accepted;
        try {
            accepted = world.setBlockStateForRestoration(position, target);
        } catch (RuntimeException exception) {
            return TransitionResult.failed("world restoration threw an exception");
        }
        if (!accepted) {
            return TransitionResult.failed("world rejected the restoration write");
        }
        if (!BlockTransitionStateMatcher.matchesRecordedSource(
                world.blockState(position),
                target
        )) {
            return TransitionResult.failed(
                    "world restoration did not retain its target state"
            );
        }
        return TransitionResult.changedResult();
    }

    private static String blockId(BlockState state) {
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        return id.substring(0, Math.min(id.length(), 64));
    }

    private record TransitionResult(boolean changed, String diagnostic) {
        private static TransitionResult unchangedResult() {
            return new TransitionResult(false, null);
        }

        private static TransitionResult changedResult() {
            return new TransitionResult(true, null);
        }

        private static TransitionResult failed(String diagnostic) {
            return new TransitionResult(false, diagnostic);
        }
    }
}
