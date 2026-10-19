package com.cookiecraftmods.roadbuilder.client.ui;

import com.cookiecraftmods.roadbuilder.client.tool.RoadBuilderTool;
import com.cookiecraftmods.roadbuilder.preset.RoadCategory;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public record RoadBuilderToolbarLayout(
        UiRectangle statusPanel,
        UiRectangle contextPanel,
        UiRectangle toolModePanel,
        UiRectangle presetPanel,
        UiRectangle panel,
        UiRectangle lanePathVisibilityButton,
        UiRectangle crosswalkToolButton,
        UiRectangle elevationDisplay,
        UiRectangle elevationLowerButton,
        UiRectangle elevationRaiseButton,
        Map<RoadBuilderToolGroup, UiRectangle> groupButtons,
        UiRectangle primaryActionButton,
        UiRectangle presetButton,
        UiRectangle elevationButton,
        UiRectangle snapOptionButton,
        UiRectangle undoButton,
        UiRectangle redoButton,
        UiRectangle cancelButton
) {
    public static final int PRESET_SLOT_COUNT = 5;
    public static final int PRESET_PAGE_BULLET_COUNT = 3;

    private static final int PADDING = 4;
    private static final int GAP = 3;
    private static final int TAB_HEIGHT = 25;
    private static final int CONTEXT_HEIGHT = 52;
    private static final int STATUS_HEIGHT = 13;
    private static final int BOTTOM_MARGIN = 6;
    private static final int PREFERRED_WIDTH = 414;
    private static final int MAXIMUM_TOOL_MODE_WIDTH = 132;
    private static final int MINIMUM_TOOL_MODE_WIDTH = 46;
    private static final int UTILITY_BUTTON_COUNT = 3;
    private static final int ROAD_TOOL_BUTTON_COUNT = 5;

    public RoadBuilderToolbarLayout {
        groupButtons = Collections.unmodifiableMap(
                new EnumMap<>(groupButtons)
        );
    }

    public static RoadBuilderToolbarLayout create(int guiWidth, int guiHeight) {
        if (guiWidth <= 0 || guiHeight <= 0) {
            throw new IllegalArgumentException("GUI dimensions must be positive");
        }

        int overallWidth = Math.min(PREFERRED_WIDTH, Math.max(1, guiWidth - 8));
        int overallX = (guiWidth - overallWidth) / 2;
        int modeWidth = overallWidth >= MINIMUM_TOOL_MODE_WIDTH + 2
                ? Math.min(
                        MAXIMUM_TOOL_MODE_WIDTH,
                        Math.max(MINIMUM_TOOL_MODE_WIDTH, overallWidth * 32 / 100)
                )
                : 0;
        int shelfGap = modeWidth == 0 ? 0 : GAP;
        int mainX = overallX + modeWidth + shelfGap;
        int mainWidth = Math.max(1, overallWidth - modeWidth - shelfGap);

        int shellBottom = Math.max(1, guiHeight - BOTTOM_MARGIN);
        int panelY = Math.max(
                0,
                shellBottom - TAB_HEIGHT - GAP - CONTEXT_HEIGHT
        );
        int panelHeight = Math.min(
                TAB_HEIGHT,
                Math.max(1, shellBottom - panelY)
        );
        UiRectangle dock = new UiRectangle(
                mainX,
                panelY,
                mainWidth,
                panelHeight
        );
        UiRectangle modeUtilities = new UiRectangle(
                overallX,
                panelY,
                modeWidth,
                panelHeight
        );

        int contextY = dock.bottom() + GAP;
        int contextHeight = Math.min(
                CONTEXT_HEIGHT,
                Math.max(1, shellBottom - contextY)
        );
        UiRectangle context = new UiRectangle(
                overallX,
                contextY,
                overallWidth,
                contextHeight
        );
        UiRectangle mode = new UiRectangle(
                overallX,
                contextY,
                modeWidth,
                contextHeight
        );
        UiRectangle presets = new UiRectangle(
                mainX,
                contextY,
                mainWidth,
                contextHeight
        );
        int statusY = Math.max(0, panelY - STATUS_HEIGHT - 2);
        UiRectangle status = new UiRectangle(
                overallX,
                statusY,
                overallWidth,
                Math.min(STATUS_HEIGHT, panelY)
        );

        int compactGap = mainWidth < 260 ? 1 : GAP;
        int compactPadding = mainWidth < 180 ? 2 : PADDING;
        int buttonY = dock.y() + compactPadding;
        int buttonHeight = Math.max(1, dock.height() - compactPadding * 2);
        int buttonCount = RoadBuilderToolGroup.values().length
                + UTILITY_BUTTON_COUNT;
        int usableWidth = Math.max(
                buttonCount,
                mainWidth - compactPadding * 2
                        - compactGap * (buttonCount - 1)
        );
        int buttonWidth = Math.max(1, usableWidth / buttonCount);
        int distributedRemainder = Math.max(
                0,
                usableWidth - buttonWidth * buttonCount
        );

        int nextX = mainX + compactPadding;
        EnumMap<RoadBuilderToolGroup, UiRectangle> groups =
                new EnumMap<>(RoadBuilderToolGroup.class);
        for (RoadBuilderToolGroup group : RoadBuilderToolGroup.values()) {
            int width = buttonWidth + (distributedRemainder-- > 0 ? 1 : 0);
            groups.put(
                    group,
                    new UiRectangle(nextX, buttonY, width, buttonHeight)
            );
            nextX += width + compactGap;
        }

        UiRectangle undo = utilityButton(
                nextX,
                buttonY,
                buttonWidth + (distributedRemainder-- > 0 ? 1 : 0),
                buttonHeight
        );
        nextX = undo.right() + compactGap;
        UiRectangle redo = utilityButton(
                nextX,
                buttonY,
                buttonWidth + (distributedRemainder-- > 0 ? 1 : 0),
                buttonHeight
        );
        nextX = redo.right() + compactGap;
        UiRectangle cancel = utilityButton(
                nextX,
                buttonY,
                Math.max(1, dock.right() - compactPadding - nextX),
                buttonHeight
        );

        int modeUtilityPadding = modeWidth < 70 ? 2 : PADDING;
        int modeUtilityGap = modeWidth < 70 ? 1 : GAP;
        int modeUtilityY = modeUtilities.y() + modeUtilityPadding;
        int modeUtilityHeight = Math.max(
                1,
                modeUtilities.height() - modeUtilityPadding * 2
        );
        int modeUtilityAvailable = Math.max(
                3,
                modeUtilities.width() - modeUtilityPadding * 2
                        - modeUtilityGap * 2
        );
        int modeUtilityWidth = Math.max(1, modeUtilityAvailable / 3);
        int modeUtilityRemainder = Math.max(
                0,
                modeUtilityAvailable - modeUtilityWidth * 3
        );
        int modeUtilityX = modeUtilities.x() + modeUtilityPadding;
        UiRectangle lanePaths = new UiRectangle(
                modeUtilityX,
                modeUtilityY,
                modeUtilityWidth + (modeUtilityRemainder-- > 0 ? 1 : 0),
                modeUtilityHeight
        );
        modeUtilityX = lanePaths.right() + modeUtilityGap;
        UiRectangle crosswalks = new UiRectangle(
                modeUtilityX,
                modeUtilityY,
                modeUtilityWidth + (modeUtilityRemainder-- > 0 ? 1 : 0),
                modeUtilityHeight
        );
        modeUtilityX = crosswalks.right() + modeUtilityGap;
        int elevationGroupWidth = Math.max(
                1,
                modeUtilities.right() - modeUtilityPadding - modeUtilityX
        );
        int arrowWidth = Math.max(7, elevationGroupWidth / 3);
        arrowWidth = Math.min(arrowWidth, Math.max(1, elevationGroupWidth - 1));
        UiRectangle elevationValue = new UiRectangle(
                modeUtilityX,
                modeUtilityY,
                Math.max(1, elevationGroupWidth - arrowWidth),
                modeUtilityHeight
        );
        int arrowX = elevationValue.right();
        int lowerHeight = Math.max(1, modeUtilityHeight / 2);
        UiRectangle elevationLower = new UiRectangle(
                arrowX,
                modeUtilityY + lowerHeight,
                arrowWidth,
                Math.max(1, modeUtilityHeight - lowerHeight)
        );
        UiRectangle elevationRaise = new UiRectangle(
                arrowX,
                modeUtilityY,
                arrowWidth,
                lowerHeight
        );
        if (modeWidth == 0) {
            UiRectangle hidden = new UiRectangle(overallX, panelY, 0, 0);
            lanePaths = hidden;
            crosswalks = hidden;
            elevationValue = hidden;
            elevationLower = hidden;
            elevationRaise = hidden;
        }

        int contentX = context.x() + compactPadding;
        int contentY = context.y() + compactPadding;
        int contentHeight = Math.max(
                1,
                context.bottom() - compactPadding - contentY
        );
        int contentWidth = Math.max(4, context.width() - compactPadding * 2);
        int primaryWidth = contentWidth * 38 / 100;
        int presetWidth = contentWidth * 27 / 100;
        int optionWidth = Math.max(
                1,
                (contentWidth - primaryWidth - presetWidth - compactGap * 3) / 2
        );
        UiRectangle primary = new UiRectangle(
                contentX,
                contentY,
                primaryWidth,
                contentHeight
        );
        UiRectangle preset = new UiRectangle(
                primary.right() + compactGap,
                contentY,
                presetWidth,
                contentHeight
        );
        UiRectangle elevation = new UiRectangle(
                preset.right() + compactGap,
                contentY,
                optionWidth,
                contentHeight
        );
        UiRectangle snap = new UiRectangle(
                elevation.right() + compactGap,
                contentY,
                Math.max(
                        1,
                        context.right() - compactPadding
                                - elevation.right() - compactGap
                ),
                contentHeight
        );

        return new RoadBuilderToolbarLayout(
                status,
                context,
                mode,
                presets,
                dock,
                lanePaths,
                crosswalks,
                elevationValue,
                elevationLower,
                elevationRaise,
                groups,
                primary,
                preset,
                elevation,
                snap,
                undo,
                redo,
                cancel
        );
    }

    private static UiRectangle utilityButton(
            int x,
            int y,
            int width,
            int height
    ) {
        return new UiRectangle(x, y, Math.max(1, width), height);
    }

    public UiRectangle groupButton(RoadBuilderToolGroup group) {
        UiRectangle button = groupButtons.get(group);
        if (button == null) {
            throw new IllegalArgumentException("unknown tool group " + group);
        }
        return button;
    }

    public UiRectangle roadButton() {
        return groupButton(RoadBuilderToolGroup.ROADS);
    }

    public UiRectangle removeButton() {
        return groupButton(RoadBuilderToolGroup.DEMOLITION);
    }

    public UiRectangle roadToolButton(RoadBuilderTool tool) {
        int index = switch (tool) {
            case STRAIGHT_ROAD -> 0;
            case SIMPLE_CURVE -> 1;
            case COMPLEX_CURVE -> 2;
            case ROUNDABOUT -> 3;
            case REPLACE -> 4;
            default -> throw new IllegalArgumentException(
                    "not a road placement tool: " + tool
            );
        };
        int padding = toolModePanel.width() < 70 ? 3 : PADDING;
        int gap = toolModePanel.width() < 70 ? 1 : GAP;
        int startX = toolModePanel.x() + padding;
        int available = Math.max(
                ROAD_TOOL_BUTTON_COUNT,
                toolModePanel.width() - padding * 2
                        - gap * (ROAD_TOOL_BUTTON_COUNT - 1)
        );
        int baseWidth = available / ROAD_TOOL_BUTTON_COUNT;
        int remainder = available - baseWidth * ROAD_TOOL_BUTTON_COUNT;
        int x = startX + index * (baseWidth + gap)
                + Math.min(index, remainder);
        int width = baseWidth + (index < remainder ? 1 : 0);
        int availableHeight = Math.max(
                2,
                toolModePanel.height() - padding * 2 - gap
        );
        int snappingHeight = availableHeight / 2;
        int y = toolModePanel.y() + padding + snappingHeight + gap;
        return new UiRectangle(
                x,
                y,
                Math.max(1, width),
                Math.max(1, toolModePanel.bottom() - padding - y)
        );
    }

    public UiRectangle roadNodeSnappingButton() {
        return roadSnappingButton(0);
    }

    public UiRectangle roadGuidelineSnappingButton() {
        return roadSnappingButton(1);
    }

    public UiRectangle roadAngleSnappingButton() {
        return roadSnappingButton(2);
    }

    public UiRectangle parallelToggleButton() { return roadSnappingButton(3); }

    private UiRectangle roadSnappingButton(int index) {
        int padding = toolModePanel.width() < 70 ? 3 : PADDING;
        int gap = toolModePanel.width() < 70 ? 1 : GAP;
        int availableWidth = Math.max(
                4,
                toolModePanel.width() - padding * 2 - gap * 3
        );
        int baseWidth = availableWidth / 4;
        int remainder = availableWidth - baseWidth * 4;
        int availableHeight = Math.max(
                2,
                toolModePanel.height() - padding * 2 - gap
        );
        int height = availableHeight / 2;
        int x = toolModePanel.x() + padding
                + index * (baseWidth + gap)
                + Math.min(index, remainder);
        int width = baseWidth + (index < remainder ? 1 : 0);
        return new UiRectangle(
                x,
                toolModePanel.y() + padding,
                Math.max(1, width),
                Math.max(1, height)
        );
    }

    public UiRectangle presetCardButton(int index) {
        if (index < 0 || index >= PRESET_SLOT_COUNT) {
            throw new IllegalArgumentException(
                    "visible preset card index must be 0.."
                            + (PRESET_SLOT_COUNT - 1)
            );
        }
        int padding = presetPanel.width() < 120 ? 2 : PADDING;
        int gap = presetPanel.width() < 180 ? 1 : GAP;
        int tabsHeight = Math.min(14, Math.max(1, presetPanel.height() / 3));
        int innerWidth = Math.max(1, presetPanel.width() - padding * 2);
        int editorWidth = Math.min(26, Math.max(1, innerWidth / 7));
        int bulletWidth = Math.min(10, Math.max(1, innerWidth / 20));
        int cardsWidth = Math.max(
                PRESET_SLOT_COUNT,
                innerWidth - editorWidth - bulletWidth
                        - gap * (PRESET_SLOT_COUNT + 1)
        );
        int cardWidth = Math.max(1, cardsWidth / PRESET_SLOT_COUNT);
        int remainder = Math.max(
                0,
                cardsWidth - cardWidth * PRESET_SLOT_COUNT
        );
        int x = presetPanel.x() + padding
                + index * (cardWidth + gap)
                + Math.min(index, remainder);
        int width = cardWidth + (index < remainder ? 1 : 0);
        return new UiRectangle(
                x,
                presetPanel.y() + padding + tabsHeight + gap,
                width,
                Math.max(
                        1,
                        presetPanel.height() - padding * 2 - tabsHeight - gap
                )
        );
    }

    public UiRectangle presetEditorButton() {
        UiRectangle last = presetCardButton(PRESET_SLOT_COUNT - 1);
        int gap = presetPanel.width() < 180 ? 1 : GAP;
        int padding = presetPanel.width() < 120 ? 2 : PADDING;
        int innerWidth = Math.max(1, presetPanel.width() - padding * 2);
        int preferredWidth = Math.min(26, Math.max(1, innerWidth / 7));
        int bulletWidth = Math.min(10, Math.max(1, innerWidth / 20));
        int x = last.right() + gap;
        return new UiRectangle(
                x,
                last.y(),
                Math.max(
                        1,
                        Math.min(
                                preferredWidth,
                                presetPanel.right() - padding - x
                                        - gap - bulletWidth
                        )
                ),
                last.height()
        );
    }

    public UiRectangle presetCategoryButton(RoadCategory category) {
        int index = category.ordinal();
        int count = RoadCategory.values().length;
        int padding = presetPanel.width() < 120 ? 2 : PADDING;
        int gap = presetPanel.width() < 180 ? 1 : GAP;
        int available = Math.max(count, presetPanel.width() - padding * 2
                - gap * (count - 1));
        int baseWidth = Math.max(1, available / count);
        int remainder = Math.max(0, available - baseWidth * count);
        int x = presetPanel.x() + padding
                + index * (baseWidth + gap)
                + Math.min(index, remainder);
        int width = baseWidth + (index < remainder ? 1 : 0);
        return new UiRectangle(
                x,
                presetPanel.y() + padding,
                width,
                Math.min(14, Math.max(1, presetPanel.height() / 3))
        );
    }

    public UiRectangle presetPageBullet(int index) {
        if (index < 0 || index >= PRESET_PAGE_BULLET_COUNT) {
            throw new IllegalArgumentException("page bullet is out of range");
        }
        UiRectangle editor = presetEditorButton();
        int gap = presetPanel.width() < 180 ? 1 : GAP;
        int size = Math.min(10, Math.max(1, editor.height() / 3));
        int totalHeight = size * PRESET_PAGE_BULLET_COUNT;
        int y = editor.y() + Math.max(0, (editor.height() - totalHeight) / 2)
                + index * size;
        return new UiRectangle(
                editor.right() + gap,
                y,
                Math.max(1, presetPanel.right() - editor.right() - gap
                        - (presetPanel.width() < 120 ? 2 : PADDING)),
                size
        );
    }

    public boolean owns(double cursorX, double cursorY) {
        return panel.contains(cursorX, cursorY)
                || lanePathVisibilityButton.contains(cursorX, cursorY)
                || crosswalkToolButton.contains(cursorX, cursorY)
                || elevationDisplay.contains(cursorX, cursorY)
                || elevationLowerButton.contains(cursorX, cursorY)
                || elevationRaiseButton.contains(cursorX, cursorY)
                || contextPanel.contains(cursorX, cursorY)
                || statusPanel.contains(cursorX, cursorY);
    }
}
