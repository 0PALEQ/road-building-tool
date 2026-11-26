package com.cookiecraftmods.roadbuilder.client.ui;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import com.cookiecraftmods.roadbuilder.client.tool.RemovalUiPhase;
import com.cookiecraftmods.roadbuilder.client.tool.RoadBuilderTool;
import com.cookiecraftmods.roadbuilder.client.tool.StraightRoadToolController;
import com.cookiecraftmods.roadbuilder.preset.RoadPreset;
import com.cookiecraftmods.roadbuilder.preset.RoadPresetCatalogEntry;
import com.cookiecraftmods.roadbuilder.preset.RoadCategory;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import com.cookiecraftmods.roadbuilder.client.networking.RoadBuilderClientNetworking;

import java.util.List;
import java.util.Objects;

import static com.cookiecraftmods.roadbuilder.client.ui.UIStyle.*;

public final class RoadBuilderToolbarRenderer {
    private static final ResourceLocation ROAD_BUILDER_ICON =
            MinecraftIds.id("roadbuilder", "textures/gui/road_builder.png");
    private static final ResourceLocation ROAD_DEMOLISHER_ICON =
            MinecraftIds.id("roadbuilder", "textures/gui/road_demolisher.png");
    private static final ResourceLocation ROAD_PRESET_EDITOR_ICON =
            MinecraftIds.id("roadbuilder", "textures/gui/road_preset_editor.png");
    private static final ResourceLocation ROAD_REPLACE_ICON =
            MinecraftIds.id("roadbuilder", "textures/gui/road_replace.png");
    private static final ResourceLocation LANE_CONNECTOR_ICON =
            MinecraftIds.id("roadbuilder", "textures/gui/lane_connector.png");
    private static final ResourceLocation STRAIGHT_ROAD_ICON =
            MinecraftIds.id("roadbuilder", "textures/gui/straight_road.png");
    private static final ResourceLocation SIMPLE_CURVE_ICON =
            MinecraftIds.id("roadbuilder", "textures/gui/simple_curve.png");
    private static final ResourceLocation COMPLEX_CURVE_ICON =
            MinecraftIds.id("roadbuilder", "textures/gui/complex_curve.png");
    private static final ResourceLocation FREEHAND_ICON =
            MinecraftIds.id("roadbuilder", "textures/gui/freehand.png");
    private static final ResourceLocation ROUNDABOUT_ICON =
            MinecraftIds.id("roadbuilder", "textures/gui/roundabout.png");
    static final ResourceLocation SETTINGS_ICON =
            MinecraftIds.id("roadbuilder", "textures/gui/settings_icon.png");
    private static final ResourceLocation LANE_PATHS_ICON =
            MinecraftIds.id("roadbuilder", "textures/gui/lane_paths.png");
    private static final ResourceLocation CROSSWALK_ICON =
            MinecraftIds.id("roadbuilder", "textures/gui/crosswalk.png");
    private static final ResourceLocation SNAP_ENDPOINT_ICON =
            MinecraftIds.id("roadbuilder", "textures/gui/snap_endpoint.png");
    private static final ResourceLocation SNAP_GUIDELINE_ICON =
            MinecraftIds.id("roadbuilder", "textures/gui/snap_guideline.png");
    private static final ResourceLocation SNAP_ANGLE_ICON =
            MinecraftIds.id("roadbuilder", "textures/gui/snap_angle.png");
    private static final ResourceLocation PARALLEL_ICON =
            MinecraftIds.id("roadbuilder", "textures/gui/parallel.png");
    private static final ResourceLocation PAGE_BULLET_ICON =
            MinecraftIds.id("roadbuilder", "textures/gui/page_bullet.png");
    private static final ResourceLocation PAGE_BULLET_ACTIVE_ICON =
            MinecraftIds.id("roadbuilder", "textures/gui/page_bullet_active.png");
    private static final int ICON_TEXTURE_SIZE = 32;

    public void render(
            GuiGraphics graphics,
            Minecraft client,
            StraightRoadToolController tool
    ) {
        Objects.requireNonNull(graphics, "graphics");
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(tool, "tool");

        RoadBuilderToolbarLayout layout = RoadBuilderToolbarLayout.create(
                graphics.guiWidth(),
                graphics.guiHeight()
        );
        CursorPosition cursor = cursor(client);
        boolean highContrast = client.options.highContrast().get();

        renderStatus(graphics, client, tool, layout);
        renderContextShelf(graphics, client, tool, layout, cursor, highContrast);
        renderDock(graphics, client, tool, layout, cursor, highContrast);
        renderTooltip(graphics, client, layout, tool, cursor);
    }

    private static void renderStatus(
            GuiGraphics graphics,
            Minecraft client,
            StraightRoadToolController tool,
            RoadBuilderToolbarLayout layout
    ) {
        UiRectangle panel = layout.statusPanel();
        if (panel.height() == 0) {
            return;
        }
        panel(graphics, panel, BACKGROUND_STATUS, BORDER_PANEL_HIGHLIGHT);
        Component status = tool.status(client);
        Component mode = Component.translatable("toolbar.roadbuilder.mode_title");
        graphics.drawString(
                client.font,
                mode,
                panel.x() + 5,
                panel.y() + 3,
                HIGHLIGHT,
                false
        );
        int statusStart = panel.x() + client.font.width(mode) + 13;
        int available = Math.max(0, panel.right() - statusStart - 5);
        graphics.enableScissor(
                statusStart,
                panel.y(),
                panel.right() - 4,
                panel.bottom()
        );
        graphics.drawString(
                client.font,
                client.font.plainSubstrByWidth(status.getString(), available),
                statusStart,
                panel.y() + 3,
                tool.removalPhase() == RemovalUiPhase.CONFIRM
                        ? HIGHLIGHT
                        : TEXT_PRIMARY,
                false
        );
        graphics.disableScissor();
    }

    private static void renderContextShelf(
            GuiGraphics graphics,
            Minecraft client,
            StraightRoadToolController tool,
            RoadBuilderToolbarLayout layout,
            CursorPosition cursor,
            boolean highContrast
    ) {
        if (tool.selectedGroup() == RoadBuilderToolGroup.ROADS) {
            if (layout.toolModePanel().width() > 0) {
                panel(
                        graphics,
                        layout.toolModePanel(),
                        BACKGROUND_PANEL_RAISED,
                        BORDER_PANEL_HIGHLIGHT
                );
            }
            panel(
                    graphics,
                    layout.presetPanel(),
                    BACKGROUND_PANEL_RAISED,
                    BORDER_PANEL_HIGHLIGHT
            );
        } else {
            panel(
                    graphics,
                    layout.contextPanel(),
                    BACKGROUND_PANEL_RAISED,
                    BORDER_PANEL_HIGHLIGHT
            );
        }

        switch (tool.selectedGroup()) {
            case ROADS -> renderRoadContext(
                    graphics,
                    client,
                    tool,
                    layout,
                    cursor,
                    highContrast
            );
            case DEMOLITION -> renderDemolitionContext(
                    graphics,
                    client,
                    tool,
                    layout,
                    cursor,
                    highContrast
            );
            case LANE_CONNECTORS -> renderLaneConnectorContext(
                    graphics,
                    client,
                    layout,
                    cursor,
                    highContrast
            );
        }
    }

    private static void renderRoadContext(
            GuiGraphics graphics,
            Minecraft client,
            StraightRoadToolController tool,
            RoadBuilderToolbarLayout layout,
            CursorPosition cursor,
            boolean highContrast
    ) {
        String[] rowKeys = {"tool_mode", "elevation", "parallel_mode", "snapping"};
        for (int row = 0; row < rowKeys.length; row++) {
            UiRectangle label = layout.toolModeLabel(row);
            if (label.width() == 0) continue;
            Component text = Component.translatable("toolbar.roadbuilder.label." + rowKeys[row]);
            graphics.drawString(client.font,
                    client.font.plainSubstrByWidth(text.getString(), label.width()),
                    label.x(), label.y() + Math.max(1, (label.height() - client.font.lineHeight) / 2),
                    TEXT_PRIMARY, false);
        }
        roadSnappingButton(
                graphics,
                layout.roadNodeSnappingButton(),
                cursor,
                tool.snappingMode().snapsNodes(),
                SNAP_ENDPOINT_ICON,
                highContrast
        );
        roadSnappingButton(
                graphics,
                layout.roadGuidelineSnappingButton(),
                cursor,
                tool.snappingMode().snapsGuidelines(),
                SNAP_GUIDELINE_ICON,
                highContrast
        );
        roadSnappingButton(
                graphics,
                layout.roadAngleSnappingButton(),
                cursor,
                tool.snappingMode().snapsAngles(),
                SNAP_ANGLE_ICON,
                highContrast
        );
        roadSnappingButton(
                graphics,
                layout.parallelToggleButton(),
                cursor,
                tool.parallelEnabled(),
                PARALLEL_ICON,
                highContrast
        );
        contextButton(graphics, client, layout.sunkenRoadButton(),
                Component.literal(tool.sunkenRoad() ? "[x] " : "[ ] ").append(
                        Component.translatable("toolbar.roadbuilder.sunken_road")),
                tool.sunkenRoad(), true, cursor, highContrast);
        renderPresetShelf(
                graphics,
                client,
                tool,
                layout,
                cursor,
                highContrast
        );
        roadToolButton(graphics, layout, tool, cursor, highContrast,
                RoadBuilderTool.STRAIGHT_ROAD);
        roadToolButton(graphics, layout, tool, cursor, highContrast,
                RoadBuilderTool.SIMPLE_CURVE);
        roadToolButton(graphics, layout, tool, cursor, highContrast,
                RoadBuilderTool.COMPLEX_CURVE);
        roadToolButton(graphics, layout, tool, cursor, highContrast,
                RoadBuilderTool.FREEHAND_DRAW);
        roadToolButton(graphics, layout, tool, cursor, highContrast,
                RoadBuilderTool.REPLACE);
    }

    private static void renderPresetShelf(
            GuiGraphics graphics,
            Minecraft client,
            StraightRoadToolController tool,
            RoadBuilderToolbarLayout layout,
            CursorPosition cursor,
            boolean highContrast
    ) {
        renderCategoryTabs(graphics, client, tool, layout, cursor, highContrast);
        List<RoadPresetCatalogEntry> entries = RoadBuilderClientNetworking
                .presetCatalog().entries().stream()
                .filter(tool.roadCategory()::matches)
                .toList();
        ResourceLocation selected = RoadBuilderClientNetworking
                .selectedPreset()
                .map(RoadPresetCatalogEntry::id)
                .orElse(null);
        RoadBuilderToolbarPresetWindow window =
                RoadBuilderToolbarPresetWindow.create(
                        entries,
                        tool.presetPage()
                );
        for (int index = 0;
             index < RoadBuilderToolbarLayout.PRESET_SLOT_COUNT;
             index++) {
            UiRectangle card = layout.presetCardButton(index);
            RoadPresetCatalogEntry entry = window.entryAt(index).orElse(null);
            boolean isSelected = entry != null
                    && entry.id().equals(selected);
            boolean hovered = card.contains(cursor.x(), cursor.y());
            fillButton(
                    graphics,
                    card,
                    isSelected
                            ? CONTROL_SELECTED
                            : hovered ? CONTROL_HOVERED : CONTROL_BACKGROUND,
                    isSelected
                            ? highContrast ? BORDER_HIGH_CONTRAST : HIGHLIGHT
                            : BORDER_PANEL_HIGHLIGHT
            );
            if (entry != null) {
                RoadBuilderClientNetworking.requestPresetContentIfMissing(
                        entry.id()
                );
                UiRectangle preview = new UiRectangle(
                        card.x() + 2,
                        card.y() + 2,
                        Math.max(1, card.width() - 4),
                        Math.max(1, card.height() - 4)
                );
                RoadPreset preset = RoadBuilderClientNetworking
                        .presetContent(entry.id())
                        .orElse(null);
                if (preset == null) {
                    RoadPresetThumbnailRenderer.renderFallback(
                            graphics,
                            preview,
                            entry.laneCount()
                    );
                } else {
                    RoadPresetThumbnailRenderer.render(
                            graphics,
                            preview,
                            preset
                    );
                }
            }
        }
        renderPageBullets(graphics, layout, tool, entries);
        UiRectangle add = layout.presetEditorButton();
        fillButton(
                graphics,
                add,
                add.contains(cursor.x(), cursor.y())
                        ? CONTROL_HOVERED
                        : CONTROL_BACKGROUND,
                BORDER_PANEL_HIGHLIGHT
        );
        drawTextureIcon(
                graphics,
                add,
                ROAD_PRESET_EDITOR_ICON,
                0xFFFFFF
        );
    }

    private static void renderCategoryTabs(
            GuiGraphics graphics,
            Minecraft client,
            StraightRoadToolController tool,
            RoadBuilderToolbarLayout layout,
            CursorPosition cursor,
            boolean highContrast
    ) {
        for (RoadCategory category : RoadCategory.values()) {
            UiRectangle tab = layout.presetCategoryButton(category);
            boolean selected = tool.roadCategory() == category;
            boolean hovered = tab.contains(cursor.x(), cursor.y());
            fillButton(
                    graphics,
                    tab,
                    selected ? CONTROL_SELECTED
                            : hovered ? CONTROL_HOVERED : CONTROL_BACKGROUND,
                    selected && highContrast
                            ? BORDER_HIGH_CONTRAST
                            : BORDER_PANEL_HIGHLIGHT
            );
            String label = Component.translatable(
                    "toolbar.roadbuilder.category."
                            + category.serializedName()
            ).getString();
            label = client.font.plainSubstrByWidth(
                    label,
                    Math.max(1, tab.width() - 2)
            );
            graphics.drawCenteredString(
                    client.font,
                    label,
                    tab.x() + tab.width() / 2,
                    tab.y() + Math.max(1, (tab.height() - 8) / 2),
                    selected ? TEXT_PRIMARY : TEXT_SECONDARY
            );
        }
    }

    private static void renderPageBullets(
            GuiGraphics graphics,
            RoadBuilderToolbarLayout layout,
            StraightRoadToolController tool,
            List<RoadPresetCatalogEntry> entries
    ) {
        int pageCount = Math.max(1, (entries.size()
                + RoadBuilderToolbarLayout.PRESET_SLOT_COUNT - 1)
                / RoadBuilderToolbarLayout.PRESET_SLOT_COUNT);
        int activePage = tool.presetPage();
        int visibleCount = Math.min(
                pageCount,
                RoadBuilderToolbarLayout.PRESET_PAGE_BULLET_COUNT
        );
        int firstPage = StraightRoadToolController.firstVisiblePresetPage(
                activePage,
                pageCount,
                visibleCount
        );
        for (int slot = 0; slot < visibleCount; slot++) {
            drawPageBullet(
                    graphics,
                    layout.presetPageBullet(slot),
                    firstPage + slot == activePage
            );
        }
    }

    private static void drawPageBullet(
            GuiGraphics graphics,
            UiRectangle bounds,
            boolean active
    ) {
        ResourceLocation texture = active
                ? PAGE_BULLET_ACTIVE_ICON
                : PAGE_BULLET_ICON;
        int size = Math.min(10, Math.min(bounds.width(), bounds.height()));
        RoadGuiDrawing.icon(graphics, texture,
                bounds.x() + (bounds.width() - size) / 2,
                bounds.y() + (bounds.height() - size) / 2, size, 10, 0xFFFFFFFF);
    }

    private static void roadSnappingButton(
            GuiGraphics graphics,
            UiRectangle button,
            CursorPosition cursor,
            boolean selected,
            ResourceLocation texture,
            boolean highContrast
    ) {
        boolean hovered = button.contains(cursor.x(), cursor.y());
        fillButton(
                graphics,
                button,
                selected
                        ? CONTROL_SELECTED
                        : hovered ? CONTROL_HOVERED : CONTROL_BACKGROUND,
                selected && highContrast
                        ? BORDER_HIGH_CONTRAST
                        : BORDER_PANEL_HIGHLIGHT
        );
        drawTextureIcon(graphics, button, texture, 0xFFFFFF);
    }

    private static void roadToolButton(
            GuiGraphics graphics,
            RoadBuilderToolbarLayout layout,
            StraightRoadToolController tool,
            CursorPosition cursor,
            boolean highContrast,
            RoadBuilderTool roadTool
    ) {
        UiRectangle button = layout.roadToolButton(roadTool);
        boolean selected = tool.activeTool() == roadTool;
        boolean focused = tool.keyboardFocusedRoadTool()
                .filter(roadTool::equals)
                .isPresent();
        boolean hovered = button.contains(cursor.x(), cursor.y());
        int background = selected
                ? CONTROL_SELECTED
                : hovered ? CONTROL_HOVERED : CONTROL_BACKGROUND;
        fillButton(
                graphics,
                button,
                background,
                focused
                        ? BORDER_HIGH_CONTRAST
                        : selected
                        ? highContrast ? BORDER_HIGH_CONTRAST : HIGHLIGHT
                        : BORDER_PANEL_HIGHLIGHT
        );
        drawRoadModeIcon(
                graphics,
                button,
                roadTool,
                selected || hovered ? TEXT_PRIMARY : TEXT_SECONDARY
        );
    }

    private static void drawRoadModeIcon(
            GuiGraphics graphics,
            UiRectangle button,
            RoadBuilderTool roadTool,
            int color
    ) {
        if (roadTool == RoadBuilderTool.REPLACE) {
            drawTextureIcon(graphics, button, ROAD_REPLACE_ICON, color);
            return;
        }
        if (roadTool == RoadBuilderTool.FREEHAND || roadTool == RoadBuilderTool.FREEHAND_DRAW) {
            drawTextureIcon(graphics, button, FREEHAND_ICON, color);
            return;
        }
        if (roadTool == RoadBuilderTool.ROUNDABOUT) {
            drawTextureIcon(graphics, button, ROUNDABOUT_ICON, color);
            return;
        }
        ResourceLocation texture = switch (roadTool) {
            case STRAIGHT_ROAD -> STRAIGHT_ROAD_ICON;
            case SIMPLE_CURVE -> SIMPLE_CURVE_ICON;
            case COMPLEX_CURVE -> COMPLEX_CURVE_ICON;
            default -> throw new IllegalArgumentException(
                    "not a road placement tool: " + roadTool
            );
        };
        drawTextureIcon(graphics, button, texture, color);
    }

    private static void drawRoadModePath(
            GuiGraphics graphics,
            RoadBuilderTool roadTool,
            int left,
            int top,
            int right,
            int bottom,
            int radius,
            int color
    ) {
        switch (roadTool) {
            case STRAIGHT_ROAD -> thickLine(
                    graphics,
                    left,
                    bottom,
                    right,
                    top,
                    radius,
                    color
            );
            case SIMPLE_CURVE -> quadraticPath(
                    graphics,
                    left,
                    bottom,
                    left,
                    top,
                    right,
                    top,
                    radius,
                    color
            );
            case COMPLEX_CURVE -> cubicPath(
                    graphics,
                    left,
                    bottom,
                    left,
                    top,
                    right,
                    bottom,
                    right,
                    top,
                    radius,
                    color
            );
            case REPLACE -> throw new IllegalStateException(
                    "replace uses its supplied texture icon"
            );
            default -> throw new IllegalArgumentException(
                    "not a road placement tool: " + roadTool
            );
        }
        square(graphics, left, bottom, radius, color);
        square(graphics, right, top, radius, color);
    }

    private static void renderDemolitionContext(
            GuiGraphics graphics,
            Minecraft client,
            StraightRoadToolController tool,
            RoadBuilderToolbarLayout layout,
            CursorPosition cursor,
            boolean highContrast
    ) {
        boolean enabled = tool.removeButtonEnabled(client);
        int color = switch (tool.removalPhase()) {
            case CONFIRM -> CONTROL_DANGER;
            case AVAILABLE -> CONTROL_DANGER;
            case PREVIEW_PENDING, EXECUTE_PENDING -> CONTROL_BACKGROUND;
            case UNAVAILABLE -> CONTROL_DISABLED;
        };
        contextButton(
                graphics,
                client,
                layout.primaryActionButton(),
                tool.removeButtonLabel(),
                color,
                enabled,
                cursor,
                highContrast
        );
        contextButton(
                graphics,
                client,
                layout.presetButton(),
                Component.translatable("toolbar.roadbuilder.safe_restore"),
                false,
                false,
                cursor,
                highContrast
        );
        contextButton(
                graphics,
                client,
                layout.elevationButton(),
                Component.translatable("toolbar.roadbuilder.segment_unit"),
                false,
                false,
                cursor,
                highContrast
        );
        contextButton(
                graphics,
                client,
                layout.snapOptionButton(),
                Component.translatable("toolbar.roadbuilder.graph_backed"),
                false,
                false,
                cursor,
                highContrast
        );
    }

    private static void renderLaneConnectorContext(
            GuiGraphics graphics,
            Minecraft client,
            RoadBuilderToolbarLayout layout,
            CursorPosition cursor,
            boolean highContrast
    ) {
        contextButton(
                graphics,
                client,
                layout.primaryActionButton(),
                Component.translatable("toolbar.roadbuilder.lane_connector"),
                true,
                true,
                cursor,
                highContrast
        );
        contextButton(
                graphics,
                client,
                layout.presetButton(),
                Component.translatable("toolbar.roadbuilder.connection_points"),
                true,
                false,
                cursor,
                highContrast
        );
        contextButton(
                graphics,
                client,
                layout.elevationButton(),
                Component.translatable("toolbar.roadbuilder.graph_backed"),
                false,
                false,
                cursor,
                highContrast
        );
        contextButton(
                graphics,
                client,
                layout.snapOptionButton(),
                Component.translatable("toolbar.roadbuilder.connector_editing"),
                false,
                false,
                cursor,
                highContrast
        );
    }

    private static void renderDock(
            GuiGraphics graphics,
            Minecraft client,
            StraightRoadToolController tool,
            RoadBuilderToolbarLayout layout,
            CursorPosition cursor,
            boolean highContrast
    ) {
        panel(graphics, layout.panel(), BACKGROUND_PANEL, BORDER_PANEL_HIGHLIGHT);
        for (RoadBuilderToolGroup group : RoadBuilderToolGroup.values()) {
            UiRectangle button = layout.groupButton(group);
            boolean selected = tool.selectedGroup() == group;
            boolean hovered = button.contains(cursor.x(), cursor.y());
            boolean destructive = group == RoadBuilderToolGroup.DEMOLITION;
            int color = destructive
                    ? hovered || selected
                            ? lighten(CONTROL_DANGER, 18)
                            : CONTROL_DANGER
                    : selected
                            ? CONTROL_SELECTED
                            : hovered ? CONTROL_HOVERED : CONTROL_BACKGROUND;
            fillButton(
                    graphics,
                    button,
                    color,
                    selected
                            ? highContrast ? BORDER_HIGH_CONTRAST : HIGHLIGHT
                            : BORDER_PANEL_HIGHLIGHT
            );
            drawGroupIcon(
                    graphics,
                    button,
                    group,
                    selected || hovered ? TEXT_PRIMARY : TEXT_SECONDARY
            );
            if (selected) {
                graphics.fill(
                        button.x() + 2,
                        button.bottom() - 2,
                        button.right() - 2,
                        button.bottom() - 1,
                        HIGHLIGHT
                );
            }
        }

        renderModeUtilities(graphics, client, tool, layout, cursor, highContrast);
        roadToolButton(graphics, layout, tool, cursor, highContrast, RoadBuilderTool.ROUNDABOUT);
        UiRectangle settings = layout.settingsButton();
        boolean hovered = settings.contains(cursor.x(), cursor.y());
        fillButton(
                graphics,
                settings,
                hovered ? CONTROL_HOVERED : CONTROL_BACKGROUND,
                highContrast && hovered ? BORDER_HIGH_CONTRAST : BORDER_PANEL_HIGHLIGHT
        );
        drawTextureIcon(graphics, settings, SETTINGS_ICON,
                hovered ? TEXT_PRIMARY : TEXT_SECONDARY);
    }

    private static void renderModeUtilities(
            GuiGraphics graphics,
            Minecraft client,
            StraightRoadToolController tool,
            RoadBuilderToolbarLayout layout,
            CursorPosition cursor,
            boolean highContrast
    ) {
        if (layout.lanePathVisibilityButton().width() == 0) {
            return;
        }
        UiRectangle lanes = layout.lanePathVisibilityButton();
        boolean lanesHovered = lanes.contains(cursor.x(), cursor.y());
        fillButton(
                graphics,
                lanes,
                tool.lanePathsVisible()
                        ? CONTROL_SELECTED
                        : lanesHovered ? CONTROL_HOVERED : CONTROL_BACKGROUND,
                tool.lanePathsVisible() && highContrast
                        ? BORDER_HIGH_CONTRAST
                        : BORDER_PANEL_HIGHLIGHT
        );
        drawTextureIcon(
                graphics,
                lanes,
                LANE_PATHS_ICON,
                tool.lanePathsVisible() || lanesHovered
                        ? TEXT_PRIMARY
                        : TEXT_SECONDARY
        );

        UiRectangle crosswalk = layout.crosswalkToolButton();
        boolean crosswalkSelected = tool.activeTool()
                == RoadBuilderTool.CROSSWALK;
        boolean crosswalkHovered = crosswalk.contains(cursor.x(), cursor.y());
        fillButton(
                graphics,
                crosswalk,
                crosswalkSelected
                        ? CONTROL_SELECTED
                        : crosswalkHovered
                                ? CONTROL_HOVERED
                                : CONTROL_BACKGROUND,
                crosswalkSelected && highContrast
                        ? BORDER_HIGH_CONTRAST
                        : BORDER_PANEL_HIGHLIGHT
        );
        drawTextureIcon(
                graphics,
                crosswalk,
                CROSSWALK_ICON,
                crosswalkSelected || crosswalkHovered
                        ? TEXT_PRIMARY
                        : TEXT_SECONDARY
        );

        if (tool.selectedGroup() != RoadBuilderToolGroup.ROADS) return;
        contextButton(graphics, client, layout.parallelDisplay(),
                Component.literal(Integer.toString(Math.abs(tool.parallelOffset()))), true, true, cursor, highContrast);
        elevationArrowButton(graphics, layout.parallelLowerButton(), cursor, false, highContrast);
        elevationArrowButton(graphics, layout.parallelRaiseButton(), cursor, true, highContrast);
        UiRectangle elevation = layout.elevationDisplay();
        fillButton(
                graphics,
                elevation,
                elevation.contains(cursor.x(), cursor.y())
                        ? CONTROL_HOVERED
                        : CONTROL_BACKGROUND,
                BORDER_PANEL_HIGHLIGHT
        );
        String value = String.format(
                java.util.Locale.ROOT,
                "%+d",
                tool.elevationOffset()
        );
        graphics.drawCenteredString(
                client.font,
                value,
                elevation.x() + elevation.width() / 2,
                elevation.y() + Math.max(
                        1,
                        (elevation.height() - client.font.lineHeight) / 2
                ),
                TEXT_PRIMARY
        );
        elevationArrowButton(
                graphics,
                layout.elevationRaiseButton(),
                cursor,
                true,
                highContrast
        );
        elevationArrowButton(
                graphics,
                layout.elevationLowerButton(),
                cursor,
                false,
                highContrast
        );
    }

    private static void elevationArrowButton(
            GuiGraphics graphics,
            UiRectangle button,
            CursorPosition cursor,
            boolean up,
            boolean highContrast
    ) {
        boolean hovered = button.contains(cursor.x(), cursor.y());
        fillButton(
                graphics,
                button,
                hovered ? CONTROL_HOVERED : CONTROL_BACKGROUND,
                highContrast && hovered
                        ? BORDER_HIGH_CONTRAST
                        : BORDER_PANEL_HIGHLIGHT
        );
        int centerX = button.x() + button.width() / 2;
        int centerY = button.y() + button.height() / 2;
        int radius = Math.max(1, Math.min(button.width(), button.height()) / 4);
        int tipY = centerY + (up ? -radius : radius);
        int baseY = centerY + (up ? radius : -radius);
        thickLine(graphics, centerX, tipY,
                centerX - radius, baseY, 0, TEXT_PRIMARY);
        thickLine(graphics, centerX, tipY,
                centerX + radius, baseY, 0, TEXT_PRIMARY);
    }

    private static void contextButton(
            GuiGraphics graphics,
            Minecraft client,
            UiRectangle button,
            Component label,
            boolean selected,
            boolean enabled,
            CursorPosition cursor,
            boolean highContrast
    ) {
        int background = selected
                ? CONTROL_SELECTED
                : enabled && button.contains(cursor.x(), cursor.y())
                        ? CONTROL_HOVERED
                        : enabled ? CONTROL_BACKGROUND : CONTROL_DISABLED;
        contextButton(
                graphics,
                client,
                button,
                label,
                background,
                enabled,
                cursor,
                highContrast
        );
    }

    private static void contextButton(
            GuiGraphics graphics,
            Minecraft client,
            UiRectangle button,
            Component label,
            int background,
            boolean enabled,
            CursorPosition cursor,
            boolean highContrast
    ) {
        int hoverColor = enabled && button.contains(cursor.x(), cursor.y())
                ? lighten(background, 18)
                : background;
        fillButton(
                graphics,
                button,
                hoverColor,
                enabled && highContrast ? BORDER_HIGH_CONTRAST : BORDER_PANEL_HIGHLIGHT
        );
        String clipped = client.font.plainSubstrByWidth(
                label.getString(),
                Math.max(0, button.width() - 6)
        );
        graphics.drawCenteredString(
                client.font,
                clipped,
                button.x() + button.width() / 2,
                button.y() + Math.max(1, (button.height() - client.font.lineHeight) / 2),
                enabled ? TEXT_PRIMARY : TEXT_DISABLED
        );
    }

    private static void panel(
            GuiGraphics graphics,
            UiRectangle rectangle,
            int background,
            int edge
    ) {
        graphics.fill(
                rectangle.x(),
                rectangle.y(),
                rectangle.right(),
                rectangle.bottom(),
                background
        );
        graphics.fill(
                rectangle.x(),
                rectangle.y(),
                rectangle.right(),
                rectangle.y() + 1,
                edge
        );
        graphics.renderOutline(
                rectangle.x(),
                rectangle.y(),
                rectangle.width(),
                rectangle.height(),
                BORDER_SHADOW
        );
    }

    private static void fillButton(
            GuiGraphics graphics,
            UiRectangle rectangle,
            int background,
            int outline
    ) {
        graphics.fill(
                rectangle.x(),
                rectangle.y(),
                rectangle.right(),
                rectangle.bottom(),
                background
        );
        graphics.renderOutline(
                rectangle.x(),
                rectangle.y(),
                rectangle.width(),
                rectangle.height(),
                outline
        );
    }

    private static void drawGroupIcon(
            GuiGraphics graphics,
            UiRectangle button,
            RoadBuilderToolGroup group,
            int color
    ) {
        if (group == RoadBuilderToolGroup.ROADS) {
            drawTextureIcon(graphics, button, ROAD_BUILDER_ICON, color);
            return;
        }
        if (group == RoadBuilderToolGroup.DEMOLITION) {
            drawTextureIcon(graphics, button, ROAD_DEMOLISHER_ICON, color);
            return;
        }
        if (group == RoadBuilderToolGroup.LANE_CONNECTORS) {
            drawTextureIcon(graphics, button, LANE_CONNECTOR_ICON, color);
        }
    }

    private static void drawTextureIcon(
            GuiGraphics graphics,
            UiRectangle button,
            ResourceLocation texture,
            int tint
    ) {
        int size = Math.max(
                1,
                Math.min(
                        ICON_TEXTURE_SIZE,
                        Math.min(button.width() - 4, button.height() - 4)
                )
        );
        int x = button.x() + (button.width() - size) / 2;
        int y = button.y() + (button.height() - size) / 2;
        RoadGuiDrawing.icon(graphics, texture, x, y, size, ICON_TEXTURE_SIZE, tint);
    }

    private static void quadraticPath(
            GuiGraphics graphics,
            int startX,
            int startY,
            int controlX,
            int controlY,
            int endX,
            int endY,
            int radius,
            int color
    ) {
        int previousX = startX;
        int previousY = startY;
        for (int step = 1; step <= 20; step++) {
            double parameter = step / 20.0;
            double inverse = 1.0 - parameter;
            int x = (int) Math.round(
                    inverse * inverse * startX
                            + 2.0 * inverse * parameter * controlX
                            + parameter * parameter * endX
            );
            int y = (int) Math.round(
                    inverse * inverse * startY
                            + 2.0 * inverse * parameter * controlY
                            + parameter * parameter * endY
            );
            thickLine(
                    graphics,
                    previousX,
                    previousY,
                    x,
                    y,
                    radius,
                    color
            );
            previousX = x;
            previousY = y;
        }
    }

    private static void cubicPath(
            GuiGraphics graphics,
            int startX,
            int startY,
            int firstControlX,
            int firstControlY,
            int secondControlX,
            int secondControlY,
            int endX,
            int endY,
            int radius,
            int color
    ) {
        int previousX = startX;
        int previousY = startY;
        for (int step = 1; step <= 24; step++) {
            double parameter = step / 24.0;
            double inverse = 1.0 - parameter;
            int x = (int) Math.round(
                    inverse * inverse * inverse * startX
                            + 3.0 * inverse * inverse * parameter
                            * firstControlX
                            + 3.0 * inverse * parameter * parameter
                            * secondControlX
                            + parameter * parameter * parameter * endX
            );
            int y = (int) Math.round(
                    inverse * inverse * inverse * startY
                            + 3.0 * inverse * inverse * parameter
                            * firstControlY
                            + 3.0 * inverse * parameter * parameter
                            * secondControlY
                            + parameter * parameter * parameter * endY
            );
            thickLine(
                    graphics,
                    previousX,
                    previousY,
                    x,
                    y,
                    radius,
                    color
            );
            previousX = x;
            previousY = y;
        }
    }

    private static void thickLine(
            GuiGraphics graphics,
            int x0,
            int y0,
            int x1,
            int y1,
            int radius,
            int color
    ) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int error = dx - dy;
        int x = x0;
        int y = y0;
        while (true) {
            square(graphics, x, y, radius, color);
            if (x == x1 && y == y1) {
                return;
            }
            int twiceError = error * 2;
            if (twiceError > -dy) {
                error -= dy;
                x += sx;
            }
            if (twiceError < dx) {
                error += dx;
                y += sy;
            }
        }
    }

    private static void square(
            GuiGraphics graphics,
            int centerX,
            int centerY,
            int radius,
            int color
    ) {
        graphics.fill(
                centerX - radius,
                centerY - radius,
                centerX + radius + 1,
                centerY + radius + 1,
                color
        );
    }

    private static void line(
            GuiGraphics graphics,
            int x0,
            int y0,
            int x1,
            int y1,
            int color
    ) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int error = dx - dy;
        int x = x0;
        int y = y0;
        while (true) {
            graphics.fill(x, y, x + 1, y + 1, color);
            if (x == x1 && y == y1) {
                return;
            }
            int twiceError = error * 2;
            if (twiceError > -dy) {
                error -= dy;
                x += sx;
            }
            if (twiceError < dx) {
                error += dx;
                y += sy;
            }
        }
    }

    private static void renderTooltip(
            GuiGraphics graphics,
            Minecraft client,
            RoadBuilderToolbarLayout layout,
            StraightRoadToolController tool,
            CursorPosition cursor
    ) {
        Component tooltip = null;
        if (tool.selectedGroup() == RoadBuilderToolGroup.ROADS) {
            List<RoadPresetCatalogEntry> presets =
                    RoadBuilderClientNetworking.presetCatalog().entries()
                            .stream()
                            .filter(tool.roadCategory()::matches)
                            .toList();
            RoadBuilderToolbarPresetWindow window =
                    RoadBuilderToolbarPresetWindow.create(
                            presets,
                            tool.presetPage()
                    );
            for (int index = 0;
                 index < RoadBuilderToolbarLayout.PRESET_SLOT_COUNT;
                 index++) {
                if (layout.presetCardButton(index).contains(
                        cursor.x(),
                        cursor.y()
                )) {
                    RoadPresetCatalogEntry preset = window.entryAt(index)
                            .orElse(null);
                    if (preset == null) {
                        break;
                    }
                    tooltip = Component.literal(
                            preset.display().fallbackName()
                                    + " \u00b7 " + preset.surfaceWidth()
                                    + "m \u00b7 " + preset.laneCount()
                                    + " lanes \u00b7 "
                                    + preset.display().category()
                    );
                    break;
                }
            }
            if (layout.presetEditorButton().contains(
                    cursor.x(),
                    cursor.y()
            )) {
                tooltip = Component.translatable(
                        "screen.roadbuilder.preset_editor"
                );
            }
            for (RoadCategory category : RoadCategory.values()) {
                if (layout.presetCategoryButton(category).contains(
                        cursor.x(),
                        cursor.y()
                )) {
                    tooltip = Component.translatable(
                            "toolbar.roadbuilder.category."
                                    + category.serializedName()
                    );
                    break;
                }
            }
        }
        for (RoadBuilderToolGroup group : RoadBuilderToolGroup.values()) {
            if (layout.groupButton(group).contains(cursor.x(), cursor.y())) {
                tooltip = group.tooltip();
                break;
            }
        }
        if (layout.settingsButton().contains(cursor.x(), cursor.y())) {
            tooltip = Component.translatable("screen.roadbuilder.settings.title");
        } else if (layout.lanePathVisibilityButton().contains(
                cursor.x(),
                cursor.y()
        )) {
            tooltip = Component.translatable(
                    "tooltip.roadbuilder.lane_paths_visibility"
            );
        } else if (layout.crosswalkToolButton().contains(
                cursor.x(),
                cursor.y()
        )) {
            tooltip = Component.translatable(
                    "tooltip.roadbuilder.crosswalk_tool"
            );
        } else if (layout.elevationDisplay().contains(
                cursor.x(),
                cursor.y()
        )) {
            tooltip = Component.translatable(
                    "tooltip.roadbuilder.elevation_reset"
            );
        } else if (layout.elevationRaiseButton().contains(
                cursor.x(),
                cursor.y()
        )) {
            tooltip = Component.translatable(
                    "tooltip.roadbuilder.elevation_raise"
            );
        } else if (layout.elevationLowerButton().contains(
                cursor.x(),
                cursor.y()
        )) {
            tooltip = Component.translatable(
                    "tooltip.roadbuilder.elevation_lower"
            );
        } else if (tool.selectedGroup() == RoadBuilderToolGroup.ROADS
                && layout.roadNodeSnappingButton()
                .contains(cursor.x(), cursor.y())) {
            tooltip = Component.translatable(
                    "tooltip.roadbuilder.snap_endpoints"
            );
        } else if (tool.selectedGroup() == RoadBuilderToolGroup.ROADS
                && layout.roadGuidelineSnappingButton()
                .contains(cursor.x(), cursor.y())) {
            tooltip = Component.translatable(
                    "tooltip.roadbuilder.snap_guidelines"
            );
        } else if (tool.selectedGroup() == RoadBuilderToolGroup.ROADS
                && layout.roadAngleSnappingButton()
                .contains(cursor.x(), cursor.y())) {
            tooltip = Component.translatable(
                    "tooltip.roadbuilder.snap_angles"
            );
        } else if (tool.selectedGroup() == RoadBuilderToolGroup.ROADS
                && layout.roadToolButton(RoadBuilderTool.STRAIGHT_ROAD)
                .contains(cursor.x(), cursor.y())) {
            tooltip = Component.translatable(
                    "tooltip.roadbuilder.straight_road"
            );
        } else if (tool.selectedGroup() == RoadBuilderToolGroup.ROADS
                && layout.roadToolButton(RoadBuilderTool.SIMPLE_CURVE)
                .contains(cursor.x(), cursor.y())) {
            tooltip = Component.translatable(
                    "tooltip.roadbuilder.simple_curve"
            );
        } else if (tool.selectedGroup() == RoadBuilderToolGroup.ROADS
                && layout.roadToolButton(RoadBuilderTool.COMPLEX_CURVE)
                .contains(cursor.x(), cursor.y())) {
            tooltip = Component.translatable(
                    "tooltip.roadbuilder.complex_curve"
            );
        } else if (tool.selectedGroup() == RoadBuilderToolGroup.ROADS
                && layout.roadToolButton(RoadBuilderTool.FREEHAND_DRAW)
                .contains(cursor.x(), cursor.y())) {
            tooltip = Component.translatable("tooltip.roadbuilder.freehand_draw");
        } else if (layout.roadToolButton(RoadBuilderTool.ROUNDABOUT)
                .contains(cursor.x(), cursor.y())) {
            tooltip = Component.translatable(
                    "tooltip.roadbuilder.roundabout"
            );
        } else if (tool.selectedGroup() == RoadBuilderToolGroup.ROADS
                && layout.roadToolButton(RoadBuilderTool.REPLACE)
                .contains(cursor.x(), cursor.y())) {
            tooltip = Component.translatable(
                    "tooltip.roadbuilder.replace"
            );
        } else if (tool.selectedGroup() == RoadBuilderToolGroup.ROADS
                && layout.sunkenRoadButton().contains(cursor.x(), cursor.y())) {
            tooltip = Component.translatable("tooltip.roadbuilder.sunken_road");
        } else if (tool.selectedGroup() == RoadBuilderToolGroup.ROADS
                && layout.parallelToggleButton().contains(cursor.x(), cursor.y())) {
            tooltip = Component.translatable("tooltip.roadbuilder.parallel", Math.abs(tool.parallelOffset()),
                    Component.translatable(tool.parallelOffset() > 0 ? "toolbar.roadbuilder.left" : "toolbar.roadbuilder.right"));
        } else if (tool.selectedGroup() == RoadBuilderToolGroup.ROADS
                && (layout.parallelDisplay().contains(cursor.x(), cursor.y())
                || layout.parallelLowerButton().contains(cursor.x(), cursor.y())
                || layout.parallelRaiseButton().contains(cursor.x(), cursor.y()))) {
            tooltip = Component.translatable("tooltip.roadbuilder.parallel_spacing");
        } else if (layout.primaryActionButton().contains(cursor.x(), cursor.y())) {
            tooltip = switch (tool.selectedGroup()) {
                case ROADS -> null;
                case DEMOLITION -> tool.removalPhase() == RemovalUiPhase.CONFIRM
                        ? Component.translatable("tooltip.roadbuilder.confirm_remove")
                        : Component.translatable("tooltip.roadbuilder.bulldozer");
                case LANE_CONNECTORS -> Component.translatable(
                        "tooltip.roadbuilder.lane_connector"
                );
            };
        }
        if (tooltip != null) {
            RoadGuiDrawing.tooltip(graphics,
                    client.font,
                    tooltip,
                    (int) cursor.x(),
                    (int) cursor.y()
            );
        }
    }

    private static CursorPosition cursor(Minecraft client) {
        Window window = client.getWindow();
        return new CursorPosition(
                client.mouseHandler.xpos()
                        * window.getGuiScaledWidth() / window.getScreenWidth(),
                client.mouseHandler.ypos()
                        * window.getGuiScaledHeight() / window.getScreenHeight()
        );
    }

    private record CursorPosition(double x, double y) {
    }
}
