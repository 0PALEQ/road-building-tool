package com.cookiecraftmods.roadbuilder.client.ui;

import com.cookiecraftmods.roadbuilder.RoadBuilder;
import com.cookiecraftmods.roadbuilder.client.config.RoadBuilderClientPreferences;
import com.cookiecraftmods.roadbuilder.client.config.RoadBuilderClientPreferencesLoader;
import com.cookiecraftmods.roadbuilder.client.tool.RoadSnapPriority;
import com.cookiecraftmods.roadbuilder.client.tool.RoadSnapSettings;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static com.cookiecraftmods.roadbuilder.client.ui.UIStyle.*;

public final class RoadBuilderSettingsScreen extends RoadBuilderScreen {
    private static final String KEY = "screen.roadbuilder.settings.";
    private static final int PADDING = 8;
    private static final int GAP = 4;

    private final RoadBuilderClientPreferences initial;
    private final Consumer<RoadBuilderClientPreferences> applyPreferences;
    private final EnumMap<Distance, String> distances = new EnumMap<>(Distance.class);
    private final EnumMap<Distance, EditBox> distanceBoxes = new EnumMap<>(Distance.class);
    private final List<Row> rows = new ArrayList<>();
    private final RoadPackImportPanel packImport = new RoadPackImportPanel();
    private Section section = Section.CAMERA;
    private Section personalSection = Section.CAMERA;
    private boolean invertMovement;
    private boolean invertHorizontalDrag;
    private boolean invertVerticalDrag;
    private boolean gridEnabled;
    private boolean tangentEnabled;
    private RoadSnapPriority priority;
    private UiRectangle panel;
    private UiRectangle body;
    private RoadTextButton saveButton;
    private RoadTextButton defaultsButton;
    private RoadTextButton sectionButton;
    private boolean rebuildPending;
    private boolean invalidDraft;
    private boolean saveFailed;

    public RoadBuilderSettingsScreen(
            RoadBuilderClientPreferences preferences,
            Consumer<RoadBuilderClientPreferences> applyPreferences
    ) {
        super(text("title"));
        this.initial = preferences;
        this.applyPreferences = applyPreferences;
        populateDraft(preferences);
    }

    @Override
    protected void init() {
        rebuildControls();
    }

    private void rebuildControls() {
        rebuildPending = false;
        setFocused(null);
        clearWidgets();
        rows.clear();
        distanceBoxes.clear();

        int panelWidth = Math.min(380, Math.max(1, width - 16));
        int panelHeight = Math.min(238, Math.max(1, height - 16));
        panel = new UiRectangle((width - panelWidth) / 2, (height - panelHeight) / 2,
                panelWidth, panelHeight);
        int left = panel.x() + PADDING;
        int contentWidth = Math.max(1, panel.width() - PADDING * 2);
        int tabCount = Section.values().length;
        int tabWidth = Math.max(1, (contentWidth - GAP * (tabCount - 1)) / tabCount);
        int tabY = panel.y() + 29;
        for (Section target : Section.values()) {
            RoadTextButton button = addButton(
                    new UiRectangle(left + target.ordinal() * (tabWidth + GAP), tabY,
                            target.ordinal() == tabCount - 1
                                    ? Math.max(1, contentWidth - (tabCount - 1) * (tabWidth + GAP))
                                    : tabWidth, 20),
                    text(target.name().toLowerCase(Locale.ROOT)),
                    section == target ? RoadTextButton.Style.SELECTED : RoadTextButton.Style.NORMAL,
                    () -> selectSection(target));
            if (section == target) {
                sectionButton = button;
            }
        }

        int bodyMargin = section == Section.SERVER ? 100 : 94;
        body = new UiRectangle(left, tabY + 24, contentWidth, Math.max(1, panel.height() - bodyMargin));
        if (section == Section.SERVER) {
            packImport.addControls(body, this::addRenderableWidget);
            int backWidth = Math.min(100, contentWidth);
            addButton(new UiRectangle(body.right() - backWidth, panel.bottom() - 26, backWidth, 20),
                    CommonComponents.GUI_BACK, RoadTextButton.Style.NORMAL, () -> selectSection(personalSection));
            return;
        }

        if (section == Section.CAMERA) {
            addToggle("invert_movement", () -> invertMovement, value -> invertMovement = value);
            addToggle("invert_horizontal_drag", () -> invertHorizontalDrag, value -> invertHorizontalDrag = value);
            addToggle("invert_vertical_drag", () -> invertVerticalDrag, value -> invertVerticalDrag = value);
        } else {
            for (Distance distance : Distance.values()) {
                addDistance(distance);
            }
            addToggle("grid_enabled", () -> gridEnabled, value -> gridEnabled = value);
            addToggle("tangent_enabled", () -> tangentEnabled, value -> tangentEnabled = value);
            RoadTextButton cycle = addRenderableWidget(new RoadTextButton(
                    row("priority"), priorityLabel(), RoadTextButton.Style.NORMAL, this::cyclePriority));
            cycle.setTooltip(Tooltip.create(text("priority_hint")));
        }

        int buttonWidth = Math.min(100, Math.max(1, (contentWidth - GAP * 2) / 3));
        int buttonY = panel.bottom() - 26;
        int right = left + contentWidth;
        defaultsButton = addButton(new UiRectangle(left, buttonY, buttonWidth, 20),
                text("defaults"), RoadTextButton.Style.NORMAL, this::restoreDefaults);
        addButton(new UiRectangle(right - 2 * buttonWidth - GAP, buttonY, buttonWidth, 20),
                CommonComponents.GUI_CANCEL, RoadTextButton.Style.NORMAL, this::onClose);
        saveButton = addButton(new UiRectangle(right - buttonWidth, buttonY, buttonWidth, 20),
                text("save"), RoadTextButton.Style.PRIMARY, this::save);
        refreshButtons();
    }

    private void selectSection(Section target) {
        if (section != target) {
            section = target;
            if (target == Section.SERVER) {
                packImport.open(minecraft.getConnection());
            } else {
                personalSection = target;
            }
            rebuildPending = true;
        }
    }

    private UiRectangle row(String key) {
        int rowCount = section == Section.CAMERA ? 3 : 6;
        int rowStep = Math.min(32, Math.max(1, (body.height() - PADDING) / rowCount));
        int rowHeight = Math.max(1, Math.min(20, rowStep - 2));
        int rowY = body.y() + (body.height() - rowCount * rowStep) / 2
                + rows.size() * rowStep + (rowStep - rowHeight) / 2;
        int right = body.right() - PADDING;
        int controlWidth = Math.max(1, Math.min(146, (body.width() - PADDING * 2) * 2 / 5));
        UiRectangle control = new UiRectangle(right - controlWidth, rowY, controlWidth, rowHeight);
        int labelWidth = Math.max(1, control.x() - body.x() - PADDING - GAP);
        var label = font.split(text(key), labelWidth);
        int lineCount = Math.min(label.size(), Math.max(1, rowHeight / font.lineHeight));
        rows.add(new Row(label.subList(0, lineCount), control));
        return control;
    }

    private void addToggle(String key, BooleanSupplier enabled, Consumer<Boolean> update) {
        RoadTextButton toggle = addRenderableWidget(new RoadTextButton(
                row(key), toggleLabel(enabled.getAsBoolean()), RoadTextButton.Style.NORMAL, button -> {
                    boolean next = !enabled.getAsBoolean();
                    update.accept(next);
                    button.setMessage(toggleLabel(next));
                    draftChanged();
                }));
        toggle.setTooltip(Tooltip.create(text(key)));
    }

    private static Component toggleLabel(boolean enabled) {
        return enabled ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF;
    }

    private void addDistance(Distance distance) {
        UiRectangle bounds = row(distance.key);
        EditBox box = new EditBox(font, bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                text(distance.key));
        box.setMaxLength(32);
        box.setValue(distances.get(distance));
        box.setTextColor(TEXT_PRIMARY);
        box.setTooltip(Tooltip.create(text("distance_hint",
                formatDistance(distance.minimum), formatDistance(distance.maximum))));
        box.setResponder(value -> {
            distances.put(distance, value);
            draftChanged();
        });
        distanceBoxes.put(distance, addRenderableWidget(box));
    }

    private RoadTextButton addButton(UiRectangle bounds, Component label,
                                     RoadTextButton.Style style, Runnable action) {
        return addRenderableWidget(new RoadTextButton(bounds, label, style, action));
    }

    private void cyclePriority(RoadTextButton button) {
        RoadSnapPriority[] values = RoadSnapPriority.values();
        priority = values[(priority.ordinal() + 1) % values.length];
        button.setMessage(priorityLabel());
        draftChanged();
    }

    private Component priorityLabel() {
        return text("priority." + priority.name().toLowerCase(Locale.ROOT));
    }

    private void populateDraft(RoadBuilderClientPreferences preferences) {
        invertMovement = preferences.invertHorizontalMovement();
        invertHorizontalDrag = preferences.invertHorizontalMouseDrag();
        invertVerticalDrag = preferences.invertVerticalMouseDrag();
        gridEnabled = preferences.snapGridEnabled();
        tangentEnabled = preferences.snapTangentEnabled();
        priority = preferences.snapPriority();
        distances.put(Distance.SNAP_RANGE, formatDistance(preferences.snapRange()));
        distances.put(Distance.GRID_SPACING, formatDistance(preferences.snapGridSpacing()));
        distances.put(Distance.GUIDELINE_RANGE, formatDistance(preferences.guidelineRange()));
    }

    private static String formatDistance(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private double readDistance(Distance distance) {
        try {
            double value = Double.parseDouble(distances.get(distance).strip().replace(',', '.'));
            if (value >= distance.minimum && value <= distance.maximum) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // Incomplete input stays in the draft while the user edits it.
        }
        return Double.NaN;
    }

    private RoadBuilderClientPreferences draftPreferences() {
        double snapRange = readDistance(Distance.SNAP_RANGE);
        double gridSpacing = readDistance(Distance.GRID_SPACING);
        double guidelineRange = readDistance(Distance.GUIDELINE_RANGE);
        if (Double.isNaN(snapRange) || Double.isNaN(gridSpacing) || Double.isNaN(guidelineRange)) {
            return null;
        }
        return new RoadBuilderClientPreferences(
                RoadBuilderClientPreferences.CURRENT_SCHEMA_VERSION,
                invertMovement, invertHorizontalDrag, invertVerticalDrag,
                snapRange, gridSpacing, priority, gridEnabled, tangentEnabled, guidelineRange);
    }

    private void draftChanged() {
        saveFailed = false;
        refreshButtons();
    }

    private void refreshButtons() {
        RoadBuilderClientPreferences draft = draftPreferences();
        invalidDraft = draft == null;
        saveButton.active = draft != null && !initial.equals(draft);
        defaultsButton.active = !RoadBuilderClientPreferences.DEFAULTS.equals(draft);
    }

    private void restoreDefaults() {
        populateDraft(RoadBuilderClientPreferences.DEFAULTS);
        saveFailed = false;
        rebuildPending = true;
    }

    private void save() {
        RoadBuilderClientPreferences draft = draftPreferences();
        if (draft == null || initial.equals(draft)) {
            return;
        }
        try {
            RoadBuilderClientPreferencesLoader.save(draft);
        } catch (IOException exception) {
            RoadBuilder.LOGGER.warn("Could not save Road Builder client preferences", exception);
            saveFailed = true;
            return;
        }
        applyPreferences.accept(draft);
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        if (rebuildPending) {
            rebuildControls();
            setFocused(sectionButton);
        }
        packImport.tick(minecraft.getConnection());
        distanceBoxes.values().forEach(RoadBuilderSettingsScreen::tickEditBox);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderRoadBackground(graphics);
        graphics.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), BACKGROUND_FRAME);
        graphics.renderOutline(panel.x(), panel.y(), panel.width(), panel.height(), BORDER_FRAME);
        RoadGuiDrawing.icon(graphics, RoadBuilderToolbarRenderer.SETTINGS_ICON,
                panel.x() + PADDING, panel.y() + 6, 18, 32, TEXT_PRIMARY);
        graphics.drawString(font, title, panel.x() + PADDING + 24, panel.y() + 11, TEXT_PRIMARY, false);
        graphics.fill(body.x(), body.y(), body.right(), body.bottom(), BACKGROUND_PANEL);
        graphics.renderOutline(body.x(), body.y(), body.width(), body.height(), BORDER_PANEL);
        if (section == Section.SERVER && !rebuildPending) {
            packImport.render(graphics, font);
            packImport.renderStatus(graphics, font, panel.bottom() - 45, 2);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }
        int labelX = body.x() + PADDING;
        for (Row row : rows) {
            UiRectangle control = row.control();
            int labelY = control.y() + (control.height() - row.label().size() * font.lineHeight) / 2;
            for (FormattedCharSequence line : row.label()) {
                graphics.drawString(font, line, labelX, labelY, TEXT_PRIMARY, false);
                labelY += font.lineHeight;
            }
        }
        Component error = saveFailed ? text("save_failed")
                : invalidDraft ? text("invalid_distances") : Component.empty();
        if (!error.getString().isEmpty()) {
            graphics.drawString(font, font.plainSubstrByWidth(error.getString(), body.width()),
                    body.x(), panel.bottom() - 37, TEXT_ERROR, false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        distanceBoxes.forEach((distance, box) -> {
            if (Double.isNaN(readDistance(distance))) {
                graphics.renderOutline(box.getX(), box.getY(), box.getWidth(), box.getHeight(), TEXT_ERROR);
            }
        });
    }

    private static Component text(String suffix, Object... arguments) {
        return Component.translatable(KEY + suffix, arguments);
    }

    private enum Section {
        CAMERA,
        SNAPPING,
        SERVER
    }

    private enum Distance {
        SNAP_RANGE("snap_range", RoadSnapSettings.MIN_RANGE, RoadSnapSettings.MAX_RANGE),
        GRID_SPACING("grid_spacing", RoadSnapSettings.MIN_GRID_SPACING, RoadSnapSettings.MAX_GRID_SPACING),
        GUIDELINE_RANGE("guideline_range", RoadSnapSettings.MIN_GUIDELINE_RANGE, RoadSnapSettings.MAX_GUIDELINE_RANGE);

        private final String key;
        private final double minimum;
        private final double maximum;

        Distance(String key, double minimum, double maximum) {
            this.key = key;
            this.minimum = minimum;
            this.maximum = maximum;
        }
    }

    private record Row(List<FormattedCharSequence> label, UiRectangle control) {
    }
}
