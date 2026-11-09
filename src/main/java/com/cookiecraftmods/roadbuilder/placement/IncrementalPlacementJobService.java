package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.RoadBuilder;
import com.cookiecraftmods.roadbuilder.network.PreparedRoadNetworkMutation;
import com.cookiecraftmods.roadbuilder.network.RoadGraph;
import com.cookiecraftmods.roadbuilder.network.RoadGraphDelta;
import com.cookiecraftmods.roadbuilder.network.RoadGraphLimits;
import com.cookiecraftmods.roadbuilder.network.RoadGraphSnapshot;
import com.cookiecraftmods.roadbuilder.persistence.RoadBuilderConstructionJobSavedData;
import com.cookiecraftmods.roadbuilder.persistence.DurableMutationJournal;
import com.cookiecraftmods.roadbuilder.networking.RoadGraphPacketCodecs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class IncrementalPlacementJobService {
    public static final int DEFAULT_ROUTING_THRESHOLD =
            ConstructionJobBatchProcessor.DEFAULT_TRANSITIONS_PER_TICK;

    private static final Comparator<ConstructionJobBlockTransition> SAFE_WRITE_ORDER =
            Comparator
                    .comparing((ConstructionJobBlockTransition transition) ->
                            transition.afterState().isAir())
                    .thenComparing((first, second) -> {
                        int firstY = first.position().getY();
                        int secondY = second.position().getY();
                        return first.afterState().isAir()
                                ? Integer.compare(secondY, firstY)
                                : Integer.compare(firstY, secondY);
                    })
                    .thenComparingInt(transition -> transition.position().getX())
                    .thenComparingInt(transition -> transition.position().getZ());

    private final int routingThreshold;

    public IncrementalPlacementJobService() {
        this(DEFAULT_ROUTING_THRESHOLD);
    }

    public IncrementalPlacementJobService(int routingThreshold) {
        if (routingThreshold < 1) {
            throw new IllegalArgumentException("routingThreshold must be positive");
        }
        this.routingThreshold = routingThreshold;
    }

    public EnqueueResult enqueueIfExpensive(
            UUID requestedBy,
            PlacementOperationId operationId,
            BlockPlacementPlan plan,
            Optional<RoadMaterializationOwner> directOwner,
            List<OperationMaterializationRepartition> repartitions,
            PreparedRoadNetworkMutation graphMutation,
            PlacementBlockWorld world,
            PlacementOwnershipLedger ledger,
            RoadGraph graph,
            RoadBuilderConstructionJobSavedData jobs
    ) {
        Objects.requireNonNull(requestedBy, "requestedBy");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(plan, "plan");
        directOwner = Objects.requireNonNull(directOwner, "directOwner");
        repartitions = List.copyOf(Objects.requireNonNull(
                repartitions,
                "repartitions"
        ));
        Objects.requireNonNull(graphMutation, "graphMutation");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(ledger, "ledger");
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(jobs, "jobs");

        if (plan.affectedBlockCount() <= routingThreshold
                || plan.affectedBlockCount()
                > RoadBuilderConstructionJobSavedData.MAXIMUM_TRANSITIONS_PER_JOB) {
            return EnqueueResult.immediate();
        }
        if (!plan.dimension().equals(world.dimension())
                || !plan.dimension().equals(ledger.dimension())
                || !plan.dimension().equals(graph.dimension())) {
            return EnqueueResult.failure(
                    PlacementCommitFailureCode.DIMENSION_MISMATCH,
                    null
            );
        }
        if (ledger.containsOperation(operationId)) {
            return EnqueueResult.failure(
                    PlacementCommitFailureCode.DUPLICATE_OPERATION_ID,
                    null
            );
        }
        if (!ledger.canRecord(plan)) {
            return EnqueueResult.failure(
                    PlacementCommitFailureCode.OWNERSHIP_LIMIT_EXCEEDED,
                    null
            );
        }
        if (directOwner.isPresent()
                && ledger.operation(directOwner.orElseThrow()).isPresent()) {
            return EnqueueResult.failure(
                    PlacementCommitFailureCode.LEDGER_REJECTED,
                    null
            );
        }

        List<ConstructionJobBlockTransition> transitions =
                new ArrayList<>(plan.affectedBlockCount());
        for (PlannedBlock block : plan.blocks()) {
            BlockPos position = block.position();
            BlockState before = world.blockState(position);
            PlacementCommitFailureCode unsupported = unsupportedTransition(
                    world,
                    position,
                    before,
                    block.targetState()
            );
            if (unsupported != null) {
                RoadBuilder.LOGGER.warn(
                        "Rejected incremental placement transition: code={}, position={}, before={}, target={}",
                        unsupported, position.toShortString(), before, block.targetState());
                return EnqueueResult.failure(unsupported, position);
            }
            transitions.add(new ConstructionJobBlockTransition(
                    position,
                    before,
                    block.targetState()
            ));
        }
        transitions.sort(SAFE_WRITE_ORDER);

        RoadGraphSnapshot beforeGraph = graph.snapshot();
        RoadGraph candidate = RoadGraph.restore(
                beforeGraph.dimension(),
                beforeGraph.revision(),
                beforeGraph.roads().values(),
                beforeGraph.nodes().values(),
                beforeGraph.segments().values(),
                beforeGraph.connectors().values(),
                RoadGraphLimits.defaults(),
                () -> {
                }
        );
        candidate.applyNetworkMutation(graphMutation);
        RoadGraphDelta delta = RoadGraphDelta.between(
                beforeGraph,
                candidate.snapshot()
        );
        IncrementalPlacementMetadata metadata;
        ConstructionJob job;
        try {
            metadata = new IncrementalPlacementMetadata(
                    directOwner,
                    repartitions,
                    delta
            );
            RoadGraphPacketCodecs.encodeDelta(delta);
            job = ConstructionJob.preparedPlacement(
                    operationId.value(),
                    requestedBy,
                    transitions,
                    metadata
            );
        } catch (IllegalArgumentException metadataLimit) {
            return EnqueueResult.immediate();
        }
        try {
            jobs.add(job);
        } catch (IllegalStateException capacityFailure) {
            return EnqueueResult.immediate();
        }
        if (world instanceof ServerLevelPlacementBlockWorld serverWorld) {
            try {
                DurableMutationJournal.flush(serverWorld.level(), jobs);
            } catch (java.io.IOException journalFailure) {
                jobs.remove(job.id());
                return EnqueueResult.failure(
                        PlacementCommitFailureCode.LEDGER_REJECTED,
                        null
                );
            }
        }
        return new EnqueueResult.Queued(job);
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
        if (job.action() != ConstructionJobAction.PLACE) {
            return new TickResult(job, TickOutcome.SKIPPED, 0, false);
        }
        if (job.placementMetadata().isEmpty()
                && job.phase() != ConstructionJobPhase.QUARANTINED) {
            ConstructionJob quarantined = job.quarantine(
                    null,
                    "placement job has no publication metadata"
            );
            return new TickResult(
                    quarantined,
                    TickOutcome.QUARANTINED,
                    0,
                    false
            );
        }
        if (job.phase() == ConstructionJobPhase.QUARANTINED) {
            job = job.rollbackFailedPlacement();
            if (job.phase() == ConstructionJobPhase.CANCELLED) {
                return new TickResult(
                        job,
                        TickOutcome.CANCELLED,
                        0,
                        false
                );
            }
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
            logQuarantine(job);
            return new TickResult(
                    job,
                    TickOutcome.QUARANTINED,
                    examined,
                    false
            );
        }
        if (job.phase() == ConstructionJobPhase.CANCELLED) {
            return new TickResult(job, TickOutcome.CANCELLED, examined, false);
        }
        if (job.phase() != ConstructionJobPhase.COMPLETING) {
            return new TickResult(job, TickOutcome.UPDATED, examined, false);
        }

        try {
            finalizePlacement(job, world, ledger, graph);
            return new TickResult(job, TickOutcome.COMPLETED, examined, true);
        } catch (RuntimeException exception) {
            ConstructionJob quarantined = job.quarantine(
                    null,
                    boundedDiagnostic(exception)
            );
            logQuarantine(quarantined);
            ConstructionJob cleanup = quarantined.rollbackFailedPlacement();
            return new TickResult(
                    cleanup,
                    cleanup.phase() == ConstructionJobPhase.CANCELLED
                            ? TickOutcome.CANCELLED
                            : TickOutcome.UPDATED,
                    examined,
                    false
            );
        }
    }

    private static void finalizePlacement(
            ConstructionJob job,
            PlacementBlockWorld world,
            PlacementOwnershipLedger ledger,
            RoadGraph graph
    ) {
        IncrementalPlacementMetadata metadata =
                job.placementMetadata().orElseThrow();
        metadata.graphDelta().applyTo(graph.snapshot());

        List<PlannedBlock> blocks = new ArrayList<>(job.transitions().size());
        Map<BlockPos, BlockState> beforeStates =
                new LinkedHashMap<>(job.transitions().size());
        for (ConstructionJobBlockTransition transition : job.transitions()) {
            BlockState committedState = world.blockState(transition.position());
            if (!BlockTransitionStateMatcher.matchesUpdateDrivenTarget(
                    committedState,
                    transition.afterState()
            )) {
                throw new IllegalStateException(
                        "completed placement transition changed at "
                                + transition.position().toShortString()
                                + ": current=" + blockId(committedState)
                                + ", target=" + blockId(transition.afterState())
                );
            }
            blocks.add(new PlannedBlock(
                    transition.position(),
                    committedState
            ));
            beforeStates.put(transition.position(), transition.beforeState());
        }
        BlockPlacementPlan plan = BlockPlacementPlan.create(
                world.dimension(),
                blocks
        );
        PlacementOperationId operationId = new PlacementOperationId(job.id());
        long ledgerRevision = ledger.revision();
        if (metadata.directOwner().isPresent()) {
            ledger.recordPlacement(
                    operationId,
                    metadata.directOwner().orElseThrow(),
                    plan,
                    beforeStates,
                    ledgerRevision
            );
        } else {
            ledger.recordPlacement(
                    operationId,
                    plan,
                    beforeStates,
                    ledgerRevision
            );
            ledger.repartitionMaterializations(metadata.repartitions());
        }
        graph.applyDelta(metadata.graphDelta());
    }

    private static PlacementCommitFailureCode unsupportedTransition(
            PlacementBlockWorld world,
            BlockPos position,
            BlockState before,
            BlockState after
    ) {
        if (world.hasBlockEntity(position) || before.hasBlockEntity()) {
            return PlacementCommitFailureCode.CURRENT_BLOCK_ENTITY_UNSUPPORTED;
        }
        if (after.hasBlockEntity()) {
            return PlacementCommitFailureCode.TARGET_BLOCK_ENTITY_UNSUPPORTED;
        }
        return null;
    }

    private static String boundedDiagnostic(RuntimeException exception) {
        String detail = exception.getMessage();
        String diagnostic = detail == null || detail.isBlank()
                ? "incremental placement finalization failed"
                : "incremental placement finalization failed: " + detail.strip();
        return diagnostic.substring(
                0,
                Math.min(
                        diagnostic.length(),
                        ConstructionJob.MAXIMUM_DIAGNOSTIC_LENGTH
                )
        );
    }

    private static String blockId(BlockState state) {
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        return id.substring(0, Math.min(id.length(), 64));
    }

    private static void logQuarantine(ConstructionJob job) {
        RoadBuilder.LOGGER.warn(
                "Incremental placement {} rolled back at {}: {}",
                job.id(),
                job.conflictPosition()
                        .map(BlockPos::toShortString)
                        .orElse("finalization"),
                job.diagnostic().orElse("unknown construction conflict")
        );
    }

    public sealed interface EnqueueResult {
        static Immediate immediate() {
            return new Immediate();
        }

        static Failure failure(
                PlacementCommitFailureCode code,
                BlockPos position
        ) {
            return new Failure(code, Optional.ofNullable(position));
        }

        record Immediate() implements EnqueueResult {
        }

        record Queued(ConstructionJob job) implements EnqueueResult {
            public Queued {
                Objects.requireNonNull(job, "job");
            }
        }

        record Failure(
                PlacementCommitFailureCode code,
                Optional<BlockPos> position
        ) implements EnqueueResult {
            public Failure {
                Objects.requireNonNull(code, "code");
                Objects.requireNonNull(position, "position");
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
