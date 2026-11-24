package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.network.PreparedSegmentRemoval;
import com.cookiecraftmods.roadbuilder.network.RoadGraph;
import com.cookiecraftmods.roadbuilder.persistence.RoadBuilderConstructionJobSavedData;
import com.cookiecraftmods.roadbuilder.persistence.DurableMutationJournal;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class IncrementalRemovalJobService {
    public static final int DEFAULT_ROUTING_THRESHOLD =
            ConstructionJobBatchProcessor.DEFAULT_TRANSITIONS_PER_TICK;
    private static final String LEGACY_ACCEPTED_WRITE_DIAGNOSTIC =
            "world write did not retain its target state";
    private static final String LEGACY_FINALIZATION_DIAGNOSTIC =
            "incremental removal finalization failed: completed removal transition changed before publication";

    private final PlacementRemovalService removalService;
    private final int routingThreshold;

    public IncrementalRemovalJobService(PlacementRemovalService removalService) {
        this(removalService, DEFAULT_ROUTING_THRESHOLD);
    }

    public IncrementalRemovalJobService(
            PlacementRemovalService removalService,
            int routingThreshold
    ) {
        this.removalService = Objects.requireNonNull(removalService, "removalService");
        if (routingThreshold < 1) {
            throw new IllegalArgumentException("routingThreshold must be positive");
        }
        this.routingThreshold = routingThreshold;
    }

    public EnqueueResult enqueueIfExpensive(
            UUID requestedBy,
            PlacementOperationId operationId,
            RemovalMode mode,
            PlacementBlockWorld world,
            PlacementOwnershipLedger ledger,
            RoadBuilderConstructionJobSavedData jobs
    ) {
        Objects.requireNonNull(requestedBy, "requestedBy");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(ledger, "ledger");
        Objects.requireNonNull(jobs, "jobs");

        IncrementalRemovalPlanningResult planning = removalService.planIncremental(
                operationId,
                mode,
                world,
                ledger
        );
        if (planning instanceof IncrementalRemovalPlanningResult.Failure failure) {
            return new EnqueueResult.Failure(failure.code(), failure.positions());
        }
        IncrementalRemovalPlan plan =
                ((IncrementalRemovalPlanningResult.Success) planning).plan();
        if (plan.transitions().size() <= routingThreshold
                || plan.transitions().size()
                > RoadBuilderConstructionJobSavedData.MAXIMUM_TRANSITIONS_PER_JOB) {
            return new EnqueueResult.Immediate(plan.summary());
        }

        UUID jobId = operationId.value();
        ConstructionJob existing = jobs.job(jobId).orElse(null);
        if (existing != null) {
            return new EnqueueResult.AlreadyQueued(existing, plan.summary());
        }

        ConstructionJob job = ConstructionJob.prepared(
                jobId,
                requestedBy,
                ConstructionJobAction.REMOVE,
                plan.transitions()
        );
        try {
            jobs.add(job);
        } catch (IllegalStateException capacityFailure) {
            return new EnqueueResult.Immediate(plan.summary());
        }
        if (world instanceof ServerLevelPlacementBlockWorld serverWorld) {
            try {
                DurableMutationJournal.flush(serverWorld.level(), jobs);
            } catch (java.io.IOException journalFailure) {
                jobs.remove(job.id());
                return new EnqueueResult.Failure(
                        PlacementRemovalFailureCode.LEDGER_REJECTED,
                        List.of()
                );
            }
        }
        return new EnqueueResult.Queued(job, plan.summary());
    }

    public TickResult tick(
            ConstructionJob suppliedJob,
            int transitionBudget,
            PlacementBlockWorld world,
            PlacementOwnershipLedger ledger,
            RoadGraph graph
    ) {
        ConstructionJob job = Objects.requireNonNull(suppliedJob, "suppliedJob");
        if (transitionBudget < 1) {
            throw new IllegalArgumentException("transitionBudget must be positive");
        }
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(ledger, "ledger");
        Objects.requireNonNull(graph, "graph");
        if (job.action() != ConstructionJobAction.REMOVE) {
            return new TickResult(job, TickOutcome.SKIPPED, 0, false);
        }
        PlacementOperationId operationId = new PlacementOperationId(job.id());
        if (ledger.operation(operationId).isEmpty()) {
            return new TickResult(
                    job.completeOrphanedRemoval(),
                    TickOutcome.COMPLETED,
                    0,
                    false
            );
        }
        if (job.phase() == ConstructionJobPhase.QUARANTINED) {
            if (!canRecover(job, ledger)) {
                return new TickResult(job, TickOutcome.QUARANTINED, 0, false);
            }
            job = recover(job, world);
        }
        if (job.phase() == ConstructionJobPhase.CANCELLED) {
            return new TickResult(job, TickOutcome.CANCELLED, 0, false);
        }

        int examined = 0;
        if (job.phase() != ConstructionJobPhase.COMPLETING) {
            ConstructionJobBatchResult batch =
                    new ConstructionJobBatchProcessor(transitionBudget).process(job, world);
            job = batch.job();
            examined = batch.examinedTransitions();
        }
        if (job.phase() == ConstructionJobPhase.QUARANTINED) {
            return new TickResult(job, TickOutcome.QUARANTINED, examined, false);
        }
        if (job.phase() == ConstructionJobPhase.CANCELLED) {
            return new TickResult(job, TickOutcome.CANCELLED, examined, false);
        }
        if (job.phase() != ConstructionJobPhase.COMPLETING) {
            return new TickResult(job, TickOutcome.UPDATED, examined, false);
        }

        try {
            boolean graphChanged = finalizeRemoval(job, ledger, graph);
            return new TickResult(job, TickOutcome.COMPLETED, examined, graphChanged);
        } catch (RuntimeException exception) {
            ConstructionJob quarantined = job.quarantine(
                    null,
                    boundedDiagnostic(exception)
            );
            return new TickResult(
                    quarantined,
                    TickOutcome.QUARANTINED,
                    examined,
                    false
            );
        }
    }

    public static boolean canRecover(
            ConstructionJob job,
            PlacementOwnershipLedger ledger
    ) {
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(ledger, "ledger");
        if (job.action() != ConstructionJobAction.REMOVE
                || job.phase() != ConstructionJobPhase.QUARANTINED) {
            return false;
        }
        PlacementOperationId operationId = new PlacementOperationId(job.id());
        if (ledger.operation(operationId).isEmpty()) {
            return true;
        }
        String diagnostic = job.diagnostic().orElse("");
        return diagnostic.equals(LEGACY_FINALIZATION_DIAGNOSTIC)
                        && job.cursor() == job.transitions().size()
                || diagnostic.equals(LEGACY_ACCEPTED_WRITE_DIAGNOSTIC)
                        && job.cursor() < job.transitions().size();
    }

    private static ConstructionJob recover(
            ConstructionJob job,
            PlacementBlockWorld world
    ) {
        String diagnostic = job.diagnostic().orElseThrow();
        if (diagnostic.equals(LEGACY_FINALIZATION_DIAGNOSTIC)) {
            return job.resumeQuarantinedRemoval(job.cursor());
        }
        ConstructionJobBlockTransition transition = job.transitions().get(
                job.cursor()
        );
        boolean removedStateStillPresent =
                BlockTransitionStateMatcher.matchesUpdateDrivenTarget(
                        world.blockState(transition.position()),
                        transition.beforeState()
                );
        return job.resumeQuarantinedRemoval(
                removedStateStillPresent
                        ? job.cursor()
                        : job.cursor() + 1
        );
    }

    private static boolean finalizeRemoval(
            ConstructionJob job,
            PlacementOwnershipLedger ledger,
            RoadGraph graph
    ) {
        PlacementOperationId operationId = new PlacementOperationId(job.id());
        PlacementOperation operation = ledger.operation(operationId).orElseThrow(
                () -> new IllegalStateException("removal operation disappeared before completion")
        );
        for (var position : operation.positions()) {
            PositionChangeStack stack = ledger.stack(position).orElseThrow(
                    () -> new IllegalStateException("removal ownership stack disappeared")
            );
            if (stack.layer(operationId).isEmpty()) {
                throw new IllegalStateException("removal ownership layer disappeared");
            }
        }

        PreparedSegmentRemoval graphRemoval = null;
        if (operation.materializationOwner().isPresent()) {
            RoadMaterializationOwner owner =
                    operation.materializationOwner().orElseThrow();
            if (owner.type() == RoadMaterializationOwner.Type.SEGMENT) {
                graphRemoval = graph.prepareSegmentRemoval(owner.segmentId());
            } else if (graph.node(owner.nodeId()).isEmpty()) {
                throw new IllegalStateException("owned graph node disappeared");
            }
        }

        long ledgerRevision = ledger.revision();
        ledger.removeOperation(operationId, ledgerRevision);
        if (graphRemoval != null) {
            graph.applyRemoval(graphRemoval);
            return true;
        }
        return false;
    }

    private static String boundedDiagnostic(RuntimeException exception) {
        String detail = exception.getMessage();
        String diagnostic = detail == null || detail.isBlank()
                ? "incremental removal finalization failed"
                : "incremental removal finalization failed: " + detail.strip();
        return diagnostic.substring(
                0,
                Math.min(diagnostic.length(), ConstructionJob.MAXIMUM_DIAGNOSTIC_LENGTH)
        );
    }

    public sealed interface EnqueueResult {
        record Immediate(RemovalSummary summary) implements EnqueueResult {
            public Immediate {
                Objects.requireNonNull(summary, "summary");
            }
        }

        record Queued(ConstructionJob job, RemovalSummary summary)
                implements EnqueueResult {
            public Queued {
                Objects.requireNonNull(job, "job");
                Objects.requireNonNull(summary, "summary");
            }
        }

        record AlreadyQueued(ConstructionJob job, RemovalSummary summary)
                implements EnqueueResult {
            public AlreadyQueued {
                Objects.requireNonNull(job, "job");
                Objects.requireNonNull(summary, "summary");
            }
        }

        record Failure(
                PlacementRemovalFailureCode code,
                List<net.minecraft.core.BlockPos> positions
        ) implements EnqueueResult {
            public Failure {
                Objects.requireNonNull(code, "code");
                positions = List.copyOf(Objects.requireNonNull(positions, "positions"));
            }
        }
    }

    public enum TickOutcome {
        UPDATED,
        COMPLETED,
        CANCELLED,
        QUARANTINED,
        SKIPPED
    }

    public record TickResult(
            ConstructionJob job,
            TickOutcome outcome,
            int examinedTransitions,
            boolean graphChanged
    ) {
        public TickResult {
            Objects.requireNonNull(job, "job");
            Objects.requireNonNull(outcome, "outcome");
            if (examinedTransitions < 0) {
                throw new IllegalArgumentException(
                        "examinedTransitions must be non-negative"
                );
            }
            if (graphChanged && outcome != TickOutcome.COMPLETED) {
                throw new IllegalArgumentException(
                        "only completed jobs can publish graph changes"
                );
            }
        }
    }
}
