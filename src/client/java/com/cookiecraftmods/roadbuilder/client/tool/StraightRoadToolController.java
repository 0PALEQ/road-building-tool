package com.cookiecraftmods.roadbuilder.client.tool;

import com.cookiecraftmods.roadbuilder.client.mode.RoadBuilderModeExitReason;
import com.cookiecraftmods.roadbuilder.client.networking.RoadBuilderClientNetworking;
import com.cookiecraftmods.roadbuilder.client.ui.RoadBuilderToolbarLayout;
import com.cookiecraftmods.roadbuilder.client.ui.RoadBuilderToolbarPresetWindow;
import com.cookiecraftmods.roadbuilder.client.ui.RoadBuilderToolGroup;
import com.cookiecraftmods.roadbuilder.client.ui.RoadPresetEditorScreen;
import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.RoadVector;
import com.cookiecraftmods.roadbuilder.geometry.FreehandGeometry;
import com.cookiecraftmods.roadbuilder.geometry.FreeformGeometry;
import com.cookiecraftmods.roadbuilder.placement.RoadCurveValidation;
import com.cookiecraftmods.roadbuilder.geometry.CubicBezierGeometry;
import com.cookiecraftmods.roadbuilder.geometry.MultiCurveGeometry;
import com.cookiecraftmods.roadbuilder.geometry.ParallelRoadGeometry;
import com.cookiecraftmods.roadbuilder.geometry.QuadraticBezierGeometry;
import com.cookiecraftmods.roadbuilder.geometry.StraightGeometry;
import com.cookiecraftmods.roadbuilder.geometry.ReferenceGeometry;
import com.cookiecraftmods.roadbuilder.geometry.RoundaboutGeometry;
import com.cookiecraftmods.roadbuilder.network.RoadGraphSnapshot;
import com.cookiecraftmods.roadbuilder.network.AuxiliaryLanePorts;
import com.cookiecraftmods.roadbuilder.geometry.LaneConnectionGeometry;
import com.cookiecraftmods.roadbuilder.network.RoadLaneGraph;
import com.cookiecraftmods.roadbuilder.network.RoadNode;
import com.cookiecraftmods.roadbuilder.network.RoadNodeId;
import com.cookiecraftmods.roadbuilder.network.RoadSegment;
import com.cookiecraftmods.roadbuilder.network.RoadSegmentId;
import com.cookiecraftmods.roadbuilder.preset.RoadPresetCatalogEntry;
import com.cookiecraftmods.roadbuilder.preset.RoadCategory;
import com.cookiecraftmods.roadbuilder.networking.AcceptedPlacement;
import com.cookiecraftmods.roadbuilder.networking.ConstructionJobCancelC2SPacket;
import com.cookiecraftmods.roadbuilder.networking.ConstructionJobProgressS2CPacket;
import com.cookiecraftmods.roadbuilder.networking.PlacementResultCode;
import com.cookiecraftmods.roadbuilder.networking.PlacementResultS2CPacket;
import com.cookiecraftmods.roadbuilder.networking.RemovalIntentC2SPacket;
import com.cookiecraftmods.roadbuilder.networking.RemovalRequestAction;
import com.cookiecraftmods.roadbuilder.networking.RemovalResultCode;
import com.cookiecraftmods.roadbuilder.networking.RemovalResultS2CPacket;
import com.cookiecraftmods.roadbuilder.networking.RemovalResultSummary;
import com.cookiecraftmods.roadbuilder.networking.SegmentRemovalIntentC2SPacket;
import com.cookiecraftmods.roadbuilder.networking.SegmentReplaceIntentC2SPacket;
import com.cookiecraftmods.roadbuilder.networking.SegmentDirectionInvertIntentC2SPacket;
import com.cookiecraftmods.roadbuilder.networking.CrosswalkToggleIntentC2SPacket;
import com.cookiecraftmods.roadbuilder.networking.LaneConnectorEditC2SPacket;
import com.cookiecraftmods.roadbuilder.networking.StraightRoadIntentC2SPacket;
import com.cookiecraftmods.roadbuilder.networking.RoadPathIntentC2SPacket;
import com.cookiecraftmods.roadbuilder.placement.ConstructionJobAction;
import com.cookiecraftmods.roadbuilder.placement.ConstructionJobPhase;
import com.cookiecraftmods.roadbuilder.placement.RemovalMode;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class StraightRoadToolController {
    private static final int TRANSIENT_STATUS_TICKS = 80;
    private static final int MAXIMUM_ELEVATION_OFFSET = 128;
    private static final int FALLBACK_PREVIEW_WIDTH = 9;
    private final RoadSnapSettings snapSettings;

    private ClientPacketListener observedConnection;
    private RoadBuilderTool activeTool = RoadBuilderTool.STRAIGHT_ROAD;
    private RoadBuilderToolGroup selectedGroup = RoadBuilderToolGroup.ROADS;
    private RoadCategory roadCategory = RoadCategory.ALL;
    private int presetPage;
    private int keyboardFocusedRoadTool = -1;
    private int elevationOffset;
    private boolean lanePathsVisible;
    private RoadLaneGraph connectorGraph;
    private RoadLaneGraph.ConnectionPoint selectedLaneConnectionPoint;
    private RoadLaneGraph.ConnectionPoint hoveredLaneConnectionPoint;
    private CursorTargetResult cursorResult = CursorTargetResult.failure(CursorTargetProblem.NO_WORLD);
    private CursorTarget startTarget;
    private boolean parallelEnabled;
    private int parallelOffset = 16;
    private RawRoadPreview parallelPreview;
    private boolean parallelPreviewInvalid;
    private CursorTarget previewEndTarget;
    private final PlacementContinuation placementContinuation =
            new PlacementContinuation();
    private ReferenceGeometry previewGeometry;
    private RawRoadPreview rawPreview;
    private final List<CursorTarget> pathPoints = new ArrayList<>();
    private boolean freehandDrawing;
    private StraightRoadPreviewProblem previewProblem = StraightRoadPreviewProblem.none();

    private UUID pendingPlacementRequestId;
    private ResourceLocation pendingPlacementDimension;
    private boolean pendingCrosswalkToggle;
    private boolean pendingDirectionInversion;
    private RoadSnappingMode snappingMode = RoadSnappingMode.ALL;
    private RoadGraphSnapshot lanePortGraph;
    private Map<RoadSegmentId, List<AuxiliaryLanePorts.Port>> lanePorts = Map.of();
    private List<AuxiliaryLanePorts.Port> visibleLanePorts = List.of();
    private AuxiliaryLanePorts.Port hoveredLanePort;
    private AuxiliaryLanePorts.Port startLanePort;
    private RoadGraphSnapshot markerGraph;
    private RoadGraphSnapshot indexedGraph;
    private RoadSnapIndex snapIndex = new RoadSnapIndex(null);
    private List<RoadNode> visibleNodes = List.of();
    private List<RoadNodeMarker> visibleNodeMarkers = List.of();
    private List<RoadSnapGuide> snapGuides = List.of();

    private RemovalUiPhase removalPhase = RemovalUiPhase.UNAVAILABLE;
    private UUID pendingRemovalRequestId;
    private RoadSegment hoveredRemovalSegment;
    private RoadSegment removalTarget;
    private long removalTargetRevision = -1L;
    private RemovalResultSummary removalPreview;
    private final ConstructionJobUiState removalJob = new ConstructionJobUiState();
    private final ConstructionJobUiState placementJob = new ConstructionJobUiState();

    private Component transientStatus;
    private int transientStatusTicks;

    public StraightRoadToolController() {
        this(RoadSnapSettings.DEFAULTS);
    }

    public StraightRoadToolController(RoadSnapSettings snapSettings) {
        this.snapSettings = Objects.requireNonNull(
                snapSettings,
                "snapSettings"
        );
    }

    public void tick(Minecraft client, boolean modeActive) {
        Objects.requireNonNull(client, "client");
        observeConnection(client.getConnection());
        discoverActivePlacementJob(client);
        discoverActiveRemovalJob(client);
        sendRequestedPlacementJobCancellation(client);
        sendRequestedJobCancellation(client);
        processPlacementResult(client);
        processRemovalResult(client);
        processPlacementJobProgress(client);
        processRemovalJobProgress(client);
        if (transientStatusTicks > 0 && --transientStatusTicks == 0) {
            transientStatus = null;
        }

        if (!modeActive) {
            return;
        }
        client.options.keyAttack.setDown(false);
        client.options.keyUse.setDown(false);
        updatePointer(client);
        RoadGraphSnapshot visibleGraph = currentGraph(client).orElse(null);
        if (visibleGraph != markerGraph) {
            markerGraph = visibleGraph;
            visibleNodes = visibleGraph == null
                    ? List.of()
                    : List.copyOf(visibleGraph.nodes().values());
            if (visibleGraph == null) {
                visibleNodeMarkers = List.of();
            } else {
                Map<RoadNodeId, List<RoadSegment>> incidentByNode =
                        new HashMap<>();
                visibleGraph.segments().values().forEach(segment -> {
                    incidentByNode.computeIfAbsent(
                            segment.startNodeId(),
                            ignored -> new ArrayList<>()
                    ).add(segment);
                    incidentByNode.computeIfAbsent(
                            segment.endNodeId(),
                            ignored -> new ArrayList<>()
                    ).add(segment);
                });
                visibleNodeMarkers = visibleNodes.stream().map(node -> {
                        List<RoadSegment> incident = incidentByNode.getOrDefault(
                                node.id(),
                                List.of()
                        );
                        boolean junction = RoadSnapResolver.hasJunctionFootprint(
                                node,
                                incident
                        );
                         return new RoadNodeMarker(
                                 node,
                                 junction ? 0.44 : 0.32,
                                 junction
                         );
                }).toList();
            }
        }
        validateRemovalTarget(client);
        refreshRemovalAvailability(client);
    }

    public void onModeEntered(Minecraft client) {
        Objects.requireNonNull(client, "client");
        clearSelection();
        cursorResult = CursorTargetResult.failure(CursorTargetProblem.MISS);
        client.options.keyAttack.setDown(false);
        client.options.keyUse.setDown(false);
        refreshRemovalAvailability(client);
    }

    public void onModeExited(RoadBuilderModeExitReason reason) {
        Objects.requireNonNull(reason, "reason");
        clearSelection();
        cursorResult = CursorTargetResult.failure(CursorTargetProblem.NO_WORLD);
        if (reason == RoadBuilderModeExitReason.DISCONNECTED) {
            clearConnectionState();
        } else if (removalPhase == RemovalUiPhase.CONFIRM) {
            removalPhase = RemovalUiPhase.AVAILABLE;
            removalTarget = null;
            removalPreview = null;
        }
    }

    public Optional<RoadBuilderTool> keyboardFocusedRoadTool() {
        if (keyboardFocusedRoadTool < 0) {
            return Optional.empty();
        }
        return Optional.of(roadTools().get(keyboardFocusedRoadTool));
    }

    public int elevationOffset() {
        return elevationOffset;
    }

    public boolean lanePathsVisible() {
        return lanePathsVisible;
    }

    public void toggleLanePathsVisibility() {
        lanePathsVisible = !lanePathsVisible;
    }

    public boolean handleToolbarKey(
            Minecraft client,
            int key,
            int action,
            int modifiers
    ) {
        Objects.requireNonNull(client, "client");
        if (selectedGroup == RoadBuilderToolGroup.ROADS
                && isRoadPlacementTool()
                && (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT)
                && (key == GLFW.GLFW_KEY_PAGE_UP
                || key == GLFW.GLFW_KEY_PAGE_DOWN)) {
            int direction = key == GLFW.GLFW_KEY_PAGE_UP ? 1 : -1;
            adjustElevation(client, direction);
            return true;
        }
        if (selectedGroup != RoadBuilderToolGroup.ROADS
                || action != GLFW.GLFW_PRESS
                || (key != GLFW.GLFW_KEY_TAB
                && key != GLFW.GLFW_KEY_LEFT
                && key != GLFW.GLFW_KEY_RIGHT
                && key != GLFW.GLFW_KEY_ENTER
                && key != GLFW.GLFW_KEY_KP_ENTER
                && key != GLFW.GLFW_KEY_SPACE)) {
            return false;
        }
        List<RoadBuilderTool> tools = roadTools();
        if (key == GLFW.GLFW_KEY_TAB) {
            int direction = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0
                    ? -1
                    : 1;
            keyboardFocusedRoadTool = keyboardFocusedRoadTool < 0
                    ? tools.indexOf(activeTool)
                    : Math.floorMod(
                            keyboardFocusedRoadTool + direction,
                            tools.size()
                    );
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) {
            int current = keyboardFocusedRoadTool < 0
                    ? tools.indexOf(activeTool)
                    : keyboardFocusedRoadTool;
            keyboardFocusedRoadTool = Math.floorMod(
                    current + (key == GLFW.GLFW_KEY_LEFT ? -1 : 1),
                    tools.size()
            );
            return true;
        }
        if (keyboardFocusedRoadTool >= 0) {
            activateRoadTool(tools.get(keyboardFocusedRoadTool));
            return true;
        }
        return false;
    }

    private static List<RoadBuilderTool> roadTools() {
        return List.of(
                RoadBuilderTool.STRAIGHT_ROAD,
                RoadBuilderTool.SIMPLE_CURVE,
                RoadBuilderTool.COMPLEX_CURVE,
                RoadBuilderTool.FREEHAND_DRAW,
                RoadBuilderTool.ROUNDABOUT,
                RoadBuilderTool.REPLACE
        );
    }

    public void updatePointer(Minecraft client) {
        Objects.requireNonNull(client, "client");
        AuxiliaryLanePorts.Port previousPort = hoveredLanePort;
        CursorTargetResult nextResult = applySnapping(client, CursorRaycaster.cast(client));
        if (!nextResult.equals(cursorResult) || !Objects.equals(previousPort, hoveredLanePort)) {
            cursorResult = nextResult;
            if (isRoadPlacementTool()) {
                captureFreehandPoint();
                rebuildPreview(client);
            }
        }
        updateHoveredSegment(client);
        updateHoveredLaneConnectionPoint(client);
    }

    public boolean handleMouseButton(
            Minecraft client,
            long windowHandle,
            int button,
            int action
    ) {
        Objects.requireNonNull(client, "client");
        if (windowHandle != client.getWindow().getWindow()
                || client.screen != null
                || client.getOverlay() != null) {
            return false;
        }
        if (action == GLFW.GLFW_RELEASE
                && button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && activeTool == RoadBuilderTool.FREEHAND
                && freehandDrawing) {
            finishFreehand(client);
            return true;
        }
        if (action != GLFW.GLFW_PRESS) {
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            handleLeftClick(client);
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (overParallelToggle(client)) {
                if (pendingPlacementRequestId == null) {
                    parallelOffset = -parallelOffset;
                    rebuildPreview(client);
                }
                return true;
            }
            if (activeTool == RoadBuilderTool.REPLACE
                    && hoveredRemovalSegment != null) {
                requestDirectionInversion(client);
            } else {
                cancelCurrentStep(client);
            }
        }
        return true;
    }

    public boolean handleMouseScroll(
            Minecraft client,
            long windowHandle,
            double verticalOffset
    ) {
        Objects.requireNonNull(client, "client");
        if (windowHandle != client.getWindow().getWindow()
                || client.screen != null
                || client.getOverlay() != null
                || selectedGroup != RoadBuilderToolGroup.ROADS
                || verticalOffset == 0.0) {
            return false;
        }
        Window window = client.getWindow();
        double guiX = client.mouseHandler.xpos()
                * window.getGuiScaledWidth() / window.getScreenWidth();
        double guiY = client.mouseHandler.ypos()
                * window.getGuiScaledHeight() / window.getScreenHeight();
        RoadBuilderToolbarLayout layout = RoadBuilderToolbarLayout.create(
                window.getGuiScaledWidth(),
                window.getGuiScaledHeight()
        );

        if (layout.parallelToggleButton().contains(guiX, guiY)) {
            if (pendingPlacementRequestId == null) {
                int magnitude = Math.max(1, Math.min(256, Math.abs(parallelOffset) + (verticalOffset > 0 ? 1 : -1)));
                parallelOffset = Integer.signum(parallelOffset) * magnitude;
                rebuildPreview(client);
            }
            return true;
        }
        if (!layout.presetPanel().contains(guiX, guiY)) {
            return false;
        }
        changePresetPage(verticalOffset > 0.0 ? -1 : 1);
        return true;
    }

    public boolean parallelEnabled() { return parallelEnabled; }

    public boolean previewInvalid() {
        return previewProblem.kind() != StraightRoadPreviewProblem.Kind.NONE;
    }

    public int parallelOffset() { return parallelOffset; }

    public List<RawRoadPreview> rawPreviews() {
        if (rawPreview == null) return List.of();
        return parallelPreview == null ? List.of(rawPreview) : List.of(rawPreview, parallelPreview);
    }

    private boolean overParallelToggle(Minecraft client) {
        Window window = client.getWindow();
        return selectedGroup == RoadBuilderToolGroup.ROADS
                && RoadBuilderToolbarLayout.create(window.getGuiScaledWidth(), window.getGuiScaledHeight())
                .parallelToggleButton().contains(
                        client.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth(),
                        client.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight());
    }

    public Optional<RawRoadPreview> rawPreview() {
        return Optional.ofNullable(rawPreview);
    }

    public Optional<BlockPos> cursorBlock() {
        return cursorResult.target().map(CursorTarget::blockPosition);
    }

    public Optional<BlockPos> startBlock() {
        return Optional.ofNullable(startTarget).map(CursorTarget::blockPosition);
    }

    public List<RoadNode> visibleNodes() {
        return visibleNodes;
    }

    public List<AuxiliaryLanePorts.Port> visibleLanePorts() {
        return isRoadPlacementTool() ? visibleLanePorts : List.of();
    }

    public List<RoadNodeMarker> visibleNodeMarkers() {
        return visibleNodeMarkers;
    }

    public List<RoadSnapGuide> snapGuides() {
        if ((activeTool != RoadBuilderTool.COMPLEX_CURVE && activeTool != RoadBuilderTool.FREEHAND_DRAW) || startTarget == null) {
            return snapGuides;
        }
        List<RoadSnapGuide> guides = new ArrayList<>(snapGuides);
        if (activeTool == RoadBuilderTool.FREEHAND_DRAW
                && previewGeometry instanceof CubicBezierGeometry cubic) {
            // Display the actual handles, including lane-tangent constraints.
            List<RoadPoint> points = cubic.controlPoints();
            for (int index = 1; index < points.size(); index++) {
                if (horizontalDistance(points.get(index - 1), points.get(index)) > 0.05) {
                    guides.add(new RoadSnapGuide(points.get(index - 1), points.get(index),
                            RoadSnapGuide.Kind.NODE_ANGLE));
                }
            }
            return List.copyOf(guides);
        }
        RoadPoint previous = startTarget.roadPoint();
        List<CursorTarget> controls = new ArrayList<>(pathPoints);
        cursorResult.target().ifPresent(controls::add);
        for (CursorTarget control : controls) {
            RoadPoint point = control.roadPoint();
            if (horizontalDistance(previous, point) > 0.05) {
                guides.add(new RoadSnapGuide(previous, point, RoadSnapGuide.Kind.NODE_ANGLE));
            }
            previous = point;
        }
        return List.copyOf(guides);
    }

    public RoadSnappingMode snappingMode() {
        return snappingMode;
    }

    public Component snappingButtonLabel() {
        return switch (snappingMode) {
            case NONE -> Component.translatable("toolbar.roadbuilder.snap_none");
            case NODES -> Component.translatable("toolbar.roadbuilder.snap_nodes");
            case GUIDELINES -> Component.translatable("toolbar.roadbuilder.snap_guidelines");
            case ANGLES -> Component.translatable("toolbar.roadbuilder.snap_angles");
            case NODES_AND_GUIDELINES -> Component.translatable(
                    "toolbar.roadbuilder.snap_nodes_guidelines"
            );
            case NODES_AND_ANGLES -> Component.translatable("toolbar.roadbuilder.snap_nodes_angles");
            case GUIDELINES_AND_ANGLES -> Component.translatable(
                    "toolbar.roadbuilder.snap_guidelines_angles"
            );
            case ALL -> Component.translatable("toolbar.roadbuilder.snap_all");
        };
    }

    public List<BlockPos> removalConflictSamples() {
        return removalPreview == null ? List.of() : removalPreview.conflictSamples();
    }

    public Optional<ReferenceGeometry> removalHighlightGeometry() {
        RoadSegment highlighted = removalTarget != null
                ? removalTarget
                : hoveredRemovalSegment;
        return Optional.ofNullable(highlighted).map(RoadSegment::referenceGeometry);
    }

    public boolean placementPending() {
        return pendingPlacementRequestId != null;
    }

    public RemovalUiPhase removalPhase() {
        return removalPhase;
    }

    public RoadBuilderTool activeTool() {
        return activeTool;
    }

    public Optional<RoadLaneGraph.ConnectionPoint>
    selectedLaneConnectionPoint() {
        return Optional.ofNullable(selectedLaneConnectionPoint);
    }

    public Optional<RoadLaneGraph.ConnectionPoint>
    hoveredLaneConnectionPoint() {
        return Optional.ofNullable(hoveredLaneConnectionPoint);
    }

    public RoadBuilderToolGroup selectedGroup() {
        return selectedGroup;
    }

    public RoadCategory roadCategory() {
        return roadCategory;
    }

    public boolean bulldozerActive() {
        return activeTool == RoadBuilderTool.BULLDOZER;
    }

    public boolean removeButtonEnabled(Minecraft client) {
        Objects.requireNonNull(client, "client");
        return pendingPlacementRequestId == null
                && removalPhase != RemovalUiPhase.PREVIEW_PENDING
                && removalPhase != RemovalUiPhase.EXECUTE_PENDING
                && RoadBuilderClientNetworking.canSendSegmentRemovalIntent()
                && currentGraph(client).isPresent();
    }

    public boolean cancelButtonEnabled() {
        return startTarget != null
                || removalPhase == RemovalUiPhase.CONFIRM
                || activeTool == RoadBuilderTool.BULLDOZER
                || activeTool == RoadBuilderTool.CROSSWALK
                || activeTool == RoadBuilderTool.LANE_CONNECTOR;
    }

    public boolean cancelInteractiveStep() {
        if (pendingPlacementRequestId != null && placementContinuation.isPending()) {
            placementContinuation.clear();
            clearSelection();
            return true;
        }
        if (startTarget != null) {
            clearSelection();
            return true;
        }
        if (removalPhase == RemovalUiPhase.CONFIRM) {
            cancelRemovalConfirmation();
            return true;
        }
        if (removalJob.isActive()) {
            removalJob.requestCancellation();
            return true;
        }
        if (placementJob.isActive()) {
            placementJob.requestCancellation();
            return true;
        }
        if (activeTool == RoadBuilderTool.BULLDOZER
                || activeTool == RoadBuilderTool.CROSSWALK
                || activeTool == RoadBuilderTool.LANE_CONNECTOR) {
            if (selectedLaneConnectionPoint != null) {
                selectedLaneConnectionPoint = null;
                return true;
            }
            activateStraightRoad();
            return true;
        }
        return false;
    }

    public Component removeButtonLabel() {
        return switch (removalPhase) {
            case CONFIRM -> Component.translatable("toolbar.roadbuilder.confirm_remove");
            case PREVIEW_PENDING -> Component.translatable("toolbar.roadbuilder.previewing");
            case EXECUTE_PENDING -> Component.translatable("toolbar.roadbuilder.removing");
            case UNAVAILABLE, AVAILABLE -> Component.translatable("toolbar.roadbuilder.bulldozer");
        };
    }

    public Component status(Minecraft client) {
        Objects.requireNonNull(client, "client");
        if (transientStatus != null) {
            return transientStatus;
        }
        if (pendingPlacementRequestId != null) {
            return Component.translatable("status.roadbuilder.placing");
        }
        if (placementJob.isActive()) {
            return Component.translatable("status.roadbuilder.placing_in_batches");
        }
        if (removalPhase == RemovalUiPhase.PREVIEW_PENDING) {
            return Component.translatable("status.roadbuilder.previewing_removal");
        }
        if (removalPhase == RemovalUiPhase.EXECUTE_PENDING) {
            return Component.translatable("status.roadbuilder.removing");
        }
        if (removalPhase == RemovalUiPhase.CONFIRM && removalPreview != null) {
            return Component.translatable(
                    "status.roadbuilder.confirm_removal",
                    removalPreview.conflictCount()
            );
        }
        if (activeTool == RoadBuilderTool.BULLDOZER) {
            if (!RoadBuilderClientNetworking.canSendSegmentRemovalIntent()) {
                return Component.translatable("status.roadbuilder.removal_unsupported");
            }
            if (currentGraph(client).isEmpty()) {
                return Component.translatable("status.roadbuilder.waiting_for_graph");
            }
            return hoveredRemovalSegment == null
                    ? Component.translatable("status.roadbuilder.hover_segment")
                    : Component.translatable("status.roadbuilder.click_segment_to_preview");
        }
        if (activeTool == RoadBuilderTool.LANE_CONNECTOR) {
            return selectedLaneConnectionPoint == null
                    ? Component.translatable(
                            "status.roadbuilder.lane_connector_select_source"
                    )
                    : Component.translatable(
                            "status.roadbuilder.lane_connector_select_target"
                    );
        }
        if (activeTool == RoadBuilderTool.REPLACE) {
            if (!RoadBuilderClientNetworking.canSendSegmentReplaceIntent()
                    || !RoadBuilderClientNetworking
                    .canSendSegmentDirectionInvertIntent()) {
                return Component.translatable("status.roadbuilder.server_unsupported");
            }
            return hoveredRemovalSegment == null
                    ? Component.translatable("status.roadbuilder.replace_hover")
                    : Component.translatable("status.roadbuilder.replace_click");
        }
        if (activeTool == RoadBuilderTool.CROSSWALK) {
            if (!RoadBuilderClientNetworking.canSendCrosswalkToggleIntent()) {
                return Component.translatable(
                        "status.roadbuilder.crosswalk_unsupported"
                );
            }
            if (hoveredRemovalSegment != null && hoveredRemovalSegment.presetSnapshot()
                    .classification().roadClass() == com.cookiecraftmods.roadbuilder.preset.RoadClass.HIGHWAY) {
                return Component.translatable("status.roadbuilder.crosswalk_highway");
            }
            return hoveredRemovalSegment == null
                    ? Component.translatable(
                            "status.roadbuilder.crosswalk_hover"
                    )
                    : Component.translatable(
                            "status.roadbuilder.crosswalk_click"
                    );
        }
        if (parallelPreviewInvalid) {
            return Component.translatable("status.roadbuilder.parallel_invalid");
        }
        boolean straight = activeTool == RoadBuilderTool.STRAIGHT_ROAD && !parallelEnabled;
        if (straight
                ? !RoadBuilderClientNetworking.canSendStraightRoadIntent()
                : !RoadBuilderClientNetworking.canSendRoadPathIntent()) {
            return Component.translatable("status.roadbuilder.server_unsupported");
        }
        if (startTarget == null) {
            return cursorInstruction();
        }
        if (activeTool == RoadBuilderTool.ROUNDABOUT) {
            if (!(previewGeometry instanceof RoundaboutGeometry roundabout)) {
                return Component.translatable(
                        "status.roadbuilder.roundabout_select_radius"
                );
            }
            return Component.translatable(
                    "status.roadbuilder.roundabout_click_to_place",
                    String.format(
                            Locale.ROOT,
                            "%.0f",
                            roundabout.radius() * 2.0
                    )
            );
        }
        if (activeTool == RoadBuilderTool.SIMPLE_CURVE
                && pathPoints.isEmpty()) {
            return Component.translatable(
                    "status.roadbuilder.curve_set_guide"
            );
        }
        if (activeTool == RoadBuilderTool.SIMPLE_CURVE) {
            if (previewGeometry instanceof QuadraticBezierGeometry curve) {
                return Component.translatable(
                        "status.roadbuilder.curve_click_to_place",
                        String.format(
                                Locale.ROOT,
                                "%.0f",
                                curveInteriorAngleDegrees(curve)
                        ),
                        String.format(
                                Locale.ROOT,
                                "%.1f",
                                horizontalDistance(
                                        curve.start(),
                                        curve.control()
                                )
                        ),
                        String.format(
                                Locale.ROOT,
                                "%.1f",
                                horizontalDistance(
                                        curve.control(),
                                        curve.end()
                                )
                        )
                );
            }
            return Component.translatable(
                    "status.roadbuilder.curve_select_end"
            );
        }
        if (activeTool == RoadBuilderTool.FREEHAND_DRAW) {
            if (!pathPoints.isEmpty()
                    && previewProblem.kind() != StraightRoadPreviewProblem.Kind.NONE) return endpointInstruction();
            return Component.translatable(pathPoints.isEmpty()
                    ? "status.roadbuilder.freehand_draw_entry"
                    : "status.roadbuilder.freehand_draw_end");
        }
        if (activeTool == RoadBuilderTool.COMPLEX_CURVE) {
            return Component.translatable(
                    pathPoints.isEmpty()
                            ? "status.roadbuilder.complex_curve_set_guide"
                            : pathPoints.size() == 1
                                    ? "status.roadbuilder.complex_curve_set_second_guide"
                                    : "status.roadbuilder.complex_curve_select_end"
            );
        }
        return endpointInstruction();
    }

    private void handleLeftClick(Minecraft client) {
        Window window = client.getWindow();
        double guiX = client.mouseHandler.xpos()
                * window.getGuiScaledWidth() / window.getScreenWidth();
        double guiY = client.mouseHandler.ypos()
                * window.getGuiScaledHeight() / window.getScreenHeight();
        RoadBuilderToolbarLayout layout = RoadBuilderToolbarLayout.create(
                window.getGuiScaledWidth(),
                window.getGuiScaledHeight()
        );

        if (layout.lanePathVisibilityButton().contains(guiX, guiY)) {
            toggleLanePathsVisibility();
            return;
        }
        if (layout.crosswalkToolButton().contains(guiX, guiY)) {
            activateCrosswalkTool();
            return;
        }
        if (layout.elevationDisplay().contains(guiX, guiY)) {
            resetElevation(client);
            return;
        }
        if (layout.elevationLowerButton().contains(guiX, guiY)) {
            adjustElevation(client, -1);
            return;
        }
        if (layout.elevationRaiseButton().contains(guiX, guiY)) {
            adjustElevation(client, 1);
            return;
        }

        for (RoadBuilderToolGroup group : RoadBuilderToolGroup.values()) {
            if (layout.groupButton(group).contains(guiX, guiY)) {
                selectGroup(client, group);
                return;
            }
        }
        if (layout.cancelButton().contains(guiX, guiY)) {
            cancelCurrentStep(client);
            return;
        }
        if (selectedGroup == RoadBuilderToolGroup.ROADS) {
            if (layout.parallelToggleButton().contains(guiX, guiY)) {
                if (pendingPlacementRequestId == null) {
                    parallelEnabled = !parallelEnabled;
                    rebuildPreview(client);
                }
                return;
            }
            if (layout.roadNodeSnappingButton().contains(guiX, guiY)) {
                setSnappingMode(client, snappingMode.toggleNodes());
                return;
            }
            if (layout.roadGuidelineSnappingButton().contains(guiX, guiY)) {
                setSnappingMode(client, snappingMode.toggleGuidelines());
                return;
            }
            if (layout.roadAngleSnappingButton().contains(guiX, guiY)) {
                setSnappingMode(client, snappingMode.toggleAngles());
                return;
            }
            List<RoadPresetCatalogEntry> presets =
                    filteredPresets();
            RoadBuilderToolbarPresetWindow presetWindow =
                    RoadBuilderToolbarPresetWindow.create(
                            presets,
                            presetPage()
                    );
            for (int index = 0;
                    index < RoadBuilderToolbarLayout.PRESET_SLOT_COUNT;
                    index++) {
                if (layout.presetCardButton(index).contains(guiX, guiY)) {
                    RoadPresetCatalogEntry preset = presetWindow.entryAt(index)
                            .orElse(null);
                    if (preset == null) {
                        return;
                    }
                    RoadBuilderClientNetworking.selectPreset(preset.id());
                    rebuildPreview(client);
                    return;
                }
            }
            if (layout.presetEditorButton().contains(guiX, guiY)) {
                openPresetEditor(client);
                return;
            }
            int pageCount = presetPageCount(presets);
            int visiblePageCount = Math.min(
                    pageCount,
                    RoadBuilderToolbarLayout.PRESET_PAGE_BULLET_COUNT
            );
            int firstVisiblePage = firstVisiblePresetPage(
                    presetPage(),
                    pageCount,
                    visiblePageCount
            );
            for (int index = 0; index < visiblePageCount; index++) {
                if (layout.presetPageBullet(index).contains(guiX, guiY)) {
                    presetPage = firstVisiblePage + index;
                    return;
                }
            }
            for (RoadCategory category : RoadCategory.values()) {
                if (layout.presetCategoryButton(category).contains(
                        guiX,
                        guiY
                )) {
                    selectRoadCategory(client, category);
                    return;
                }
            }
            for (RoadBuilderTool roadTool : roadTools()) {
                if (layout.roadToolButton(roadTool).contains(guiX, guiY)) {
                    activateRoadTool(roadTool);
                    return;
                }
            }
        }
        if (layout.primaryActionButton().contains(guiX, guiY)) {
            handlePrimaryAction(client);
            return;
        }
        if (layout.owns(guiX, guiY)) {
            return;
        }
        handleWorldClick(client);
    }

    private void handleWorldClick(Minecraft client) {
        if (selectedGroup != RoadBuilderToolGroup.ROADS
                && selectedGroup != RoadBuilderToolGroup.DEMOLITION
                && selectedGroup != RoadBuilderToolGroup.LANE_CONNECTORS) {
            return;
        }
        if (activeTool == RoadBuilderTool.LANE_CONNECTOR) {
            handleLaneConnectorClick(client);
            return;
        }
        if (activeTool == RoadBuilderTool.BULLDOZER) {
            if (removalPhase == RemovalUiPhase.AVAILABLE
                    && hoveredRemovalSegment != null) {
                requestRemoval(client, RemovalRequestAction.PREVIEW);
            } else if (removalPhase == RemovalUiPhase.CONFIRM
                    && removalTargetUnderCursor(client)) {
                requestRemoval(client, RemovalRequestAction.EXECUTE);
            }
            return;
        }
        if (activeTool == RoadBuilderTool.REPLACE) {
            requestReplacement(client);
            return;
        }
        if (activeTool == RoadBuilderTool.CROSSWALK) {
            requestCrosswalkToggle(client);
            return;
        }
        if (pendingPlacementRequestId != null) {
            if (activeTool == RoadBuilderTool.FREEHAND_DRAW) {
                return;
            }
            queueNextPlacementTarget(client);
            return;
        }
        if (placementJob.isActive()) {
            return;
        }
        if (removalPhase == RemovalUiPhase.PREVIEW_PENDING
                || removalPhase == RemovalUiPhase.CONFIRM
                || removalPhase == RemovalUiPhase.EXECUTE_PENDING) {
            return;
        }
        updatePointer(client);
        CursorTarget target = cursorResult.target().orElse(null);
        if (target == null) {
            return;
        }

        if (startTarget == null) {
            startTarget = target;
            startLanePort = hoveredLanePort;
            pathPoints.clear();
            if (activeTool == RoadBuilderTool.FREEHAND) {
                pathPoints.add(target);
                freehandDrawing = true;
            }
            rebuildPreview(client);
            return;
        }
        switch (activeTool) {
            case STRAIGHT_ROAD, ROUNDABOUT -> submitPreview(client);
            case FREEHAND_DRAW -> {
                if (pathPoints.isEmpty()) {
                    if (horizontalDistance(startTarget.roadPoint(), target.roadPoint()) < 0.05) return;
                    pathPoints.add(target);
                    rebuildPreview(client);
                } else {
                    submitPreview(client);
                }
            }
            case SIMPLE_CURVE, COMPLEX_CURVE -> {
                if (pathPoints.size() < (activeTool == RoadBuilderTool.SIMPLE_CURVE ? 1 : 2)) {
                    CursorTarget previous = pathPoints.isEmpty()
                            ? startTarget : pathPoints.get(pathPoints.size() - 1);
                    if (horizontalDistance(previous.roadPoint(), target.roadPoint()) < 0.05) {
                        return;
                    }
                    pathPoints.add(target);
                    rebuildPreview(client);
                } else {
                    submitPreview(client);
                }
            }
            case FREEHAND, REPLACE, CROSSWALK, BULLDOZER, LANE_CONNECTOR,
                    NAVIGATION -> {
            }
        }
    }

    private void submitPreview(Minecraft client) {
        if (activeTool == RoadBuilderTool.FREEHAND_DRAW && pathPoints.isEmpty()) {
            return;
        }
        if (previewGeometry == null
                || previewProblem.kind() != StraightRoadPreviewProblem.Kind.NONE
                || client == null || client.level == null
                || pendingPlacementRequestId != null
                || placementJob.isActive()) {
            return;
        }
        boolean straight = activeTool == RoadBuilderTool.STRAIGHT_ROAD && !parallelEnabled;
        if (straight
                ? !RoadBuilderClientNetworking.canSendStraightRoadIntent()
                : !RoadBuilderClientNetworking.canSendRoadPathIntent()) {
            showTransient(
                    client,
                    Component.translatable("message.roadbuilder.server_unsupported")
            );
            return;
        }
        UUID requestId = UUID.randomUUID();
        ResourceLocation dimensionId = client.level.dimension().location();
        RoadPresetCatalogEntry selectedPreset =
                RoadBuilderClientNetworking.selectedPreset().orElse(null);
        if (selectedPreset == null) {
            showTransient(
                    client,
                    Component.translatable("message.roadbuilder.no_road_presets")
            );
            return;
        }
        ResourceLocation presetId = selectedPreset.id();
        int presetRevision = selectedPreset.revision();
        long graphRevision = RoadBuilderClientNetworking
                .roadGraph(client.level.dimension())
                .map(RoadGraphSnapshot::revision)
                .orElse(-1L);
        boolean sent;
        if (straight) {
            RoadPoint start = previewGeometry.start();
            RoadPoint end = previewGeometry.end();
            sent = RoadBuilderClientNetworking.sendStraightRoadIntent(
                    StraightRoadIntentC2SPacket.current(
                            requestId,
                            dimensionId,
                            presetId,
                            presetRevision,
                            graphRevision,
                            start.x(),
                            start.y(),
                            start.z(),
                            end.x(),
                            end.y(),
                            end.z()
                    )
            );
        } else {
            RoadPathIntentC2SPacket packet =
                    RoadPathIntentC2SPacket.current(
                            requestId,
                            dimensionId,
                            presetId,
                            presetRevision,
                            graphRevision,
                            pathType(),
                            canonicalPathPoints()
                    ).withParallelOffset(parallelEnabled ? parallelOffset : 0);
            sent = RoadBuilderClientNetworking.sendRoadPathIntent(packet);
        }
        if (!sent) {
            showTransient(client, Component.translatable("message.roadbuilder.server_unsupported"));
            return;
        }

        pendingPlacementRequestId = requestId;
        pendingPlacementDimension = dimensionId;
        if (activeTool == RoadBuilderTool.ROUNDABOUT) {
            placementContinuation.clear();
        } else {
            placementContinuation.submitted(previewEndTarget);
        }
    }

    private void queueNextPlacementTarget(Minecraft client) {
        CursorTarget continuation = placementContinuation.endpoint();
        if (continuation == null) {
            return;
        }
        CursorTargetResult nextResult = applySnapping(
                client,
                CursorRaycaster.cast(client),
                continuation
        );
        CursorTarget target = nextResult.target().orElse(null);
        if (target == null
                || continuation.blockPosition().equals(target.blockPosition())) {
            placementContinuation.clearQueuedTarget();
            return;
        }
        placementContinuation.queue(target);
    }

    private void rebuildPreview(Minecraft client) {
        previewGeometry = null;
        rawPreview = null;
        parallelPreview = null;
        parallelPreviewInvalid = false;
        previewEndTarget = null;
        previewProblem = StraightRoadPreviewProblem.none();
        if (startTarget == null) {
            return;
        }

        CursorTarget endpoint = cursorResult.target().orElse(null);
        if (endpoint == null) {
            previewProblem = StraightRoadPreviewProblem.of(
                    StraightRoadPreviewProblem.Kind.NO_TARGET
            );
            return;
        }
        if (client.level == null) {
            previewProblem = StraightRoadPreviewProblem.of(
                    StraightRoadPreviewProblem.Kind.NO_TARGET
            );
            return;
        }

        ReferenceGeometry geometry;
        try {
            geometry = buildPreviewGeometry(endpoint);
            if (activeTool != RoadBuilderTool.FREEHAND_DRAW
                    && !parallelEnabled && snapSettings.tangentEnabled()) {
                RoadVector exit = hoveredLanePort == null ? null : hoveredLanePort.direction();
                geometry = LaneConnectionGeometry.align(geometry,
                        startLanePort == null ? null : startLanePort.direction(), exit);
            }
        } catch (IllegalArgumentException invalid) {
            previewProblem = StraightRoadPreviewProblem.of(
                    StraightRoadPreviewProblem.Kind.NO_HORIZONTAL_DIRECTION
            );
            return;
        }
        if (geometry == null) {
            return;
        }
        previewGeometry = geometry;
        RoadPoint continuationEnd = geometry instanceof RoundaboutGeometry roundabout
                ? roundabout.radiusPoint()
                : geometry.end();
        previewEndTarget = continuationEnd.equals(endpoint.roadPoint())
                ? endpoint
                : new CursorTarget(
                        BlockPos.containing(
                                continuationEnd.x(),
                                continuationEnd.y(),
                                continuationEnd.z()
                        ),
                        continuationEnd
                );
        double width = RoadBuilderClientNetworking.selectedPreset()
                .map(RoadPresetCatalogEntry::surfaceWidth)
                .orElse(FALLBACK_PREVIEW_WIDTH);
        ReferenceGeometry displayGeometry = geometry;
        try {
            var graph = currentGraph(client);
            if (graph.isPresent()) displayGeometry = new com.cookiecraftmods.roadbuilder.placement.RoadOverpassPlanner()
                    .adjust(geometry, (int)Math.ceil(width), graph.get().segments().values());
        } catch (IllegalArgumentException invalid) {
            previewProblem = StraightRoadPreviewProblem.planningFailure(
                    com.cookiecraftmods.roadbuilder.placement.BlockPlanFailureCode.INVALID_GEOMETRY);
            return;
        }
        rawPreview = new RawRoadPreview(displayGeometry, width);
        if (geometry.length() < com.cookiecraftmods.roadbuilder.network.RoadSegment.MINIMUM_BUILD_LENGTH)
            previewProblem = StraightRoadPreviewProblem.planningFailure(
                    com.cookiecraftmods.roadbuilder.placement.BlockPlanFailureCode.INVALID_GEOMETRY);
        double radius = RoadCurveValidation.minimumRadius(width,
                RoadBuilderClientNetworking.selectedPreset().map(RoadPresetCatalogEntry::roadClass)
                        .orElse(com.cookiecraftmods.roadbuilder.preset.RoadClass.LOCAL));
        var curveFailure = RoadCurveValidation.validate(displayGeometry, radius);
        if (curveFailure != null) previewProblem = StraightRoadPreviewProblem.planningFailure(curveFailure);
        if (parallelEnabled) {
            try {
                var parallel = ParallelRoadGeometry.create(geometry, parallelOffset);
                if (!(parallel instanceof RoundaboutGeometry)) {
                    parallel = com.cookiecraftmods.roadbuilder.geometry.ReferenceGeometryReverser.reverse(parallel);
                }
                var graph = currentGraph(client);
                ReferenceGeometry parallelDisplay = graph.isEmpty() ? parallel
                        : new com.cookiecraftmods.roadbuilder.placement.RoadOverpassPlanner().adjust(
                                parallel, (int)Math.ceil(width), graph.get().segments().values());
                parallelPreview = new RawRoadPreview(parallelDisplay, width);
                var parallelFailure = RoadCurveValidation.validate(parallelDisplay, radius);
                if (parallelFailure != null) previewProblem = StraightRoadPreviewProblem.planningFailure(parallelFailure);
            } catch (IllegalArgumentException invalid) {
                parallelPreviewInvalid = true;
                previewGeometry = null;
                rawPreview = null;
                previewEndTarget = null;
            }
        }
    }

    private ReferenceGeometry buildPreviewGeometry(CursorTarget cursor) {
        RoadPoint start = startTarget.roadPoint();
        return switch (activeTool) {
            case STRAIGHT_ROAD -> {
                requireDifferentEndpoint(startTarget, cursor);
                yield new StraightGeometry(start, cursor.roadPoint());
            }
            case SIMPLE_CURVE -> {
                if (pathPoints.isEmpty()) {
                    requireDifferentEndpoint(startTarget, cursor);
                    yield new StraightGeometry(start, cursor.roadPoint());
                }
                requireDifferentEndpoint(startTarget, cursor);
                yield new QuadraticBezierGeometry(
                        start,
                        pathPoints.get(0).roadPoint(),
                        cursor.roadPoint()
                );
            }
            case COMPLEX_CURVE -> {
                if (pathPoints.isEmpty()) {
                    requireDifferentEndpoint(startTarget, cursor);
                    yield new StraightGeometry(start, cursor.roadPoint());
                }
                requireDifferentEndpoint(startTarget, cursor);
                if (pathPoints.size() == 1) {
                    yield new QuadraticBezierGeometry(
                            start, pathPoints.get(0).roadPoint(), cursor.roadPoint()
                    );
                }
                yield new CubicBezierGeometry(
                        start, pathPoints.get(0).roadPoint(),
                        pathPoints.get(1).roadPoint(), cursor.roadPoint()
                );
            }
            case FREEHAND_DRAW -> {
                requireDifferentEndpoint(startTarget, cursor);
                if (pathPoints.isEmpty()) {
                    yield new StraightGeometry(start, cursor.roadPoint());
                }
                RoadVector entry = start.vectorTo(pathPoints.get(0).roadPoint());
                RoadVector exit = null;
                if (!parallelEnabled && snapSettings.tangentEnabled()) {
                    // Solve both handles together: rotating the entry afterwards
                    // leaves the automatic exit based on the old entry direction.
                    RoadVector attachedEntry = startLanePort != null ? startLanePort.direction()
                            : LaneConnectionSnapping.openEndDirection(indexedGraph, start, true);
                    if (attachedEntry != null) entry = attachedEntry;
                    exit = hoveredLanePort != null ? hoveredLanePort.direction()
                            : LaneConnectionSnapping.openEndDirection(indexedGraph, cursor.roadPoint(), false);
                }
                yield FreeformGeometry.create(start, cursor.roadPoint(), entry, exit);
            }
            case FREEHAND -> {
                List<RoadPoint> stroke = pathPoints.stream()
                        .map(CursorTarget::roadPoint)
                        .collect(java.util.stream.Collectors.toCollection(
                                ArrayList::new
                        ));
                if (stroke.isEmpty()) {
                    stroke.add(start);
                }
                if (stroke.get(stroke.size() - 1)
                        .distanceTo(cursor.roadPoint()) >= 0.05) {
                    stroke.add(cursor.roadPoint());
                }
                yield stroke.size() < 2
                        ? null
                        : new FreehandGeometry(stroke);
            }
            case ROUNDABOUT -> {
                double deltaX = cursor.roadPoint().x() - start.x();
                double deltaZ = cursor.roadPoint().z() - start.z();
                double rawRadius = Math.hypot(deltaX, deltaZ);
                if (rawRadius < 0.05) {
                    throw new IllegalArgumentException(
                            "a roundabout needs a radius direction"
                    );
                }
                double minimumRadius = Math.max(
                        RoundaboutGeometry.MINIMUM_RADIUS,
                        RoadBuilderClientNetworking.selectedPreset()
                                .map(entry -> RoundaboutGeometry.minimumRadius(
                                        entry.surfaceWidth()
                                ))
                                .orElse(RoundaboutGeometry.MINIMUM_RADIUS)
                );
                double radius = Math.min(
                        RoundaboutGeometry.MAXIMUM_RADIUS,
                        Math.max(minimumRadius, Math.rint(rawRadius))
                );
                RoadPoint radiusPoint = new RoadPoint(
                        start.x() + deltaX / rawRadius * radius,
                        start.y(),
                        start.z() + deltaZ / rawRadius * radius
                );
                yield new RoundaboutGeometry(start, radiusPoint);
            }
            case REPLACE, CROSSWALK, BULLDOZER, LANE_CONNECTOR,
                    NAVIGATION -> null;
        };
    }

    private static void requireDifferentEndpoint(
            CursorTarget start,
            CursorTarget end
    ) {
        if (start.blockPosition().equals(end.blockPosition())) {
            throw new IllegalArgumentException(
                    "road endpoints must be different"
            );
        }
    }

    private void requestRemoval(Minecraft client, RemovalRequestAction action) {
        if (!removeButtonEnabled(client) || client.level == null) {
            return;
        }
        RoadSegment segment = action == RemovalRequestAction.EXECUTE
                ? removalTarget
                : hoveredRemovalSegment;
        RoadGraphSnapshot graph = currentGraph(client).orElse(null);
        if (segment == null || graph == null) {
            refreshRemovalAvailability(client);
            return;
        }
        long expectedRevision = action == RemovalRequestAction.EXECUTE
                ? removalTargetRevision
                : graph.revision();
        UUID requestId = UUID.randomUUID();
        SegmentRemovalIntentC2SPacket packet = SegmentRemovalIntentC2SPacket.current(
                requestId,
                client.level.dimension().location(),
                segment.id().value(),
                expectedRevision,
                action,
                RemovalMode.SAFE
        );
        if (!RoadBuilderClientNetworking.sendSegmentRemovalIntent(packet)) {
            showTransient(client, Component.translatable("message.roadbuilder.removal_unsupported"));
            return;
        }

        clearSelection();
        pendingRemovalRequestId = requestId;
        removalTarget = segment;
        removalTargetRevision = expectedRevision;
        removalPreview = null;
        removalPhase = action == RemovalRequestAction.PREVIEW
                ? RemovalUiPhase.PREVIEW_PENDING
                : RemovalUiPhase.EXECUTE_PENDING;
    }

    private void processPlacementResult(Minecraft client) {
        if (pendingPlacementRequestId == null) {
            return;
        }
        PlacementResultS2CPacket result = RoadBuilderClientNetworking
                .placementResult(pendingPlacementRequestId)
                .orElse(null);
        if (result == null) {
            return;
        }

        ResourceLocation submittedDimension = pendingPlacementDimension;
        boolean crosswalkToggle = pendingCrosswalkToggle;
        boolean directionInversion = pendingDirectionInversion;
        pendingPlacementRequestId = null;
        pendingPlacementDimension = null;
        pendingCrosswalkToggle = false;
        pendingDirectionInversion = false;
        PlacementContinuation.Completion continuationState =
                placementContinuation.complete();
        CursorTarget continuation = continuationState.endpoint();
        CursorTarget queuedTarget = continuationState.queuedTarget();
        if (result.resultCode() == PlacementResultCode.ACCEPTED) {
            AcceptedPlacement accepted = result.acceptedPlacement().orElseThrow();
            if (accepted.queued()) {
                placementJob.start(
                        accepted.operationId(),
                        submittedDimension,
                        0,
                        ConstructionJobPhase.PREPARED
                );
            }
            if (crosswalkToggle) {
                showTransient(
                        client,
                        Component.translatable(
                                "message.roadbuilder.crosswalk_updated",
                                accepted.affectedBlockCount()
                        )
                );
            } else if (directionInversion) {
                showTransient(
                        client,
                        Component.translatable(
                                "message.roadbuilder.direction_inverted",
                                accepted.affectedBlockCount()
                        )
                );
            } else {
                showTransient(
                        client,
                        Component.translatable(
                                "message.roadbuilder.placement_accepted",
                                accepted.affectedBlockCount()
                        )
                );
            }
            clearSelection();
            startTarget = continuation;
            if (queuedTarget != null) {
                cursorResult = CursorTargetResult.success(queuedTarget);
            }
            rebuildPreview(client);
            if (!accepted.queued()
                    && queuedTarget != null
                    && previewGeometry != null
                    && !placementJob.isActive()) {
                submitPreview(client);
            }
            refreshRemovalAvailability(client);
        } else {
            showTransient(
                    client,
                    Component.translatable(
                            "message.roadbuilder.placement_rejected",
                            placementResultLabel(result.resultCode())
                    )
            );
        }
    }

    private void selectGroup(
            Minecraft client,
            RoadBuilderToolGroup group
    ) {
        selectedGroup = Objects.requireNonNull(group, "group");
        switch (group) {
            case ROADS -> activateStraightRoad();
            case DEMOLITION -> {
                if (activeTool != RoadBuilderTool.BULLDOZER) {
                    activateBulldozer(client);
                } else if (removalPhase == RemovalUiPhase.CONFIRM) {
                    requestRemoval(client, RemovalRequestAction.EXECUTE);
                }
            }
            case LANE_CONNECTORS -> activateLaneConnector();
        }
    }

    private void openPresetEditor(Minecraft client) {
        List<RoadPresetCatalogEntry> presets =
                RoadBuilderClientNetworking.presetCatalog().entries();
        if (presets.isEmpty()) {
            client.setScreen(RoadPresetEditorScreen.createNew(
                    new ResourceLocation("roadbuilder_world", "new_road")
            ));
            return;
        }

        RoadPresetCatalogEntry target = RoadBuilderClientNetworking
                .selectedPreset()
                .orElse(presets.get(0));
        RoadBuilderClientNetworking.selectPreset(target.id());
        client.setScreen(new RoadPresetEditorScreen(target.id()));
    }

    public int presetPage() {
        int pageCount = presetPageCount(filteredPresets());
        presetPage = Math.max(0, Math.min(presetPage, pageCount - 1));
        return presetPage;
    }

    private void changePresetPage(int direction) {
        List<RoadPresetCatalogEntry> presets = filteredPresets();
        presetPage = Math.max(
                0,
                Math.min(
                        presetPage() + Integer.signum(direction),
                        presetPageCount(presets) - 1
                )
        );
    }

    private static int presetPageCount(List<RoadPresetCatalogEntry> presets) {
        return Math.max(1, (presets.size()
                + RoadBuilderToolbarLayout.PRESET_SLOT_COUNT - 1)
                / RoadBuilderToolbarLayout.PRESET_SLOT_COUNT);
    }

    public static int firstVisiblePresetPage(
            int activePage,
            int pageCount,
            int visiblePageCount
    ) {
        return Math.max(
                0,
                Math.min(
                        activePage - visiblePageCount / 2,
                        pageCount - visiblePageCount
                )
        );
    }

    private List<RoadPresetCatalogEntry> filteredPresets() {
        return RoadBuilderClientNetworking.presetCatalog().entries().stream()
                .filter(roadCategory::matches)
                .toList();
    }

    private void selectRoadCategory(
            Minecraft client,
            RoadCategory category
    ) {
        roadCategory = Objects.requireNonNull(category, "category");
        presetPage = 0;
        List<RoadPresetCatalogEntry> presets = filteredPresets();
        if (!presets.isEmpty()
                && RoadBuilderClientNetworking.selectedPreset()
                        .filter(category::matches)
                        .isEmpty()) {
            RoadBuilderClientNetworking.selectPreset(presets.get(0).id());
            rebuildPreview(client);
        }
    }

    private void handlePrimaryAction(Minecraft client) {
        switch (selectedGroup) {
            case ROADS -> activateStraightRoad();
            case DEMOLITION -> {
                if (activeTool != RoadBuilderTool.BULLDOZER) {
                    activateBulldozer(client);
                } else if (removalPhase == RemovalUiPhase.CONFIRM) {
                    requestRemoval(client, RemovalRequestAction.EXECUTE);
                }
            }
            case LANE_CONNECTORS -> activateLaneConnector();
        }
    }

    private void setSnappingMode(
            Minecraft client,
            RoadSnappingMode mode
    ) {
        snappingMode = Objects.requireNonNull(mode, "mode");
        updatePointer(client);
        rebuildPreview(client);
    }

    private void adjustElevation(Minecraft client, int direction) {
        elevationOffset = Math.max(
                -MAXIMUM_ELEVATION_OFFSET,
                Math.min(
                        MAXIMUM_ELEVATION_OFFSET,
                        elevationOffset + Integer.signum(direction)
                )
        );
        updatePointer(client);
        rebuildPreview(client);
        showTransient(
                client,
                Component.translatable(
                        "message.roadbuilder.elevation_changed",
                        String.format(Locale.ROOT, "%+d", elevationOffset)
                )
        );
    }

    private void resetElevation(Minecraft client) {
        elevationOffset = 0;
        updatePointer(client);
        rebuildPreview(client);
        showTransient(
                client,
                Component.translatable("message.roadbuilder.elevation_reset")
        );
    }

    private void processRemovalResult(Minecraft client) {
        if (pendingRemovalRequestId == null) {
            return;
        }
        RemovalResultS2CPacket result = RoadBuilderClientNetworking
                .removalResult(pendingRemovalRequestId)
                .orElse(null);
        if (result == null) {
            return;
        }

        pendingRemovalRequestId = null;
        if (result.resultCode() != RemovalResultCode.SUCCESS
                && result.resultCode() != RemovalResultCode.QUEUED) {
            showTransient(
                    client,
                    Component.translatable(
                            "message.roadbuilder.removal_rejected",
                            removalResultLabel(result.resultCode())
                    )
            );
            resetRemovalInteraction();
            refreshRemovalAvailability(client);
            return;
        }

        RemovalResultSummary summary = result.summary().orElseThrow();
        if (result.action() == RemovalRequestAction.PREVIEW) {
            removalPreview = summary;
            removalPhase = RemovalUiPhase.CONFIRM;
            showTransient(
                    client,
                    Component.translatable(
                            "message.roadbuilder.removal_preview_ready",
                            summary.conflictCount()
                    )
            );
            return;
        }

        if (result.resultCode() == RemovalResultCode.QUEUED) {
            removalJob.start(
                    result.operationId(),
                    client.level == null ? null : client.level.dimension().location(),
                    -1,
                    null
            );
            showTransient(
                    client,
                    Component.translatable(
                            "message.roadbuilder.removal_queued",
                            summary.restoredCount()
                    )
            );
            resetRemovalInteraction();
            refreshRemovalAvailability(client);
            return;
        }

        showTransient(
                client,
                Component.translatable(
                        "message.roadbuilder.removal_succeeded",
                        summary.restoredCount(),
                        summary.conflictCount()
                )
        );
        resetRemovalInteraction();
        requestCurrentGraphResync(client);
        refreshRemovalAvailability(client);
    }

    private void processRemovalJobProgress(Minecraft client) {
        processConstructionJobProgress(client, removalJob, "removal", true);
    }

    private void processPlacementJobProgress(Minecraft client) {
        processConstructionJobProgress(client, placementJob, "placement", false);
    }

    private void processConstructionJobProgress(
            Minecraft client,
            ConstructionJobUiState job,
            String messageDomain,
            boolean refreshAfterTerminalState
    ) {
        if (!job.isActive()) {
            return;
        }
        ConstructionJobProgressS2CPacket progress = RoadBuilderClientNetworking
                .jobProgress(job.id())
                .orElse(null);
        if (progress == null || !job.update(
                progress.phase(),
                progress.completedTransitions()
        )) {
            return;
        }
        String messageKey = "message.roadbuilder."
                + messageDomain
                + "_job_";
        switch (progress.phase()) {
            case PREPARED, APPLYING -> showTransient(
                    client,
                    Component.translatable(
                            messageKey + "progress",
                            progress.completedTransitions(),
                            progress.totalTransitions()
                    )
            );
            case CANCELLING -> showTransient(
                    client,
                    Component.translatable(
                            messageKey + "cancelling",
                            progress.completedTransitions()
                    )
            );
            case COMPLETING, CANCELLED, QUARANTINED -> {
                String terminalMessage = switch (progress.phase()) {
                    case COMPLETING -> "completed";
                    case CANCELLED -> "cancelled";
                    case QUARANTINED -> "quarantined";
                    default -> throw new IllegalStateException();
                };
                showTransient(
                        client,
                        Component.translatable(messageKey + terminalMessage)
                );
                RoadBuilderClientNetworking.forgetJobProgress(job.id());
                job.clear();
                if (progress.phase() == ConstructionJobPhase.COMPLETING) {
                    requestCurrentGraphResync(client);
                    refreshRemovalAvailability(client);
                } else if (refreshAfterTerminalState) {
                    refreshRemovalAvailability(client);
                }
            }
        }
    }

    private void discoverActivePlacementJob(Minecraft client) {
        if (placementJob.isActive() || client.level == null) {
            return;
        }
        ResourceLocation dimension = client.level.dimension().location();
        ConstructionJobProgressS2CPacket progress = RoadBuilderClientNetworking
                .jobProgress()
                .values()
                .stream()
                .filter(candidate -> candidate.action() == ConstructionJobAction.PLACE)
                .filter(candidate -> candidate.dimensionId().equals(dimension))
                .reduce((first, second) -> second)
                .orElse(null);
        if (progress != null) {
            placementJob.start(progress.jobId(), progress.dimensionId(), -1, null);
        }
    }

    private void sendRequestedPlacementJobCancellation(Minecraft client) {
        if (!placementJob.consumeCancellationRequest()) {
            return;
        }
        if (RoadBuilderClientNetworking.sendConstructionJobCancel(
                ConstructionJobCancelC2SPacket.current(
                        UUID.randomUUID(),
                        placementJob.dimension(),
                        placementJob.id()
                )
        )) {
            showTransient(
                    client,
                    Component.translatable(
                            "message.roadbuilder.placement_job_cancel_requested"
                    )
            );
        }
    }

    private void discoverActiveRemovalJob(Minecraft client) {
        if (removalJob.isActive() || client.level == null) {
            return;
        }
        ResourceLocation dimension = client.level.dimension().location();
        ConstructionJobProgressS2CPacket progress = RoadBuilderClientNetworking
                .jobProgress()
                .values()
                .stream()
                .filter(candidate -> candidate.action() == ConstructionJobAction.REMOVE)
                .filter(candidate -> candidate.dimensionId().equals(dimension))
                .reduce((first, second) -> second)
                .orElse(null);
        if (progress != null) {
            removalJob.start(progress.jobId(), progress.dimensionId(), -1, null);
        }
    }

    private void sendRequestedJobCancellation(Minecraft client) {
        if (!removalJob.consumeCancellationRequest()) {
            return;
        }
        if (RoadBuilderClientNetworking.sendConstructionJobCancel(
                ConstructionJobCancelC2SPacket.current(
                        UUID.randomUUID(),
                        removalJob.dimension(),
                        removalJob.id()
                )
        )) {
            showTransient(
                    client,
                    Component.translatable(
                            "message.roadbuilder.removal_job_cancel_requested"
                    )
            );
        }
    }

    private static void requestCurrentGraphResync(Minecraft client) {
        if (client.level != null) {
            RoadBuilderClientNetworking.requestRoadGraphResync(
                    client.level.dimension()
            );
        }
    }

    private void resetRemovalInteraction() {
        removalPhase = RemovalUiPhase.AVAILABLE;
        removalTarget = null;
        removalTargetRevision = -1L;
        removalPreview = null;
        hoveredRemovalSegment = null;
    }

    private void observeConnection(ClientPacketListener connection) {
        if (connection == observedConnection) {
            return;
        }
        clearConnectionState();
        observedConnection = connection;
    }

    private void clearConnectionState() {
        pendingPlacementRequestId = null;
        pendingPlacementDimension = null;
        pendingCrosswalkToggle = false;
        pendingDirectionInversion = false;
        placementContinuation.clear();
        pendingRemovalRequestId = null;
        hoveredRemovalSegment = null;
        removalTarget = null;
        removalTargetRevision = -1L;
        removalPreview = null;
        removalPhase = RemovalUiPhase.UNAVAILABLE;
        placementJob.clear();
        removalJob.clear();
    }

    private void clearSelection() {
        startTarget = null;
        startLanePort = null;
        hoveredLanePort = null;
        visibleLanePorts = List.of();
        pathPoints.clear();
        freehandDrawing = false;
        previewEndTarget = null;
        previewGeometry = null;
        rawPreview = null;
        parallelPreview = null;
        parallelPreviewInvalid = false;
        snapGuides = List.of();
        previewProblem = StraightRoadPreviewProblem.none();
        selectedLaneConnectionPoint = null;
        hoveredLaneConnectionPoint = null;
    }

    private void cancelCurrentStep(Minecraft client) {
        if ((activeTool == RoadBuilderTool.COMPLEX_CURVE || activeTool == RoadBuilderTool.FREEHAND_DRAW)
                && pendingPlacementRequestId == null && !pathPoints.isEmpty()) {
            pathPoints.remove(pathPoints.size() - 1);
            updatePointer(client);
            rebuildPreview(client);
            return;
        }
        cancelInteractiveStep();
    }

    private CursorTargetResult applySnapping(
            Minecraft client,
            CursorTargetResult rawResult
    ) {
        return applySnapping(client, rawResult, startTarget);
    }

    private CursorTargetResult applySnapping(
            Minecraft client,
            CursorTargetResult rawResult,
            CursorTarget snapStart
    ) {
        CursorTarget raw = rawResult.target().orElse(null);
        if (raw == null || !isRoadPlacementTool()) {
            snapGuides = List.of();
            hoveredLanePort = null;
            visibleLanePorts = List.of();
            return rawResult;
        }

        RoadGraphSnapshot graph = currentGraph(client).orElse(null);
        boolean temporaryOverride = GLFW.glfwGetKey(
                client.getWindow().getWindow(),
                GLFW.GLFW_KEY_LEFT_ALT
        ) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(
                client.getWindow().getWindow(),
                GLFW.GLFW_KEY_RIGHT_ALT
        ) == GLFW.GLFW_PRESS;
        RoadPoint elevatedPoint = new RoadPoint(
                raw.roadPoint().x(),
                raw.roadPoint().y() + elevationOffset,
                raw.roadPoint().z()
        );
        CursorTarget elevated = new CursorTarget(
                BlockPos.containing(
                        elevatedPoint.x(),
                        elevatedPoint.y(),
                        elevatedPoint.z()
                ),
                elevatedPoint
        );
        if (graph != indexedGraph) {
            indexedGraph = graph;
            snapIndex = new RoadSnapIndex(graph);
        }
        hoveredLanePort = null;
        visibleLanePorts = List.of();
        boolean selectingEndpoint = LaneConnectionSnapping.selectingEndpoint(
                activeTool, snapStart != null, pathPoints.size());
        if (selectingEndpoint && snappingMode.snapsNodes() && !parallelEnabled
                && activeTool != RoadBuilderTool.ROUNDABOUT) {
            if (graph != lanePortGraph) {
                lanePortGraph = graph;
                lanePorts = graph == null ? Map.of()
                        : AuxiliaryLanePorts.ports(graph.segments().values()).stream()
                                .collect(java.util.stream.Collectors.groupingBy(AuxiliaryLanePorts.Port::segmentId));
            }
            int width = RoadBuilderClientNetworking.selectedPreset().map(RoadPresetCatalogEntry::surfaceWidth)
                    .orElse(Integer.MAX_VALUE);
            visibleLanePorts = snapIndex.segmentsNear(elevatedPoint, null, snapSettings.guidelineRange()).stream()
                    .flatMap(segment -> lanePorts.getOrDefault(segment.id(), List.of()).stream())
                    .filter(port -> port.roadWidth() > width)
                    .filter(port -> port.incoming() == (snapStart == null))
                    .filter(port -> port.position().distanceTo(elevatedPoint) <= snapSettings.guidelineRange())
                    .toList();
            hoveredLanePort = temporaryOverride ? null : LaneConnectionSnapping.nearest(
                    elevatedPoint, visibleLanePorts, snapIndex.nodesNear(elevatedPoint),
                    snapSettings.range()).orElse(null);
            snapGuides = List.of();
            if (hoveredLanePort != null) {
                RoadPoint position = hoveredLanePort.position();
                return CursorTargetResult.success(new CursorTarget(BlockPos.containing(
                        position.x(), position.y(), position.z()), position));
            }
        }
        RoadSnapResolver.CurveAngleContext curveAngleContext =
                activeTool == RoadBuilderTool.SIMPLE_CURVE
                        && startTarget != null
                        && !pathPoints.isEmpty()
                        ? new RoadSnapResolver.CurveAngleContext(
                                startTarget.roadPoint(),
                                pathPoints.get(0).roadPoint()
                        )
                        : null;
        if (activeTool == RoadBuilderTool.COMPLEX_CURVE
                && startTarget != null && !pathPoints.isEmpty()) {
            RoadPoint first = pathPoints.get(0).roadPoint();
            RoadPoint pivot = pathPoints.get(pathPoints.size() - 1).roadPoint();
            // Keep the exit tangent relative to the entry tangent so that
            // parallel ends produce an S-bend, even for rotated roads.
            RoadPoint reference = pathPoints.size() == 1
                    ? startTarget.roadPoint()
                    : pivot.subtract(startTarget.roadPoint().vectorTo(first));
            curveAngleContext = new RoadSnapResolver.CurveAngleContext(
                    reference, pivot, false
            );
        }
        RoadSnapResult snap = RoadSnapResolver.resolve(
                elevated,
                snapStart,
                snapIndex.nodesNear(elevated.roadPoint(), snappingMode.snapsAngles()
                        ? snapSettings.guidelineRange() + snapSettings.range() : 32.0),
                snapIndex.segmentsNear(elevated.roadPoint(),
                        snapStart == null ? null : snapStart.roadPoint(),
                        snappingMode.snapsGuidelines() || snappingMode.snapsAngles()
                                ? snapSettings.guidelineRange()
                                + snapSettings.range() : snapSettings.range()),
                snappingMode,
                snapSettings,
                temporaryOverride || activeTool == RoadBuilderTool.FREEHAND
                        && freehandDrawing,
                curveAngleContext
        );
        snapGuides = snap.guides();
        return CursorTargetResult.success(snap.target());
    }

    private void cancelRemovalConfirmation() {
        if (removalPhase != RemovalUiPhase.CONFIRM) {
            return;
        }
        removalTarget = null;
        removalTargetRevision = -1L;
        removalPreview = null;
        removalPhase = RemovalUiPhase.AVAILABLE;
    }

    private void refreshRemovalAvailability(Minecraft client) {
        if (removalPhase == RemovalUiPhase.PREVIEW_PENDING
                || removalPhase == RemovalUiPhase.CONFIRM
                || removalPhase == RemovalUiPhase.EXECUTE_PENDING) {
            return;
        }
        removalPhase = RoadBuilderClientNetworking.canSendSegmentRemovalIntent()
                && currentGraph(client).isPresent()
                ? RemovalUiPhase.AVAILABLE
                : RemovalUiPhase.UNAVAILABLE;
    }

    private Optional<RoadGraphSnapshot> currentGraph(Minecraft client) {
        if (client.level == null) {
            return Optional.empty();
        }
        return RoadBuilderClientNetworking.roadGraph(client.level.dimension());
    }

    private Component cursorInstruction() {
        return switch (cursorResult.problem()) {
            case NONE -> Component.translatable(
                    activeTool == RoadBuilderTool.ROUNDABOUT
                            ? "status.roadbuilder.roundabout_select_center"
                            : "status.roadbuilder.select_start"
            );
            case NOT_TOP_FACE -> Component.translatable("status.roadbuilder.aim_top_face");
            case NO_WORLD, MISS -> Component.translatable("status.roadbuilder.aim_surface");
        };
    }

    private Component endpointInstruction() {
        return switch (previewProblem.kind()) {
            case NONE -> Component.translatable(
                    "status.roadbuilder.click_to_place",
                    String.format(
                            Locale.ROOT,
                            "%.1f",
                            previewGeometry == null
                                    ? 0.0
                                    : previewGeometry.length()
                    ),
                    previewGradePercent(),
                    previewAngleDegrees(),
                    previewEndTarget == null
                            ? "0.0"
                            : String.format(
                                    Locale.ROOT,
                                    "%.1f",
                                    previewEndTarget.roadPoint().y()
                            )
            );
            case NO_TARGET -> Component.translatable("status.roadbuilder.aim_end_surface");
            case SAME_POINT -> Component.translatable("status.roadbuilder.choose_different_end");
            case NO_HORIZONTAL_DIRECTION -> Component.translatable(
                    "status.roadbuilder.choose_horizontal_end"
            );
            case PLANNING_FAILED -> Component.translatable(
                    "status.roadbuilder.preview_rejected",
                    planningFailureLabel(previewProblem.planningFailure().orElseThrow())
            );
        };
    }

    private static Component planningFailureLabel(com.cookiecraftmods.roadbuilder.placement.BlockPlanFailureCode code) {
        return switch (code) {
            case TOO_SHARP_TURN -> placementResultLabel(PlacementResultCode.TOO_SHARP_TURN);
            case SELF_INTERSECTION -> placementResultLabel(PlacementResultCode.SELF_INTERSECTION);
            default -> Component.literal(code.name());
        };
    }

    private String previewGradePercent() {
        if (previewGeometry == null) {
            return "0.0";
        }
        double maximum = 0.0;
        for (int index = 0; index <= 32; index++) {
            var tangent = previewGeometry.sample(
                    previewGeometry.length() * index / 32.0
            ).tangent();
            maximum = Math.max(
                    maximum,
                    Math.abs(tangent.y()) / tangent.horizontalLength()
            );
        }
        return String.format(Locale.ROOT, "%.1f", maximum * 100.0);
    }

    private String previewAngleDegrees() {
        if (previewGeometry == null) {
            return "0.0";
        }
        var tangent = previewGeometry.sample(0.0).tangent();
        double angle = Math.toDegrees(Math.atan2(tangent.z(), tangent.x()));
        if (angle < 0.0) {
            angle += 360.0;
        }
        return String.format(Locale.ROOT, "%.1f", angle);
    }

    private static double curveInteriorAngleDegrees(
            QuadraticBezierGeometry curve
    ) {
        var first = curve.control().vectorTo(curve.start());
        var second = curve.control().vectorTo(curve.end());
        double lengths = first.horizontalLength() * second.horizontalLength();
        if (lengths <= 1.0e-9) {
            return 0.0;
        }
        double cosine = (first.x() * second.x() + first.z() * second.z())
                / lengths;
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, cosine))));
    }

    private static double horizontalDistance(
            RoadPoint first,
            RoadPoint second
    ) {
        return Math.hypot(first.x() - second.x(), first.z() - second.z());
    }

    private void showTransient(Minecraft client, Component message) {
        transientStatus = Objects.requireNonNull(message, "message");
        transientStatusTicks = TRANSIENT_STATUS_TICKS;
    }

    private static Component placementResultLabel(PlacementResultCode code) {
        return Component.translatable(
                "result.roadbuilder.placement." + code.name().toLowerCase(Locale.ROOT)
        );
    }

    private static Component removalResultLabel(RemovalResultCode code) {
        return Component.translatable(
                "result.roadbuilder.removal." + code.name().toLowerCase(Locale.ROOT)
        );
    }

    private void activateStraightRoad() {
        activateRoadTool(RoadBuilderTool.STRAIGHT_ROAD);
    }

    private void activateCrosswalkTool() {
        selectedGroup = RoadBuilderToolGroup.ROADS;
        cancelRemovalConfirmation();
        clearSelection();
        activeTool = RoadBuilderTool.CROSSWALK;
        keyboardFocusedRoadTool = -1;
    }

    private void activateRoadTool(RoadBuilderTool tool) {
        if (tool != RoadBuilderTool.STRAIGHT_ROAD
                && tool != RoadBuilderTool.SIMPLE_CURVE
                && tool != RoadBuilderTool.COMPLEX_CURVE
                && tool != RoadBuilderTool.FREEHAND_DRAW
                && tool != RoadBuilderTool.REPLACE
                && tool != RoadBuilderTool.FREEHAND
                && tool != RoadBuilderTool.ROUNDABOUT) {
            throw new IllegalArgumentException(
                    "not a road placement tool: " + tool
            );
        }
        selectedGroup = RoadBuilderToolGroup.ROADS;
        cancelRemovalConfirmation();
        clearSelection();
        activeTool = tool;
        keyboardFocusedRoadTool = roadTools().indexOf(tool);
        if (tool != RoadBuilderTool.REPLACE) {
            hoveredRemovalSegment = null;
        }
    }

    private boolean isRoadPlacementTool() {
        return activeTool == RoadBuilderTool.STRAIGHT_ROAD
                || activeTool == RoadBuilderTool.SIMPLE_CURVE
                || activeTool == RoadBuilderTool.COMPLEX_CURVE
                || activeTool == RoadBuilderTool.FREEHAND_DRAW
                || activeTool == RoadBuilderTool.FREEHAND
                || activeTool == RoadBuilderTool.ROUNDABOUT;
    }

    private void captureFreehandPoint() {
        if (!freehandDrawing
                || activeTool != RoadBuilderTool.FREEHAND
                || pathPoints.size()
                >= RoadPathIntentC2SPacket.MAXIMUM_POINTS) {
            return;
        }
        CursorTarget target = cursorResult.target().orElse(null);
        if (target == null) {
            return;
        }
        CursorTarget previous = pathPoints.isEmpty()
                ? startTarget
                : pathPoints.get(pathPoints.size() - 1);
        if (previous == null
                || previous.roadPoint().distanceTo(target.roadPoint())
                >= 0.25) {
            pathPoints.add(target);
        }
    }

    private void finishFreehand(Minecraft client) {
        captureFreehandPoint();
        freehandDrawing = false;
        rebuildPreview(client);
        if (previewGeometry != null) {
            submitPreview(client);
        } else {
            clearSelection();
        }
    }

    private RoadPathIntentC2SPacket.PathType pathType() {
        if (previewGeometry instanceof CubicBezierGeometry) {
            return RoadPathIntentC2SPacket.PathType.CUBIC;
        }
        return switch (activeTool) {
            case SIMPLE_CURVE ->
                    RoadPathIntentC2SPacket.PathType.QUADRATIC;
            case COMPLEX_CURVE, FREEHAND_DRAW ->
                    RoadPathIntentC2SPacket.PathType.CUBIC;
            case STRAIGHT_ROAD -> RoadPathIntentC2SPacket.PathType.STRAIGHT;
            case FREEHAND -> RoadPathIntentC2SPacket.PathType.FREEHAND;
            case ROUNDABOUT ->
                    RoadPathIntentC2SPacket.PathType.ROUNDABOUT;
            default -> throw new IllegalStateException(
                    "active tool has no path packet type: " + activeTool
            );
        };
    }

    private List<RoadPoint> canonicalPathPoints() {
        if (previewGeometry instanceof StraightGeometry straight) {
            return List.of(straight.start(), straight.end());
        }
        if (previewGeometry
                instanceof QuadraticBezierGeometry quadratic) {
            return quadratic.controlPoints();
        }
        if (previewGeometry instanceof CubicBezierGeometry cubic) {
            return cubic.controlPoints();
        }
        if (previewGeometry instanceof MultiCurveGeometry multi) {
            return multi.anchors();
        }
        if (previewGeometry instanceof FreehandGeometry freehand) {
            return freehand.sourcePoints();
        }
        if (previewGeometry instanceof RoundaboutGeometry roundabout) {
            return List.of(
                    roundabout.center(),
                    roundabout.radiusPoint()
            );
        }
        throw new IllegalStateException(
                "preview geometry does not match a path packet"
        );
    }

    private void activateBulldozer(Minecraft client) {
        selectedGroup = RoadBuilderToolGroup.DEMOLITION;
        clearSelection();
        activeTool = RoadBuilderTool.BULLDOZER;
        updateHoveredSegment(client);
        refreshRemovalAvailability(client);
    }

    private void activateLaneConnector() {
        selectedGroup = RoadBuilderToolGroup.LANE_CONNECTORS;
        cancelRemovalConfirmation();
        clearSelection();
        activeTool = RoadBuilderTool.LANE_CONNECTOR;
        keyboardFocusedRoadTool = -1;
        hoveredRemovalSegment = null;
        updateHoveredLaneConnectionPoint(Minecraft.getInstance());
    }

    private void updateHoveredLaneConnectionPoint(Minecraft client) {
        if (activeTool != RoadBuilderTool.LANE_CONNECTOR) {
            hoveredLaneConnectionPoint = null;
            connectorGraph = null;
            return;
        }
        RoadGraphSnapshot snapshot = currentGraph(client).orElse(null);
        CursorRay ray = CursorRaycaster.ray(client).orElse(null);
        if (snapshot == null || ray == null) {
            hoveredLaneConnectionPoint = null;
            return;
        }
        if (connectorGraph == null
                || connectorGraph.snapshot().revision()
                        != snapshot.revision()
                || !connectorGraph.snapshot().dimension().equals(
                        snapshot.dimension()
                )) {
            connectorGraph = RoadLaneGraph.from(snapshot);
            if (selectedLaneConnectionPoint != null) {
                selectedLaneConnectionPoint = connectorGraph
                        .connectionPoints().stream()
                        .filter(point -> point.laneId().equals(
                                selectedLaneConnectionPoint.laneId()
                        ) && point.nodeId().equals(
                                selectedLaneConnectionPoint.nodeId()
                        ) && point.kind()
                                == selectedLaneConnectionPoint.kind())
                        .findFirst()
                        .orElse(null);
            }
        }
        hoveredLaneConnectionPoint = LaneConnectionPointSelector.select(
                connectorGraph,
                ray
        ).orElse(null);
    }

    private void handleLaneConnectorClick(Minecraft client) {
        RoadLaneGraph.ConnectionPoint hovered = hoveredLaneConnectionPoint;
        if (hovered == null) {
            return;
        }
        if (selectedLaneConnectionPoint == null) {
            if (hovered.kind()
                    != RoadLaneGraph.ConnectionPointKind.INCOMING) {
                showTransient(
                        client,
                        Component.translatable(
                                "message.roadbuilder.lane_connector_incoming_first"
                        )
                );
                return;
            }
            selectedLaneConnectionPoint = hovered;
            return;
        }
        if (selectedLaneConnectionPoint.equals(hovered)) {
            selectedLaneConnectionPoint = null;
            return;
        }
        if (connectorGraph == null
                || !connectorGraph.canConnect(
                        selectedLaneConnectionPoint,
                        hovered
                )) {
            if (hovered.kind()
                    == RoadLaneGraph.ConnectionPointKind.INCOMING) {
                selectedLaneConnectionPoint = hovered;
            } else {
                showTransient(
                        client,
                        Component.translatable(
                                "message.roadbuilder.lane_connector_invalid_target"
                        )
                );
            }
            return;
        }
        RoadGraphSnapshot snapshot = connectorGraph == null
                ? null
                : connectorGraph.snapshot();
        if (snapshot == null || client.level == null) {
            return;
        }
        boolean sent = RoadBuilderClientNetworking.sendLaneConnectorEdit(
                LaneConnectorEditC2SPacket.current(
                        client.level.dimension().location(),
                        snapshot.revision(),
                        selectedLaneConnectionPoint.laneId().value(),
                        hovered.laneId().value()
                )
        );
        if (sent) {
            showTransient(
                    client,
                    Component.translatable(
                            "message.roadbuilder.lane_connector_updated"
                    )
            );
            selectedLaneConnectionPoint = null;
        } else {
            showTransient(
                    client,
                    Component.translatable(
                            "message.roadbuilder.server_unsupported"
                    )
            );
        }
    }

    private void updateHoveredSegment(Minecraft client) {
        boolean selectsSegment = activeTool == RoadBuilderTool.BULLDOZER
                || activeTool == RoadBuilderTool.REPLACE
                || activeTool == RoadBuilderTool.CROSSWALK;
        if (!selectsSegment
                || removalPhase == RemovalUiPhase.PREVIEW_PENDING
                || removalPhase == RemovalUiPhase.CONFIRM
                || removalPhase == RemovalUiPhase.EXECUTE_PENDING) {
            if (!selectsSegment) {
                hoveredRemovalSegment = null;
            }
            return;
        }
        RoadGraphSnapshot graph = currentGraph(client).orElse(null);
        CursorRay ray = CursorRaycaster.ray(client).orElse(null);
        hoveredRemovalSegment = graph == null || ray == null
                ? null
                : RoadSegmentHoverSelector.select(graph, ray).orElse(null);
    }

    private void requestReplacement(Minecraft client) {
        if (client.level == null
                || hoveredRemovalSegment == null
                || pendingPlacementRequestId != null
                || placementJob.isActive()) {
            return;
        }
        RoadGraphSnapshot graph = currentGraph(client).orElse(null);
        RoadPresetCatalogEntry preset = RoadBuilderClientNetworking
                .selectedPreset().orElse(null);
        if (graph == null || preset == null) {
            return;
        }
        UUID requestId = UUID.randomUUID();
        if (!RoadBuilderClientNetworking.sendSegmentReplaceIntent(
                SegmentReplaceIntentC2SPacket.current(
                        requestId,
                        client.level.dimension().location(),
                        hoveredRemovalSegment.id().value(),
                        graph.revision(),
                        preset.id(),
                        preset.revision()
                ))) {
            showTransient(
                    client,
                    Component.translatable("message.roadbuilder.server_unsupported")
            );
            return;
        }
        pendingPlacementRequestId = requestId;
        pendingPlacementDimension = client.level.dimension().location();
    }

    private void requestDirectionInversion(Minecraft client) {
        if (client.level == null
                || hoveredRemovalSegment == null
                || pendingPlacementRequestId != null
                || placementJob.isActive()) {
            return;
        }
        RoadGraphSnapshot graph = currentGraph(client).orElse(null);
        if (graph == null) {
            return;
        }
        UUID requestId = UUID.randomUUID();
        if (!RoadBuilderClientNetworking.sendSegmentDirectionInvertIntent(
                SegmentDirectionInvertIntentC2SPacket.current(
                        requestId,
                        client.level.dimension().location(),
                        hoveredRemovalSegment.id().value(),
                        graph.revision()
                ))) {
            showTransient(
                    client,
                    Component.translatable("message.roadbuilder.server_unsupported")
            );
            return;
        }
        pendingPlacementRequestId = requestId;
        pendingPlacementDimension = client.level.dimension().location();
        pendingDirectionInversion = true;
    }

    private void requestCrosswalkToggle(Minecraft client) {
        if (client.level == null
                || hoveredRemovalSegment == null
                || hoveredRemovalSegment.presetSnapshot().classification().roadClass()
                        == com.cookiecraftmods.roadbuilder.preset.RoadClass.HIGHWAY
                || pendingPlacementRequestId != null
                || placementJob.isActive()) {
            return;
        }
        RoadGraphSnapshot graph = currentGraph(client).orElse(null);
        if (graph == null) {
            return;
        }
        UUID requestId = UUID.randomUUID();
        RoadNodeId nodeId = nearestEndpoint(
                hoveredRemovalSegment,
                cursorResult.target().map(CursorTarget::roadPoint)
                        .orElse(hoveredRemovalSegment.referenceGeometry()
                                .sample(0.0).position())
        );
        if (!RoadBuilderClientNetworking.sendCrosswalkToggleIntent(
                CrosswalkToggleIntentC2SPacket.current(
                        requestId,
                        client.level.dimension().location(),
                        hoveredRemovalSegment.id().value(),
                        nodeId.value(),
                        graph.revision()
                )
        )) {
            showTransient(
                    client,
                    Component.translatable(
                            "message.roadbuilder.server_unsupported"
                    )
            );
            return;
        }
        pendingPlacementRequestId = requestId;
        pendingPlacementDimension = client.level.dimension().location();
        pendingCrosswalkToggle = true;
    }

    private static RoadNodeId nearestEndpoint(
            RoadSegment segment,
            RoadPoint cursor
    ) {
        RoadPoint start = segment.referenceGeometry().sample(0.0).position();
        RoadPoint end = segment.referenceGeometry().sample(
                segment.referenceGeometry().length()
        ).position();
        return cursor.distanceTo(start) <= cursor.distanceTo(end)
                ? segment.startNodeId()
                : segment.endNodeId();
    }

    private boolean removalTargetUnderCursor(Minecraft client) {
        if (removalTarget == null) {
            return false;
        }
        RoadGraphSnapshot graph = currentGraph(client).orElse(null);
        CursorRay ray = CursorRaycaster.ray(client).orElse(null);
        return graph != null
                && ray != null
                && RoadSegmentHoverSelector.select(graph, ray)
                .map(RoadSegment::id)
                .filter(removalTarget.id()::equals)
                .isPresent();
    }

    private void validateRemovalTarget(Minecraft client) {
        if (removalTarget == null) {
            return;
        }
        RoadGraphSnapshot graph = currentGraph(client).orElse(null);
        if (graph != null
                && graph.revision() == removalTargetRevision
                && graph.segments().containsKey(removalTarget.id())) {
            return;
        }
        if (removalPhase == RemovalUiPhase.CONFIRM) {
            showTransient(client, Component.translatable("message.roadbuilder.graph_changed"));
            cancelRemovalConfirmation();
        }
    }
}
