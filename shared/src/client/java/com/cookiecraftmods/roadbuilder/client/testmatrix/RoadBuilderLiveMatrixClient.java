package com.cookiecraftmods.roadbuilder.client.testmatrix;

import com.cookiecraftmods.roadbuilder.client.networking.RoadClientTransport;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import com.cookiecraftmods.roadbuilder.RoadBuilder;
import com.cookiecraftmods.roadbuilder.client.networking.RoadBuilderClientNetworking;
import com.cookiecraftmods.roadbuilder.geometry.FreehandGeometry;
import com.cookiecraftmods.roadbuilder.geometry.MultiCurveGeometry;
import com.cookiecraftmods.roadbuilder.geometry.QuadraticBezierGeometry;
import com.cookiecraftmods.roadbuilder.geometry.ReferenceGeometry;
import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.StraightGeometry;
import com.cookiecraftmods.roadbuilder.network.RoadGraphSnapshot;
import com.cookiecraftmods.roadbuilder.network.RoadSegment;
import com.cookiecraftmods.roadbuilder.networking.ConstructionJobCancelC2SPacket;
import com.cookiecraftmods.roadbuilder.networking.ConstructionJobCancelResultCode;
import com.cookiecraftmods.roadbuilder.networking.ConstructionJobProgressS2CPacket;
import com.cookiecraftmods.roadbuilder.networking.PlacementResultCode;
import com.cookiecraftmods.roadbuilder.networking.PlacementResultS2CPacket;
import com.cookiecraftmods.roadbuilder.networking.RemovalRequestAction;
import com.cookiecraftmods.roadbuilder.networking.RemovalResultCode;
import com.cookiecraftmods.roadbuilder.networking.RemovalResultS2CPacket;
import com.cookiecraftmods.roadbuilder.networking.RoadGraphDeltaS2CPacket;
import com.cookiecraftmods.roadbuilder.networking.RoadGraphResyncC2SPacket;
import com.cookiecraftmods.roadbuilder.networking.RoadGraphSnapshotS2CPacket;
import com.cookiecraftmods.roadbuilder.networking.RoadPathIntentC2SPacket;
import com.cookiecraftmods.roadbuilder.networking.SegmentRemovalIntentC2SPacket;
import com.cookiecraftmods.roadbuilder.networking.StraightRoadIntentC2SPacket;
import com.cookiecraftmods.roadbuilder.placement.BlockPlanResult;
import com.cookiecraftmods.roadbuilder.placement.BlockPlacementPlan;
import com.cookiecraftmods.roadbuilder.placement.ConstructionLimits;
import com.cookiecraftmods.roadbuilder.placement.RemovalMode;
import com.cookiecraftmods.roadbuilder.placement.StraightRoadSegmentMaterializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class RoadBuilderLiveMatrixClient {
    private static final int TIMEOUT_TICKS = 20 * 300;
    private static final ResourceLocation PRESET =
            MinecraftIds.id("roadbuilder", "marked_two_way");
    private static final int PRESET_REVISION = 1;
    private static RoadBuilderLiveMatrixClient instance;

    private final String role;
    private final String phase;
    private final List<ToolCase> tools;
    private int ticks;
    private int step;
    private int waitTicks;
    private long revisionBeforeAction = -1L;
    private int segmentsBeforeAction = -1;
    private UUID pendingRequest;
    private UUID acceptedOperation;
    private UUID cancelRequest;
    private UUID demolitionPreviewRequest;
    private UUID demolitionExecuteRequest;
    private RoadSegment demolitionSegment;
    private PlacementResultS2CPacket firstPlacementResult;
    private boolean reconnectStarted;
    private boolean reconnectCompleted;
    private int initialConnectAttempts;
    private int nextInitialConnectTick = 40;
    private boolean readyEvidenceWritten;
    private boolean stopping;

    private RoadBuilderLiveMatrixClient(String role, String phase) {
        this.role = role;
        this.phase = phase;
        this.tools = toolCases();
    }

    public static synchronized void registerIfEnabled() {
        if (instance != null
                || !Boolean.getBoolean("roadbuilder.matrix.enabled")) {
            return;
        }
        String role = System.getProperty("roadbuilder.matrix.role", "");
        if (!"actor".equals(role) && !"observer".equals(role)) {
            return;
        }
        instance = new RoadBuilderLiveMatrixClient(
                role,
                System.getProperty("roadbuilder.matrix.phase", "place")
        );
    }

    public static void tickIfEnabled(Minecraft client) {
        RoadBuilderLiveMatrixClient active = instance;
        if (active == null || active.stopping) {
            return;
        }
        active.tick(client);
    }

    private void tick(Minecraft client) {
        try {
            if (++ticks > TIMEOUT_TICKS) {
                throw new IllegalStateException(
                        role + " timed out in " + phase + " step " + step
                );
            }
            if (client.level == null
                    && client.getConnection() == null
                    && !reconnectStarted
                    && !readyEvidenceWritten
                    && !(client.screen instanceof ConnectScreen)
                    && client.getOverlay() == null
                    && ticks >= nextInitialConnectTick) {
                require(initialConnectAttempts < 3, "could not connect to the matrix server after three attempts");
                initialConnectAttempts++;
                nextInitialConnectTick = ticks + 100;
                reconnect(client);
                return;
            }
            if (ready(client) && !readyEvidenceWritten) {
                readyEvidenceWritten = true;
                writeEvidence(
                        "client-ready-" + role,
                        Instant.now().toString()
                );
            }
            if ("place".equals(phase)) {
                if ("actor".equals(role)) {
                    tickPlaceActor(client);
                } else {
                    tickPlaceObserver(client);
                }
            } else if ("reload".equals(phase)) {
                if ("actor".equals(role)) {
                    tickReloadActor(client);
                } else {
                    tickReloadObserver(client);
                }
            } else {
                throw new IllegalStateException("unknown matrix phase " + phase);
            }
        } catch (RuntimeException exception) {
            fail(client, exception.getMessage());
        }
    }

    private void tickPlaceActor(Minecraft client) {
        if (!ready(client)) {
            return;
        }
        RoadGraphSnapshot graph = graph(client).orElseThrow();
        switch (step) {
            case 0 -> {
                requireChannels();
                writeEvidence("channel-negotiation", "all channels advertised");
                step++;
            }
            case 1, 2, 3, 4 -> runPlacementTool(
                    client,
                    graph,
                    step - 1
            );
            case 5 -> beginExactReplay(graph);
            case 6 -> finishExactReplay(graph);
            case 7 -> beginCancellation(graph);
            case 8 -> requestCancellation(client);
            case 9 -> finishCancellation(graph);
            case 10 -> {
                writeEvidence(
                        "actor-tools",
                        "straight,simple_curve,complex_curve,freehand"
                );
                writeEvidence("actor-complete", Instant.now().toString());
                stop(client);
            }
            default -> throw new IllegalStateException(
                    "invalid actor place step " + step
            );
        }
    }

    private void runPlacementTool(
            Minecraft client,
            RoadGraphSnapshot graph,
            int toolIndex
    ) {
        ToolCase tool = tools.get(toolIndex);
        if (pendingRequest == null) {
            pendingRequest = requestId("placement-" + tool.name());
            revisionBeforeAction = graph.revision();
            segmentsBeforeAction = graph.segments().size();
            waitTicks = 0;
            if (!sendTool(tool, pendingRequest)) {
                throw new IllegalStateException(
                        "could not send " + tool.name()
                );
            }
            return;
        }

        PlacementResultS2CPacket result =
                RoadBuilderClientNetworking.placementResult(pendingRequest)
                        .orElse(null);
        if (result == null) {
            return;
        }
        require(
                result.resultCode() == PlacementResultCode.ACCEPTED,
                tool.name() + " was rejected: " + result.resultCode()
        );
        int serverBlocks = result.acceptedPlacement()
                .orElseThrow()
                .affectedBlockCount();
        if (graph.revision() <= revisionBeforeAction
                || graph.segments().size() <= segmentsBeforeAction) {
            return;
        }

        if (tool.surface == null) {
            tool.surface = materializedSurface(graph, tool);
            require(serverBlocks >= tool.surface.affectedBlockCount(),
                    tool.name() + " reported fewer changes than its road surface covers");
        }
        var unmatched = tool.surface.blocks().stream()
                .filter(block -> !client.level.getBlockState(block.position()).equals(block.targetState()))
                .findFirst();
        if (unmatched.isPresent()) {
            require(++waitTicks <= 80, tool.name() + " surface did not synchronize at " + unmatched.get().position());
            return;
        }

        writeEvidence(
                "tool-" + tool.name(),
                "surface_blocks=" + tool.surface.affectedBlockCount()
                        + ",server_blocks=" + serverBlocks
                        + ",graph_revision=" + graph.revision()
                        + ",points=" + tool.pointCount()
        );
        if (toolIndex == 0) {
            firstPlacementResult = result;
        }
        pendingRequest = null;
        acceptedOperation = null;
        step++;
    }

    private void beginExactReplay(RoadGraphSnapshot graph) {
        ToolCase first = tools.get(0);
        UUID replayId = requestId("placement-" + first.name());
        revisionBeforeAction = graph.revision();
        require(
                sendTool(first, replayId),
                "could not send exact replay"
        );
        waitTicks = 0;
        step++;
    }

    private void finishExactReplay(RoadGraphSnapshot graph) {
        if (++waitTicks < 20) {
            return;
        }
        PlacementResultS2CPacket replay =
                RoadBuilderClientNetworking.placementResult(
                        requestId("placement-" + tools.get(0).name())
                ).orElseThrow(() -> new IllegalStateException(
                        "exact replay result was not returned"
                ));
        require(
                firstPlacementResult != null
                        && replay.acceptedPlacement().equals(
                        firstPlacementResult.acceptedPlacement()
                ),
                "exact replay did not return the original operation"
        );
        require(
                graph.revision() == revisionBeforeAction,
                "exact replay mutated the graph"
        );
        writeEvidence("exact-replay", "same operation; no second mutation");
        step++;
    }

    private void beginCancellation(RoadGraphSnapshot graph) {
        UUID request = requestId("cancel-placement");
        StraightGeometry geometry = new StraightGeometry(
                new RoadPoint(4.5, 65.0, 72.5),
                new RoadPoint(96.5, 68.0, 72.5)
        );
        revisionBeforeAction = graph.revision();
        segmentsBeforeAction = graph.segments().size();
        pendingRequest = request;
        require(
                RoadBuilderClientNetworking.sendStraightRoadIntent(
                        straightPacket(request, geometry)
                ),
                "could not send cancellable placement"
        );
        step++;
    }

    private void requestCancellation(Minecraft client) {
        PlacementResultS2CPacket result =
                RoadBuilderClientNetworking.placementResult(pendingRequest)
                        .orElse(null);
        if (result == null) {
            return;
        }
        require(
                result.resultCode() == PlacementResultCode.ACCEPTED,
                "cancellable placement rejected: " + result.resultCode()
        );
        var accepted = result.acceptedPlacement().orElseThrow();
        require(accepted.affectedBlockCount() > 256, "cancellation fixture was not incremental");
        acceptedOperation = accepted.operationId();
        cancelRequest = requestId("cancel-request");
        require(
                RoadBuilderClientNetworking.sendConstructionJobCancel(
                        ConstructionJobCancelC2SPacket.current(
                                cancelRequest,
                                client.level.dimension().location(),
                                acceptedOperation
                        )
                ),
                "could not send job cancellation"
        );
        waitTicks = 0;
        step++;
    }

    private void finishCancellation(RoadGraphSnapshot graph) {
        var cancelResult =
                RoadBuilderClientNetworking.jobCancelResult(cancelRequest)
                        .orElse(null);
        if (cancelResult == null) {
            return;
        }
        require(
                cancelResult.resultCode()
                        == ConstructionJobCancelResultCode.ACCEPTED
                        || cancelResult.resultCode()
                        == ConstructionJobCancelResultCode.ALREADY_CANCELLING
                        || cancelResult.resultCode()
                        == ConstructionJobCancelResultCode.ALREADY_CANCELLED,
                "job cancellation rejected: " + cancelResult.resultCode()
        );
        Optional<ConstructionJobProgressS2CPacket> progress =
                RoadBuilderClientNetworking.jobProgress(acceptedOperation);
        boolean cancelled = progress
                .map(value -> value.phase()
                        == com.cookiecraftmods.roadbuilder.placement
                        .ConstructionJobPhase.CANCELLED)
                .orElse(false);
        if (!cancelled && ++waitTicks < 80) {
            return;
        }
        require(
                graph.revision() == revisionBeforeAction
                        && graph.segments().size() == segmentsBeforeAction,
                "cancelled placement published graph state"
        );
        writeEvidence(
                "job-cancellation",
                "accepted and rolled back before graph publication"
        );
        pendingRequest = null;
        step++;
    }

    private void tickPlaceObserver(Minecraft client) {
        if (reconnectStarted && client.level == null) {
            if (++waitTicks >= 20) {
                reconnect(client);
                waitTicks = Integer.MIN_VALUE / 2;
            }
            return;
        }
        if (!ready(client)) {
            return;
        }
        RoadGraphSnapshot graph = graph(client).orElseThrow();
        switch (step) {
            case 0 -> {
                requireChannels();
                pendingRequest = requestId("survival-authority");
                StraightGeometry unauthorized = new StraightGeometry(
                        new RoadPoint(4.5, 65.0, -18.5),
                        new RoadPoint(20.5, 66.0, -18.5)
                );
                require(
                        RoadBuilderClientNetworking.sendStraightRoadIntent(
                                straightPacket(pendingRequest, unauthorized)
                        ),
                        "observer could not send authority probe"
                );
                step++;
            }
            case 1 -> {
                PlacementResultS2CPacket result =
                        RoadBuilderClientNetworking
                                .placementResult(pendingRequest)
                                .orElse(null);
                if (result == null) {
                    return;
                }
                require(
                        result.resultCode()
                                == PlacementResultCode.NOT_AUTHORIZED,
                        "Survival authority probe returned "
                                + result.resultCode()
                );
                writeEvidence(
                        "creative-survival-authority",
                        "Survival rejected; actor is Creative"
                );
                step++;
            }
            case 2 -> {
                if (graph.segments().isEmpty()) {
                    return;
                }
                reconnectStarted = true;
                waitTicks = 0;
                client.getConnection().getConnection().disconnect(
                        Component.literal("Road Builder matrix reconnect")
                );
                step++;
            }
            case 3 -> {
                if (!reconnectCompleted) {
                    reconnectCompleted = true;
                    writeEvidence(
                            "observer-reconnect",
                            "full snapshot restored "
                                    + graph.segments().size()
                                    + " segment(s)"
                    );
                }
                if (graph.segments().size() < tools.size()
                        || !exists("actor-tools")) {
                    return;
                }
                writeEvidence(
                        "observer-delta",
                        "revision=" + graph.revision()
                                + ",segments=" + graph.segments().size()
                );
                writeEvidence("observer-complete", Instant.now().toString());
                stop(client);
            }
            default -> throw new IllegalStateException(
                    "invalid observer place step " + step
            );
        }
    }

    private void tickReloadActor(Minecraft client) {
        if (!ready(client)) {
            return;
        }
        RoadGraphSnapshot graph = graph(client).orElseThrow();
        switch (step) {
            case 0 -> {
                require(
                        graph.segments().size() >= tools.size(),
                        "save/reload lost road segments"
                );
                writeEvidence(
                        "save-reload",
                        "revision=" + graph.revision()
                                + ",segments=" + graph.segments().size()
                );
                writeEvidence("dimension-request", "nether");
                step++;
            }
            case 1 -> {
                if (!client.level.dimension().equals(Level.NETHER)
                        || RoadBuilderClientNetworking
                        .roadGraph(Level.NETHER)
                        .isEmpty()) {
                    return;
                }
                writeEvidence(
                        "dimension-nether-observed",
                        "empty/full graph snapshot received"
                );
                step++;
            }
            case 2 -> {
                if (!client.level.dimension().equals(Level.OVERWORLD)) {
                    return;
                }
                RoadGraphSnapshot restored =
                        RoadBuilderClientNetworking.roadGraph(Level.OVERWORLD)
                                .orElse(null);
                if (restored == null
                        || restored.segments().size() < tools.size()) {
                    return;
                }
                writeEvidence(
                        "dimension-round-trip",
                        "overworld graph restored at revision "
                                + restored.revision()
                );
                step++;
            }
            case 3 -> beginDemolition(graph);
            case 4 -> executeDemolition(client, graph);
            case 5 -> finishDemolition(graph);
            case 6 -> {
                if (!exists("observer-complete")) {
                    return;
                }
                writeEvidence("actor-complete", Instant.now().toString());
                stop(client);
            }
            default -> throw new IllegalStateException(
                    "invalid actor reload step " + step
            );
        }
    }

    private void beginDemolition(RoadGraphSnapshot graph) {
        demolitionSegment = graph.segments().values().stream()
                .min(Comparator.comparing(segment ->
                        segment.id().value().toString()))
                .orElseThrow();
        segmentsBeforeAction = graph.segments().size();
        revisionBeforeAction = graph.revision();
        demolitionPreviewRequest = requestId("demolition-preview");
        require(
                RoadBuilderClientNetworking.sendSegmentRemovalIntent(
                        removalPacket(
                                demolitionPreviewRequest,
                                graph,
                                demolitionSegment,
                                RemovalRequestAction.PREVIEW
                        )
                ),
                "could not send demolition preview"
        );
        step++;
    }

    private void executeDemolition(
            Minecraft client,
            RoadGraphSnapshot graph
    ) {
        RemovalResultS2CPacket preview =
                RoadBuilderClientNetworking.removalResult(
                        demolitionPreviewRequest
                ).orElse(null);
        if (preview == null) {
            return;
        }
        require(
                preview.resultCode() == RemovalResultCode.SUCCESS,
                "demolition preview failed: " + preview.resultCode()
        );
        demolitionExecuteRequest = requestId("demolition-execute");
        require(
                RoadBuilderClientNetworking.sendSegmentRemovalIntent(
                        removalPacket(
                                demolitionExecuteRequest,
                                graph,
                                demolitionSegment,
                                RemovalRequestAction.EXECUTE
                        )
                ),
                "could not execute demolition"
        );
        step++;
    }

    private void finishDemolition(RoadGraphSnapshot graph) {
        RemovalResultS2CPacket result =
                RoadBuilderClientNetworking.removalResult(
                        demolitionExecuteRequest
                ).orElse(null);
        if (result == null) {
            return;
        }
        require(
                result.resultCode() == RemovalResultCode.SUCCESS
                        || result.resultCode() == RemovalResultCode.QUEUED,
                "demolition failed: " + result.resultCode()
        );
        if (graph.revision() <= revisionBeforeAction
                || graph.segments().size() >= segmentsBeforeAction) {
            return;
        }
        writeEvidence(
                "graph-demolition",
                "result=" + result.resultCode()
                        + ",operation=" + result.operationId()
                        + ",remaining_segments="
                        + graph.segments().size()
        );
        writeEvidence("demolition-observed", "ok");
        step++;
    }

    private void tickReloadObserver(Minecraft client) {
        if (!ready(client)) {
            return;
        }
        RoadGraphSnapshot graph = graph(client).orElseThrow();
        switch (step) {
            case 0 -> {
                require(
                        graph.segments().size() >= tools.size(),
                        "observer save/reload snapshot lost roads"
                );
                segmentsBeforeAction = graph.segments().size();
                revisionBeforeAction = graph.revision();
                writeEvidence(
                        "observer-reload-snapshot",
                        "revision=" + graph.revision()
                                + ",segments=" + graph.segments().size()
                );
                step++;
            }
            case 1 -> {
                if (!exists("demolition-observed")
                        || graph.revision() <= revisionBeforeAction
                        || graph.segments().size() >= segmentsBeforeAction) {
                    return;
                }
                writeEvidence(
                        "observer-demolition-delta",
                        "revision=" + graph.revision()
                                + ",segments=" + graph.segments().size()
                );
                writeEvidence("observer-complete", Instant.now().toString());
                stop(client);
            }
            default -> throw new IllegalStateException(
                    "invalid observer reload step " + step
            );
        }
    }

    private boolean ready(Minecraft client) {
        return client.level != null
                && client.player != null
                && client.getConnection() != null
                && graph(client).isPresent();
    }

    private Optional<RoadGraphSnapshot> graph(Minecraft client) {
        if (client.level == null) {
            return Optional.empty();
        }
        return RoadBuilderClientNetworking.roadGraph(
                client.level.dimension()
        );
    }

    private void requireChannels() {
        require(
                RoadBuilderClientNetworking.canSendStraightRoadIntent()
                        && RoadBuilderClientNetworking
                        .canSendRoadPathIntent()
                        && RoadBuilderClientNetworking
                        .canSendSegmentRemovalIntent()
                        && RoadBuilderClientNetworking
                        .canSendConstructionJobCancel()
                        && RoadClientTransport.canSend(
                        RoadGraphResyncC2SPacket.TYPE
                ),
                "not all road channels were negotiated"
        );
    }

    private boolean sendTool(ToolCase tool, UUID requestId) {
        if (tool.geometry() instanceof StraightGeometry straight) {
            return RoadBuilderClientNetworking.sendStraightRoadIntent(
                    straightPacket(requestId, straight)
            );
        }
        RoadPathIntentC2SPacket.PathType type =
                switch (tool.name()) {
                    case "simple_curve" ->
                            RoadPathIntentC2SPacket.PathType.QUADRATIC;
                    case "complex_curve" ->
                            RoadPathIntentC2SPacket.PathType.MULTI;
                    case "freehand" ->
                            RoadPathIntentC2SPacket.PathType.FREEHAND;
                    default -> throw new IllegalStateException(
                            "unknown tool " + tool.name()
                    );
                };
        return RoadBuilderClientNetworking.sendRoadPathIntent(
                RoadPathIntentC2SPacket.current(
                        requestId,
                        Level.OVERWORLD.location(),
                        PRESET,
                        PRESET_REVISION,
                        type,
                        controlPoints(tool.geometry())
                )
        );
    }

    private static StraightRoadIntentC2SPacket straightPacket(
            UUID requestId,
            StraightGeometry geometry
    ) {
        return StraightRoadIntentC2SPacket.current(
                requestId,
                Level.OVERWORLD.location(),
                PRESET,
                PRESET_REVISION,
                geometry.start().x(),
                geometry.start().y(),
                geometry.start().z(),
                geometry.end().x(),
                geometry.end().y(),
                geometry.end().z()
        );
    }

    private static SegmentRemovalIntentC2SPacket removalPacket(
            UUID requestId,
            RoadGraphSnapshot graph,
            RoadSegment segment,
            RemovalRequestAction action
    ) {
        return SegmentRemovalIntentC2SPacket.current(
                requestId,
                Level.OVERWORLD.location(),
                segment.id().value(),
                graph.revision(),
                action,
                RemovalMode.SAFE
        );
    }

    private static BlockPlacementPlan materializedSurface(RoadGraphSnapshot graph, ToolCase tool) {
        ReferenceGeometry geometry = tool.geometry();
        RoadSegment segment = graph.segments().values().stream()
                .filter(road -> road.presetId().equals(PRESET) && road.presetRevision() == PRESET_REVISION)
                .filter(road -> road.referenceGeometry().start().distanceTo(geometry.start()) < 0.01
                        && road.referenceGeometry().end().distanceTo(geometry.end()) < 0.01)
                .findFirst().orElseThrow(() -> new IllegalStateException(tool.name() + " is missing from the graph"));
        var result = new StraightRoadSegmentMaterializer().plan(graph.dimension(), segment, ConstructionLimits.defaults());
        if (result instanceof BlockPlanResult.Failure failure) {
            throw new IllegalStateException(tool.name() + " surface planning failed: " + failure.code());
        }
        return ((BlockPlanResult.Success) result).plan();
    }

    private static List<RoadPoint> controlPoints(
            ReferenceGeometry geometry
    ) {
        if (geometry instanceof QuadraticBezierGeometry quadratic) {
            return quadratic.controlPoints();
        }
        if (geometry instanceof MultiCurveGeometry multi) {
            return multi.anchors();
        }
        if (geometry instanceof FreehandGeometry freehand) {
            return freehand.sourcePoints();
        }
        throw new IllegalArgumentException(
                "unsupported path geometry " + geometry.getClass()
        );
    }

    private static List<ToolCase> toolCases() {
        List<RoadPoint> freehand = new ArrayList<>(64);
        for (int index = 0; index < 64; index++) {
            double x = 4.5 + index * 0.75;
            freehand.add(new RoadPoint(
                    x,
                    65.0 + index * 2.0 / 63.0,
                    54.5 + Math.sin(index * 0.31) * 3.0
            ));
        }
        return List.of(
                new ToolCase(
                        "straight",
                        new StraightGeometry(
                                new RoadPoint(4.5, 65.0, 0.5),
                                new RoadPoint(52.5, 67.0, 0.5)
                        )
                ),
                new ToolCase(
                        "simple_curve",
                        new QuadraticBezierGeometry(
                                new RoadPoint(4.5, 65.0, 18.5),
                                new RoadPoint(28.5, 67.0, 25.5),
                                new RoadPoint(52.5, 67.0, 18.5)
                        )
                ),
                new ToolCase(
                        "complex_curve",
                        new MultiCurveGeometry(List.of(
                                new RoadPoint(4.5, 65.0, 36.5),
                                new RoadPoint(20.5, 66.0, 30.5),
                                new RoadPoint(36.5, 67.0, 42.5),
                                new RoadPoint(52.5, 67.0, 36.5)
                        ))
                ),
                new ToolCase(
                        "freehand",
                        new FreehandGeometry(freehand)
                )
        );
    }

    private static void reconnect(Minecraft client) {
        int port = Integer.parseInt(System.getProperty(
                "roadbuilder.matrix.port",
                "25576"
        ));
        MatrixClientConnection.connect(client, port);
    }

    private void stop(Minecraft client) {
        stopping = true;
        client.stop();
    }

    private void fail(Minecraft client, String detail) {
        String safe = detail == null ? "unknown failure" : detail;
        RoadBuilder.LOGGER.error(
                "Road Builder live matrix {} {} failed: {}",
                phase,
                role,
                safe
        );
        writeEvidence("failure-" + role, safe);
        stop(client);
    }

    private void writeEvidence(String name, String value) {
        Path path = evidencePath(name);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(
                    path,
                    value + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "could not write matrix evidence " + path,
                    exception
            );
        }
    }

    private boolean exists(String name) {
        return Files.isRegularFile(evidencePath(name));
    }

    private Path evidencePath(String name) {
        String directory = System.getProperty(
                "roadbuilder.matrix.evidenceDir",
                ""
        );
        if (directory.isBlank()) {
            throw new IllegalStateException(
                    "roadbuilder.matrix.evidenceDir is required"
            );
        }
        return Path.of(directory)
                .resolve(phase + "-" + name + ".txt");
    }

    private UUID requestId(String suffix) {
        return UUID.nameUUIDFromBytes((
                System.getProperty(
                        "roadbuilder.matrix.handedness",
                        "RHT"
                )
                        + ":" + phase + ":" + role + ":" + suffix
        ).getBytes(StandardCharsets.UTF_8));
    }

    private static void require(boolean condition, String detail) {
        if (!condition) {
            throw new IllegalStateException(detail);
        }
    }

    private static final class ToolCase {
        private final String name;
        private final ReferenceGeometry geometry;
        private BlockPlacementPlan surface;

        private ToolCase(String name, ReferenceGeometry geometry) {
            this.name = name;
            this.geometry = geometry;
        }

        private String name() {
            return name;
        }

        private ReferenceGeometry geometry() {
            return geometry;
        }

        private int pointCount() {
            if (geometry instanceof StraightGeometry) {
                return 2;
            }
            return controlPoints(geometry).size();
        }
    }
}
