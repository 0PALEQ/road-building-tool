package com.cookiecraftmods.roadbuilder.client.networking;

import com.cookiecraftmods.roadbuilder.networking.RoadPacket;

import com.cookiecraftmods.roadbuilder.networking.PlacementResultS2CPacket;
import com.cookiecraftmods.roadbuilder.networking.ConstructionJobCancelC2SPacket;
import com.cookiecraftmods.roadbuilder.networking.ConstructionJobCancelResultS2CPacket;
import com.cookiecraftmods.roadbuilder.networking.ConstructionJobProgressS2CPacket;
import com.cookiecraftmods.roadbuilder.networking.RoadGraphDeltaS2CPacket;
import com.cookiecraftmods.roadbuilder.networking.RoadGraphResyncC2SPacket;
import com.cookiecraftmods.roadbuilder.networking.RoadGraphSnapshotS2CPacket;
import com.cookiecraftmods.roadbuilder.networking.RoadGraphSnapshotChunkS2CPacket;
import com.cookiecraftmods.roadbuilder.networking.SegmentRemovalIntentC2SPacket;
import com.cookiecraftmods.roadbuilder.networking.SegmentReplaceIntentC2SPacket;
import com.cookiecraftmods.roadbuilder.networking.SegmentDirectionInvertIntentC2SPacket;
import com.cookiecraftmods.roadbuilder.networking.CrosswalkToggleIntentC2SPacket;
import com.cookiecraftmods.roadbuilder.networking.LaneConnectorEditC2SPacket;
import com.cookiecraftmods.roadbuilder.networking.RemovalIntentC2SPacket;
import com.cookiecraftmods.roadbuilder.networking.RemovalResultS2CPacket;
import com.cookiecraftmods.roadbuilder.networking.StraightRoadIntentC2SPacket;
import com.cookiecraftmods.roadbuilder.networking.ClientRoadIntentC2SPacket;
import com.cookiecraftmods.roadbuilder.client.preset.ClientRoadPacks;
import com.cookiecraftmods.roadbuilder.networking.RoadPathIntentC2SPacket;
import com.cookiecraftmods.roadbuilder.networking.PresetCatalogS2CPacket;
import com.cookiecraftmods.roadbuilder.networking.PresetContentRequestC2SPacket;
import com.cookiecraftmods.roadbuilder.networking.PresetContentS2CPacket;
import com.cookiecraftmods.roadbuilder.networking.PresetSaveIntentC2SPacket;
import com.cookiecraftmods.roadbuilder.networking.PresetSaveResultS2CPacket;
import com.cookiecraftmods.roadbuilder.networking.PresetDeleteIntentC2SPacket;
import com.cookiecraftmods.roadbuilder.preset.RoadPreset;
import com.cookiecraftmods.roadbuilder.preset.RoadPresetCatalog;
import com.cookiecraftmods.roadbuilder.preset.RoadPresetCatalogEntry;
import com.cookiecraftmods.roadbuilder.client.config.RoadPresetSelectionPreferences;
import net.minecraft.client.Minecraft;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class RoadBuilderClientNetworking {
    public static final int MAX_CACHED_PLACEMENT_RESULTS = 256;
    public static final int MAX_CACHED_REMOVAL_RESULTS = 256;
    public static final int MAX_CACHED_JOB_PROGRESS = 128;
    public static final int MAX_CACHED_JOB_CANCEL_RESULTS = 256;
    public static final int MAX_CACHED_PRESET_CONTENT_RESULTS = 64;
    public static final int MAX_CACHED_PRESET_SAVE_RESULTS = 256;

    private static final Map<UUID, PlacementResultS2CPacket> PLACEMENT_RESULTS = new LinkedHashMap<>();
    private static final Map<UUID, RemovalResultS2CPacket> REMOVAL_RESULTS = new LinkedHashMap<>();
    private static final Map<UUID, ConstructionJobProgressS2CPacket> JOB_PROGRESS =
            new LinkedHashMap<>();
    private static final Map<UUID, ConstructionJobCancelResultS2CPacket>
            JOB_CANCEL_RESULTS = new LinkedHashMap<>();
    private static final ClientRoadGraphCache ROAD_GRAPHS = new ClientRoadGraphCache();
    private static final RoadGraphSnapshotChunkAssembler SNAPSHOT_CHUNKS =
            new RoadGraphSnapshotChunkAssembler();
    private static RoadPresetCatalog serverPresetCatalog = new RoadPresetCatalog(0, java.util.List.of());
    private static long localCatalogRevision;
    private static RoadPresetCatalog presetCatalog =
            new RoadPresetCatalog(0, java.util.List.of());
    private static final Map<ResourceLocation, RoadPreset> PRESET_CONTENT =
            new LinkedHashMap<>();
    private static final Map<UUID, PresetContentRequest>
            PRESET_CONTENT_REQUESTS = new LinkedHashMap<>();
    private static final Map<UUID, PresetContentS2CPacket>
            PRESET_CONTENT_RESULTS = new LinkedHashMap<>();
    private static final Map<UUID, PresetSaveResultS2CPacket>
            PRESET_SAVE_RESULTS = new LinkedHashMap<>();
    private static ResourceLocation selectedPresetId;
    private static ResourceLocation preferredPresetId =
            RoadPresetSelectionPreferences.load().orElse(null);
    private static String presetSelectionNotice = "";
    private static ResourceLocation requestedDimension;
    private static boolean registered;

    private RoadBuilderClientNetworking() {
    }

        public static synchronized void register() {
        if (registered) {
            return;
        }

        boolean receiverRegistered = RoadClientTransport.registerGlobalReceiver(
                PlacementResultS2CPacket.TYPE,
                packet -> rememberPlacementResult(packet)
        );
        if (!receiverRegistered) {
            throw new IllegalStateException("placement result receiver is already registered");
        }

        boolean removalReceiverRegistered = RoadClientTransport.registerGlobalReceiver(
                RemovalResultS2CPacket.TYPE,
                packet -> rememberRemovalResult(packet)
        );
        if (!removalReceiverRegistered) {
            throw new IllegalStateException("removal result receiver is already registered");
        }

        boolean jobProgressReceiverRegistered =
                RoadClientTransport.registerGlobalReceiver(
                        ConstructionJobProgressS2CPacket.TYPE,
                        packet -> rememberJobProgress(packet)
                );
        if (!jobProgressReceiverRegistered) {
            throw new IllegalStateException(
                    "construction-job progress receiver is already registered"
            );
        }

        boolean jobCancelResultReceiverRegistered =
                RoadClientTransport.registerGlobalReceiver(
                        ConstructionJobCancelResultS2CPacket.TYPE,
                        packet ->
                                rememberJobCancelResult(packet)
                );
        if (!jobCancelResultReceiverRegistered) {
            throw new IllegalStateException(
                    "construction-job cancel result receiver is already registered"
            );
        }

        boolean snapshotReceiverRegistered = RoadClientTransport.registerGlobalReceiver(
                RoadGraphSnapshotS2CPacket.TYPE,
                packet -> ROAD_GRAPHS.replace(packet.snapshot())
        );
        if (!snapshotReceiverRegistered) {
            throw new IllegalStateException("road graph snapshot receiver is already registered");
        }

        boolean chunkReceiverRegistered =
                RoadClientTransport.registerGlobalReceiver(
                        RoadGraphSnapshotChunkS2CPacket.TYPE,
                        packet ->
                                SNAPSHOT_CHUNKS.acceptAsync(
                                        packet,
                                        ROAD_GRAPHS::replace
                                )
                );
        if (!chunkReceiverRegistered) {
            throw new IllegalStateException(
                    "road graph snapshot chunk receiver is already registered"
            );
        }

        boolean deltaReceiverRegistered = RoadClientTransport.registerGlobalReceiver(
                RoadGraphDeltaS2CPacket.TYPE,
                packet -> receiveGraphDelta(packet)
        );
        if (!deltaReceiverRegistered) {
            throw new IllegalStateException("road graph delta receiver is already registered");
        }

        boolean catalogReceiverRegistered =
                RoadClientTransport.registerGlobalReceiver(
                        PresetCatalogS2CPacket.TYPE,
                        packet ->
                                receivePresetCatalog(packet.catalog())
                );
        if (!catalogReceiverRegistered) {
            throw new IllegalStateException(
                    "preset catalog receiver is already registered"
            );
        }
        boolean contentReceiverRegistered =
                RoadClientTransport.registerGlobalReceiver(
                        PresetContentS2CPacket.TYPE,
                        packet ->
                                rememberPresetContent(packet)
                );
        if (!contentReceiverRegistered) {
            throw new IllegalStateException(
                    "preset content receiver is already registered"
            );
        }
        boolean saveResultReceiverRegistered =
                RoadClientTransport.registerGlobalReceiver(
                        PresetSaveResultS2CPacket.TYPE,
                        packet ->
                                rememberPresetSaveResult(packet)
                );
        if (!saveResultReceiverRegistered) {
            throw new IllegalStateException(
                    "preset save result receiver is already registered"
            );
        }

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clearConnectionState());
        registered = true;
    }

        public static synchronized void tick(Minecraft client) {
        Objects.requireNonNull(client, "client");
        if (client.level == null || client.player == null) {
            requestedDimension = null;
            return;
        }

        ResourceKey<Level> dimension = client.level.dimension();
        ResourceLocation dimensionId = dimension.location();
        if (dimensionId.equals(requestedDimension)
                || !RoadClientTransport.canSend(RoadGraphResyncC2SPacket.TYPE)) {
            return;
        }

        RoadClientTransport.send(RoadGraphResyncC2SPacket.current(
                dimensionId,
                ROAD_GRAPHS.knownRevision(dimension)
        ));
        requestedDimension = dimensionId;
    }

        public static boolean canSendStraightRoadIntent() {
        return RoadClientTransport.canSend(StraightRoadIntentC2SPacket.TYPE);
    }

        public static boolean sendStraightRoadIntent(StraightRoadIntentC2SPacket packet) {
        Objects.requireNonNull(packet, "packet");
        if (!canSendStraightRoadIntent()) {
            return false;
        }

        return sendPresetIntent(packet, packet.presetId());
    }

    public static boolean canSendRoadPathIntent() {
        return RoadClientTransport.canSend(RoadPathIntentC2SPacket.TYPE);
    }

    public static boolean sendRoadPathIntent(
            RoadPathIntentC2SPacket packet
    ) {
        Objects.requireNonNull(packet, "packet");
        if (!canSendRoadPathIntent()) {
            return false;
        }
        return sendPresetIntent(packet, packet.presetId());
    }

    private static boolean sendPresetIntent(
            RoadPacket packet,
            ResourceLocation presetId
    ) {
        RoadPreset local = ClientRoadPacks.presets().get(presetId);
        if (local == null) {
            RoadClientTransport.send(packet);
            return true;
        }
        if (!RoadClientTransport.canSend(ClientRoadIntentC2SPacket.TYPE)) {
            return false;
        }
        final ClientRoadIntentC2SPacket request;
        try {
            request = new ClientRoadIntentC2SPacket(packet, local);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        RoadClientTransport.send(request);
        return true;
    }

    public static synchronized RoadPresetCatalog presetCatalog() {
        return presetCatalog;
    }

    public static synchronized Optional<RoadPresetCatalogEntry>
    selectedPreset() {
        return presetCatalog.entries().stream()
                .filter(entry -> entry.id().equals(selectedPresetId))
                .findFirst();
    }

    public static synchronized boolean selectPreset(ResourceLocation id) {
        Objects.requireNonNull(id, "id");
        boolean exists = presetCatalog.entries().stream()
                .anyMatch(entry -> entry.id().equals(id));
        if (exists) {
            selectedPresetId = id;
            preferredPresetId = id;
            RoadPresetSelectionPreferences.save(id);
            presetSelectionNotice = "";
        }
        return exists;
    }

    public static synchronized String presetSelectionNotice() {
        return presetSelectionNotice;
    }

    public static synchronized boolean requestPresetContent(
            UUID requestId,
            ResourceLocation id
    ) {
        return requestPresetContent(requestId, id, true);
    }

    public static synchronized boolean requestPresetContentIfMissing(
            ResourceLocation id
    ) {
        Objects.requireNonNull(id, "id");
        if (ClientRoadPacks.presets().containsKey(id)) return true;
        if (PRESET_CONTENT.containsKey(id)
                || PRESET_CONTENT_REQUESTS.values().stream()
                .anyMatch(request -> request.presetId().equals(id))) {
            return true;
        }
        return requestPresetContent(UUID.randomUUID(), id, false);
    }

    private static boolean requestPresetContent(
            UUID requestId,
            ResourceLocation id,
            boolean retainResult
    ) {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(id, "id");
        RoadPreset local = ClientRoadPacks.presets().get(id);
        if (local != null) {
            if (retainResult) rememberBounded(PRESET_CONTENT_RESULTS, requestId,
                    PresetContentS2CPacket.current(requestId, id, local), MAX_CACHED_PRESET_CONTENT_RESULTS);
            return true;
        }
        if (!RoadClientTransport.canSend(
                PresetContentRequestC2SPacket.TYPE
        )) {
            return false;
        }
        Optional<PresetContentRequest> request = presetCatalog.entries()
                .stream()
                .filter(entry -> entry.id().equals(id))
                .findFirst()
                .map(entry -> new PresetContentRequest(
                        id,
                        entry.fingerprint(),
                        retainResult
                ));
        if (request.isEmpty()) {
            return false;
        }
        rememberBounded(
                PRESET_CONTENT_REQUESTS,
                requestId,
                request.orElseThrow(),
                MAX_CACHED_PRESET_CONTENT_RESULTS
        );
        RoadClientTransport.send(PresetContentRequestC2SPacket.current(
                requestId,
                id
        ));
        return true;
    }

    public static synchronized void cancelPresetContentRequest(
            UUID requestId
    ) {
        UUID id = Objects.requireNonNull(requestId, "requestId");
        PRESET_CONTENT_REQUESTS.remove(id);
        PRESET_CONTENT_RESULTS.remove(id);
    }

    public static synchronized Optional<RoadPreset> presetContent(
            ResourceLocation id
    ) {
        RoadPreset local = ClientRoadPacks.presets().get(id);
        if (local != null) return Optional.of(local);
        return Optional.ofNullable(PRESET_CONTENT.get(
                Objects.requireNonNull(id, "id")
        ));
    }

    public static synchronized Optional<PresetContentS2CPacket>
    takePresetContent(UUID requestId) {
        return Optional.ofNullable(PRESET_CONTENT_RESULTS.remove(
                Objects.requireNonNull(requestId, "requestId")
        ));
    }

    public static boolean sendPresetSave(PresetSaveIntentC2SPacket packet) {
        Objects.requireNonNull(packet, "packet");
        if (!RoadClientTransport.canSend(PresetSaveIntentC2SPacket.TYPE)) {
            return false;
        }
        RoadClientTransport.send(packet);
        return true;
    }

    public static boolean sendPresetDelete(
            PresetDeleteIntentC2SPacket packet
    ) {
        Objects.requireNonNull(packet, "packet");
        if (!RoadClientTransport.canSend(PresetDeleteIntentC2SPacket.TYPE)) {
            return false;
        }
        RoadClientTransport.send(packet);
        return true;
    }

    public static synchronized Optional<PresetSaveResultS2CPacket>
    presetSaveResult(UUID requestId) {
        return Optional.ofNullable(PRESET_SAVE_RESULTS.get(
                Objects.requireNonNull(requestId, "requestId")
        ));
    }

    public static synchronized Optional<PresetSaveResultS2CPacket>
    takePresetSaveResult(UUID requestId) {
        return Optional.ofNullable(PRESET_SAVE_RESULTS.remove(
                Objects.requireNonNull(requestId, "requestId")
        ));
    }

        public static void requestRoadGraphResync(ResourceKey<Level> dimension) {
        Objects.requireNonNull(dimension, "dimension");
        if (!RoadClientTransport.canSend(RoadGraphResyncC2SPacket.TYPE)) {
            return;
        }
        RoadClientTransport.send(RoadGraphResyncC2SPacket.current(
                dimension.location(),
                ROAD_GRAPHS.knownRevision(dimension)
        ));
    }

        public static boolean canSendRemovalIntent() {
        return RoadClientTransport.canSend(RemovalIntentC2SPacket.TYPE);
    }

        public static boolean sendRemovalIntent(RemovalIntentC2SPacket packet) {
        Objects.requireNonNull(packet, "packet");
        if (!canSendRemovalIntent()) {
            return false;
        }

        RoadClientTransport.send(packet);
        return true;
    }

        public static boolean canSendSegmentRemovalIntent() {
        return RoadClientTransport.canSend(SegmentRemovalIntentC2SPacket.TYPE);
    }

        public static boolean sendSegmentRemovalIntent(SegmentRemovalIntentC2SPacket packet) {
        Objects.requireNonNull(packet, "packet");
        if (!canSendSegmentRemovalIntent()) {
            return false;
        }

        RoadClientTransport.send(packet);
        return true;
    }

    public static boolean canSendSegmentReplaceIntent() {
        return RoadClientTransport.canSend(SegmentReplaceIntentC2SPacket.TYPE);
    }

    public static boolean sendSegmentReplaceIntent(
            SegmentReplaceIntentC2SPacket packet
    ) {
        Objects.requireNonNull(packet, "packet");
        if (!canSendSegmentReplaceIntent()) {
            return false;
        }
        return sendPresetIntent(packet, packet.presetId());
    }

    public static boolean canSendSegmentDirectionInvertIntent() {
        return RoadClientTransport.canSend(
                SegmentDirectionInvertIntentC2SPacket.TYPE
        );
    }

    public static boolean sendSegmentDirectionInvertIntent(
            SegmentDirectionInvertIntentC2SPacket packet
    ) {
        Objects.requireNonNull(packet, "packet");
        if (!canSendSegmentDirectionInvertIntent()) {
            return false;
        }
        RoadClientTransport.send(packet);
        return true;
    }

    public static boolean canSendCrosswalkToggleIntent() {
        return RoadClientTransport.canSend(
                CrosswalkToggleIntentC2SPacket.TYPE
        );
    }

    public static boolean sendCrosswalkToggleIntent(
            CrosswalkToggleIntentC2SPacket packet
    ) {
        Objects.requireNonNull(packet, "packet");
        if (!canSendCrosswalkToggleIntent()) {
            return false;
        }
        RoadClientTransport.send(packet);
        return true;
    }

    public static boolean canSendLaneConnectorEdit() {
        return RoadClientTransport.canSend(LaneConnectorEditC2SPacket.TYPE);
    }

    public static boolean sendLaneConnectorEdit(
            LaneConnectorEditC2SPacket packet
    ) {
        Objects.requireNonNull(packet, "packet");
        if (!canSendLaneConnectorEdit()) {
            return false;
        }
        RoadClientTransport.send(packet);
        return true;
    }

        public static boolean canSendConstructionJobCancel() {
        return RoadClientTransport.canSend(ConstructionJobCancelC2SPacket.TYPE);
    }

        public static boolean sendConstructionJobCancel(
            ConstructionJobCancelC2SPacket packet
    ) {
        Objects.requireNonNull(packet, "packet");
        if (!canSendConstructionJobCancel()) {
            return false;
        }
        RoadClientTransport.send(packet);
        return true;
    }

        public static synchronized Optional<PlacementResultS2CPacket> placementResult(UUID requestId) {
        Objects.requireNonNull(requestId, "requestId");
        return Optional.ofNullable(PLACEMENT_RESULTS.get(requestId));
    }

        public static synchronized Map<UUID, PlacementResultS2CPacket> placementResults() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(PLACEMENT_RESULTS));
    }

        public static synchronized Optional<RemovalResultS2CPacket> removalResult(UUID requestId) {
        Objects.requireNonNull(requestId, "requestId");
        return Optional.ofNullable(REMOVAL_RESULTS.get(requestId));
    }

        public static synchronized Map<UUID, RemovalResultS2CPacket> removalResults() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(REMOVAL_RESULTS));
    }

    public static synchronized Optional<ConstructionJobProgressS2CPacket> jobProgress(
            UUID jobId
    ) {
        return Optional.ofNullable(JOB_PROGRESS.get(Objects.requireNonNull(jobId, "jobId")));
    }

    public static synchronized Map<UUID, ConstructionJobProgressS2CPacket>
    jobProgress() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(JOB_PROGRESS));
    }

    public static synchronized void forgetJobProgress(UUID jobId) {
        JOB_PROGRESS.remove(Objects.requireNonNull(jobId, "jobId"));
    }

    public static synchronized Optional<ConstructionJobCancelResultS2CPacket>
    jobCancelResult(UUID requestId) {
        return Optional.ofNullable(
                JOB_CANCEL_RESULTS.get(Objects.requireNonNull(requestId, "requestId"))
        );
    }

        public static Optional<com.cookiecraftmods.roadbuilder.network.RoadGraphSnapshot> roadGraph(
            ResourceKey<Level> dimension
    ) {
        return ROAD_GRAPHS.snapshot(dimension);
    }

        public static synchronized void clearPlacementResults() {
        PLACEMENT_RESULTS.clear();
    }

        public static synchronized void clearRemovalResults() {
        REMOVAL_RESULTS.clear();
    }

        public static synchronized void clearResults() {
        PLACEMENT_RESULTS.clear();
        REMOVAL_RESULTS.clear();
        JOB_PROGRESS.clear();
        JOB_CANCEL_RESULTS.clear();
        PRESET_CONTENT_REQUESTS.clear();
        PRESET_CONTENT_RESULTS.clear();
        PRESET_SAVE_RESULTS.clear();
    }

        public static synchronized void clearConnectionState() {
        clearResults();
        ROAD_GRAPHS.clear();
        SNAPSHOT_CHUNKS.clear();
        serverPresetCatalog = new RoadPresetCatalog(0, java.util.List.of());
        presetCatalog = new RoadPresetCatalog(0, java.util.List.of());
        PRESET_CONTENT.clear();
        selectedPresetId = null;
        presetSelectionNotice = "";
        requestedDimension = null;
    }

    private static synchronized void rememberPlacementResult(PlacementResultS2CPacket packet) {
        UUID requestId = packet.requestId();

        // Reinsert an existing request so eviction follows result arrival order.
        PLACEMENT_RESULTS.remove(requestId);
        PLACEMENT_RESULTS.put(requestId, packet);

        while (PLACEMENT_RESULTS.size() > MAX_CACHED_PLACEMENT_RESULTS) {
            UUID oldestRequestId = PLACEMENT_RESULTS.keySet().iterator().next();
            PLACEMENT_RESULTS.remove(oldestRequestId);
        }
    }

    private static synchronized void rememberRemovalResult(RemovalResultS2CPacket packet) {
        UUID requestId = packet.requestId();
        REMOVAL_RESULTS.remove(requestId);
        REMOVAL_RESULTS.put(requestId, packet);

        while (REMOVAL_RESULTS.size() > MAX_CACHED_REMOVAL_RESULTS) {
            UUID oldestRequestId = REMOVAL_RESULTS.keySet().iterator().next();
            REMOVAL_RESULTS.remove(oldestRequestId);
        }
    }

    private static synchronized void rememberJobProgress(
            ConstructionJobProgressS2CPacket packet
    ) {
        rememberBounded(
                JOB_PROGRESS,
                packet.jobId(),
                packet,
                MAX_CACHED_JOB_PROGRESS
        );
    }

    private static synchronized void rememberJobCancelResult(
            ConstructionJobCancelResultS2CPacket packet
    ) {
        rememberBounded(
                JOB_CANCEL_RESULTS,
                packet.requestId(),
                packet,
                MAX_CACHED_JOB_CANCEL_RESULTS
        );
    }

    private static <T> void rememberBounded(
            Map<UUID, T> values,
            UUID id,
            T value,
            int maximumSize
    ) {
        values.remove(id);
        values.put(id, value);
        while (values.size() > maximumSize) {
            values.remove(values.keySet().iterator().next());
        }
    }

    private static synchronized void receiveGraphDelta(RoadGraphDeltaS2CPacket packet) {
        ClientRoadGraphCache.DeltaResult result = ROAD_GRAPHS.apply(packet.delta());
        if (result == ClientRoadGraphCache.DeltaResult.APPLIED
                || result == ClientRoadGraphCache.DeltaResult.STALE
                || !RoadClientTransport.canSend(RoadGraphResyncC2SPacket.TYPE)) {
            return;
        }

        ResourceKey<Level> dimension = packet.delta().dimension();
        RoadClientTransport.send(RoadGraphResyncC2SPacket.current(
                dimension.location(),
                ROAD_GRAPHS.knownRevision(dimension)
        ));
        requestedDimension = dimension.location();
    }

    private static synchronized void receivePresetCatalog(RoadPresetCatalog replacement) {
        serverPresetCatalog = replacement;
        refreshClientRoadPacks();
    }

    public static synchronized void refreshClientRoadPacks() {
        applyPresetCatalog(serverPresetCatalog.withClientPacks(++localCatalogRevision,
                ClientRoadPacks.presets()));
    }

    private static synchronized void applyPresetCatalog(RoadPresetCatalog replacement) {
        RoadPresetCatalog priorCatalog = presetCatalog;
        RoadPresetCatalogEntry previous = selectedPreset().orElse(null);
        presetCatalog = replacement;
        if (selectedPresetId == null && preferredPresetId != null) {
            selectedPresetId = replacement.entries().stream()
                    .map(RoadPresetCatalogEntry::id)
                    .filter(preferredPresetId::equals)
                    .findFirst()
                    .orElse(null);
        }
        RoadPresetCatalogEntry same = replacement.entries().stream()
                .filter(entry -> entry.id().equals(selectedPresetId))
                .findFirst()
                .orElse(null);
        boolean incompatible = previous != null && same != null
                && (previous.surfaceWidth() != same.surfaceWidth()
                        || previous.laneCount() != same.laneCount());
        if (same == null || incompatible) {
            selectedPresetId = replacement.entries().stream()
                    .map(RoadPresetCatalogEntry::id)
                    .findFirst()
                    .orElse(null);
            presetSelectionNotice = previous == null
                    ? ""
                    : incompatible
                            ? "Selected road changed incompatibly; using a fallback."
                            : "Selected road is unavailable; using a fallback.";
        }
        PRESET_CONTENT.keySet().removeIf(id -> replacement.entries().stream()
                .filter(entry -> entry.id().equals(id))
                .findFirst()
                .map(replacementEntry -> priorCatalog.entries().stream()
                        .filter(entry -> entry.id().equals(id))
                        .findFirst()
                        .map(priorEntry -> !priorEntry.fingerprint().equals(
                                replacementEntry.fingerprint()
                        ))
                        .orElse(true))
                .orElse(true));
        PRESET_CONTENT_REQUESTS.entrySet().removeIf(request ->
                replacement.entries().stream()
                        .filter(entry -> entry.id().equals(
                                request.getValue().presetId()
                        ))
                        .noneMatch(entry -> entry.fingerprint().equals(
                                request.getValue().fingerprint()
                        ))
        );
    }

    private static synchronized void rememberPresetContent(
            PresetContentS2CPacket packet
    ) {
        PresetContentRequest request = PRESET_CONTENT_REQUESTS.remove(
                packet.requestId()
        );
        if (request == null || !request.presetId().equals(packet.presetId())) {
            return;
        }
        boolean current = presetCatalog.entries().stream()
                .filter(entry -> entry.id().equals(packet.presetId()))
                .anyMatch(entry -> entry.fingerprint().equals(
                                request.fingerprint()
                        )
                        && entry.revision() == packet.preset().revision()
                        && entry.surfaceWidth()
                                == packet.preset().surfaceBlockWidth()
                        && entry.laneCount() == packet.preset().lanes().size());
        if (!current) {
            return;
        }
        PRESET_CONTENT.put(packet.presetId(), packet.preset());
        if (request.retainResult()) {
            rememberBounded(
                    PRESET_CONTENT_RESULTS,
                    packet.requestId(),
                    packet,
                    MAX_CACHED_PRESET_CONTENT_RESULTS
            );
        }
    }

    private static synchronized void rememberPresetSaveResult(
            PresetSaveResultS2CPacket packet
    ) {
        rememberBounded(
                PRESET_SAVE_RESULTS,
                packet.requestId(),
                packet,
                MAX_CACHED_PRESET_SAVE_RESULTS
        );
    }

    private record PresetContentRequest(
            ResourceLocation presetId,
            String fingerprint,
            boolean retainResult
    ) {
    }
}
