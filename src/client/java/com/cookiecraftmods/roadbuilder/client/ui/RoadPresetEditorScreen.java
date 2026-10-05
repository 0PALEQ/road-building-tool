package com.cookiecraftmods.roadbuilder.client.ui;

import com.cookiecraftmods.roadbuilder.networking.PresetContentS2CPacket;
import com.cookiecraftmods.roadbuilder.networking.PresetDeleteIntentC2SPacket;
import com.cookiecraftmods.roadbuilder.networking.PresetSaveIntentC2SPacket;
import com.cookiecraftmods.roadbuilder.networking.PresetSaveResultCode;
import com.cookiecraftmods.roadbuilder.networking.PresetSaveResultS2CPacket;
import com.cookiecraftmods.roadbuilder.preset.RoadCategory;
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
import java.util.function.BiConsumer;

import static com.cookiecraftmods.roadbuilder.client.ui.RoadPresetEditorUi.*;

public final class RoadPresetEditorScreen extends Screen {
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
    private int catalogScroll;
    private long observedCatalogRevision = -1;
    private String nameValue = "";
    private String category = "medium";
    private String searchQuery = "";
    private Component status = Component.translatable(KEY + "loading");
    private LibraryTab libraryTab = LibraryTab.COMPONENTS;
    private EditBox nameBox;
    private EditBox searchBox;
    private RoadPresetEditorButton saveButton;
    private final List<PaletteCardHit> paletteCardHits = new ArrayList<>();
    private final List<AbstractWidget> catalogWidgets = new ArrayList<>();
    private RoadPresetEditorDragState dragState;

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
        screen.source = RoadPresetTemplates.blank();
        screen.draft = new RoadPresetEditorDraft(screen.source);
        screen.expectedRevision = 0;
        screen.editable = true;
        screen.dirty = true;
        screen.nameValue = screen.source.display().fallbackName();
        screen.category = screen.source.display().category();
        screen.status = Component.translatable(KEY + "status.new_road");
        return screen;
    }

    @Override
    protected void init() {
        captureInputs();
        focus.capture(nameBox, searchBox);
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
        saveButton = null;
        paletteCardHits.clear();
        catalogWidgets.clear();
        catalogRefreshPending = false;

        addCloseButton();
        if (draft == null) {
            addRetryButton();
            rebuildPending = false;
            return;
        }

        addPropertiesControls();
        addLibraryControls();
        addFooterControls();
        focus.restore(nameBox, searchBox);
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
                RoadPresetEditorButton.Style.GHOST,
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
                layout.libraryPanel()
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
                RoadPresetEditorButton.Style.PRIMARY,
                true,
                this::requestContent
        );
    }

    private void addPropertiesControls() {
        UiRectangle content = inset(layout.propertiesPanel(), 6);
        boolean compactInspector = content.height() < 100
                && hasSelectedComponent();
        if (!compactInspector && content.height() >= 38) {
            int fieldY = content.y() + 18;
            nameBox = new EditBox(
                    font,
                    content.x(),
                    fieldY,
                    content.width(),
                    18,
                    Component.translatable(KEY + "name")
            );
            nameBox.setMaxLength(64);
            nameBox.setValue(nameValue);
            nameBox.setEditable(canMutate());
            nameBox.setResponder(this::onNameChanged);
            addRenderableWidget(nameBox);
        }
        if (!compactInspector && content.height() >= 59) {
            int categoryY = content.y() + 39;
            RoadPresetEditorButton categoryButton = addButton(
                    new UiRectangle(
                            content.x(),
                            categoryY,
                            content.width(),
                            18
                    ),
                    Component.translatable(
                            KEY + "category"
                    ).append(" / ").append(Component.translatable(
                            KEY + "category." + normalizedCategory()
                    )),
                    RoadPresetEditorButton.Style.NORMAL,
                    canMutate(),
                    this::cycleCategory
            );
            categoryButton.active = canMutate();
        }

        int inspectorY = compactInspector
                ? content.y() + 15
                : content.y() + 83;
        if (selection.primary() >= 0
                && selection.primary() < draft.components().size()) {
            addComponentInspector(content, inspectorY);
        } else {
            addRoadMaterialControls(content, inspectorY);
        }
    }

    private void addRoadMaterialControls(UiRectangle content, int y) {
        addRoadMaterialControl(content, y, RoadSurfaceRole.PAVEMENT,
                KEY + "material.pavement");
        addRoadMaterialControl(content, y + 21, RoadSurfaceRole.MARKING,
                KEY + "material.marking");
        addRoadMaterialControl(content, y + 42, RoadSurfaceRole.SIDEWALK,
                KEY + "material.sidewalk");
        addRoadMaterialControl(content, y + 63, RoadSurfaceRole.GRASS_MEDIAN,
                KEY + "material.grass_median");
        addRoadMaterialControl(content, y + 84, RoadSurfaceRole.CONCRETE_MEDIAN,
                KEY + "material.concrete_median");
    }

    private void addRoadMaterialControl(
            UiRectangle content,
            int y,
            RoadSurfaceRole role,
            String labelKey
    ) {
        int labelWidth = Math.min(68, Math.max(38, content.width() * 42 / 100));
        UiRectangle labelBounds = new UiRectangle(
                content.x(), y, labelWidth, 18
        );
        int setButtonWidth = Math.min(18, Math.max(14, content.width() / 10));
        int gap = 2;
        UiRectangle fieldBounds = new UiRectangle(
                labelBounds.right() + 3,
                y,
                Math.max(
                        0,
                        content.right() - labelBounds.right() - 3
                                - setButtonWidth * 2 - gap * 2
                ),
                18
        );
        addFittingLabel(
                content,
                labelBounds,
                Component.translatable(labelKey),
                false
        );
        if (!containedBy(content, fieldBounds)) {
            return;
        }
        RoadPresetBlockField field = new RoadPresetBlockField(
                fieldBounds,
                this,
                draft.materialPalette().blockFor(role),
                draft.materialPalette().blockSetFor(role).size(),
                replacement -> mutate(() -> {
                    List<ResourceLocation> blocks = new ArrayList<>(
                            draft.materialPalette().blockSetFor(role)
                    );
                    blocks.set(0, replacement);
                    draft.setRoadMaterialSet(role, blocks);
                })
        );
        field.active = canMutate();
        addRenderableWidget(field);
        int addX = fieldBounds.right() + gap;
        addFittingButton(
                content,
                new UiRectangle(addX, y, setButtonWidth, 18),
                Component.literal("+"),
                RoadPresetEditorButton.Style.NORMAL,
                canMutate()
                        && draft.materialPalette().blockSetFor(role).size() < 16,
                () -> RoadPresetBlockSelectorScreen.open(
                        this,
                        draft.materialPalette().blockFor(role),
                        replacement -> mutate(() -> {
                            List<ResourceLocation> blocks = new ArrayList<>(
                                    draft.materialPalette().blockSetFor(role)
                            );
                            if (!blocks.contains(replacement)) {
                                blocks.add(replacement);
                                draft.setRoadMaterialSet(role, blocks);
                            }
                        })
                )
        );
        addFittingButton(
                content,
                new UiRectangle(
                        addX + setButtonWidth + gap,
                        y,
                        setButtonWidth,
                        18
                ),
                Component.literal("−"),
                RoadPresetEditorButton.Style.NORMAL,
                canMutate()
                        && draft.materialPalette().blockSetFor(role).size() > 1,
                () -> mutate(() -> {
                    List<ResourceLocation> blocks = new ArrayList<>(
                            draft.materialPalette().blockSetFor(role)
                    );
                    blocks.remove(blocks.size() - 1);
                    draft.setRoadMaterialSet(role, blocks);
                })
        );
    }

    private void addComponentInspector(UiRectangle content, int y) {
        RoadPresetEditorComponent selected =
                draft.components().get(selection.primary());
        int rowHeight = 18;
        int gap = 3;

        addFittingButton(
                content,
                new UiRectangle(content.x(), y, content.width(), rowHeight),
                Component.translatable(KEY + "road_materials"),
                RoadPresetEditorButton.Style.NORMAL,
                true,
                () -> {
                    clearComponentSelection();
                    requestRebuild();
                }
        );
        y += rowHeight + gap;

        addFittingLabel(
                content,
                new UiRectangle(content.x(), y, content.width(), rowHeight),
                componentLabel(selected),
                true
        );

        int widthY = y + rowHeight + gap;
        List<UiRectangle> widthControls = columns(
                content.x(), widthY, content.width(), rowHeight, 3, gap
        );
        addFittingButton(
                content,
                widthControls.get(0),
                Component.literal("−"),
                RoadPresetEditorButton.Style.NORMAL,
                canMutate()
                        && selection.isSingle()
                        && selected.hasAdjustableWidth()
                        && selected.width() > 1,
                () -> replaceSelectedComponent(
                        selected.withWidth(selected.width() - 1)
                )
        );
        RoadPresetEditorLabel widthLabel = addFittingLabel(
                content,
                widthControls.get(1),
                Component.translatable(KEY + "width"),
                false
        );
        if (widthLabel != null) {
            widthLabel.setTooltip(Tooltip.create(Component.translatable(
                    KEY + "component_width",
                    selected.width()
            )));
        }
        addFittingButton(
                content,
                widthControls.get(2),
                Component.literal("+"),
                RoadPresetEditorButton.Style.NORMAL,
                canMutate() && selection.isSingle()
                        && selected.hasAdjustableWidth(),
                () -> replaceSelectedComponent(
                        selected.withWidth(selected.width() + 1)
                )
        );

        int inheritanceY = widthY + rowHeight + gap;
        addFittingButton(
                content,
                new UiRectangle(
                        content.x(), inheritanceY, content.width(), rowHeight
                ),
                Component.translatable(selected.inheritsRoadMaterial()
                        ? KEY + "material.inherited"
                        : KEY + "material.override"),
                selected.inheritsRoadMaterial()
                        ? RoadPresetEditorButton.Style.SELECTED
                        : RoadPresetEditorButton.Style.NORMAL,
                canMutate() && selection.isSingle(),
                () -> mutate(() -> {
                    if (selected.inheritsRoadMaterial()) {
                        draft.setComponentMaterialOverride(
                                selection.primary(),
                                selected.block()
                        );
                    } else {
                        draft.clearComponentMaterialOverride(
                                selection.primary()
                        );
                    }
                })
        );

        int materialY = inheritanceY + rowHeight + gap;
        int setButtonWidth = Math.min(22, Math.max(18, content.width() / 7));
        UiRectangle materialBounds = new UiRectangle(
                content.x(), materialY,
                Math.max(0, content.width() - setButtonWidth * 2 - gap * 2),
                rowHeight
        );
        if (containedBy(content, materialBounds)) {
            RoadPresetBlockField materialField = new RoadPresetBlockField(
                    materialBounds,
                    this,
                    selected.block(),
                    selected.blockSet().size(),
                    replacement -> {
                        if (!replacement.equals(selected.block())) {
                            List<net.minecraft.resources.ResourceLocation>
                                    replacementSet = new ArrayList<>(
                                            selected.blockSet()
                                    );
                            replacementSet.set(0, replacement);
                            replaceSelectedComponent(
                                    selected.withBlockSet(replacementSet)
                            );
                        }
                    }
            );
            materialField.active = canMutate()
                    && selection.isSingle();
            addRenderableWidget(materialField);
        }
        int addX = materialBounds.right() + gap;
        addFittingButton(
                content,
                new UiRectangle(addX, materialY, setButtonWidth, rowHeight),
                Component.literal("+"),
                RoadPresetEditorButton.Style.NORMAL,
                canMutate() && selection.isSingle()
                        && selected.blockSet().size() < 16,
                () -> RoadPresetBlockSelectorScreen.open(
                        this,
                        selected.block(),
                        replacement -> {
                            if (!selected.blockSet().contains(replacement)) {
                                List<net.minecraft.resources.ResourceLocation>
                                        replacementSet = new ArrayList<>(
                                                selected.blockSet()
                                        );
                                replacementSet.add(replacement);
                                replaceSelectedComponent(
                                        selected.withBlockSet(replacementSet)
                                );
                            }
                        }
                )
        );
        addFittingButton(
                content,
                new UiRectangle(
                        addX + setButtonWidth + gap,
                        materialY,
                        setButtonWidth,
                        rowHeight
                ),
                Component.literal("−"),
                RoadPresetEditorButton.Style.NORMAL,
                canMutate() && selection.isSingle()
                        && selected.blockSet().size() > 1,
                () -> {
                    List<net.minecraft.resources.ResourceLocation>
                            replacementSet = new ArrayList<>(
                                    selected.blockSet()
                            );
                    replacementSet.remove(replacementSet.size() - 1);
                    replaceSelectedComponent(
                            selected.withBlockSet(replacementSet)
                    );
                }
        );

        int actionY = materialY + rowHeight + gap;
        if (selected.isVehicleLane()) {
            addFittingButton(
                    content,
                    new UiRectangle(
                            content.x(), actionY, content.width(), rowHeight
                    ),
                    directionButtonLabel(selected.direction()),
                    RoadPresetEditorButton.Style.NORMAL,
                    canMutate() && selection.isSingle(),
                    () -> replaceSelectedComponent(
                            selected.withDirection(
                                    opposite(selected.direction())
                            )
                    )
            );
            actionY += rowHeight + gap;
        }

        List<UiRectangle> actions = columns(
                content.x(), actionY, content.width(), rowHeight, 3, gap
        );
        addFittingButton(
                content,
                actions.get(0),
                Component.literal("←"),
                RoadPresetEditorButton.Style.NORMAL,
                canMutate() && selection.isSingle()
                        && selection.primary() > 0,
                () -> moveSelectedComponent(-1)
        );
        addFittingButton(
                content,
                actions.get(1),
                Component.literal("→"),
                RoadPresetEditorButton.Style.NORMAL,
                canMutate()
                        && selection.isSingle()
                        && selection.primary() + 1
                                < draft.components().size(),
                () -> moveSelectedComponent(1)
        );
        addFittingButton(
                content,
                actions.get(2),
                Component.literal("×"),
                RoadPresetEditorButton.Style.DANGER,
                canRemoveSelectedComponent(),
                this::removeSelectedComponent
        );
    }

    private void addLibraryControls() {
        UiRectangle tabs = layout.catalogTabs();
        int tabGap = Math.min(3, tabs.width());
        int firstWidth = Math.max(0, (tabs.width() - tabGap) / 2);
        addButton(
                new UiRectangle(tabs.x(), tabs.y(), firstWidth, tabs.height()),
                Component.translatable(KEY + "available_components"),
                libraryTab == LibraryTab.COMPONENTS
                        ? RoadPresetEditorButton.Style.SELECTED
                        : RoadPresetEditorButton.Style.NORMAL,
                true,
                () -> selectLibraryTab(LibraryTab.COMPONENTS)
        );
        addButton(
                new UiRectangle(
                        tabs.x() + firstWidth + tabGap,
                        tabs.y(),
                        Math.max(0, tabs.width() - firstWidth - tabGap),
                        tabs.height()
                ),
                Component.translatable(KEY + "saved_roads"),
                libraryTab == LibraryTab.SAVED_ROADS
                        ? RoadPresetEditorButton.Style.SELECTED
                        : RoadPresetEditorButton.Style.NORMAL,
                true,
                () -> selectLibraryTab(LibraryTab.SAVED_ROADS)
        );

        UiRectangle search = layout.catalogSearch();
        if (search.width() > 0 && search.height() > 0) {
            searchBox = new EditBox(
                    font,
                    search.x(),
                    search.y(),
                    search.width(),
                    search.height(),
                    Component.translatable(KEY + "search")
            );
            searchBox.setMaxLength(64);
            searchBox.setValue(searchQuery);
            searchBox.setHint(Component.translatable(KEY + "search"));
            searchBox.setResponder(this::onSearchChanged);
            addRenderableWidget(searchBox);
        }
        addCatalogCards();
    }

    private void addCatalogCards() {
        UiRectangle viewport = layout.catalogViewport();
        if (viewport.width() <= 0 || viewport.height() <= 0) {
            return;
        }
        if (libraryTab == LibraryTab.COMPONENTS) {
            List<RoadPresetEditorComponentCatalog.Entry> items =
                    filteredComponents();
            addCardGrid(
                    viewport,
                    items.size(),
                    (index, bounds) -> addComponentCard(
                            items.get(index),
                            bounds
                    )
            );
        } else {
            List<RoadPresetCatalogEntry> entries = filteredPresets();
            addCardGrid(
                    viewport,
                    entries.size(),
                    (index, bounds) -> addPresetCard(
                            entries.get(index),
                            bounds
                    )
            );
        }
    }

    private void addCardGrid(
            UiRectangle viewport,
            int itemCount,
            BiConsumer<Integer, UiRectangle> cardAdder
    ) {
        int columns = viewport.width() >= 300 ? 2 : 1;
        int gap = 3;
        int cardHeight = Math.max(1, Math.min(34, viewport.height()));
        int visibleRows = Math.max(
                1,
                (viewport.height() + gap) / (cardHeight + gap)
        );
        int totalRows = (itemCount + columns - 1) / columns;
        int maximumScroll = Math.max(0, totalRows - visibleRows);
        catalogScroll = clamp(catalogScroll, 0, maximumScroll);
        int firstIndex = catalogScroll * columns;
        int lastIndex = Math.min(
                itemCount,
                firstIndex + visibleRows * columns
        );
        for (int index = firstIndex; index < lastIndex; index++) {
            int relative = index - firstIndex;
            int column = relative % columns;
            int row = relative / columns;
            int available = viewport.width() - gap * (columns - 1);
            int baseWidth = available / columns;
            int x = viewport.x() + column * (baseWidth + gap);
            int cardWidth = column == columns - 1
                    ? viewport.right() - x
                    : baseWidth;
            cardAdder.accept(index, new UiRectangle(
                    x,
                    viewport.y() + row * (cardHeight + gap),
                    cardWidth,
                    cardHeight
            ));
        }
    }

    private void addComponentCard(
            RoadPresetEditorComponentCatalog.Entry item,
            UiRectangle bounds
    ) {
        RoadPresetEditorComponent prototype =
                RoadPresetEditorComponentCatalog.prototype(item.kind(), draft);
        RoadPresetComponentButton button = new RoadPresetComponentButton(
                bounds,
                prototype,
                item.name(),
                item.subtitle(),
                () -> addComponent(item.kind())
        );
        button.active = canMutate();
        paletteCardHits.add(new PaletteCardHit(
                bounds,
                item.kind(),
                prototype
        ));
        addCatalogWidget(button);
    }

    private void addPresetCard(
            RoadPresetCatalogEntry entry,
            UiRectangle bounds
    ) {
        boolean selected = entry.id().equals(editingId);
        RoadPresetEditorButton button = new RoadPresetEditorButton(
                bounds,
                Component.literal(entry.display().fallbackName()),
                Component.translatable(
                        KEY + "width_lanes",
                        entry.surfaceWidth(),
                        entry.laneCount()
                ),
                selected
                        ? RoadPresetEditorButton.Style.SELECTED
                        : RoadPresetEditorButton.Style.NORMAL,
                () -> selectPreset(entry.id())
        );
        button.active = !requests.savePending() && !requests.contentPending();
        addCatalogWidget(button);
    }

    private void addFooterControls() {
        UiRectangle content = inset(layout.statusBar(), 2);
        if (content.width() <= 0 || content.height() <= 0) {
            return;
        }
        boolean compact = content.width() < 620;
        int gap = 3;
        int packWidth = compact ? 48 : 92;
        int templateWidth = compact ? 46 : 104;
        int resetWidth = compact ? 40 : 52;
        int mirrorWidth = compact ? 34 : 54;
        int deleteWidth = compact ? 42 : 76;
        int saveWidth = compact ? 48 : 78;
        int controlsWidth = packWidth + templateWidth + resetWidth + mirrorWidth
                + deleteWidth + saveWidth + gap * 5;
        int x = Math.max(content.x(), content.right() - controlsWidth);

        addButton(
                new UiRectangle(x, content.y(), packWidth, content.height()),
                Component.translatable(KEY + "create_road_pack"),
                RoadPresetEditorButton.Style.NORMAL,
                !gateway.catalog().entries().isEmpty(),
                () -> minecraft.setScreen(new RoadPackScreen(this))
        );
        x += packWidth + gap;
        addButton(
                new UiRectangle(x, content.y(), templateWidth, content.height()),
                compact
                        ? Component.translatable(KEY + "duplicate")
                        : Component.translatable(KEY + "use_as_template"),
                RoadPresetEditorButton.Style.NORMAL,
                !requests.savePending(),
                this::duplicateDraft
        );
        x += templateWidth + gap;
        addButton(
                new UiRectangle(x, content.y(), resetWidth, content.height()),
                Component.translatable(KEY + "reset"),
                RoadPresetEditorButton.Style.NORMAL,
                dirty && !requests.savePending(),
                this::resetDraft
        );
        x += resetWidth + gap;
        addButton(
                new UiRectangle(x, content.y(), mirrorWidth, content.height()),
                compact
                        ? Component.literal("↔")
                        : Component.translatable(KEY + "mirror"),
                RoadPresetEditorButton.Style.NORMAL,
                canMutate(),
                () -> mutate(draft::mirror)
        );
        x += mirrorWidth + gap;
        addButton(
                new UiRectangle(x, content.y(), deleteWidth, content.height()),
                confirmations.deleteArmed()
                        ? Component.translatable(KEY + "confirm_delete")
                        : Component.translatable(KEY + "delete"),
                RoadPresetEditorButton.Style.DANGER,
                canDelete(),
                this::deleteDraft
        );
        x += deleteWidth + gap;
        saveButton = addButton(
                new UiRectangle(x, content.y(), saveWidth, content.height()),
                Component.translatable(KEY + "save"),
                RoadPresetEditorButton.Style.PRIMARY,
                false,
                this::saveDraft
        );
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

    private void selectLibraryTab(LibraryTab tab) {
        captureInputs();
        libraryTab = Objects.requireNonNull(tab, "tab");
        catalogScroll = 0;
        requestRebuild();
    }

    private void duplicateDraft() {
        captureInputs();
        if (draft == null || requests.savePending()) {
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
        if (!canMutate() || !dirty) {
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
            nameBox.tick();
        }
        if (searchBox != null) {
            searchBox.tick();
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
        catalogScroll = 0;
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
            focus.capture(nameBox, searchBox);
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
    }

    private void toggleComponentSelection(int component) {
        selection.toggle(component);
    }

    private void clearComponentSelection() {
        selection.clear();
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
        renderBackground(graphics);
        boolean componentCatalog = libraryTab == LibraryTab.COMPONENTS;
        boolean catalogEmpty = componentCatalog
                ? filteredComponents().isEmpty()
                : filteredPresets().isEmpty();
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
                        status,
                        statusIsError,
                        nameValue,
                        category,
                        catalogEmpty,
                        componentCatalog,
                        mouseX,
                        mouseY,
                        dragVisual()
                )
        );
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(
            double mouseX,
            double mouseY,
            int button
    ) {
        if (draft != null && button == 0) {
            Optional<PaletteCardHit> paletteHit = paletteCardHits.stream()
                    .filter(hit -> hit.bounds().contains(mouseX, mouseY))
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
                if (hasShiftDown()) {
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
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY
    ) {
        if (button == 0 && dragState != null) {
            dragState.update(mouseX, mouseY, insertionIndexAt(
                    mouseX,
                    mouseY
            ));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(
            double mouseX,
            double mouseY,
            int button
    ) {
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
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (dragState != null && keyCode == 256) {
            dragState = null;
            return true;
        }
        boolean textInputFocused = nameBox != null && nameBox.isFocused()
                || searchBox != null && searchBox.isFocused();
        if (keyCode == GLFW.GLFW_KEY_DELETE
                && !textInputFocused
                && !selection.isEmpty()) {
            if (canRemoveSelectedComponent()) {
                removeSelectedComponent();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private int componentIndexAt(double mouseX, double mouseY) {
        return crossSectionGeometry(
                RoadPresetEditorRenderer.roadBounds(layout.preview())
        ).componentIndexAt(mouseX, mouseY).orElse(-1);
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
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double delta
    ) {
        if (layout != null
                && layout.catalogViewport().contains(mouseX, mouseY)
                && delta != 0.0) {
            catalogScroll -= (int) Math.signum(delta);
            catalogScroll = Math.max(0, catalogScroll);
            requestCatalogRefresh();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
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

    private RoadPresetEditorButton addButton(
            UiRectangle bounds,
            Component message,
            RoadPresetEditorButton.Style style,
            boolean active,
            Runnable action
    ) {
        RoadPresetEditorButton button = new RoadPresetEditorButton(
                bounds,
                message,
                style,
                action
        );
        button.active = active && bounds.width() > 0 && bounds.height() > 0;
        addRenderableWidget(button);
        return button;
    }

    private void addCatalogWidget(AbstractWidget widget) {
        catalogWidgets.add(widget);
        addRenderableWidget(widget);
    }

    private void requestCatalogRefresh() {
        catalogRefreshPending = true;
    }

    private void refreshCatalogCards() {
        catalogWidgets.forEach(this::removeWidget);
        catalogWidgets.clear();
        paletteCardHits.clear();
        addCatalogCards();
        catalogRefreshPending = false;
    }

    private RoadPresetEditorButton addFittingButton(
            UiRectangle container,
            UiRectangle bounds,
            Component message,
            RoadPresetEditorButton.Style style,
            boolean active,
            Runnable action
    ) {
        if (!containedBy(container, bounds)) {
            return null;
        }
        return addButton(bounds, message, style, active, action);
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
        addRenderableWidget(label);
        return label;
    }

    private static Component message(RuntimeException exception) {
        Objects.requireNonNull(exception, "exception");
        return Component.translatable(KEY + "validation_error");
    }

    private enum LibraryTab {
        COMPONENTS,
        SAVED_ROADS
    }

    private record PaletteCardHit(
            UiRectangle bounds,
            RoadPresetEditorComponentCatalog.Kind kind,
            RoadPresetEditorComponent prototype
    ) {
    }

}
