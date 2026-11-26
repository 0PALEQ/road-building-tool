package com.cookiecraftmods.roadbuilder.persistence;

import com.cookiecraftmods.roadbuilder.RoadBuilder;
import com.cookiecraftmods.roadbuilder.networking.RoadGraphPacketCodecs;
import com.cookiecraftmods.roadbuilder.placement.ConstructionJob;
import com.cookiecraftmods.roadbuilder.placement.ConstructionJobAction;
import com.cookiecraftmods.roadbuilder.placement.ConstructionJobBlockTransition;
import com.cookiecraftmods.roadbuilder.placement.ConstructionJobPhase;
import com.cookiecraftmods.roadbuilder.placement.IncrementalPlacementMetadata;
import com.cookiecraftmods.roadbuilder.placement.MaterializationPartition;
import com.cookiecraftmods.roadbuilder.placement.OperationMaterializationRepartition;
import com.cookiecraftmods.roadbuilder.placement.PlacementOperationId;
import com.cookiecraftmods.roadbuilder.placement.RoadMaterializationOwner;
import com.mojang.serialization.DataResult;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class RoadBuilderConstructionJobSavedData extends RoadSavedData {
    public static final int CURRENT_SCHEMA_VERSION = 2;
    public static final int MAXIMUM_JOB_COUNT = 128;
    public static final int MAXIMUM_TRANSITIONS_PER_JOB = 2_000_000;
    public static final int MAXIMUM_TOTAL_TRANSITION_COUNT = 4_000_000;
    public static final String DATA_NAME = "roadbuilder_construction_jobs";

    private static final String SCHEMA_KEY = "schema_version";
    private static final String JOBS_KEY = "jobs";
    private static final String ID_KEY = "id";
    private static final String REQUESTED_BY_KEY = "requested_by";
    private static final String ACTION_KEY = "action";
    private static final String PHASE_KEY = "phase";
    private static final String CURSOR_KEY = "cursor";
    private static final String HAS_CONFLICT_KEY = "has_conflict";
    private static final String CONFLICT_POSITION_KEY = "conflict_position";
    private static final String DIAGNOSTIC_KEY = "diagnostic";
    private static final String TRANSITIONS_KEY = "transitions";
    private static final String HAS_PLACEMENT_METADATA_KEY =
            "has_placement_metadata";
    private static final String PLACEMENT_METADATA_KEY = "placement_metadata";
    private static final String HAS_DIRECT_OWNER_KEY = "has_direct_owner";
    private static final String OWNER_TYPE_KEY = "owner_type";
    private static final String OWNER_ID_KEY = "owner_id";
    private static final String REPARTITIONS_KEY = "repartitions";
    private static final String SOURCE_OPERATION_ID_KEY = "source_operation_id";
    private static final String PARTITIONS_KEY = "partitions";
    private static final String OPERATION_ID_KEY = "operation_id";
    private static final String POSITIONS_KEY = "positions";
    private static final String GRAPH_DELTA_KEY = "graph_delta";
    private static final String POSITION_KEY = "position";
    private static final String BEFORE_KEY = "before";
    private static final String AFTER_KEY = "after";
    private static final Set<String> ROOT_KEYS = Set.of(SCHEMA_KEY, JOBS_KEY);
    private static final Set<String> JOB_KEYS = Set.of(
            ID_KEY,
            REQUESTED_BY_KEY,
            ACTION_KEY,
            PHASE_KEY,
            CURSOR_KEY,
            HAS_CONFLICT_KEY,
            CONFLICT_POSITION_KEY,
            DIAGNOSTIC_KEY,
            TRANSITIONS_KEY,
            HAS_PLACEMENT_METADATA_KEY,
            PLACEMENT_METADATA_KEY
    );
    private static final Set<String> LEGACY_JOB_KEYS = Set.of(
            ID_KEY,
            REQUESTED_BY_KEY,
            ACTION_KEY,
            PHASE_KEY,
            CURSOR_KEY,
            HAS_CONFLICT_KEY,
            CONFLICT_POSITION_KEY,
            DIAGNOSTIC_KEY,
            TRANSITIONS_KEY
    );
    private static final Set<String> TRANSITION_KEYS = Set.of(
            POSITION_KEY,
            BEFORE_KEY,
            AFTER_KEY
    );
    private static final Set<String> PLACEMENT_METADATA_KEYS = Set.of(
            HAS_DIRECT_OWNER_KEY,
            OWNER_TYPE_KEY,
            OWNER_ID_KEY,
            REPARTITIONS_KEY,
            GRAPH_DELTA_KEY
    );
    private static final Set<String> REPARTITION_KEYS = Set.of(
            SOURCE_OPERATION_ID_KEY,
            PARTITIONS_KEY
    );
    private static final Set<String> PARTITION_KEYS = Set.of(
            OPERATION_ID_KEY,
            OWNER_TYPE_KEY,
            OWNER_ID_KEY,
            POSITIONS_KEY
    );

    private final ResourceKey<Level> dimension;
    private final Map<UUID, ConstructionJob> compatibleJobs;
    private final CompoundTag incompatibleRawTag;
    private final String incompatibilityReason;
    private boolean loadedJobsNeedWorldReconciliation;
    private boolean durableJournalRecovered;

    private RoadBuilderConstructionJobSavedData(ResourceKey<Level> dimension) {
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.compatibleJobs = new LinkedHashMap<>();
        this.incompatibleRawTag = null;
        this.incompatibilityReason = null;
        this.loadedJobsNeedWorldReconciliation = false;
    }

    private RoadBuilderConstructionJobSavedData(
            ResourceKey<Level> dimension,
            Map<UUID, ConstructionJob> jobs
    ) {
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.compatibleJobs = new LinkedHashMap<>(Objects.requireNonNull(jobs, "jobs"));
        this.incompatibleRawTag = null;
        this.incompatibilityReason = null;
        this.loadedJobsNeedWorldReconciliation = !jobs.isEmpty();
    }

    private RoadBuilderConstructionJobSavedData(
            ResourceKey<Level> dimension,
            CompoundTag incompatibleRawTag,
            String incompatibilityReason
    ) {
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.compatibleJobs = null;
        this.incompatibleRawTag = Objects.requireNonNull(
                incompatibleRawTag,
                "incompatibleRawTag"
        ).copy();
        this.incompatibilityReason = Objects.requireNonNull(
                incompatibilityReason,
                "incompatibilityReason"
        );
        this.loadedJobsNeedWorldReconciliation = false;
    }

    public static RoadBuilderConstructionJobSavedData get(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        ResourceKey<Level> dimension = level.dimension();
        RoadBuilderConstructionJobSavedData data =
                loadOrCreate(
                level.getDataStorage(),
                tag -> load(dimension, tag),
                () -> create(dimension),
                DATA_NAME
        );
        if (!data.durableJournalRecovered && data.isCompatible()) {
            data.durableJournalRecovered = true;
            try {
                DurableMutationJournal.recover(level, data);
            } catch (java.io.IOException recoveryFailure) {
                RoadBuilder.LOGGER.error(
                        "Could not recover durable Road Builder mutation journal in {}",
                        dimension.location(),
                        recoveryFailure
                );
            }
        }
        return data;
    }

    public static RoadBuilderConstructionJobSavedData create(
            ResourceKey<Level> dimension
    ) {
        return new RoadBuilderConstructionJobSavedData(dimension);
    }

    public static RoadBuilderConstructionJobSavedData load(
            ResourceKey<Level> dimension,
            CompoundTag tag
    ) {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(tag, "tag");
        CompoundTag retained = tag.copy();
        try {
            return new RoadBuilderConstructionJobSavedData(dimension, decode(tag));
        } catch (RuntimeException exception) {
            String message = exception.getMessage();
            return new RoadBuilderConstructionJobSavedData(
                    dimension,
                    retained,
                    message == null || message.isBlank()
                            ? exception.getClass().getSimpleName()
                            : message
            );
        }
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public boolean isCompatible() {
        return compatibleJobs != null;
    }

    public Optional<String> incompatibilityReason() {
        return Optional.ofNullable(incompatibilityReason);
    }

    public Optional<CompoundTag> incompatibleRawTag() {
        return Optional.ofNullable(incompatibleRawTag).map(CompoundTag::copy);
    }

    public List<ConstructionJob> jobs() {
        requireCompatible();
        return compatibleJobs.values().stream()
                .sorted(Comparator.comparing(ConstructionJob::id))
                .toList();
    }

    public Optional<ConstructionJob> job(UUID id) {
        requireCompatible();
        return Optional.ofNullable(compatibleJobs.get(Objects.requireNonNull(id, "id")));
    }

        public int prepareLoadedJobsForWorldReconciliation() {
        requireCompatible();
        if (!loadedJobsNeedWorldReconciliation) {
            return 0;
        }
        loadedJobsNeedWorldReconciliation = false;
        int changed = 0;
        for (Map.Entry<UUID, ConstructionJob> entry : compatibleJobs.entrySet()) {
            ConstructionJob current = entry.getValue();
            ConstructionJob recovery = current.restartForWorldReconciliation();
            if (!recovery.equals(current)) {
                entry.setValue(recovery);
                changed++;
            }
        }
        if (changed > 0) {
            setDirty();
        }
        return changed;
    }

    public void add(ConstructionJob job) {
        requireCompatible();
        Objects.requireNonNull(job, "job");
        if (compatibleJobs.containsKey(job.id())) {
            throw new IllegalArgumentException("construction job already exists: " + job.id());
        }
        requireCapacity(job, 0);
        compatibleJobs.put(job.id(), job);
        setDirty();
    }

    void replaceFromDurableJournal(List<ConstructionJob> recoveredJobs) {
        requireCompatible();
        Objects.requireNonNull(recoveredJobs, "recoveredJobs");
        Map<UUID, ConstructionJob> replacement = new LinkedHashMap<>();
        int transitionCount = 0;
        for (ConstructionJob job : recoveredJobs) {
            Objects.requireNonNull(job, "recoveredJobs contains null");
            if (replacement.put(job.id(), job) != null) {
                throw new IllegalArgumentException(
                        "duplicate durable journal job " + job.id()
                );
            }
            transitionCount = Math.addExact(
                    transitionCount,
                    job.transitions().size()
            );
        }
        if (replacement.size() > MAXIMUM_JOB_COUNT
                || transitionCount > MAXIMUM_TOTAL_TRANSITION_COUNT) {
            throw new IllegalArgumentException(
                    "durable journal exceeds construction-job limits"
            );
        }
        compatibleJobs.clear();
        compatibleJobs.putAll(replacement);
        loadedJobsNeedWorldReconciliation = !replacement.isEmpty();
        setDirty();
    }

    public void replace(ConstructionJob job) {
        requireCompatible();
        Objects.requireNonNull(job, "job");
        ConstructionJob existing = compatibleJobs.get(job.id());
        if (existing == null) {
            throw new IllegalArgumentException("unknown construction job: " + job.id());
        }
        if (!existing.requestedBy().equals(job.requestedBy())
                || existing.action() != job.action()
                || !existing.transitions().equals(job.transitions())
                || !existing.placementMetadata().equals(job.placementMetadata())) {
            throw new IllegalArgumentException(
                    "job replacement may only advance persisted lifecycle state"
            );
        }
        compatibleJobs.put(job.id(), job);
        setDirty();
    }

    public ConstructionJob remove(UUID id) {
        requireCompatible();
        ConstructionJob removed = compatibleJobs.remove(Objects.requireNonNull(id, "id"));
        if (removed == null) {
            throw new IllegalArgumentException("unknown construction job: " + id);
        }
        setDirty();
        return removed;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        Objects.requireNonNull(tag, "tag");
        if (!isCompatible()) {
            return incompatibleRawTag.copy();
        }
        for (String key : List.copyOf(tag.getAllKeys())) {
            tag.remove(key);
        }
        tag.putInt(SCHEMA_KEY, CURRENT_SCHEMA_VERSION);
        ListTag jobs = new ListTag();
        for (ConstructionJob job : jobs()) {
            CompoundTag jobTag = new CompoundTag();
            RoadNbtIo.putUuid(jobTag, ID_KEY, job.id());
            RoadNbtIo.putUuid(jobTag, REQUESTED_BY_KEY, job.requestedBy());
            jobTag.putString(ACTION_KEY, job.action().serializedName());
            jobTag.putString(PHASE_KEY, job.phase().serializedName());
            jobTag.putInt(CURSOR_KEY, job.cursor());
            jobTag.putBoolean(HAS_CONFLICT_KEY, job.conflictPosition().isPresent());
            jobTag.putLong(
                    CONFLICT_POSITION_KEY,
                    job.conflictPosition().orElse(BlockPos.ZERO).asLong()
            );
            jobTag.putString(DIAGNOSTIC_KEY, job.diagnostic().orElse(""));

            ListTag transitions = new ListTag();
            for (ConstructionJobBlockTransition transition : job.transitions()) {
                CompoundTag transitionTag = new CompoundTag();
                transitionTag.putLong(POSITION_KEY, transition.position().asLong());
                transitionTag.put(
                        BEFORE_KEY,
                        StrictBlockStateNbtCodec.write(transition.beforeState())
                );
                transitionTag.put(
                        AFTER_KEY,
                        StrictBlockStateNbtCodec.write(transition.afterState())
                );
                transitions.add(transitionTag);
            }
            jobTag.put(TRANSITIONS_KEY, transitions);
            jobTag.putBoolean(
                    HAS_PLACEMENT_METADATA_KEY,
                    job.placementMetadata().isPresent()
            );
            jobTag.put(
                    PLACEMENT_METADATA_KEY,
                    job.placementMetadata()
                            .map(RoadBuilderConstructionJobSavedData::writePlacementMetadata)
                            .orElseGet(CompoundTag::new)
            );
            jobs.add(jobTag);
        }
        tag.put(JOBS_KEY, jobs);
        return tag;
    }

    private static Map<UUID, ConstructionJob> decode(CompoundTag root) {
        requireExactKeys(root, ROOT_KEYS, "root");
        if (!(root.get(SCHEMA_KEY) instanceof IntTag schema)
                || (schema.getAsInt() != 1
                && schema.getAsInt() != CURRENT_SCHEMA_VERSION)) {
            throw malformed(
                    "unsupported construction-job schema; expected "
                            + CURRENT_SCHEMA_VERSION
            );
        }
        if (!(root.get(JOBS_KEY) instanceof ListTag jobs)) {
            throw malformed("jobs must be a list");
        }
        requireCompoundElements(jobs, "jobs");
        if (jobs.size() > MAXIMUM_JOB_COUNT) {
            throw malformed("job count exceeds " + MAXIMUM_JOB_COUNT);
        }

        Map<UUID, ConstructionJob> decoded = new LinkedHashMap<>();
        long totalTransitions = 0;
        for (int jobIndex = 0; jobIndex < jobs.size(); jobIndex++) {
            CompoundTag jobTag = jobs.getCompound(jobIndex);
            String path = "jobs[" + jobIndex + "]";
            boolean legacy = schema.getAsInt() == 1;
            requireExactKeys(jobTag, legacy ? LEGACY_JOB_KEYS : JOB_KEYS, path);
            UUID id = readUuid(jobTag.get(ID_KEY), path + ".id");
            UUID requestedBy = readUuid(
                    jobTag.get(REQUESTED_BY_KEY),
                    path + ".requested_by"
            );
            ConstructionJobAction action = ConstructionJobAction.fromSerializedName(
                    readString(jobTag.get(ACTION_KEY), path + ".action")
            ).orElseThrow(() -> malformed(path + ".action is unknown"));
            ConstructionJobPhase phase = ConstructionJobPhase.fromSerializedName(
                    readString(jobTag.get(PHASE_KEY), path + ".phase")
            ).orElseThrow(() -> malformed(path + ".phase is unknown"));
            int cursor = readInt(jobTag.get(CURSOR_KEY), path + ".cursor");
            boolean hasConflict = readBoolean(
                    jobTag.get(HAS_CONFLICT_KEY),
                    path + ".has_conflict"
            );
            BlockPos conflictPosition = BlockPos.of(readLong(
                    jobTag.get(CONFLICT_POSITION_KEY),
                    path + ".conflict_position"
            ));
            String diagnostic = readString(
                    jobTag.get(DIAGNOSTIC_KEY),
                    path + ".diagnostic"
            );

            if (!(jobTag.get(TRANSITIONS_KEY) instanceof ListTag transitions)) {
                throw malformed(path + ".transitions must be a list");
            }
            requireCompoundElements(transitions, path + ".transitions");
            if (transitions.isEmpty()
                    || transitions.size() > MAXIMUM_TRANSITIONS_PER_JOB) {
                throw malformed(
                        path + ".transitions must contain 1-"
                                + MAXIMUM_TRANSITIONS_PER_JOB + " entries"
                );
            }
            totalTransitions += transitions.size();
            if (totalTransitions > MAXIMUM_TOTAL_TRANSITION_COUNT) {
                throw malformed(
                        "total job transitions exceed "
                                + MAXIMUM_TOTAL_TRANSITION_COUNT
                );
            }

            List<ConstructionJobBlockTransition> decodedTransitions =
                    new ArrayList<>(transitions.size());
            for (int transitionIndex = 0;
                 transitionIndex < transitions.size();
                 transitionIndex++) {
                CompoundTag transitionTag = transitions.getCompound(transitionIndex);
                String transitionPath = path + ".transitions[" + transitionIndex + "]";
                requireExactKeys(transitionTag, TRANSITION_KEYS, transitionPath);
                decodedTransitions.add(new ConstructionJobBlockTransition(
                        BlockPos.of(readLong(
                                transitionTag.get(POSITION_KEY),
                                transitionPath + ".position"
                        )),
                        readBlockState(
                                transitionTag.get(BEFORE_KEY),
                                transitionPath + ".before"
                        ),
                        readBlockState(
                                transitionTag.get(AFTER_KEY),
                                transitionPath + ".after"
                        )
                ));
            }

            Optional<IncrementalPlacementMetadata> placementMetadata;
            if (legacy) {
                placementMetadata = Optional.empty();
            } else {
                boolean hasPlacementMetadata = readBoolean(
                        jobTag.get(HAS_PLACEMENT_METADATA_KEY),
                        path + ".has_placement_metadata"
                );
                if (!(jobTag.get(PLACEMENT_METADATA_KEY)
                        instanceof CompoundTag metadataTag)) {
                    throw malformed(path + ".placement_metadata must be a compound");
                }
                if (hasPlacementMetadata) {
                    placementMetadata = Optional.of(readPlacementMetadata(
                            metadataTag,
                            path + ".placement_metadata"
                    ));
                } else {
                    if (!metadataTag.isEmpty()) {
                        throw malformed(
                                path + ".placement_metadata must be empty when absent"
                        );
                    }
                    placementMetadata = Optional.empty();
                }
            }

            ConstructionJob job = new ConstructionJob(
                    id,
                    requestedBy,
                    action,
                    phase,
                    decodedTransitions,
                    cursor,
                    hasConflict
                            ? Optional.of(conflictPosition)
                            : Optional.empty(),
                    diagnostic.isEmpty()
                            ? Optional.empty()
                            : Optional.of(diagnostic),
                    placementMetadata
            );
            if (decoded.put(id, job) != null) {
                throw malformed("duplicate construction job id " + id);
            }
        }
        return decoded;
    }

    private static CompoundTag writePlacementMetadata(
            IncrementalPlacementMetadata metadata
    ) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(HAS_DIRECT_OWNER_KEY, metadata.directOwner().isPresent());
        RoadMaterializationOwner directOwner = metadata.directOwner().orElse(null);
        tag.putString(
                OWNER_TYPE_KEY,
                directOwner == null ? "" : directOwner.type().serializedName()
        );
        RoadNbtIo.putUuid(tag,
                OWNER_ID_KEY,
                directOwner == null ? new UUID(0L, 0L) : directOwner.ownerId()
        );

        ListTag repartitions = new ListTag();
        for (OperationMaterializationRepartition repartition :
                metadata.repartitions()) {
            CompoundTag repartitionTag = new CompoundTag();
            RoadNbtIo.putUuid(repartitionTag,
                    SOURCE_OPERATION_ID_KEY,
                    repartition.sourceOperationId().value()
            );
            ListTag partitions = new ListTag();
            for (MaterializationPartition partition : repartition.partitions()) {
                CompoundTag partitionTag = new CompoundTag();
                RoadNbtIo.putUuid(partitionTag,
                        OPERATION_ID_KEY,
                        partition.operationId().value()
                );
                partitionTag.putString(
                        OWNER_TYPE_KEY,
                        partition.owner().type().serializedName()
                );
                RoadNbtIo.putUuid(partitionTag, OWNER_ID_KEY, partition.owner().ownerId());
                partitionTag.putLongArray(
                        POSITIONS_KEY,
                        partition.positions().stream()
                                .mapToLong(BlockPos::asLong)
                                .toArray()
                );
                partitions.add(partitionTag);
            }
            repartitionTag.put(PARTITIONS_KEY, partitions);
            repartitions.add(repartitionTag);
        }
        tag.put(REPARTITIONS_KEY, repartitions);
        tag.putByteArray(
                GRAPH_DELTA_KEY,
                RoadGraphPacketCodecs.encodeDelta(metadata.graphDelta())
        );
        return tag;
    }

    private static IncrementalPlacementMetadata readPlacementMetadata(
            CompoundTag tag,
            String path
    ) {
        requireExactKeys(tag, PLACEMENT_METADATA_KEYS, path);
        boolean hasDirectOwner = readBoolean(
                tag.get(HAS_DIRECT_OWNER_KEY),
                path + ".has_direct_owner"
        );
        String ownerTypeName = readString(
                tag.get(OWNER_TYPE_KEY),
                path + ".owner_type"
        );
        UUID ownerId = readUuid(tag.get(OWNER_ID_KEY), path + ".owner_id");
        Optional<RoadMaterializationOwner> directOwner;
        if (hasDirectOwner) {
            RoadMaterializationOwner.Type ownerType =
                    RoadMaterializationOwner.Type.fromSerializedName(ownerTypeName)
                            .orElseThrow(() -> malformed(
                                    path + ".owner_type is unknown"
                            ));
            directOwner = Optional.of(new RoadMaterializationOwner(
                    ownerType,
                    ownerId
            ));
        } else {
            if (!ownerTypeName.isEmpty()
                    || ownerId.getMostSignificantBits() != 0L
                    || ownerId.getLeastSignificantBits() != 0L) {
                throw malformed(
                        path + " must use empty direct-owner sentinel values"
                );
            }
            directOwner = Optional.empty();
        }

        if (!(tag.get(REPARTITIONS_KEY) instanceof ListTag repartitionTags)) {
            throw malformed(path + ".repartitions must be a list");
        }
        requireCompoundElements(repartitionTags, path + ".repartitions");
        if (repartitionTags.size()
                > IncrementalPlacementMetadata.MAXIMUM_REPARTITION_COUNT) {
            throw malformed(path + ".repartitions exceeds its limit");
        }
        List<OperationMaterializationRepartition> repartitions =
                new ArrayList<>(repartitionTags.size());
        long totalPartitionCount = 0;
        long totalPositionReferences = 0;
        for (int repartitionIndex = 0;
             repartitionIndex < repartitionTags.size();
             repartitionIndex++) {
            CompoundTag repartitionTag =
                    repartitionTags.getCompound(repartitionIndex);
            String repartitionPath =
                    path + ".repartitions[" + repartitionIndex + "]";
            requireExactKeys(repartitionTag, REPARTITION_KEYS, repartitionPath);
            PlacementOperationId sourceId = new PlacementOperationId(readUuid(
                    repartitionTag.get(SOURCE_OPERATION_ID_KEY),
                    repartitionPath + ".source_operation_id"
            ));
            if (!(repartitionTag.get(PARTITIONS_KEY)
                    instanceof ListTag partitionTags)) {
                throw malformed(repartitionPath + ".partitions must be a list");
            }
            requireCompoundElements(
                    partitionTags,
                    repartitionPath + ".partitions"
            );
            if (partitionTags.isEmpty()
                    || partitionTags.size()
                    > IncrementalPlacementMetadata.MAXIMUM_PARTITION_COUNT) {
                throw malformed(
                        repartitionPath + ".partitions exceeds its limit"
                );
            }
            List<MaterializationPartition> partitions =
                    new ArrayList<>(partitionTags.size());
            totalPartitionCount += partitionTags.size();
            if (totalPartitionCount
                    > IncrementalPlacementMetadata.MAXIMUM_PARTITION_COUNT) {
                throw malformed(path + ".partitions exceeds its total limit");
            }
            for (int partitionIndex = 0;
                 partitionIndex < partitionTags.size();
                 partitionIndex++) {
                CompoundTag partitionTag =
                        partitionTags.getCompound(partitionIndex);
                String partitionPath =
                        repartitionPath + ".partitions[" + partitionIndex + "]";
                requireExactKeys(partitionTag, PARTITION_KEYS, partitionPath);
                PlacementOperationId operationId = new PlacementOperationId(
                        readUuid(
                                partitionTag.get(OPERATION_ID_KEY),
                                partitionPath + ".operation_id"
                        )
                );
                RoadMaterializationOwner.Type ownerType =
                        RoadMaterializationOwner.Type.fromSerializedName(
                                readString(
                                        partitionTag.get(OWNER_TYPE_KEY),
                                        partitionPath + ".owner_type"
                                )
                        ).orElseThrow(() -> malformed(
                                partitionPath + ".owner_type is unknown"
                        ));
                RoadMaterializationOwner owner =
                        new RoadMaterializationOwner(
                                ownerType,
                                readUuid(
                                        partitionTag.get(OWNER_ID_KEY),
                                        partitionPath + ".owner_id"
                                )
                        );
                Tag rawPositions = partitionTag.get(POSITIONS_KEY);
                if (!(rawPositions instanceof LongArrayTag positionArray)) {
                    throw malformed(
                            partitionPath + ".positions must be a long array"
                    );
                }
                long[] packedPositions = positionArray.getAsLongArray();
                if (packedPositions.length == 0
                        || packedPositions.length
                        > MAXIMUM_TRANSITIONS_PER_JOB) {
                    throw malformed(
                            partitionPath + ".positions exceeds its limit"
                    );
                }
                totalPositionReferences += packedPositions.length;
                if (totalPositionReferences
                        > IncrementalPlacementMetadata
                        .MAXIMUM_POSITION_REFERENCE_COUNT) {
                    throw malformed(
                            path + ".positions exceeds its total limit"
                    );
                }
                List<BlockPos> positions =
                        new ArrayList<>(packedPositions.length);
                for (long packedPosition : packedPositions) {
                    positions.add(BlockPos.of(packedPosition));
                }
                partitions.add(new MaterializationPartition(
                        operationId,
                        owner,
                        positions
                ));
            }
            repartitions.add(new OperationMaterializationRepartition(
                    sourceId,
                    partitions
            ));
        }

        Tag rawDelta = tag.get(GRAPH_DELTA_KEY);
        if (!(rawDelta instanceof ByteArrayTag deltaBytes)) {
            throw malformed(path + ".graph_delta must be a byte array");
        }
        return new IncrementalPlacementMetadata(
                directOwner,
                repartitions,
                RoadGraphPacketCodecs.decodeDelta(deltaBytes.getAsByteArray())
        );
    }

    private void requireCapacity(ConstructionJob job, int replacedTransitionCount) {
        if (compatibleJobs.size() >= MAXIMUM_JOB_COUNT) {
            throw new IllegalStateException("construction job count limit exceeded");
        }
        if (job.transitions().size() > MAXIMUM_TRANSITIONS_PER_JOB) {
            throw new IllegalStateException("construction job transition limit exceeded");
        }
        long currentTransitions = compatibleJobs.values().stream()
                .mapToLong(value -> value.transitions().size())
                .sum();
        if (currentTransitions - replacedTransitionCount + job.transitions().size()
                > MAXIMUM_TOTAL_TRANSITION_COUNT) {
            throw new IllegalStateException(
                    "total construction job transition limit exceeded"
            );
        }
    }

    private void requireCompatible() {
        if (!isCompatible()) {
            throw new IllegalStateException(
                    "Road Builder job data is incompatible: " + incompatibilityReason
            );
        }
    }

    private static UUID readUuid(Tag value, String path) {
        if (!(value instanceof IntArrayTag array) || array.size() != 4) {
            throw malformed(path + " must be a four-int UUID");
        }
        try {
            return RoadNbtIo.readUuid(array);
        } catch (RuntimeException exception) {
            throw malformed(path + " is not a valid UUID");
        }
    }

    private static String readString(Tag value, String path) {
        if (!(value instanceof StringTag string)) {
            throw malformed(path + " must be a string");
        }
        return string.getAsString();
    }

    private static int readInt(Tag value, String path) {
        if (!(value instanceof IntTag integer)) {
            throw malformed(path + " must be an int");
        }
        return integer.getAsInt();
    }

    private static long readLong(Tag value, String path) {
        if (!(value instanceof LongTag number)) {
            throw malformed(path + " must be a long");
        }
        return number.getAsLong();
    }

    private static boolean readBoolean(Tag value, String path) {
        if (!(value instanceof ByteTag bool)
                || (bool.getAsByte() != 0 && bool.getAsByte() != 1)) {
            throw malformed(path + " must be a boolean byte");
        }
        return bool.getAsByte() == 1;
    }

    private static BlockState readBlockState(Tag value, String path) {
        if (!(value instanceof CompoundTag stateTag)) {
            throw malformed(path + " must be a compound");
        }
        DataResult<BlockState> result = StrictBlockStateNbtCodec.read(stateTag);
        return result.result().orElseThrow(() -> malformed(
                path + " is invalid: " + result.error()
                        .map(error -> error.message())
                        .orElse("unknown block-state error")
        ));
    }

    private static void requireCompoundElements(ListTag list, String path) {
        if (!list.isEmpty() && list.stream().anyMatch(element -> element.getId() != Tag.TAG_COMPOUND)) {
            throw malformed(path + " entries must be compounds");
        }
    }

    private static void requireExactKeys(
            CompoundTag tag,
            Set<String> expected,
            String path
    ) {
        if (!tag.getAllKeys().equals(expected)) {
            throw malformed(path + " must contain exactly " + expected);
        }
    }

    private static IllegalArgumentException malformed(String message) {
        return new IllegalArgumentException(message);
    }
}
