package com.cookiecraftmods.roadbuilder.persistence;

import com.cookiecraftmods.roadbuilder.placement.BlockChangeLayer;
import com.cookiecraftmods.roadbuilder.placement.PlacementOperationId;
import com.cookiecraftmods.roadbuilder.placement.PlacementOwnershipLimits;
import com.cookiecraftmods.roadbuilder.placement.PlacementOwnershipLedger;
import com.cookiecraftmods.roadbuilder.placement.PositionChangeStack;
import com.cookiecraftmods.roadbuilder.placement.RoadMaterializationOwner;
import com.mojang.serialization.DataResult;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class RoadBuilderPlacementSavedData extends RoadSavedData {
    public static final int CURRENT_SCHEMA_VERSION = 2;
    public static final int MAXIMUM_POSITION_COUNT =
            PlacementOwnershipLimits.DEFAULT_MAXIMUM_POSITION_COUNT;
    public static final int MAXIMUM_TOTAL_LAYER_COUNT =
            PlacementOwnershipLimits.DEFAULT_MAXIMUM_TOTAL_LAYER_COUNT;
    public static final int MAXIMUM_STACK_DEPTH =
            PlacementOwnershipLimits.DEFAULT_MAXIMUM_STACK_DEPTH;
    public static final String DATA_NAME = "roadbuilder_placement_ownership";

    private static final String SCHEMA_VERSION_KEY = "schema_version";
    private static final String OWNERSHIP_KEY = "ownership";
    private static final String MATERIALIZATIONS_KEY = "materializations";
    private static final String POSITION_KEY = "position";
    private static final String LAYERS_KEY = "layers";
    private static final String OPERATION_KEY = "operation";
    private static final String BEFORE_STATE_KEY = "before_state";
    private static final String AFTER_STATE_KEY = "after_state";
    private static final String OWNER_TYPE_KEY = "owner_type";
    private static final String OWNER_ID_KEY = "owner_id";

    private static final Set<String> ROOT_KEYS_V1 = Set.of(SCHEMA_VERSION_KEY, OWNERSHIP_KEY);
    private static final Set<String> ROOT_KEYS_V2 = Set.of(
            SCHEMA_VERSION_KEY,
            OWNERSHIP_KEY,
            MATERIALIZATIONS_KEY
    );
    private static final Set<String> POSITION_KEYS = Set.of(POSITION_KEY, LAYERS_KEY);
    private static final Set<String> LAYER_KEYS = Set.of(
            OPERATION_KEY,
            BEFORE_STATE_KEY,
            AFTER_STATE_KEY
    );
    private static final Set<String> MATERIALIZATION_KEYS = Set.of(
            OPERATION_KEY,
            OWNER_TYPE_KEY,
            OWNER_ID_KEY
    );
    private static final Comparator<BlockPos> POSITION_ORDER = Comparator
            .comparingInt((BlockPos position) -> position.getX())
            .thenComparingInt(BlockPos::getY)
            .thenComparingInt(BlockPos::getZ);
    private static final ReadLimits DEFAULT_LIMITS = new ReadLimits(
            MAXIMUM_POSITION_COUNT,
            MAXIMUM_TOTAL_LAYER_COUNT,
            MAXIMUM_STACK_DEPTH
    );

    private final ResourceKey<Level> dimension;
    private final PlacementOwnershipLedger compatibleLedger;
    private final CompoundTag incompatibleRawTag;
    private final String incompatibilityReason;

    private RoadBuilderPlacementSavedData(ResourceKey<Level> dimension) {
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.incompatibleRawTag = null;
        this.incompatibilityReason = null;
        this.compatibleLedger = new PlacementOwnershipLedger(dimension, this::setDirty);
    }

    private RoadBuilderPlacementSavedData(
            ResourceKey<Level> dimension,
            Map<BlockPos, PositionChangeStack> stacks,
            Map<PlacementOperationId, RoadMaterializationOwner> materializationOwners
    ) {
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.incompatibleRawTag = null;
        this.incompatibilityReason = null;
        this.compatibleLedger = PlacementOwnershipLedger.restore(
                dimension,
                stacks,
                materializationOwners,
                this::setDirty
        );
    }

    private RoadBuilderPlacementSavedData(
            ResourceKey<Level> dimension,
            CompoundTag incompatibleRawTag,
            String incompatibilityReason
    ) {
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.compatibleLedger = null;
        this.incompatibleRawTag = Objects.requireNonNull(
                incompatibleRawTag,
                "incompatibleRawTag"
        ).copy();
        this.incompatibilityReason = Objects.requireNonNull(
                incompatibilityReason,
                "incompatibilityReason"
        );
    }

        public static RoadBuilderPlacementSavedData get(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        ResourceKey<Level> dimension = level.dimension();
        return loadOrCreate(
                level.getDataStorage(),
                tag -> load(dimension, tag),
                () -> create(dimension),
                DATA_NAME
        );
    }

        public static RoadBuilderPlacementSavedData create(ResourceKey<Level> dimension) {
        return new RoadBuilderPlacementSavedData(dimension);
    }

        public static RoadBuilderPlacementSavedData load(
            ResourceKey<Level> dimension,
            CompoundTag tag
    ) {
        return load(dimension, tag, DEFAULT_LIMITS);
    }

    static RoadBuilderPlacementSavedData load(
            ResourceKey<Level> dimension,
            CompoundTag tag,
            ReadLimits limits
    ) {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(limits, "limits");
        CompoundTag retainedRawTag = tag.copy();

        try {
            DecodedData decoded = decode(tag, limits);
            return new RoadBuilderPlacementSavedData(
                    dimension,
                    decoded.stacks(),
                    decoded.materializationOwners()
            );
        } catch (RuntimeException exception) {
            String message = exception.getMessage();
            String reason = message == null || message.isBlank()
                    ? exception.getClass().getSimpleName()
                    : message;
            return new RoadBuilderPlacementSavedData(dimension, retainedRawTag, reason);
        }
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public boolean isCompatible() {
        return compatibleLedger != null;
    }

        public PlacementOwnershipLedger ledger() {
        if (compatibleLedger == null) {
            throw new IllegalStateException(
                    "Road Builder placement data is incompatible: " + incompatibilityReason
            );
        }
        return compatibleLedger;
    }

    public Optional<String> incompatibilityReason() {
        return Optional.ofNullable(incompatibilityReason);
    }

        public Optional<CompoundTag> incompatibleRawTag() {
        return Optional.ofNullable(incompatibleRawTag).map(CompoundTag::copy);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        Objects.requireNonNull(tag, "tag");
        if (compatibleLedger == null) {
            return incompatibleRawTag.copy();
        }

        // SavedData supplies an empty compound, but clearing also keeps direct
        // callers from accidentally producing a non-canonical root shape.
        for (String key : List.copyOf(tag.getAllKeys())) {
            tag.remove(key);
        }
        tag.putInt(SCHEMA_VERSION_KEY, CURRENT_SCHEMA_VERSION);

        List<Map.Entry<BlockPos, PositionChangeStack>> entries = new ArrayList<>(
                compatibleLedger.positionStacks().entrySet()
        );
        entries.sort(Map.Entry.comparingByKey(POSITION_ORDER));
        ListTag ownership = new ListTag();
        for (Map.Entry<BlockPos, PositionChangeStack> entry : entries) {
            CompoundTag positionTag = new CompoundTag();
            positionTag.putLong(POSITION_KEY, entry.getKey().asLong());

            ListTag layers = new ListTag();
            for (BlockChangeLayer layer : entry.getValue().layers()) {
                CompoundTag layerTag = new CompoundTag();
                layerTag.putUUID(OPERATION_KEY, layer.operationId().value());
                layerTag.put(BEFORE_STATE_KEY, StrictBlockStateNbtCodec.write(layer.beforeState()));
                layerTag.put(AFTER_STATE_KEY, StrictBlockStateNbtCodec.write(layer.afterState()));
                layers.add(layerTag);
            }
            positionTag.put(LAYERS_KEY, layers);
            ownership.add(positionTag);
        }
        tag.put(OWNERSHIP_KEY, ownership);

        ListTag materializations = new ListTag();
        for (var operation : compatibleLedger.operations()) {
            operation.materializationOwner().ifPresent(owner -> {
                CompoundTag materializationTag = new CompoundTag();
                materializationTag.putUUID(OPERATION_KEY, operation.id().value());
                materializationTag.putString(OWNER_TYPE_KEY, owner.type().serializedName());
                materializationTag.putUUID(OWNER_ID_KEY, owner.ownerId());
                materializations.add(materializationTag);
            });
        }
        tag.put(MATERIALIZATIONS_KEY, materializations);
        return tag;
    }

    private static DecodedData decode(
            CompoundTag root,
            ReadLimits limits
    ) {
        Tag rawSchemaVersion = root.get(SCHEMA_VERSION_KEY);
        if (!(rawSchemaVersion instanceof IntTag schemaVersionTag)) {
            throw malformed("schema_version must be an int");
        }
        int schemaVersion = schemaVersionTag.getAsInt();
        if (schemaVersion != 1 && schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw malformed("unsupported placement ownership schema version "
                    + schemaVersion + "; expected 1 or " + CURRENT_SCHEMA_VERSION);
        }
        requireExactKeys(root, schemaVersion == 1 ? ROOT_KEYS_V1 : ROOT_KEYS_V2, "root");

        Tag rawOwnership = root.get(OWNERSHIP_KEY);
        if (!(rawOwnership instanceof ListTag ownership)) {
            throw malformed("ownership must be a list");
        }
        requireCompoundElements(ownership, "ownership");
        if (ownership.size() > limits.maximumPositions()) {
            throw malformed("ownership position count exceeds " + limits.maximumPositions());
        }

        Map<BlockPos, PositionChangeStack> stacks = new LinkedHashMap<>(ownership.size());
        long totalLayers = 0;
        for (int positionIndex = 0; positionIndex < ownership.size(); positionIndex++) {
            CompoundTag positionTag = ownership.getCompound(positionIndex);
            requireExactKeys(positionTag, POSITION_KEYS, "ownership[" + positionIndex + "]");

            Tag rawPosition = positionTag.get(POSITION_KEY);
            if (!(rawPosition instanceof LongTag positionLong)) {
                throw malformed("ownership[" + positionIndex + "].position must be a long");
            }
            BlockPos position = BlockPos.of(positionLong.getAsLong()).immutable();
            if (stacks.containsKey(position)) {
                throw malformed("duplicate ownership position: " + position.toShortString());
            }

            Tag rawLayers = positionTag.get(LAYERS_KEY);
            if (!(rawLayers instanceof ListTag layers)) {
                throw malformed("ownership[" + positionIndex + "].layers must be a list");
            }
            requireCompoundElements(layers, "ownership[" + positionIndex + "].layers");
            if (layers.isEmpty()) {
                throw malformed("ownership position stack must not be empty");
            }
            if (layers.size() > limits.maximumStackDepth()) {
                throw malformed("ownership stack depth exceeds " + limits.maximumStackDepth());
            }
            totalLayers += layers.size();
            if (totalLayers > limits.maximumTotalLayers()) {
                throw malformed("ownership total layer count exceeds "
                        + limits.maximumTotalLayers());
            }

            List<BlockChangeLayer> decodedLayers = new ArrayList<>(layers.size());
            Set<PlacementOperationId> operationIds = new HashSet<>(layers.size());
            for (int layerIndex = 0; layerIndex < layers.size(); layerIndex++) {
                CompoundTag layerTag = layers.getCompound(layerIndex);
                String path = "ownership[" + positionIndex + "].layers[" + layerIndex + "]";
                requireExactKeys(layerTag, LAYER_KEYS, path);

                PlacementOperationId operationId = readOperationId(
                        layerTag.get(OPERATION_KEY),
                        path + ".operation"
                );
                if (!operationIds.add(operationId)) {
                    throw malformed("operation appears more than once at position "
                            + position.toShortString() + ": " + operationId);
                }

                BlockState beforeState = readBlockState(
                        layerTag.get(BEFORE_STATE_KEY),
                        path + ".before_state"
                );
                BlockState afterState = readBlockState(
                        layerTag.get(AFTER_STATE_KEY),
                        path + ".after_state"
                );
                decodedLayers.add(new BlockChangeLayer(operationId, beforeState, afterState));
            }
            stacks.put(position, PositionChangeStack.restore(decodedLayers));
        }

        validateCrossStackOrdering(stacks.values());
        Map<PlacementOperationId, RoadMaterializationOwner> materializationOwners =
                schemaVersion == 1
                        ? Map.of()
                        : decodeMaterializationOwners(
                                root,
                                stacks,
                                limits.maximumTotalLayers()
                        );
        // This decoder owns stacks exclusively. Map.copyOf uses linear probing,
        // which becomes quadratic for dense BlockPos footprints with clustered hashes.
        // Keep the validated hash map and expose only a read-only view.
        return new DecodedData(Collections.unmodifiableMap(stacks), materializationOwners);
    }

    private static Map<PlacementOperationId, RoadMaterializationOwner> decodeMaterializationOwners(
            CompoundTag root,
            Map<BlockPos, PositionChangeStack> stacks,
            int maximumMaterializations
    ) {
        Tag rawMaterializations = root.get(MATERIALIZATIONS_KEY);
        if (!(rawMaterializations instanceof ListTag materializations)) {
            throw malformed("materializations must be a list");
        }
        requireCompoundElements(materializations, "materializations");
        if (materializations.size() > maximumMaterializations) {
            throw malformed(
                    "materialization count exceeds " + maximumMaterializations
            );
        }

        Set<PlacementOperationId> knownOperations = new HashSet<>();
        for (PositionChangeStack stack : stacks.values()) {
            for (BlockChangeLayer layer : stack.layers()) {
                knownOperations.add(layer.operationId());
            }
        }

        Map<PlacementOperationId, RoadMaterializationOwner> owners = new LinkedHashMap<>();
        Set<RoadMaterializationOwner> uniqueOwners = new HashSet<>();
        for (int index = 0; index < materializations.size(); index++) {
            CompoundTag materializationTag = materializations.getCompound(index);
            String path = "materializations[" + index + "]";
            requireExactKeys(materializationTag, MATERIALIZATION_KEYS, path);

            PlacementOperationId operationId = readOperationId(
                    materializationTag.get(OPERATION_KEY),
                    path + ".operation"
            );
            if (!knownOperations.contains(operationId)) {
                throw malformed(path + " references unknown operation " + operationId);
            }
            Tag rawOwnerType = materializationTag.get(OWNER_TYPE_KEY);
            if (!(rawOwnerType instanceof StringTag ownerTypeTag)) {
                throw malformed(path + ".owner_type must be a string");
            }
            RoadMaterializationOwner.Type ownerType = RoadMaterializationOwner.Type
                    .fromSerializedName(ownerTypeTag.getAsString())
                    .orElseThrow(() -> malformed(
                            path + ".owner_type is unknown: " + ownerTypeTag.getAsString()
                    ));
            UUID ownerId = readUuid(
                    materializationTag.get(OWNER_ID_KEY),
                    path + ".owner_id"
            );
            RoadMaterializationOwner owner = new RoadMaterializationOwner(ownerType, ownerId);
            if (owners.put(operationId, owner) != null) {
                throw malformed("duplicate materialization operation " + operationId);
            }
            if (!uniqueOwners.add(owner)) {
                throw malformed("graph owner has multiple materializations: " + owner);
            }
        }
        return Map.copyOf(owners);
    }

    private static PlacementOperationId readOperationId(Tag rawOperation, String path) {
        if (!(rawOperation instanceof IntArrayTag operationArray)
                || operationArray.size() != 4) {
            throw malformed(path + " must be a four-int UUID");
        }
        try {
            return new PlacementOperationId(NbtUtils.loadUUID(operationArray));
        } catch (RuntimeException exception) {
            throw malformed(path + " is not a valid UUID");
        }
    }

    private static UUID readUuid(Tag rawUuid, String path) {
        if (!(rawUuid instanceof IntArrayTag uuidArray) || uuidArray.size() != 4) {
            throw malformed(path + " must be a four-int UUID");
        }
        try {
            return NbtUtils.loadUUID(uuidArray);
        } catch (RuntimeException exception) {
            throw malformed(path + " is not a valid UUID");
        }
    }

    private static BlockState readBlockState(Tag rawState, String path) {
        if (!(rawState instanceof CompoundTag stateTag)) {
            throw malformed(path + " must be a compound");
        }
        DataResult<BlockState> result = StrictBlockStateNbtCodec.read(stateTag);
        Optional<BlockState> decoded = result.result();
        if (decoded.isEmpty()) {
            String detail = result.error().map(error -> error.message())
                    .orElse("unknown block-state error");
            throw malformed(path + " is invalid: " + detail);
        }
        return decoded.orElseThrow();
    }

        private static void validateCrossStackOrdering(Iterable<PositionChangeStack> stacks) {
        Map<PlacementOperationId, Set<PlacementOperationId>> successors = new HashMap<>();
        Map<PlacementOperationId, Integer> incomingEdges = new HashMap<>();

        for (PositionChangeStack stack : stacks) {
            List<BlockChangeLayer> layers = stack.layers();
            for (BlockChangeLayer layer : layers) {
                successors.computeIfAbsent(layer.operationId(), ignored -> new HashSet<>());
                incomingEdges.putIfAbsent(layer.operationId(), 0);
            }
            for (int index = 1; index < layers.size(); index++) {
                PlacementOperationId older = layers.get(index - 1).operationId();
                PlacementOperationId newer = layers.get(index).operationId();
                if (successors.get(older).add(newer)) {
                    incomingEdges.merge(newer, 1, Integer::sum);
                }
            }
        }

        ArrayDeque<PlacementOperationId> ready = new ArrayDeque<>();
        incomingEdges.forEach((operationId, count) -> {
            if (count == 0) {
                ready.add(operationId);
            }
        });
        int visited = 0;
        while (!ready.isEmpty()) {
            PlacementOperationId operationId = ready.removeFirst();
            visited++;
            for (PlacementOperationId successor : successors.get(operationId)) {
                int remaining = incomingEdges.merge(successor, -1, Integer::sum);
                if (remaining == 0) {
                    ready.add(successor);
                }
            }
        }
        if (visited != incomingEdges.size()) {
            throw malformed("operation ordering contains a cross-stack cycle");
        }
    }

    private static void requireCompoundElements(ListTag list, String path) {
        if (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) {
            throw malformed(path + " entries must be compounds");
        }
    }

    private static void requireExactKeys(
            CompoundTag tag,
            Set<String> expectedKeys,
            String path
    ) {
        if (!tag.getAllKeys().equals(expectedKeys)) {
            throw malformed(path + " must contain exactly " + expectedKeys);
        }
    }

    private static IllegalArgumentException malformed(String message) {
        return new IllegalArgumentException(message);
    }

    private record DecodedData(
            Map<BlockPos, PositionChangeStack> stacks,
            Map<PlacementOperationId, RoadMaterializationOwner> materializationOwners
    ) {
    }

    record ReadLimits(int maximumPositions, int maximumTotalLayers, int maximumStackDepth) {
        ReadLimits {
            if (maximumPositions < 0 || maximumTotalLayers < 0 || maximumStackDepth < 1) {
                throw new IllegalArgumentException("invalid placement SavedData read limits");
            }
        }
    }
}
