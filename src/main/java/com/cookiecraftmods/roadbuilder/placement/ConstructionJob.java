package com.cookiecraftmods.roadbuilder.placement;

import net.minecraft.core.BlockPos;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record ConstructionJob(
        UUID id,
        UUID requestedBy,
        ConstructionJobAction action,
        ConstructionJobPhase phase,
        List<ConstructionJobBlockTransition> transitions,
        int cursor,
        Optional<BlockPos> conflictPosition,
        Optional<String> diagnostic,
        Optional<IncrementalPlacementMetadata> placementMetadata
) {
    public static final int MAXIMUM_DIAGNOSTIC_LENGTH = 256;

    public ConstructionJob {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(requestedBy, "requestedBy");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(phase, "phase");
        transitions = List.copyOf(Objects.requireNonNull(transitions, "transitions"));
        if (transitions.isEmpty()) {
            throw new IllegalArgumentException("a construction job needs transitions");
        }
        Set<BlockPos> positions = new HashSet<>(transitions.size());
        for (ConstructionJobBlockTransition transition : transitions) {
            ConstructionJobBlockTransition value = Objects.requireNonNull(
                    transition,
                    "transitions contains null"
            );
            if (!positions.add(value.position())) {
                throw new IllegalArgumentException(
                        "duplicate job transition at " + value.position().toShortString()
                );
            }
        }
        if (cursor < 0 || cursor > transitions.size()) {
            throw new IllegalArgumentException("job cursor is outside its transition list");
        }
        conflictPosition = Objects.requireNonNull(
                conflictPosition,
                "conflictPosition"
        ).map(BlockPos::immutable);
        diagnostic = Objects.requireNonNull(diagnostic, "diagnostic")
                .map(String::strip);
        placementMetadata = Objects.requireNonNull(
                placementMetadata,
                "placementMetadata"
        );
        if (diagnostic.filter(String::isEmpty).isPresent()) {
            throw new IllegalArgumentException("job diagnostic must not be blank");
        }
        if (diagnostic.map(String::length).orElse(0) > MAXIMUM_DIAGNOSTIC_LENGTH) {
            throw new IllegalArgumentException(
                    "job diagnostic exceeds " + MAXIMUM_DIAGNOSTIC_LENGTH + " characters"
            );
        }
        if (phase == ConstructionJobPhase.PREPARED && cursor != 0) {
            throw new IllegalArgumentException("a prepared job must have cursor zero");
        }
        if (phase == ConstructionJobPhase.COMPLETING && cursor != transitions.size()) {
            throw new IllegalArgumentException(
                    "a completing job must have applied every transition"
            );
        }
        if (phase == ConstructionJobPhase.CANCELLED && cursor != 0) {
            throw new IllegalArgumentException("a cancelled job must have cursor zero");
        }
        boolean quarantined = phase == ConstructionJobPhase.QUARANTINED;
        if (quarantined != diagnostic.isPresent()) {
            throw new IllegalArgumentException(
                    "only quarantined jobs carry a diagnostic"
            );
        }
        if (!quarantined && conflictPosition.isPresent()) {
            throw new IllegalArgumentException(
                    "only quarantined jobs carry a conflict position"
            );
        }
        if (action == ConstructionJobAction.REMOVE
                && placementMetadata.isPresent()) {
            throw new IllegalArgumentException(
                    "removal jobs cannot carry placement publication metadata"
            );
        }
    }

        public ConstructionJob(
            UUID id,
            UUID requestedBy,
            ConstructionJobAction action,
            ConstructionJobPhase phase,
            List<ConstructionJobBlockTransition> transitions,
            int cursor,
            Optional<BlockPos> conflictPosition,
            Optional<String> diagnostic
    ) {
        this(
                id,
                requestedBy,
                action,
                phase,
                transitions,
                cursor,
                conflictPosition,
                diagnostic,
                Optional.empty()
        );
    }

    public static ConstructionJob prepared(
            UUID id,
            UUID requestedBy,
            ConstructionJobAction action,
            List<ConstructionJobBlockTransition> transitions
    ) {
        return new ConstructionJob(
                id,
                requestedBy,
                action,
                ConstructionJobPhase.PREPARED,
                transitions,
                0,
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
    }

    public static ConstructionJob preparedPlacement(
            UUID id,
            UUID requestedBy,
            List<ConstructionJobBlockTransition> transitions,
            IncrementalPlacementMetadata metadata
    ) {
        return new ConstructionJob(
                id,
                requestedBy,
                ConstructionJobAction.PLACE,
                ConstructionJobPhase.PREPARED,
                transitions,
                0,
                Optional.empty(),
                Optional.empty(),
                Optional.of(Objects.requireNonNull(metadata, "metadata"))
        );
    }

    public ConstructionJob start() {
        requirePhase(ConstructionJobPhase.PREPARED);
        return with(ConstructionJobPhase.APPLYING, cursor);
    }

    public ConstructionJob advance(int transitionCount) {
        requirePhase(ConstructionJobPhase.APPLYING);
        if (transitionCount <= 0 || transitionCount > transitions.size() - cursor) {
            throw new IllegalArgumentException(
                    "advance count must fit the remaining transitions"
            );
        }
        int nextCursor = cursor + transitionCount;
        ConstructionJobPhase nextPhase = nextCursor == transitions.size()
                ? ConstructionJobPhase.COMPLETING
                : ConstructionJobPhase.APPLYING;
        return with(nextPhase, nextCursor);
    }

    public ConstructionJob requestCancellation() {
        return switch (phase) {
            case PREPARED -> with(ConstructionJobPhase.CANCELLED, 0);
            case APPLYING -> cursor == 0
                    ? with(ConstructionJobPhase.CANCELLED, 0)
                    : with(ConstructionJobPhase.CANCELLING, cursor);
            case CANCELLING, CANCELLED -> this;
            case COMPLETING, QUARANTINED -> throw new IllegalStateException(
                    "job cannot be cancelled while " + phase.serializedName()
            );
        };
    }

    public ConstructionJob rollback(int transitionCount) {
        requirePhase(ConstructionJobPhase.CANCELLING);
        if (transitionCount <= 0 || transitionCount > cursor) {
            throw new IllegalArgumentException(
                    "rollback count must fit the applied transition prefix"
            );
        }
        int nextCursor = cursor - transitionCount;
        return with(
                nextCursor == 0
                        ? ConstructionJobPhase.CANCELLED
                        : ConstructionJobPhase.CANCELLING,
                nextCursor
        );
    }

    public ConstructionJob quarantine(BlockPos position, String reason) {
        Objects.requireNonNull(reason, "reason");
        return new ConstructionJob(
                id,
                requestedBy,
                action,
                ConstructionJobPhase.QUARANTINED,
                transitions,
                cursor,
                Optional.ofNullable(position),
                Optional.of(reason),
                placementMetadata
        );
    }

        public ConstructionJob rollbackFailedPlacement() {
        if (action != ConstructionJobAction.PLACE
                || phase != ConstructionJobPhase.QUARANTINED) {
            throw new IllegalStateException(
                    "only a quarantined placement can enter failed-placement cleanup"
            );
        }
        return with(
                transitions.isEmpty()
                        ? ConstructionJobPhase.CANCELLED
                        : ConstructionJobPhase.CANCELLING,
                transitions.size()
        );
    }

    ConstructionJob resumeQuarantinedRemoval(int nextCursor) {
        if (action != ConstructionJobAction.REMOVE
                || phase != ConstructionJobPhase.QUARANTINED) {
            throw new IllegalStateException(
                    "only a quarantined removal can be resumed"
            );
        }
        if (nextCursor < cursor
                || nextCursor > transitions.size()
                || nextCursor > cursor + 1) {
            throw new IllegalArgumentException(
                    "a recovered removal may retry or accept only its current transition"
            );
        }
        return with(
                nextCursor == transitions.size()
                        ? ConstructionJobPhase.COMPLETING
                        : ConstructionJobPhase.APPLYING,
                nextCursor
        );
    }

    ConstructionJob completeOrphanedRemoval() {
        if (action != ConstructionJobAction.REMOVE) {
            throw new IllegalStateException(
                    "only a removal can be completed after its operation vanished"
            );
        }
        return with(ConstructionJobPhase.COMPLETING, transitions.size());
    }

        public ConstructionJob restartForWorldReconciliation() {
        return switch (phase) {
            case PREPARED, APPLYING, COMPLETING ->
                    with(ConstructionJobPhase.APPLYING, 0);
            case CANCELLING, CANCELLED ->
                    with(ConstructionJobPhase.CANCELLING, transitions.size());
            case QUARANTINED -> recoverableUpdateDrivenSourceConflict()
                    ? with(
                            ConstructionJobPhase.CANCELLING,
                            transitions.size()
                    )
                    : this;
        };
    }

    private boolean recoverableUpdateDrivenSourceConflict() {
        if (!diagnostic.orElse("").equals(
                "world state matches neither before nor after"
        ) || conflictPosition.isEmpty()) {
            return false;
        }
        BlockPos conflict = conflictPosition.orElseThrow();
        return transitions.stream()
                .filter(transition -> transition.position().equals(conflict))
                .map(ConstructionJobBlockTransition::beforeState)
                .anyMatch(
                        BlockTransitionStateMatcher
                                ::canDisappearDuringPlannedUpdates
                );
    }

    private ConstructionJob with(ConstructionJobPhase nextPhase, int nextCursor) {
        return new ConstructionJob(
                id,
                requestedBy,
                action,
                nextPhase,
                transitions,
                nextCursor,
                Optional.empty(),
                Optional.empty(),
                placementMetadata
        );
    }

    private void requirePhase(ConstructionJobPhase expected) {
        if (phase != expected) {
            throw new IllegalStateException(
                    "job is " + phase.serializedName()
                            + ", expected " + expected.serializedName()
            );
        }
    }
}
