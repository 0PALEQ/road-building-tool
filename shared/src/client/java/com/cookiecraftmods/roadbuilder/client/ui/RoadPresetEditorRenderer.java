package com.cookiecraftmods.roadbuilder.client.ui;

import com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection;
import com.cookiecraftmods.roadbuilder.preset.RoadMaterialNoise;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;

import static com.cookiecraftmods.roadbuilder.client.ui.UIStyle.*;

final class RoadPresetEditorRenderer {
    private static final String KEY = "screen.roadbuilder.preset_editor.";
    private final GuiGraphics graphics;
    private final Font font;
    private final View view;
    private final RoadPresetEditorLayout layout;
    private final RoadPresetEditorDraft draft;

    private RoadPresetEditorRenderer(
            GuiGraphics graphics,
            Font font,
            View view
    ) {
        this.graphics = Objects.requireNonNull(graphics, "graphics");
        this.font = Objects.requireNonNull(font, "font");
        this.view = Objects.requireNonNull(view, "view");
        layout = view.layout();
        draft = view.draft();
    }

    static void render(GuiGraphics graphics, Font font, View view) {
        new RoadPresetEditorRenderer(graphics, font, view).render();
    }

    private void render() {
        graphics.fill(
                0, 0, view.screenWidth(), view.screenHeight(), BACKGROUND_SCRIM
        );
        renderShell();
        if (draft == null) {
            renderLoading();
            return;
        }
        renderProperties();
        renderWorkspace();
        renderCatalogEmptyState();
        renderStatus();
        renderDragGhost();
    }

    private void renderShell() {
        UiRectangle frame = layout.frame();
        fill(frame, BACKGROUND_FRAME);
        graphics.renderOutline(frame.x(), frame.y(), frame.width(), frame.height(), BORDER_FRAME);
        UiRectangle header = layout.header();
        Component name = view.nameValue().isBlank()
                ? Component.translatable(KEY + "title") : Component.literal(view.nameValue());
        if (view.dirty()) {
            name = name.copy().append(" *");
        }
        drawTrimmed(name, header.x() + 4, header.y() + 3,
                Math.max(1, header.width() - 38), TEXT_PRIMARY);
        drawTrimmed(Component.translatable(KEY + (view.editable() ? "header_editable" : "header_template")),
                header.x() + 4, header.y() + 16, Math.max(1, header.width() - 38), TEXT_SECONDARY);
        renderPanel(layout.propertiesPanel());
        renderPanel(layout.workspacePanel());
        fill(layout.statusBar(), BACKGROUND_STATUS);
    }
    private void renderPanel(UiRectangle panel) {
        graphics.fill(
                panel.x(), panel.y(), panel.right(), panel.bottom(),
                BACKGROUND_PANEL
        );
        graphics.renderOutline(
                panel.x(), panel.y(), panel.width(), panel.height(), BORDER_PANEL
        );
    }

    private void renderLoading() {
        UiRectangle content = union(
                layout.propertiesPanel(), layout.workspacePanel()
        );
        drawCenteredTrimmed(
                view.status(),
                content.x() + content.width() / 2,
                content.y() + Math.max(2, content.height() / 2 - 4),
                Math.max(1, content.width() - 18),
                view.statusIsError() ? HIGHLIGHT_STRONG : TEXT_PRIMARY
        );
    }

    private void renderProperties() {
        if (view.tab() != RoadPresetEditorTab.LAYOUT
                && view.tab() != RoadPresetEditorTab.SAVED_ROADS
                && !(view.tab() == RoadPresetEditorTab.MATERIALS && view.materialSetOpen())) {
            UiRectangle heading = layout.panelHeading();
            drawTrimmed(Component.translatable(view.tab().key()), heading.x(), heading.y() + 6,
                    heading.width(), HIGHLIGHT_STRONG);
        }
        UiRectangle viewport = view.activeViewport();
        if (viewport != null && view.scrollMaximum() > 0 && viewport.height() > 38) {
            int x = viewport.right() + 9;
            int top = viewport.y() + 19;
            int trackHeight = viewport.height() - 38;
            graphics.fill(x, top, x + 3, top + trackHeight, CONTROL_BACKGROUND);
            int thumbHeight = Math.max(6, trackHeight * viewport.height()
                    / (view.scrollMaximum() + viewport.height()));
            thumbHeight = Math.min(trackHeight, thumbHeight);
            int thumbTop = top + (trackHeight - thumbHeight) * view.scrollOffset() / view.scrollMaximum();
            graphics.fill(x, thumbTop, x + 3, thumbTop + thumbHeight, HIGHLIGHT_STRONG);
        }
    }

    private void renderWorkspace() {
        UiRectangle preview = layout.preview();
        fill(preview, BACKGROUND_WORKSPACE);
        for (int x = preview.x() + 12; x < preview.right(); x += 16) {
            graphics.fill(x, preview.y() + 32, x + 1, preview.bottom(), BACKGROUND_PANEL_RAISED);
        }
        for (int y = preview.y() + 32; y < preview.bottom(); y += 16) {
            graphics.fill(preview.x(), y, preview.right(), y + 1, BACKGROUND_PANEL_RAISED);
        }
        drawTrimmed(Component.translatable(KEY + "preview"), preview.x() + 3, preview.y() + 3,
                Math.max(1, preview.width() - 30), HIGHLIGHT_STRONG);
        drawTrimmed(Component.translatable(KEY + "width_lanes", draft.surfaceBlockWidth(), draft.laneCount()),
                preview.x() + 3, preview.y() + 17, Math.max(1, preview.width() - 6), TEXT_SECONDARY);
        UiRectangle road = roadBounds(preview);
        if (road.width() > 0 && road.height() > 0) {
            renderComponentRow(road);
            graphics.renderOutline(road.x(), road.y(), road.width(), road.height(), BORDER_PANEL);
            RoadPresetEditorCrossSectionGeometry geometry = geometry(road);
            for (int index = 0; index < draft.components().size(); index++) {
                UiRectangle part = geometry.componentRectangle(index);
                String width = Integer.toString(draft.components().get(index).width());
                if (part.width() >= font.width(width) + 4 && preview.height() >= 75) {
                    drawCenteredTrimmed(Component.literal(width), part.x() + part.width() / 2,
                            road.bottom() + 5, part.width(), TEXT_SECONDARY);
                }
            }
        }
        if (preview.height() >= 130) {
            drawCenteredTrimmed(Component.translatable(KEY + "preview_hint"),
                    preview.x() + preview.width() / 2, preview.bottom() - 11,
                    Math.max(1, preview.width() - 6), TEXT_SECONDARY);
        }
        if (view.dragVisual() != null && view.dragVisual().active() && view.dragVisual().dropIndex() >= 0) {
            renderDropCaret(road, view.dragVisual().dropIndex());
        }
    }

    static UiRectangle roadBounds(UiRectangle preview) {
        int horizontalInset = Math.min(12, preview.width() / 10);
        int topInset = Math.min(36, preview.height() / 3);
        int bottomInset = Math.min(preview.height() >= 130 ? 32 : 18,
                Math.max(0, (preview.height() - topInset) / 3));
        return new UiRectangle(preview.x() + horizontalInset, preview.y() + topInset,
                Math.max(0, preview.width() - horizontalInset * 2),
                Math.max(0, preview.height() - topInset - bottomInset));
    }
    private void renderComponentRow(UiRectangle bounds) {
        RoadPresetEditorCrossSectionGeometry geometry = geometry(bounds);
        for (int index = 0; index < draft.components().size(); index++) {
            UiRectangle componentBounds = geometry.componentRectangle(index);
            RoadPresetEditorComponent component = draft.components().get(index);
            renderComponent(componentBounds, component, true);

            boolean hovered = componentBounds.contains(
                    view.mouseX(), view.mouseY()
            );
            if (hovered || view.selectedComponents().contains(index)) {
                graphics.renderOutline(
                        componentBounds.x(),
                        componentBounds.y(),
                        componentBounds.width(),
                        componentBounds.height(),
                        hovered ? BORDER_HOVERED : HIGHLIGHT_STRONG
                );
                if (view.selectedComponents().contains(index)) {
                    graphics.fill(componentBounds.x(), componentBounds.y(), componentBounds.right(),
                            Math.min(componentBounds.bottom(), componentBounds.y() + 3), HIGHLIGHT_STRONG);
                }
            }
        }
    }

    private void renderComponent(
            UiRectangle bounds,
            RoadPresetEditorComponent component,
            boolean repeatLaneArrows
    ) {
        if (bounds.width() <= 0 || bounds.height() <= 0) {
            return;
        }
        switch (component.kind()) {
            case VEHICLE_LANE, BUS_LANE -> {
                int material = materialColor(component.block());
                fillMaterial(bounds, component);
                renderLaneArrows(
                        bounds,
                        component.direction(),
                        repeatLaneArrows,
                        contrastingColor(material)
                );
            }
            case PAVEMENT -> {
                int material = materialColor(component.block());
                fillMaterial(bounds, component);
                renderPavementTexture(bounds, material);
            }
            case SIDEWALK -> renderSidewalk(bounds, component);
            case RAILING -> {
                fillMaterial(bounds, component);
                int x = bounds.x() + bounds.width() / 2;
                graphics.fill(x - 1, bounds.y(), x + 1, bounds.bottom(), 0xFFD0D0D0);
                for (int y = bounds.y() + 4; y < bounds.bottom(); y += 12) {
                    graphics.fill(x - 3, y, x + 3, Math.min(y + 3, bounds.bottom()), 0xFF777777);
                }
            }
            case GRASS_MEDIAN -> renderMedian(
                    bounds,
                    component,
                    true
            );
            case CONCRETE_MEDIAN -> renderMedian(
                    bounds,
                    component,
                    false
            );
            case SOLID_MARKING -> fillMaterial(bounds, component);
            case DASHED_MARKING -> renderDashed(bounds, component);
            case DOUBLE_CONTINUOUS_MARKING ->
                    renderDouble(bounds, component);
        }
        graphics.fill(
                bounds.right() - 1,
                bounds.y(),
                bounds.right(),
                bounds.bottom(),
                OVERLAY_COMPONENT_DIVIDER
        );
    }

    private void renderLaneArrows(
            UiRectangle bounds,
            LaneTravelDirection direction,
            boolean repeated,
            int color
    ) {
        if (bounds.width() < 5 || bounds.height() < 7) {
            return;
        }
        int arrowCount = repeated
                ? Math.max(
                        1,
                        Math.min(
                                4,
                                (bounds.height() + PREVIEW_ARROW_SPACING / 2)
                                        / PREVIEW_ARROW_SPACING
                        )
                )
                : 1;
        for (int index = 0; index < arrowCount; index++) {
            int centerY = bounds.y()
                    + (index * 2 + 1) * bounds.height()
                    / (arrowCount * 2);
            renderArrow(bounds, centerY, direction, color);
        }
    }

    private void renderArrow(
            UiRectangle bounds,
            int centerY,
            LaneTravelDirection direction,
            int color
    ) {
        int centerX = bounds.x() + bounds.width() / 2;
        int headHalfWidth = Math.min(4, (bounds.width() - 3) / 2);
        int headRows = headHalfWidth + 1;
        int glyphHeight = Math.min(18, bounds.height() - 2);
        if (glyphHeight < headRows + 2) {
            return;
        }
        int top = centerY - glyphHeight / 2;
        int bottom = top + glyphHeight;

        renderArrowShape(
                bounds,
                centerX + 1,
                top + 1,
                bottom + 1,
                headHalfWidth,
                direction,
                color == TEXT_PRIMARY ? SHADOW_DARK : SHADOW_LIGHT
        );
        renderArrowShape(
                bounds,
                centerX,
                top,
                bottom,
                headHalfWidth,
                direction,
                color
        );
    }

    private void renderArrowShape(
            UiRectangle clip,
            int centerX,
            int top,
            int bottom,
            int headHalfWidth,
            LaneTravelDirection direction,
            int color
    ) {
        int headRows = headHalfWidth + 1;
        int shaftHalfWidth = headHalfWidth >= 2 ? 1 : 0;
        if (direction == LaneTravelDirection.FORWARD) {
            fillClipped(
                    clip,
                    centerX - shaftHalfWidth,
                    top + headRows - 1,
                    centerX + shaftHalfWidth + 1,
                    bottom,
                    color
            );
            for (int row = 0; row < headRows; row++) {
                fillClipped(
                        clip,
                        centerX - row,
                        top + row,
                        centerX + row + 1,
                        top + row + 1,
                        color
                );
            }
            return;
        }

        int headBase = bottom - headRows;
        fillClipped(
                clip,
                centerX - shaftHalfWidth,
                top,
                centerX + shaftHalfWidth + 1,
                headBase + 1,
                color
        );
        for (int row = 0; row < headRows; row++) {
            int y = bottom - row - 1;
            fillClipped(
                    clip,
                    centerX - row,
                    y,
                    centerX + row + 1,
                    y + 1,
                    color
            );
        }
    }

    private void fillClipped(
            UiRectangle clip,
            int left,
            int top,
            int right,
            int bottom,
            int color
    ) {
        int clippedLeft = Math.max(clip.x(), left);
        int clippedTop = Math.max(clip.y(), top);
        int clippedRight = Math.min(clip.right(), right);
        int clippedBottom = Math.min(clip.bottom(), bottom);
        if (clippedLeft < clippedRight && clippedTop < clippedBottom) {
            graphics.fill(
                    clippedLeft,
                    clippedTop,
                    clippedRight,
                    clippedBottom,
                    color
            );
        }
    }

    private void renderPavementTexture(UiRectangle bounds, int material) {
        int texture = withAlpha(darken(material, 24), 0x66);
        for (int y = bounds.y() + 5; y < bounds.bottom(); y += 7) {
            int x = bounds.x() + 2 + Math.floorMod(y, 5);
            if (x < bounds.right()) {
                graphics.fill(x, y, x + 1, y + 1, texture);
            }
        }
    }

    private void renderSidewalk(
            UiRectangle bounds,
            RoadPresetEditorComponent component
    ) {
        int material = materialColor(component.block());
        fillMaterial(bounds, component);
        graphics.fill(
                bounds.x(), bounds.y(), bounds.right(), bounds.y() + 1,
                lighten(material, 32)
        );
        int row = Math.max(5, Math.min(9, bounds.height() / 5));
        for (int y = bounds.y() + row; y < bounds.bottom(); y += row) {
            graphics.fill(
                    bounds.x(), y, bounds.right(), y + 1,
                    darken(material, 32)
            );
        }
    }

    private void renderMedian(
            UiRectangle bounds,
            RoadPresetEditorComponent component,
            boolean grass
    ) {
        int material = materialColor(component.block());
        fillMaterial(bounds, component);
        int edge = grass ? lighten(material, 24) : darken(material, 28);
        graphics.fill(bounds.x(), bounds.y(), bounds.x() + 1,
                bounds.bottom(), edge);
        graphics.fill(bounds.right() - 1, bounds.y(), bounds.right(),
                bounds.bottom(), edge);
    }

    private void renderDashed(
            UiRectangle bounds,
            RoadPresetEditorComponent component
    ) {
        fillMaterial(bounds, component);
        int dash = Math.max(3, bounds.width() * 3 / component.width());
        for (int y = bounds.y() + dash; y < bounds.bottom(); y += dash * 2) {
            fillMaterial(new UiRectangle(bounds.x(), y, bounds.width(),
                    Math.min(dash, bounds.bottom() - y)), draft.underlayBlockSet(component), dash);
        }
    }

    private void renderDouble(
            UiRectangle bounds,
            RoadPresetEditorComponent component
    ) {
        fillMaterial(bounds, component);
        int third = Math.max(1, bounds.width() / 3);
        if (bounds.width() > third * 2) {
            fillMaterial(new UiRectangle(bounds.x() + third, bounds.y(),
                    bounds.width() - third * 2, bounds.height()),
                    draft.underlayBlockSet(component), Math.max(4, third));
        }
    }

    private void fillMaterial(UiRectangle bounds, RoadPresetEditorComponent component) {
        fillMaterial(bounds, component.blockSet(), Math.max(4, bounds.width() / component.width()));
    }

    private void fillMaterial(UiRectangle bounds, java.util.List<ResourceLocation> blocks, int tile) {
        if (blocks.size() == 1) {
            fill(bounds, materialColor(blocks.get(0)));
            return;
        }
        int[] colors = blocks.stream().mapToInt(RoadPresetEditorRenderer::materialColor).toArray();
        for (int y = bounds.y(); y < bounds.bottom(); y += tile) {
            for (int x = bounds.x(); x < bounds.right(); x += tile) {
                int choice = RoadMaterialNoise.sampleIndex(x / tile, 0, y / tile, colors.length);
                graphics.fill(x, y, Math.min(bounds.right(), x + tile), Math.min(bounds.bottom(), y + tile),
                        colors[choice]);
            }
        }
    }

    private void renderDropCaret(UiRectangle bounds, int insertionIndex) {
        if (bounds.width() <= 0 || bounds.height() <= 0) {
            return;
        }
        RoadPresetEditorCrossSectionGeometry geometry = geometry(bounds);
        int safeIndex = Math.max(
                0,
                Math.min(insertionIndex, geometry.componentCount())
        );
        int x = geometry.caretX(safeIndex);
        graphics.fill(
                x - 1, bounds.y(), x + 2, bounds.bottom(), HIGHLIGHT
        );
        graphics.fill(
                x - 3, bounds.y(), x + 4, Math.min(bounds.bottom(), bounds.y() + 3),
                HIGHLIGHT
        );
    }

    private RoadPresetEditorCrossSectionGeometry geometry(UiRectangle bounds) {
        return RoadPresetEditorCrossSectionGeometry.create(
                bounds,
                draft.components().stream()
                        .map(RoadPresetEditorComponent::width)
                        .toList()
        );
    }

    private void renderDragGhost() {
        DragVisual drag = view.dragVisual();
        if (drag == null || !drag.active()) {
            return;
        }
        int width = 56;
        int height = 28;
        UiRectangle ghost = new UiRectangle(
                clamp((int) Math.round(drag.mouseX()) - width / 2,
                        1, Math.max(1, view.screenWidth() - width - 1)),
                clamp((int) Math.round(drag.mouseY()) - height - 8,
                        1, Math.max(1, view.screenHeight() - height - 1)),
                width,
                height
        );
        graphics.fill(
                ghost.x() - 2, ghost.y() - 2,
                ghost.right() + 2, ghost.bottom() + 2,
                OVERLAY_DRAG_GHOST
        );
        renderComponent(ghost, drag.component(), false);
        graphics.renderOutline(
                ghost.x() - 1,
                ghost.y() - 1,
                ghost.width() + 2,
                ghost.height() + 2,
                HIGHLIGHT
        );
    }

    private void renderCatalogEmptyState() {
        if (!view.catalogEmpty()) {
            return;
        }
        UiRectangle viewport = layout.catalogViewport();
        drawCenteredTrimmed(
                Component.translatable(
                        view.componentCatalog()
                                ? KEY + "no_components"
                                : KEY + "no_results"
                ),
                viewport.x() + viewport.width() / 2,
                viewport.y() + Math.max(4, viewport.height() / 2 - 4),
                Math.max(1, viewport.width() - 8),
                TEXT_SECONDARY
        );
    }

    private void renderStatus() {
        UiRectangle content = inset(layout.statusBar(), 4);
        int available = Math.max(1, content.width() - 4);
        Optional<String> validation = draft.validationError(
                view.expectedRevision() + 1,
                view.nameValue(),
                view.category()
        );
        boolean explicitError = view.statusIsError()
                && !view.status().getString().isEmpty();
        Component message = explicitError
                ? view.status()
                : validation.<Component>map(error -> Component.translatable(KEY
                        + (view.nameValue().isBlank() ? "name_required" : "validation_error")))
                        .orElse(view.status());
        int color = validation.isPresent() || explicitError
                ? 0xFFFFAAAA : TEXT_SECONDARY;
        if (view.dirty()
                && !explicitError
                && validation.isEmpty()
                && view.status().getString().isEmpty()) {
            message = Component.literal("• ").append(
                    Component.translatable(KEY + "unsaved_title")
            );
            color = HIGHLIGHT;
        }
        drawTrimmed(
                message,
                content.x(),
                content.y(),
                available,
                color
        );
    }

    private static int materialColor(ResourceLocation block) {
        return RoadPresetBlockAppearance.opaquePreviewColor(block);
    }

    private static int contrastingColor(int color) {
        int red = color >> 16 & 0xFF;
        int green = color >> 8 & 0xFF;
        int blue = color & 0xFF;
        int luminance = (red * 299 + green * 587 + blue * 114) / 1000;
        return luminance >= 150 ? TEXT_INVERSE : TEXT_PRIMARY;
    }

    private void fill(UiRectangle bounds, int color) {
        graphics.fill(
                bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), color
        );
    }

    private void drawTrimmed(
            Component value,
            int x,
            int y,
            int maximumWidth,
            int color
    ) {
        if (maximumWidth <= 0) {
            return;
        }
        graphics.drawString(
                font,
                font.plainSubstrByWidth(value.getString(), maximumWidth),
                x,
                y,
                color,
                false
        );
    }

    private void drawCenteredTrimmed(
            Component value,
            int centerX,
            int y,
            int maximumWidth,
            int color
    ) {
        String trimmed = font.plainSubstrByWidth(
                value.getString(), maximumWidth
        );
        graphics.drawCenteredString(font, trimmed, centerX, y, color);
    }

    private static UiRectangle inset(UiRectangle rectangle, int amount) {
        int horizontal = Math.min(amount, rectangle.width() / 2);
        int vertical = Math.min(amount, rectangle.height() / 2);
        return new UiRectangle(
                rectangle.x() + horizontal,
                rectangle.y() + vertical,
                rectangle.width() - horizontal * 2,
                rectangle.height() - vertical * 2
        );
    }

    private static UiRectangle union(
            UiRectangle first,
            UiRectangle second
    ) {
        int x = Math.min(first.x(), second.x());
        int y = Math.min(first.y(), second.y());
        int right = Math.max(first.right(), second.right());
        int bottom = Math.max(first.bottom(), second.bottom());
        return new UiRectangle(x, y, right - x, bottom - y);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    record DragVisual(
            RoadPresetEditorComponent component,
            double mouseX,
            double mouseY,
            boolean active,
            int dropIndex
    ) {
        DragVisual {
            Objects.requireNonNull(component, "component");
        }
    }

    record View(
            int screenWidth,
            int screenHeight,
            ResourceLocation editingId,
            RoadPresetEditorLayout layout,
            RoadPresetEditorDraft draft,
            int expectedRevision,
            boolean editable,
            boolean dirty,
            int selectedComponent,
            java.util.Set<Integer> selectedComponents,
            Component status,
            boolean statusIsError,
            String nameValue,
            String category,
            boolean catalogEmpty,
            boolean componentCatalog,
            int mouseX,
            int mouseY,
            DragVisual dragVisual,
            RoadPresetEditorTab tab,
            boolean materialSetOpen,
            UiRectangle activeViewport,
            int scrollOffset,
            int scrollMaximum
    ) {
        View {
            Objects.requireNonNull(editingId, "editingId");
            Objects.requireNonNull(layout, "layout");
            selectedComponents = java.util.Set.copyOf(Objects.requireNonNull(
                    selectedComponents,
                    "selectedComponents"
            ));
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(nameValue, "nameValue");
            Objects.requireNonNull(category, "category");
        }
    }
}
