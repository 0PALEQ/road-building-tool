package com.cookiecraftmods.roadbuilder.client.ui;

import com.cookiecraftmods.roadbuilder.networking.PresetContentS2CPacket;
import com.cookiecraftmods.roadbuilder.networking.PresetDeleteIntentC2SPacket;
import com.cookiecraftmods.roadbuilder.networking.PresetSaveIntentC2SPacket;
import com.cookiecraftmods.roadbuilder.networking.PresetSaveResultCode;
import com.cookiecraftmods.roadbuilder.networking.PresetSaveResultS2CPacket;
import com.cookiecraftmods.roadbuilder.preset.RoadCategory;
import com.cookiecraftmods.roadbuilder.preset.RoadClass;
import com.cookiecraftmods.roadbuilder.preset.RoadClassification;
import com.cookiecraftmods.roadbuilder.preset.RoadIntersectionStyle;
import com.cookiecraftmods.roadbuilder.preset.IntersectionControl;
import com.cookiecraftmods.roadbuilder.preset.RoadPreset;
import com.cookiecraftmods.roadbuilder.preset.RoadPresetCatalogEntry;
import com.cookiecraftmods.roadbuilder.preset.RoadPresetTemplates;
import com.cookiecraftmods.roadbuilder.preset.RoadSurfaceRole;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static com.cookiecraftmods.roadbuilder.client.ui.RoadPresetEditorUi.*;

public final class RoadPresetEditorScreen extends RoadBuilderScreen {
    private static final String KEY = "screen.roadbuilder.preset_editor.";
    private static final int CONTENT_TIMEOUT_TICKS = 100;
    private static final int SAVE_TIMEOUT_TICKS = 200;

    private ResourceLocation editingId;
    private RoadPreset source;
    private RoadPresetEditorDraft draft;
    private RoadPresetEditorLayout layout;
    private final RoadPresetEditorGateway gateway;
    private int expectedRevision;
    private boolean editable;
    private boolean dirty;
    private boolean statusIsError;
    private boolean rebuildPending;
    private boolean catalogRefreshPending;
    private final RoadPresetEditorFocus focus = new RoadPresetEditorFocus();
    private final RoadPresetEditorRequestState requests =
            new RoadPresetEditorRequestState();
    private final RoadPresetEditorSelection selection =
            new RoadPresetEditorSelection();
    private final RoadPresetEditorConfirmations confirmations =
            new RoadPresetEditorConfirmations();
    private long observedCatalogRevision = -1;
    private String nameValue = "";
    private String category = "medium";
    private String searchQuery = "";
    private Component status = Component.translatable(KEY + "loading");
    private RoadPresetEditorTab tab = RoadPresetEditorTab.LAYOUT;
    private boolean inspectSelection;
    private RoadSurfaceRole editingMaterialRole;
    private RoadSurfaceRole editingComponentMaterialRole;
    private final RoadPresetEditorScroll panelScroll = new RoadPresetEditorScroll();
    private final List<PanelWidget> panelWidgets = new ArrayList<>();
    private UiRectangle activeViewport;
    private boolean buildingPanel;
    private int panelBottom;
    private RoadTextButton scrollUp;
    private RoadTextButton scrollDown;
    private EditBox nameBox;
    private EditBox searchBox;
    private EditBox speedBox;
    private String speedValue;
    private boolean invalidSpeed;
    private RoadTextButton saveButton;
    private final List<PaletteCardHit> paletteCardHits = new ArrayList<>();
    private RoadPresetEditorDragState dragState;
    private boolean draggingScrollbar;

    public RoadPresetEditorScreen(ResourceLocation presetId) {
        this(presetId, RoadPresetEditorGateway.production());
    }

    RoadPresetEditorScreen(
            ResourceLocation presetId,
            RoadPresetEditorGateway gateway
    ) {
        super(Component.translatable("screen.roadbuilder.preset_editor"));
        editingId = Objects.requireNonNull(presetId, "presetId");
        this.gateway = Objects.requireNonNull(gateway, "gateway");
    }

    public static RoadPresetEditorScreen createNew(ResourceLocation presetId) {
        return createNew(presetId, RoadPresetEditorGateway.production());
    }

    static RoadPresetEditorScreen createNew(
            ResourceLocation presetId,
            RoadPresetEditorGateway gateway
    ) {
        RoadPresetEditorScreen screen = new RoadPresetEditorScreen(
                presetId,
                gateway
        );
        screen.startNewDraft(presetId);
        return screen;
    }

    @Override
    protected void init() {
        dragState = null;
        draggingScrollbar = false;
        captureInputs();
        focus.capture(nameBox, searchBox, speedBox);
        layout = RoadPresetEditorLayout.create(width, height);
        if (draft == null && !requests.contentPending()) {
            requestContent();
        }
        rebuildControls();
    }

    private void rebuildControls() {
        clearWidgets();
        layout = RoadPresetEditorLayout.create(width, height);
        nameBox = null;
        searchBox = null;
        speedBox = null;
        saveButton = null;
        paletteCardHits.clear();
        panelWidgets.clear();
        scrollUp = null;
        scrollDown = null;
        catalogRefreshPending = false;

        addCloseButton();
        if (draft == null) {
            addRetryButton();
            rebuildPending = false;
            return;
        }

        addNavigation();
        addPropertiesControls();
        addWorkspaceControls();
        addFooterControls();
        EditBox focusedBox = focus.targetBox(nameBox, searchBox, speedBox);
        if (focusedBox != null) {
            panelWidgets.stream().filter(entry -> entry.widget() == focusedBox).findFirst()
                    .ifPresent(entry -> panelScroll.reveal(entry.top(), focusedBox.getHeight(), activeViewport.height()));
            positionPanelWidgets();
        }
        focus.restore(nameBox, searchBox, speedBox);
        if (nameBox != null && nameBox.visible && nameBox.isFocused()) {
            setFocused(nameBox);
        } else if (searchBox != null && searchBox.isFocused()) {
            setFocused(searchBox);
        } else if (speedBox != null && speedBox.visible && speedBox.isFocused()) {
            setFocused(speedBox);
        }
        refreshSaveState();
        rebuildPending = false;
    }

    private void addCloseButton() {
        UiRectangle header = layout.header();
        int buttonHeight = Math.min(20, Math.max(12, header.height() - 8));
        int buttonWidth = Math.min(24, header.width());
        UiRectangle bounds = new UiRectangle(
                header.right() - buttonWidth - 4,
                header.y() + Math.max(0, (header.height() - buttonHeight) / 2),
                buttonWidth,
                buttonHeight
        );
        addButton(
                bounds,
                Component.literal("×"),
                RoadTextButton.Style.GHOST,
                true,
                this::onClose
        );
    }

    private void addRetryButton() {
        if (requests.contentPending()) {
            return;
        }
        UiRectangle body = union(
                layout.propertiesPanel(),
                layout.workspacePanel()
        );
        int buttonWidth = Math.min(100, Math.max(1, body.width() - 12));
        int buttonHeight = Math.min(20, Math.max(1, body.height()));
        addButton(
                new UiRectangle(
                        body.x() + (body.width() - buttonWidth) / 2,
                        body.y() + Math.max(0, body.height() / 2 + 14),
                        buttonWidth,
                        buttonHeight
                ),
                Component.translatable(KEY + "retry"),
                RoadTextButton.Style.PRIMARY,
                true,
                this::requestContent
        );
    }

    private void addNavigation() {
        UiRectangle bounds = layout.navigation();
        RoadPresetEditorTab[] tabs = RoadPresetEditorTab.values();
        List<UiRectangle> cells = columns(bounds.x(), bounds.y(),
                bounds.width(), bounds.height(), tabs.length, 3);
        for (int index = 0; index < tabs.length; index++) {
            RoadPresetEditorTab target = tabs[index];
            String labelKey = target.key() + (target == RoadPresetEditorTab.SAVED_ROADS
                    && !layout.wide() ? ".compact" : "");
            addButton(cells.get(index), Component.translatable(labelKey),
                    tab == target ? RoadTextButton.Style.SELECTED
                            : RoadTextButton.Style.GHOST,
                    true, () -> selectTab(target));
        }
    }

    private void selectTab(RoadPresetEditorTab target) {
        captureInputs();
        tab = target;
        editingMaterialRole = null;
        editingComponentMaterialRole = null;
        panelScroll.reset();
        searchQuery = "";
        if (searchBox != null) {
            searchBox.setValue("");
        }
        requestRebuild();
    }

    private boolean showingCatalog() {
        return tab == RoadPresetEditorTab.SAVED_ROADS
                || tab == RoadPresetEditorTab.LAYOUT && !inspectSelection;
    }

    private void addPropertiesControls() {
        UiRectangle heading = layout.panelHeading();
        if (tab == RoadPresetEditorTab.LAYOUT) {
            List<UiRectangle> tabs = columns(heading.x(), heading.y(),
                    heading.width(), heading.height(), 2, 3);
            addButton(tabs.get(0), Component.translatable(KEY + "add_components"),
                    !inspectSelection ? RoadTextButton.Style.SELECTED
                            : RoadTextButton.Style.NORMAL,
                    true, () -> {
                        inspectSelection = false;
                        editingComponentMaterialRole = null;
                        panelScroll.reset();
                        requestRebuild();
                    });
            addButton(tabs.get(1), Component.translatable(KEY + "selection"),
                    inspectSelection ? RoadTextButton.Style.SELECTED
                            : RoadTextButton.Style.NORMAL,
                    hasSelectedComponent(), () -> {
                        inspectSelection = true;
                        panelScroll.reset();
                        requestRebuild();
                    });
        } else if (tab == RoadPresetEditorTab.SAVED_ROADS) {
            addButton(heading, Component.translatable(KEY + "create_road_pack"),
                    RoadTextButton.Style.NORMAL,
                    !gateway.catalog().entries().isEmpty(),
                    () -> minecraft.setScreen(new RoadPackScreen(this)));
        } else if (tab == RoadPresetEditorTab.MATERIALS && editingMaterialRole != null) {
            addButton(heading, Component.translatable(KEY + "back_materials"),
                    RoadTextButton.Style.GHOST, true, () -> {
                        editingMaterialRole = null;
                        panelScroll.reset();
                        requestRebuild();
                    });
        }
        activeViewport = showingCatalog()
                ? layout.catalogViewport() : layout.propertiesViewport();
        panelBottom = activeViewport.y();
        buildingPanel = true;
        // Lay out the complete form before applying the viewport's scroll offset.
        UiRectangle content = new UiRectangle(activeViewport.x(), activeViewport.y(),
                activeViewport.width(), 10000);
        if (showingCatalog()) {
            buildingPanel = false;
            addLibraryControls();
            buildingPanel = true;
            addCatalogCards();
        } else {
            switch (tab) {
                case LAYOUT -> addComponentInspector(content, content.y());
                case MATERIALS -> {
                    if (editingMaterialRole == null) {
                        addRoadMaterialControls(content, content.y());
                    } else {
                        RoadSurfaceRole role = editingMaterialRole;
                        addMaterialSetControls(content, content.y(),
                                Component.translatable(materialKey(role)),
                                draft.materialPalette().blockSetFor(role),
                                blocks -> draft.setRoadMaterialSet(role, blocks));
                    }
                }
                case JUNCTIONS -> addJunctionControls(content);
                case DETAILS -> addDetailsControls(content);
                case SAVED_ROADS -> { }
            }
        }
        buildingPanel = false;
        panelScroll.resize(panelBottom - activeViewport.y(), activeViewport.height());
        addScrollControls();
        positionPanelWidgets();
    }

    private void addRoadMaterialControls(UiRectangle content, int y) {
        y = addHelp(content, y, "materials_hint") + 8;
        for (RoadSurfaceRole role : RoadSurfaceRole.values()) {
            List<ResourceLocation> blocks = draft.materialPalette().blockSetFor(role);
            RoadTextButton button = new RoadTextButton(
                    new UiRectangle(content.x(), y, content.width(), 34),
                    Component.translatable(materialKey(role)),
                    paletteSummary(blocks.size()),
                    RoadTextButton.Style.NORMAL, () -> {
                        editingMaterialRole = role;
                        panelScroll.reset();
                        requestRebuild();
                    });
            button.setTooltip(Tooltip.create(Component.literal(blocks.toString())));
            addPanelWidget(button);
            y += 39;
        }
    }

    private static String materialKey(RoadSurfaceRole role) {
        return KEY + "material." + switch (role) {
            case PAVEMENT -> "pavement";
            case MARKING -> "marking";
            case SIDEWALK -> "sidewalk";
            case RAILING -> "railing";
            case GRASS_MEDIAN -> "grass_median";
            case CONCRETE_MEDIAN -> "concrete_median";
        };
    }

    private static Component paletteSummary(int size) {
        return Component.translatable(KEY + (size == 1 ? "material_set_single" : "material_set_count"), size);
    }

    private void addMaterialSetControls(
            UiRectangle content, int y, Component title,
            List<ResourceLocation> blocks, Consumer<List<ResourceLocation>> update
    ) {
        y = addText(content, y, title, true) + 5;
        y = addHelp(content, y, "variations_hint") + 7;
        addButton(new UiRectangle(content.x(), y, content.width(), 20),
                Component.translatable(KEY + "add_variation", blocks.size()),
                RoadTextButton.Style.NORMAL,
                canMutate() && blocks.size() < 16,
                () -> RoadPresetBlockSelectorScreen.open(this, blocks.get(0),
                        replacement -> mutate(() -> {
                            List<ResourceLocation> changed = new ArrayList<>(blocks);
                            if (!changed.contains(replacement)) {
                                changed.add(replacement);
                                update.accept(changed);
                            }
                        })));
        y += 26;
        for (int index = 0; index < blocks.size(); index++) {
            final int blockIndex = index;
            y = addText(content, y, Component.translatable(
                    KEY + (index == 0 ? "base_block" : "variation"), index), false) + 3;
            RoadPresetBlockField field = new RoadPresetBlockField(
                    new UiRectangle(content.x(), y, content.width() - 24, 24),
                    this, blocks.get(index), replacement -> mutate(() -> {
                        List<ResourceLocation> changed = new ArrayList<>(blocks);
                        changed.set(blockIndex, replacement);
                        update.accept(changed);
                    }));
            field.active = canMutate();
            field.setTooltip(Tooltip.create(Component.literal(blocks.get(index).toString())));
            addPanelWidget(field);
            RoadTextButton remove = addButton(
                    new UiRectangle(content.right() - 20, y, 20, 24),
                    Component.literal("×"), RoadTextButton.Style.GHOST,
                    canMutate() && blocks.size() > 1, () -> mutate(() -> {
                        List<ResourceLocation> changed = new ArrayList<>(blocks);
                        changed.remove(blockIndex);
                        update.accept(changed);
                    }));
            remove.setTooltip(Tooltip.create(Component.translatable(KEY + "remove_variation")));
            y += 31;
        }
    }

    private void addComponentInspector(UiRectangle content, int y) {
        if (!hasSelectedComponent()) {
            addHelp(content, y, "click_render_hint");
            return;
        }
        if (!selection.isSingle()) {
            y = addText(content, y, Component.translatable(
                    KEY + "selection_count", selection.selected().size()), true) + 8;
            y = addHelp(content, y, "multi_selection_hint") + 10;
            addButton(new UiRectangle(content.x(), y, content.width(), 20),
                    Component.translatable(KEY + "remove_selected"),
                    RoadTextButton.Style.DANGER, canRemoveSelectedComponent(),
                    this::removeSelectedComponent);
            return;
        }
        int componentIndex = selection.primary();
        RoadPresetEditorComponent selected = draft.components().get(componentIndex);
        if (editingComponentMaterialRole != null) {
            boolean underlay = editingComponentMaterialRole == RoadSurfaceRole.PAVEMENT
                    && selected.hasUnderlay();
            addButton(new UiRectangle(content.x(), y, content.width(), 20),
                    Component.translatable(KEY + "back_component"),
                    RoadTextButton.Style.GHOST, true, () -> {
                        editingComponentMaterialRole = null;
                        panelScroll.reset();
                        requestRebuild();
                    });
            addMaterialSetControls(content, y + 28,
                    Component.translatable(materialKey(editingComponentMaterialRole)),
                    underlay ? draft.underlayBlockSet(selected) : selected.blockSet(),
                    blocks -> draft.replaceComponent(componentIndex, underlay
                            ? draft.components().get(componentIndex).withUnderlayBlockSet(blocks)
                            : draft.components().get(componentIndex).withBlockSet(blocks)));
            return;
        }
        y = addText(content, y, componentLabel(selected), true) + 8;
        addStepper(content, y, Component.translatable(KEY + "width_value", selected.width()),
                canMutate() && selected.hasAdjustableWidth() && selected.width() > 1,
                canMutate() && selected.hasAdjustableWidth()
                        && draft.surfaceBlockWidth() < RoadPreset.MAXIMUM_SURFACE_BLOCK_WIDTH,
                () -> replaceSelectedComponent(selected.withWidth(selected.width() - 1)),
                () -> replaceSelectedComponent(selected.withWidth(selected.width() + 1)));
        y += 27;
        if (!selected.hasAdjustableWidth()) {
            y = addHelp(content, y, "three_blocks_wide") + 6;
        }
        if (selected.isVehicleLane()) {
            y = addText(content, y, Component.translatable(KEY + "travel_direction"), false) + 3;
            addButton(new UiRectangle(content.x(), y, content.width(), 20),
                    directionButtonLabel(selected.direction()),
                    RoadTextButton.Style.NORMAL, canMutate(),
                    () -> replaceSelectedComponent(selected.withDirection(opposite(selected.direction()))));
            y += 28;
        }
        y = addComponentMaterialControl(content, y, selected.kind().surfaceRole(),
                selected.inheritsRoadMaterial(), selected.blockSet(),
                () -> draft.clearComponentMaterialOverride(componentIndex));
        if (selected.hasUnderlay()) {
            y = addComponentMaterialControl(content, y, RoadSurfaceRole.PAVEMENT,
                    selected.underlayBlockSet().isEmpty(), draft.underlayBlockSet(selected),
                    () -> draft.replaceComponent(componentIndex, selected.withUnderlayBlockSet(List.of())));
        }
        List<UiRectangle> moves = columns(content.x(), y, content.width(), 20, 2, 3);
        addButton(moves.get(0), Component.translatable(KEY + "move_left"),
                RoadTextButton.Style.NORMAL, canMutate() && componentIndex > 0,
                () -> moveSelectedComponent(-1));
        addButton(moves.get(1), Component.translatable(KEY + "move_right"),
                RoadTextButton.Style.NORMAL,
                canMutate() && componentIndex + 1 < draft.components().size(),
                () -> moveSelectedComponent(1));
        y += 27;
        RoadTextButton remove = addButton(
                new UiRectangle(content.x(), y, content.width(), 20),
                Component.translatable(KEY + "remove_component"),
                RoadTextButton.Style.DANGER, canRemoveSelectedComponent(),
                this::removeSelectedComponent);
        if (!canRemoveSelectedComponent()) {
            remove.setTooltip(Tooltip.create(Component.translatable(KEY + "keep_lane")));
        }
    }

    private void addDetailsControls(UiRectangle content) {
        int y = addText(content, content.y(), Component.translatable(KEY + "name"), false) + 3;
        nameBox = new EditBox(font, content.x(), y, content.width(), 20,
                Component.translatable(KEY + "name"));
        nameBox.setMaxLength(64);
        nameBox.setValue(nameValue);
        nameBox.setEditable(canMutate());
        nameBox.setResponder(this::onNameChanged);
        addPanelWidget(nameBox);
        y += 28;
        y = addText(content, y, Component.translatable(KEY + "category"), false) + 3;
        addButton(new UiRectangle(content.x(), y, content.width(), 20),
                Component.translatable(KEY + "category." + normalizedCategory()),
                RoadTextButton.Style.NORMAL, canMutate(), this::cycleCategory);
        y += 28;
        RoadClassification classification = draft.classification();
        y = addText(content, y, Component.translatable(KEY + "road_class"), false) + 3;
        addButton(new UiRectangle(content.x(), y, content.width(), 20),
                Component.translatable(KEY + "class." + classification.roadClass().serializedName()),
                RoadTextButton.Style.NORMAL, canMutate(), () -> mutate(() -> {
                    RoadClass[] classes = RoadClass.values();
                    draft.setClassification(new RoadClassification(
                            classes[(classification.roadClass().ordinal() + 1) % classes.length],
                            classification.priorityRank(), classification.nominalSpeed()));
                }));
        y += 28;
        addStepper(content, y, Component.translatable(KEY + "priority_value", classification.priorityRank()),
                canMutate() && classification.priorityRank() > 0,
                canMutate() && classification.priorityRank() < 15,
                () -> changePriority(-1), () -> changePriority(1));
        y += 26;
        y = addHelp(content, y, "priority_hint") + 7;
        y = addText(content, y, Component.translatable(KEY + "nominal_speed"), false) + 3;
        speedBox = new EditBox(font, content.x(), y, content.width(), 20,
                Component.translatable(KEY + "nominal_speed"));
        speedBox.setMaxLength(24);
        speedBox.setValue(speedValue == null ? Double.toString(classification.nominalSpeed()) : speedValue);
        speedBox.setEditable(canMutate());
        speedBox.setTextColor(invalidSpeed ? UIStyle.TEXT_ERROR : UIStyle.TEXT_PRIMARY);
        speedBox.setResponder(this::onSpeedChanged);
        addPanelWidget(speedBox);
        y += 28;
        y = addHelp(content, y, "details_hint") + 10;
        addButton(new UiRectangle(content.x(), y, content.width(), 20),
                Component.translatable(KEY + "reset"), RoadTextButton.Style.NORMAL,
                dirty && !requests.savePending(), this::resetDraft);
        y += 27;
        addButton(new UiRectangle(content.x(), y, content.width(), 20),
                Component.translatable(KEY + (confirmations.deleteArmed() ? "confirm_delete" : "delete")),
                RoadTextButton.Style.DANGER, canDelete(), this::deleteDraft);
    }

    private void changePriority(int amount) {
        mutate(() -> {
            RoadClassification value = draft.classification();
            draft.setClassification(new RoadClassification(value.roadClass(),
                    clamp(value.priorityRank() + amount, 0, 15), value.nominalSpeed()));
        });
    }

    private void onSpeedChanged(String replacement) {
        speedValue = replacement;
        if (!canMutate()) {
            return;
        }
        try {
            double speed = Double.parseDouble(replacement);
            RoadClassification value = draft.classification();
            draft.setClassification(new RoadClassification(value.roadClass(), value.priorityRank(), speed));
            invalidSpeed = false;
        } catch (IllegalArgumentException invalid) {
            invalidSpeed = true;
        }
        dirty = true;
        clearConfirmations();
        setStatus(Component.empty(), false);
        speedBox.setTextColor(invalidSpeed ? UIStyle.TEXT_ERROR : UIStyle.TEXT_PRIMARY);
        refreshSaveState();
    }

    private void addJunctionControls(UiRectangle content) {
        int y = addHelp(content, content.y(), "junctions_hint") + 8;
        RoadIntersectionStyle style = draft.intersectionStyle();
        addButton(new UiRectangle(content.x(), y, content.width(), 20),
                Component.translatable(KEY + "crosswalks_value",
                        Component.translatable(KEY + (style.defaultCrosswalks() ? "on" : "off"))),
                style.defaultCrosswalks() ? RoadTextButton.Style.SELECTED
                        : RoadTextButton.Style.NORMAL, canMutate(),
                () -> updateJunction(old -> new RoadIntersectionStyle(old.pavementBlock(),
                        old.markingBlock(), old.crosswalkBlock(), old.stopLineBlock(),
                        !old.defaultCrosswalks(), old.defaultControl(), old.pavementOverride(), old.crosswalkOverride())));
        y += 27;
        y = addText(content, y, Component.translatable(KEY + "junction_control"), false) + 3;
        addButton(new UiRectangle(content.x(), y, content.width(), 20),
                Component.translatable(KEY + "control." + style.defaultControl().serializedName()),
                RoadTextButton.Style.NORMAL, canMutate(), () -> updateJunction(old -> {
                    IntersectionControl[] controls = IntersectionControl.values();
                    return new RoadIntersectionStyle(old.pavementBlock(), old.markingBlock(),
                            old.crosswalkBlock(), old.stopLineBlock(), old.defaultCrosswalks(),
                            controls[(old.defaultControl().ordinal() + 1) % controls.length],
                            old.pavementOverride(), old.crosswalkOverride());
                }));
        y += 28;
        for (RoadPresetEditorJunctionMaterial material : RoadPresetEditorJunctionMaterial.values()) {
            y = addText(content, y, Component.translatable(material.key()), false) + 3;
            boolean automaticPavement = material == RoadPresetEditorJunctionMaterial.PAVEMENT
                    && !style.usesPavementOverride(draft.materialPalette().pavementBlock());
            Optional<ResourceLocation> block = automaticPavement ? Optional.empty() : material.block(style);
            int toggleWidth = material.optional() ? 24 : 0;
            if (block.isPresent()) {
                RoadPresetBlockField field = new RoadPresetBlockField(
                        new UiRectangle(content.x(), y, content.width() - toggleWidth, 24),
                        this, block.orElseThrow(), replacement -> updateJunction(
                                old -> material.replace(old, Optional.of(replacement))));
                field.active = canMutate();
                addPanelWidget(field);
            } else {
                addButton(new UiRectangle(content.x(), y, content.width() - toggleWidth, 24),
                        Component.translatable(KEY + (automaticPavement ? "blend_road_pavement"
                                : material == RoadPresetEditorJunctionMaterial.CROSSWALK
                                        ? "inherit_crosswalk_material" : "no_material")),
                        RoadTextButton.Style.NORMAL, canMutate(),
                        () -> RoadPresetBlockSelectorScreen.open(this, automaticPavement
                                ? draft.materialPalette().pavementBlock() : draft.materialPalette().markingBlock(),
                                replacement -> updateJunction(old -> material.replace(old, Optional.of(replacement)))));
            }
            if (material.optional()) {
                RoadTextButton toggle = addButton(
                        new UiRectangle(content.right() - 20, y, 20, 24),
                        Component.literal(block.isPresent() ? "×" : "+"),
                        RoadTextButton.Style.GHOST, canMutate(),
                        () -> updateJunction(old -> material.replace(old, block.isPresent()
                                ? Optional.empty() : Optional.of(draft.materialPalette().markingBlock()))));
                toggle.setTooltip(Tooltip.create(Component.translatable(KEY
                        + (block.isPresent() ? "clear_junction_material" : "enable_junction_material"))));
            }
            y += 32;
        }
        addButton(new UiRectangle(content.x(), y, content.width(), 20),
                Component.translatable(KEY + "match_road_materials"),
                RoadTextButton.Style.NORMAL, canMutate(), () -> updateJunction(old ->
                        new RoadIntersectionStyle(draft.materialPalette().pavementBlock(),
                                Optional.of(draft.materialPalette().markingBlock()),
                                Optional.empty(), old.stopLineBlock(),
                                old.defaultCrosswalks(), old.defaultControl(), Optional.of(false), Optional.of(false))));
    }

    private void updateJunction(UnaryOperator<RoadIntersectionStyle> update) {
        mutate(() -> draft.setIntersectionStyle(update.apply(draft.intersectionStyle())));
    }

    private void addStepper(UiRectangle content, int y, Component label,
                            boolean decrease, boolean increase, Runnable minus, Runnable plus) {
        addButton(new UiRectangle(content.x(), y, 22, 20), Component.literal("−"),
                RoadTextButton.Style.NORMAL, decrease, minus);
        addFittingLabel(content, new UiRectangle(content.x() + 25, y, content.width() - 50, 20),
                label, false);
        addButton(new UiRectangle(content.right() - 22, y, 22, 20), Component.literal("+"),
                RoadTextButton.Style.NORMAL, increase, plus);
    }

    private int addHelp(UiRectangle content, int y, String key) {
        String suffix = activeViewport.height() < 140
                && List.of("materials_hint", "variations_hint", "junctions_hint", "details_hint",
                        "priority_hint", "multi_selection_hint").contains(key) ? ".compact" : "";
        return addText(content, y, Component.translatable(KEY + key + suffix), false);
    }

    private int addText(UiRectangle content, int y, Component message, boolean heading) {
        int textHeight = Math.max(font.lineHeight,
                font.split(message, Math.max(1, content.width())).size() * font.lineHeight);
        addPanelWidget(new RoadPresetEditorText(new UiRectangle(content.x(), y, content.width(), textHeight),
                message, heading ? UIStyle.TEXT_PRIMARY : UIStyle.TEXT_SECONDARY));
        return y + textHeight;
    }

    private void addLibraryControls() {
        UiRectangle search = layout.catalogSearch();
        searchBox = new EditBox(font, search.x(), search.y(), search.width(), search.height(),
                Component.translatable(KEY + "search"));
        searchBox.setMaxLength(64);
        searchBox.setValue(searchQuery);
        searchBox.setHint(Component.translatable(KEY + (tab == RoadPresetEditorTab.SAVED_ROADS
                ? "search_roads" : "search_components")).withStyle(style -> style.withColor(0xE0E0E0)));
        searchBox.setResponder(this::onSearchChanged);
        addRenderableWidget(searchBox);
    }

    private void addCatalogCards() {
        UiRectangle viewport = layout.catalogViewport();
        if (tab == RoadPresetEditorTab.LAYOUT) {
            List<RoadPresetEditorComponentCatalog.Entry> items = filteredComponents();
            for (int index = 0; index < items.size(); index++) {
                RoadPresetEditorComponentCatalog.Entry item = items.get(index);
                RoadPresetEditorComponent prototype = RoadPresetEditorComponentCatalog.prototype(item.kind(), draft);
                RoadPresetComponentButton button = new RoadPresetComponentButton(
                        new UiRectangle(viewport.x(), viewport.y() + index * 38, viewport.width(), 34),
                        prototype, item.name(), item.subtitle(), () -> addComponent(item.kind()));
                button.active = canMutate() && draft.surfaceBlockWidth() + prototype.width()
                        <= RoadPreset.MAXIMUM_SURFACE_BLOCK_WIDTH;
                paletteCardHits.add(new PaletteCardHit(button, item.kind(), prototype));
                addPanelWidget(button);
            }
        } else {
            List<RoadPresetCatalogEntry> entries = filteredPresets();
            for (int index = 0; index < entries.size(); index++) {
                RoadPresetCatalogEntry entry = entries.get(index);
                RoadTextButton button = new RoadTextButton(
                        new UiRectangle(viewport.x(), viewport.y() + index * 38, viewport.width(), 34),
                        Component.literal(entry.display().fallbackName()),
                        Component.translatable(KEY + "width_lanes", entry.surfaceWidth(), entry.laneCount()),
                        entry.id().equals(editingId) ? RoadTextButton.Style.SELECTED
                                : RoadTextButton.Style.NORMAL, () -> selectPreset(entry.id()));
                button.active = !requests.savePending() && !requests.contentPending();
                addPanelWidget(button);
            }
        }
    }

    private void addWorkspaceControls() {
        UiRectangle preview = layout.preview();
        RoadTextButton mirror = addButton(
                new UiRectangle(preview.right() - 24, preview.y(), 24, 20),
                Component.literal("↔"), RoadTextButton.Style.GHOST, canMutate(),
                () -> mutate(() -> {
                    draft.mirror();
                    clearComponentSelection();
                }));
        mirror.setTooltip(Tooltip.create(Component.translatable(KEY + "mirror")));
    }

    private int addComponentMaterialControl(UiRectangle content, int y, RoadSurfaceRole role,
            boolean inherited, List<ResourceLocation> blocks, Runnable reset) {
        y = addText(content, y, Component.translatable(materialKey(role)), false) + 3;
        addPanelWidget(new RoadTextButton(
                new UiRectangle(content.x(), y, content.width(), 34),
                Component.translatable(KEY + (inherited ? "material.inherited" : "material.override")),
                paletteSummary(blocks.size()), RoadTextButton.Style.NORMAL, () -> {
                    editingComponentMaterialRole = role;
                    panelScroll.reset();
                    requestRebuild();
                }));
        y += 39;
        addButton(new UiRectangle(content.x(), y, content.width(), 20),
                Component.translatable(KEY + "reset_material"), RoadTextButton.Style.GHOST,
                canMutate() && !inherited, () -> mutate(reset));
        return y + 28;
    }

    private void addFooterControls() {
        UiRectangle content = inset(layout.statusBar(), 3);
        int buttonY = content.bottom() - 20;
        int saveWidth = Math.min(100, content.width() / 3);
        int copyWidth = Math.min(118, (content.width() - 8) / 3);
        addButton(new UiRectangle(content.x(), buttonY, copyWidth, 20),
                Component.translatable(KEY + "new_road"), RoadTextButton.Style.NORMAL,
                !requests.savePending(), this::newDraft);
        addButton(new UiRectangle(content.x() + copyWidth + 4, buttonY, copyWidth, 20),
                Component.translatable(KEY + (editable ? "duplicate" : "use_as_template")),
                RoadTextButton.Style.NORMAL, !requests.savePending(), this::duplicateDraft);
        saveButton = addButton(new UiRectangle(content.right() - saveWidth, buttonY, saveWidth, 20),
                Component.translatable(KEY + "save"), RoadTextButton.Style.PRIMARY,
                false, this::saveDraft);
        saveButton.setTooltip(Tooltip.create(Component.translatable(KEY
                + (editable ? "save_shortcut" : "read_only"))));
    }

    private <T extends AbstractWidget> T addPanelWidget(T widget) {
        addRenderableWidget(widget);
        if (buildingPanel) {
            panelWidgets.add(new PanelWidget(widget, widget.getY() - activeViewport.y()));
            panelBottom = Math.max(panelBottom, widget.getY() + widget.getHeight());
        }
        return widget;
    }

    private void addScrollControls() {
        if (activeViewport.height() < 34) {
            return;
        }
        int x = activeViewport.right() + 3;
        scrollUp = addButton(new UiRectangle(x, activeViewport.y(), 15, 16),
                Component.literal("↑"), RoadTextButton.Style.GHOST, true,
                () -> scrollPanel(-RoadPresetEditorScroll.STEP));
        scrollDown = addButton(new UiRectangle(x, activeViewport.bottom() - 16, 15, 16),
                Component.literal("↓"), RoadTextButton.Style.GHOST, true,
                () -> scrollPanel(RoadPresetEditorScroll.STEP));
        scrollUp.setTooltip(Tooltip.create(Component.translatable(KEY + "scroll_up")));
        scrollDown.setTooltip(Tooltip.create(Component.translatable(KEY + "scroll_down")));
    }

    private void scrollPanel(int amount) {
        if (panelScroll.move(amount)) {
            positionPanelWidgets();
        }
    }

    private void positionPanelWidgets() {
        for (PanelWidget entry : panelWidgets) {
            AbstractWidget widget = entry.widget();
            widget.setY(activeViewport.y() + entry.top() - panelScroll.offset());
            widget.visible = widget.getY() >= activeViewport.y()
                    && widget.getY() + widget.getHeight() <= activeViewport.bottom();
            if (!widget.visible && widget.isFocused()) {
                widget.setFocused(false);
                if (getFocused() == widget) {
                    setFocused(null);
                }
            }
        }
        if (scrollUp != null) {
            scrollUp.active = panelScroll.offset() > 0;
            scrollDown.active = panelScroll.offset() < panelScroll.maximum();
            scrollUp.visible = panelScroll.maximum() > 0;
            scrollDown.visible = scrollUp.visible;
        }
    }
    private void addComponent(RoadPresetEditorComponentCatalog.Kind kind) {
        int insertion = selection.primary() >= 0
                ? selection.primary() + 1
                : draft.components().size();
        insertComponent(kind, insertion);
    }

    private void insertComponent(
            RoadPresetEditorComponentCatalog.Kind kind,
            int insertionIndex
    ) {
        mutate(() -> {
            selectOnlyComponent(draft.insertComponent(
                    insertionIndex,
                    RoadPresetEditorComponentCatalog.prototype(kind, draft)
            ));
        });
    }

    private void replaceSelectedComponent(
            RoadPresetEditorComponent replacement
    ) {
        if (selection.isSingle()) {
            mutate(() -> draft.replaceComponent(
                    selection.primary(),
                    replacement
            ));
        }
    }

    private void removeSelectedComponent() {
        if (!canRemoveSelectedComponent()) {
            return;
        }
        mutate(() -> {
            List<Integer> removals = selection.selected().stream()
                    .sorted(Comparator.reverseOrder())
                    .toList();
            for (int index : removals) {
                draft.removeComponent(index);
            }
            clearComponentSelection();
        });
    }

    private boolean canRemoveSelectedComponent() {
        if (!canMutate() || selection.isEmpty()
                || selection.selected().stream().anyMatch(index ->
                        index < 0 || index >= draft.components().size())) {
            return false;
        }
        long selectedLanes = selection.selected().stream()
                .map(draft.components()::get)
                .filter(RoadPresetEditorComponent::isVehicleLane)
                .count();
        return selection.selected().size() < draft.components().size()
                && selectedLanes < draft.laneCount();
    }

    private void moveSelectedComponent(int amount) {
        mutate(() -> {
            if (selection.isSingle() && selection.primary() >= 0) {
                int insertion = amount < 0
                        ? selection.primary() - 1
                        : selection.primary() + 2;
                selectOnlyComponent(draft.moveComponent(
                        selection.primary(),
                        insertion
                ));
            }
        });
    }

    private void mutate(Runnable mutation) {
        captureInputs();
        if (!canMutate()) {
            return;
        }
        try {
            mutation.run();
            dirty = true;
            clearConfirmations();
            setStatus(Component.empty(), false);
        } catch (IllegalArgumentException
                | IndexOutOfBoundsException invalid) {
            setStatus(message(invalid), true);
        }
        requestRebuild();
    }

    private void cycleCategory() {
        mutate(() -> {
            category = RoadCategory.nextEditable(category).serializedName();
        });
    }

    void newDraft() {
        captureInputs();
        if (requests.savePending()) {
            return;
        }
        ResourceLocation id = RoadPresetEditorCopyFactory.uniqueId("new_road", this::catalogContains);
        if (dirty && !confirmations.confirmSwitch(id)) {
            setStatus(Component.translatable(KEY + "unsaved_switch"), true);
        } else {
            startNewDraft(id);
        }
        requestRebuild();
    }

    private void startNewDraft(ResourceLocation id) {
        editingId = id;
        draft = new RoadPresetEditorDraft(RoadPresetTemplates.blank());
        draft.setIntersectionStyle(new RoadIntersectionStyle(
                draft.materialPalette().pavementBlock(),
                Optional.of(draft.materialPalette().markingBlock()),
                Optional.empty(),
                Optional.empty(), true, IntersectionControl.FREE, Optional.of(false), Optional.of(false)));
        source = draft.build(1);
        expectedRevision = 0;
        editable = true;
        dirty = true;
        speedValue = null;
        invalidSpeed = false;
        replaceNameValue(source.display().fallbackName());
        category = source.display().category();
        clearComponentSelection();
        if (requests.contentPending()) {
            gateway.cancelContentRequest(requests.contentRequestId());
        }
        requests.clear();
        clearConfirmations();
        selectTab(RoadPresetEditorTab.LAYOUT);
        setStatus(Component.translatable(KEY + "status.new_road"), false);
    }

    private void duplicateDraft() {
        captureInputs();
        if (draft == null || requests.savePending() || invalidSpeed) {
            return;
        }
        try {
            RoadPreset current = draft.build(
                    Math.max(1, expectedRevision + 1),
                    nameValue,
                    category
            );
            RoadPresetEditorCopyFactory.Copy result =
                    RoadPresetEditorCopyFactory.create(
                            editingId,
                            current,
                            Component.translatable(
                                    KEY + "copy_name",
                                    nameValue.strip()
                            ).getString(),
                            category,
                            this::catalogContains
                    );
            ResourceLocation copyId = result.id();
            String copyName = result.name();
            RoadPreset copy = result.preset();
            editingId = copyId;
            expectedRevision = 0;
            editable = true;
            source = copy;
            draft = new RoadPresetEditorDraft(copy);
            speedValue = null;
            invalidSpeed = false;
            replaceNameValue(copyName);
            clearComponentSelection();
            dirty = true;
            clearConfirmations();
            setStatus(
                    Component.translatable(
                            KEY + "status.editing_copy",
                            editingId
                    ),
                    false
            );
        } catch (IllegalArgumentException invalid) {
            setStatus(message(invalid), true);
        }
        requestRebuild();
    }

    private boolean catalogContains(ResourceLocation id) {
        return gateway.catalog().entries().stream()
                .anyMatch(entry -> entry.id().equals(id));
    }

    private void resetDraft() {
        captureInputs();
        if (source == null || requests.savePending()) {
            return;
        }
        draft = new RoadPresetEditorDraft(source);
        speedValue = null;
        invalidSpeed = false;
        replaceNameValue(source.display().fallbackName());
        category = source.display().category();
        clearComponentSelection();
        dirty = expectedRevision == 0;
        clearConfirmations();
        setStatus(
                Component.translatable(
                        editable ? KEY + "editable" : KEY + "read_only"
                ),
                false
        );
        requestRebuild();
    }

    void saveDraft() {
        captureInputs();
        if (!canMutate() || !dirty || invalidSpeed) {
            return;
        }
        try {
            RoadPreset candidate = draft.build(
                    expectedRevision + 1,
                    nameValue,
                    category
            );
            var requestId = requests.beginSave(candidate);
            if (!gateway.sendSave(
                    PresetSaveIntentC2SPacket.current(
                            requestId,
                            editingId,
                            expectedRevision,
                            candidate
                    )
            )) {
                requests.finishSave();
                setStatus(
                        Component.translatable(
                                KEY + "error.save_unsupported"
                        ),
                        true
                );
            } else {
                setStatus(Component.translatable(KEY + "saving"), false);
            }
        } catch (IllegalArgumentException invalid) {
            setStatus(message(invalid), true);
        }
        requestRebuild();
    }

    private void deleteDraft() {
        captureInputs();
        if (!canDelete()) {
            return;
        }
        if (confirmations.armDelete()) {
            setStatus(
                    Component.translatable(KEY + "delete_prompt"),
                    true
            );
            requestRebuild();
            return;
        }

        var requestId = requests.beginDelete();
        if (!gateway.sendDelete(
                PresetDeleteIntentC2SPacket.current(
                        requestId,
                        editingId,
                        expectedRevision
                )
        )) {
            requests.finishSave();
            setStatus(
                    Component.translatable(
                            KEY + "error.delete_unsupported"
                    ),
                    true
            );
        } else {
            setStatus(Component.translatable(KEY + "deleting"), false);
        }
        requestRebuild();
    }

    private void selectPreset(ResourceLocation id) {
        captureInputs();
        if (requests.savePending()) {
            setStatus(
                    Component.translatable(KEY + "mutation_pending"),
                    true
            );
            requestRebuild();
            return;
        }
        if (id.equals(editingId) && draft != null) {
            gateway.selectPreset(id);
            return;
        }
        if (dirty && !confirmations.confirmSwitch(id)) {
            setStatus(
                    Component.translatable(KEY + "unsaved_switch"),
                    true
            );
            requestRebuild();
            return;
        }

        gateway.selectPreset(id);
        editingId = id;
        confirmations.clear();
        source = null;
        draft = null;
        expectedRevision = 0;
        editable = false;
        dirty = false;
        clearComponentSelection();
        replaceNameValue(gateway.catalog().entries()
                .stream()
                .filter(entry -> entry.id().equals(id))
                .map(entry -> entry.display().fallbackName())
                .findFirst()
                .orElse(""));
        category = "medium";
        requests.clear();
        requestContent();
        requestRebuild();
    }

    void requestContent() {
        if (requests.contentPending()) {
            return;
        }
        var requestId = requests.beginContentRequest();
        if (!gateway.requestContent(
                requestId,
                editingId
        )) {
            requests.finishContentRequest();
            setStatus(
                    Component.translatable(KEY + "load_unsupported"),
                    true
            );
        } else {
            setStatus(Component.translatable(KEY + "loading"), false);
        }
        requestRebuild();
    }

    @Override
    public void tick() {
        super.tick();
        if (nameBox != null) {
            tickEditBox(nameBox);
        }
        if (searchBox != null) {
            tickEditBox(searchBox);
        }
        if (speedBox != null) {
            tickEditBox(speedBox);
        }
        pollRequests();
        long catalogRevision = gateway.catalog().revision();
        if (observedCatalogRevision != catalogRevision) {
            observedCatalogRevision = catalogRevision;
            requestRebuild();
        }
        if (rebuildPending) {
            rebuildControls();
        } else if (catalogRefreshPending) {
            refreshCatalogCards();
        }
    }

    void pollRequests() {
        pollContentRequest();
        pollSaveRequest();
    }

    private void onNameChanged(String replacement) {
        if (replacement.equals(nameValue)) {
            return;
        }
        nameValue = replacement;
        if (editable && draft != null) {
            dirty = true;
            clearConfirmations();
            setStatus(Component.empty(), false);
        }
        refreshSaveState();
    }

    private void onSearchChanged(String replacement) {
        if (replacement.equals(searchQuery)) {
            return;
        }
        searchQuery = replacement;
        panelScroll.reset();
        requestCatalogRefresh();
    }

    private void pollContentRequest() {
        if (!requests.contentPending()) {
            return;
        }
        Optional<PresetContentS2CPacket> response =
                gateway.takeContent(
                        requests.contentRequestId()
                );
        if (response.isPresent()) {
            PresetContentS2CPacket packet = response.orElseThrow();
            requests.finishContentRequest();
            if (!packet.presetId().equals(editingId)) {
                setStatus(
                        Component.translatable(
                                KEY + "error.content_unavailable"
                        ),
                        true
                );
                requestRebuild();
                return;
            }
            loadSource(packet.preset());
            return;
        }
        if (requests.tickContent(CONTENT_TIMEOUT_TICKS)) {
            gateway.cancelContentRequest(
                    requests.contentRequestId()
            );
            requests.finishContentRequest();
            setStatus(
                    Component.translatable(KEY + "load_timeout"),
                    true
            );
            requestRebuild();
        }
    }

    private void pollSaveRequest() {
        if (!requests.savePending()) {
            return;
        }
        Optional<PresetSaveResultS2CPacket> result =
                gateway.takeSaveResult(
                        requests.saveRequestId()
                );
        if (result.isPresent()) {
            handleSaveResult(result.orElseThrow());
            return;
        }
        if (requests.tickSave(SAVE_TIMEOUT_TICKS)) {
            requests.finishSave();
            setStatus(
                    Component.translatable(KEY + "save_timeout"),
                    true
            );
            requestRebuild();
        }
    }

    private void loadSource(RoadPreset preset) {
        source = Objects.requireNonNull(preset, "preset");
        draft = new RoadPresetEditorDraft(preset);
        speedValue = null;
        invalidSpeed = false;
        expectedRevision = preset.revision();
        editable = gateway.catalog().entries()
                .stream()
                .filter(entry -> entry.id().equals(editingId))
                .findFirst()
                .map(RoadPresetCatalogEntry::editable)
                .orElse(false);
        replaceNameValue(preset.display().fallbackName());
        category = preset.display().category();
        dirty = false;
        clearComponentSelection();
        clearConfirmations();
        setStatus(
                Component.translatable(
                        editable ? KEY + "editable" : KEY + "read_only"
                ),
                false
        );
        requestRebuild();
    }

    private void handleSaveResult(PresetSaveResultS2CPacket result) {
        RoadPresetEditorRequestState.SaveCompletion completion =
                requests.finishSave();
        if (result.code() == PresetSaveResultCode.ACCEPTED) {
            if (completion.deleting()) {
                dirty = false;
                super.onClose();
                return;
            }
            RoadPreset accepted = completion.candidate();
            if (accepted != null) {
                expectedRevision = result.presetRevision();
                source = accepted;
                draft = new RoadPresetEditorDraft(accepted);
                replaceNameValue(accepted.display().fallbackName());
                category = accepted.display().category();
            }
            dirty = false;
            clearConfirmations();
            setStatus(Component.translatable(KEY + "saved"), false);
            gateway.selectPreset(editingId);
        } else {
            setStatus(rejectionMessage(result.code()), true);
        }
        requestRebuild();
    }

    private static Component rejectionMessage(PresetSaveResultCode code) {
        return switch (code) {
            case STALE_REVISION -> Component.translatable(
                    KEY + "stale_revision"
            );
            case NOT_AUTHORIZED -> Component.translatable(
                    KEY + "not_authorized"
            );
            case UNKNOWN_BLOCK -> Component.translatable(
                    KEY + "unknown_block"
            );
            case PAYLOAD_LIMIT -> Component.translatable(
                    KEY + "payload_limit"
            );
            case INVALID_PRESET -> Component.translatable(
                    KEY + "invalid_preset"
            );
            case ACCEPTED -> Component.empty();
        };
    }

    private void captureInputs() {
        if (nameBox != null) {
            String currentName = nameBox.getValue();
            if (!currentName.equals(nameValue)) {
                nameValue = currentName;
                if (editable && draft != null) {
                    dirty = true;
                    clearConfirmations();
                }
            }
        }
        if (searchBox != null) {
            searchQuery = searchBox.getValue();
        }
    }

    private void requestRebuild() {
        if (!rebuildPending) {
            focus.capture(nameBox, searchBox, speedBox);
        }
        captureInputs();
        rebuildPending = true;
    }

    private void refreshSaveState() {
        if (saveButton == null || draft == null) {
            return;
        }
        saveButton.active = canMutate()
                && dirty
                && !invalidSpeed
                && draft.validationError(
                        expectedRevision + 1,
                        nameValue,
                        category
                ).isEmpty();
    }

    private boolean canMutate() {
        return editable && draft != null && !requests.savePending();
    }

    private boolean canDelete() {
        return canMutate() && expectedRevision > 0;
    }

    private boolean hasSelectedComponent() {
        return selection.primary() >= 0;
    }

    private void selectOnlyComponent(int component) {
        selection.selectOnly(component);
        showSelection();
    }

    private void toggleComponentSelection(int component) {
        selection.toggle(component);
        showSelection();
    }

    private void clearComponentSelection() {
        selection.clear();
        inspectSelection = false;
        editingComponentMaterialRole = null;
        panelScroll.reset();
    }

    private void showSelection() {
        tab = RoadPresetEditorTab.LAYOUT;
        inspectSelection = !selection.isEmpty();
        editingComponentMaterialRole = null;
        panelScroll.reset();
    }

    private void replaceNameValue(String replacement) {
        nameValue = Objects.requireNonNull(replacement, "replacement");
        if (nameBox != null && !nameBox.getValue().equals(nameValue)) {
            nameBox.setValue(nameValue);
        }
    }

    private void clearConfirmations() {
        confirmations.clear();
    }

    private void setStatus(Component message, boolean error) {
        status = Objects.requireNonNull(message, "message");
        statusIsError = error;
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        renderRoadBackground(graphics);
        boolean componentCatalog = tab == RoadPresetEditorTab.LAYOUT;
        boolean catalogEmpty = showingCatalog() && (componentCatalog
                ? filteredComponents().isEmpty()
                : filteredPresets().isEmpty());
        RoadPresetEditorRenderer.render(
                graphics,
                font,
                new RoadPresetEditorRenderer.View(
                        width,
                        height,
                        editingId,
                        layout,
                        draft,
                        expectedRevision,
                        editable,
                        dirty,
                        selection.primary(),
                        selection.selected(),
                        invalidSpeed ? Component.translatable(KEY + "invalid_speed") : status,
                        invalidSpeed || statusIsError,
                        nameValue,
                        category,
                        catalogEmpty,
                        componentCatalog,
                        mouseX,
                        mouseY,
                        dragVisual(),
                        tab,
                        editingMaterialRole != null,
                        activeViewport,
                        panelScroll.offset(),
                        panelScroll.maximum()
                )
        );
        super.render(graphics, mouseX, mouseY, partialTick);
        if (draft != null && dragState == null && !draggingScrollbar) {
            int component = componentIndexAt(mouseX, mouseY);
            if (component >= 0) {
                RoadPresetEditorComponent part = draft.components().get(component);
                Component tooltip = componentLabel(part).copy().append(" · ")
                        .append(Component.translatable(KEY + "component_width", part.width()));
                RoadGuiDrawing.tooltip(graphics, font, font.split(tooltip, Math.min(260, width - 20)), mouseX, mouseY);
            } else if (layout.statusBar().contains(mouseX, mouseY)
                    && mouseY < layout.statusBar().y() + 15) {
                Component feedback = invalidSpeed ? Component.translatable(KEY + "invalid_speed") : status;
                if (nameValue.isBlank()) {
                    feedback = Component.translatable(KEY + "name_required");
                }
                if (!feedback.getString().isEmpty()) {
                    RoadGuiDrawing.tooltip(graphics, font, font.split(feedback, Math.min(300, width - 20)), mouseX, mouseY);
                }
            }
        }
    }

    @Override
    protected boolean roadMouseClicked(double mouseX, double mouseY, int button, boolean shiftDown) {
        if (draft != null && button == 0) {
            if (activeViewport != null && panelScroll.maximum() > 0
                    && mouseX >= activeViewport.right() + 3
                    && mouseX < activeViewport.right() + 18
                    && mouseY >= activeViewport.y() + 18
                    && mouseY < activeViewport.bottom() - 18) {
                draggingScrollbar = true;
                scrollToMouse(mouseY);
                return true;
            }
            Optional<PaletteCardHit> paletteHit = paletteCardHits.stream()
                    .filter(hit -> hit.widget().visible && hit.widget().active
                            && hit.bounds().contains(mouseX, mouseY))
                    .findFirst();
            if (paletteHit.isPresent() && canMutate()) {
                PaletteCardHit hit = paletteHit.orElseThrow();
                dragState = RoadPresetEditorDragState.palette(
                        hit.kind(), hit.prototype(), mouseX, mouseY
                );
                return true;
            }

            int component = componentIndexAt(mouseX, mouseY);
            if (component >= 0) {
                if (shiftDown) {
                    toggleComponentSelection(component);
                    dragState = null;
                    requestRebuild();
                    return true;
                }
                selectOnlyComponent(component);
                if (canMutate()) {
                    dragState = RoadPresetEditorDragState.existing(
                            component,
                            draft.components().get(component),
                            mouseX,
                            mouseY
                    );
                } else {
                    requestRebuild();
                }
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean roadMouseDragged(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingScrollbar) {
            scrollToMouse(mouseY);
            return true;
        }
        if (button == 0 && dragState != null) {
            dragState.update(mouseX, mouseY, insertionIndexAt(
                    mouseX,
                    mouseY
            ));
            return true;
        }
        return false;
    }

    @Override
    protected boolean roadMouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        if (button == 0 && dragState != null) {
            RoadPresetEditorDragState completed = dragState;
            dragState = null;
            if (completed.active() && completed.dropIndex() >= 0) {
                if (completed.sourceIndex() >= 0) {
                    mutate(() -> selectOnlyComponent(draft.moveComponent(
                            completed.sourceIndex(),
                            completed.dropIndex()
                    )));
                } else {
                    insertComponent(
                            completed.kind(),
                            completed.dropIndex()
                    );
                }
            } else if (!completed.active()
                    && completed.sourceIndex() < 0) {
                addComponent(completed.kind());
            } else {
                requestRebuild();
            }
            return true;
        }
        return false;
    }

    @Override
    protected boolean roadKeyPressed(int keyCode, boolean controlDown) {
        if (draggingScrollbar && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            draggingScrollbar = false;
            return true;
        }
        if (dragState != null && keyCode == 256) {
            dragState = null;
            return true;
        }
        boolean textInputFocused = nameBox != null && nameBox.isFocused()
                || searchBox != null && searchBox.isFocused()
                || speedBox != null && speedBox.isFocused();
        if (controlDown && keyCode == GLFW.GLFW_KEY_S) {
            saveDraft();
            return true;
        }
        if (!textInputFocused && activeViewport != null
                && (keyCode == GLFW.GLFW_KEY_PAGE_DOWN || keyCode == GLFW.GLFW_KEY_PAGE_UP)) {
            scrollPanel((keyCode == GLFW.GLFW_KEY_PAGE_UP ? -1 : 1)
                    * Math.max(24, activeViewport.height() - 24));
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE
                && !textInputFocused
                && tab == RoadPresetEditorTab.LAYOUT
                && !selection.isEmpty()) {
            if (canRemoveSelectedComponent()) {
                removeSelectedComponent();
            }
            return true;
        }
        return false;
    }

    private int componentIndexAt(double mouseX, double mouseY) {
        return crossSectionGeometry(
                RoadPresetEditorRenderer.roadBounds(layout.preview())
        ).componentIndexAt(mouseX, mouseY).orElse(-1);
    }

    private void scrollToMouse(double mouseY) {
        int trackHeight = activeViewport.height() - 38;
        if (trackHeight <= 0 || panelScroll.maximum() <= 0) {
            return;
        }
        int thumb = Math.min(trackHeight, Math.max(6,
                trackHeight * activeViewport.height() / (panelScroll.maximum() + activeViewport.height())));
        double fraction = (mouseY - activeViewport.y() - 19 - thumb / 2.0)
                / Math.max(1, trackHeight - thumb);
        int target = (int) Math.round(Math.max(0, Math.min(1, fraction)) * panelScroll.maximum());
        scrollPanel(target - panelScroll.offset());
    }

    private int insertionIndexAt(double mouseX, double mouseY) {
        return crossSectionGeometry(
                RoadPresetEditorRenderer.roadBounds(layout.preview())
        ).insertionIndexAt(mouseX, mouseY).orElse(-1);
    }

    private RoadPresetEditorCrossSectionGeometry crossSectionGeometry(
            UiRectangle target
    ) {
        return RoadPresetEditorCrossSectionGeometry.create(
                target,
                draft.components().stream()
                        .map(RoadPresetEditorComponent::width)
                        .toList()
        );
    }

    private RoadPresetEditorRenderer.DragVisual dragVisual() {
        if (dragState == null) {
            return null;
        }
        return new RoadPresetEditorRenderer.DragVisual(
                dragState.component(),
                dragState.currentX(),
                dragState.currentY(),
                dragState.active(),
                dragState.dropIndex()
        );
    }

    @Override
    protected boolean scrollRoadPanel(
            double mouseX,
            double mouseY,
            double delta
    ) {
        if (draft != null && layout != null
                && layout.propertiesPanel().contains(mouseX, mouseY)
                && delta != 0.0) {
            scrollPanel(-(int) Math.signum(delta) * RoadPresetEditorScroll.STEP);
            return true;
        }
        return false;
    }

    @Override
    public void onClose() {
        captureInputs();
        if (requests.savePending()) {
            setStatus(
                    Component.translatable(KEY + "mutation_pending"),
                    true
            );
            requestRebuild();
            return;
        }
        if (requests.contentPending()) {
            gateway.cancelContentRequest(requests.contentRequestId());
            requests.finishContentRequest();
        }
        if (dirty && !confirmations.confirmClose()) {
            setStatus(
                    Component.translatable(KEY + "unsaved_close"),
                    true
            );
            requestRebuild();
            return;
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private List<RoadPresetEditorComponentCatalog.Entry> filteredComponents() {
        return RoadPresetEditorComponentCatalog.filtered(searchQuery);
    }

    private List<RoadPresetCatalogEntry> filteredPresets() {
        String filter = normalizedSearch();
        return gateway.catalog().entries().stream()
                .filter(entry -> filter.isEmpty()
                        || (entry.display().fallbackName() + " "
                                + entry.display().category() + " "
                                + entry.id())
                                .toLowerCase(Locale.ROOT)
                                .contains(filter))
                .toList();
    }

    private String normalizedSearch() {
        return searchQuery.strip().toLowerCase(Locale.ROOT);
    }

    private String normalizedCategory() {
        return RoadCategory.fromDisplayCategory(category).serializedName();
    }

    private RoadTextButton addButton(
            UiRectangle bounds,
            Component message,
            RoadTextButton.Style style,
            boolean active,
            Runnable action
    ) {
        RoadTextButton button = new RoadTextButton(
                bounds,
                message,
                style,
                action
        );
        button.active = active && bounds.width() > 0 && bounds.height() > 0;
        button.setTooltip(Tooltip.create(message));
        addPanelWidget(button);
        return button;
    }

    private void requestCatalogRefresh() {
        catalogRefreshPending = true;
    }

    private void refreshCatalogCards() {
        catalogRefreshPending = false;
        requestRebuild();
    }

    private RoadPresetEditorLabel addFittingLabel(
            UiRectangle container,
            UiRectangle bounds,
            Component message,
            boolean selected
    ) {
        if (!containedBy(container, bounds)) {
            return null;
        }
        RoadPresetEditorLabel label = new RoadPresetEditorLabel(
                bounds,
                message,
                selected
        );
        addPanelWidget(label);
        return label;
    }

    private static Component message(RuntimeException exception) {
        Objects.requireNonNull(exception, "exception");
        return Component.translatable(KEY + "validation_error");
    }

    private record PanelWidget(AbstractWidget widget, int top) {
    }

    private record PaletteCardHit(
            AbstractWidget widget,
            RoadPresetEditorComponentCatalog.Kind kind,
            RoadPresetEditorComponent prototype
    ) {
        UiRectangle bounds() {
            return new UiRectangle(widget.getX(), widget.getY(),
                    widget.getWidth(), widget.getHeight());
        }
    }

}
