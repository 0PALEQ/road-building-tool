package com.cookiecraftmods.roadbuilder.client.ui;

/** Geometry shared by rendering, controls and hit testing, in scaled GUI pixels. */
public record RoadPresetEditorLayout(
        UiRectangle frame,
        UiRectangle header,
        UiRectangle navigation,
        UiRectangle workspacePanel,
        UiRectangle propertiesPanel,
        UiRectangle statusBar,
        UiRectangle preview,
        UiRectangle panelHeading,
        UiRectangle propertiesViewport,
        UiRectangle catalogSearch,
        UiRectangle catalogViewport,
        boolean wide
) {
    public static final int WIDE_BREAKPOINT = 560;
    private static final int MAX_FRAME_WIDTH = 1120;

    public static RoadPresetEditorLayout create(int guiWidth, int guiHeight) {
        if (guiWidth <= 0 || guiHeight <= 0) {
            throw new IllegalArgumentException("GUI dimensions must be positive");
        }
        int margin = Math.min(8, Math.min(guiWidth, guiHeight) / 30);
        int frameWidth = Math.min(MAX_FRAME_WIDTH, guiWidth - margin * 2);
        UiRectangle frame = new UiRectangle(
                (guiWidth - frameWidth) / 2, margin,
                frameWidth, guiHeight - margin * 2
        );
        UiRectangle inner = RoadPresetEditorUi.inset(frame, 5);
        int gap = Math.min(4, inner.height() / 8);
        int headerHeight = Math.min(28, inner.height() / 5);
        int navHeight = Math.min(22, inner.height() / 6);
        int footerHeight = Math.min(40, inner.height() / 4);
        UiRectangle header = new UiRectangle(
                inner.x(), inner.y(), inner.width(), headerHeight
        );
        UiRectangle navigation = new UiRectangle(
                inner.x(), header.bottom() + gap, inner.width(), navHeight
        );
        int bodyHeight = Math.max(0, inner.height() - headerHeight
                - navHeight - footerHeight - gap * 3);
        int bodyY = navigation.bottom() + gap;
        int columnGap = Math.min(6, inner.width() / 4);
        int usableWidth = inner.width() - columnGap;
        boolean wide = guiWidth >= WIDE_BREAKPOINT;
        int inspectorWidth = wide
                ? Math.min(288, Math.max(224, usableWidth * 36 / 100))
                : usableWidth * 56 / 100;
        inspectorWidth = Math.min(usableWidth, inspectorWidth);
        UiRectangle workspace = new UiRectangle(
                inner.x(), bodyY, usableWidth - inspectorWidth, bodyHeight
        );
        UiRectangle properties = new UiRectangle(
                workspace.right() + columnGap, bodyY, inspectorWidth, bodyHeight
        );
        UiRectangle content = RoadPresetEditorUi.inset(properties, 6);
        int headingHeight = Math.min(20, content.height());
        UiRectangle heading = new UiRectangle(
                content.x(), content.y(), content.width(), headingHeight
        );
        int contentY = Math.min(content.bottom(), heading.bottom() + 4);
        UiRectangle viewport = new UiRectangle(
                content.x(), contentY, Math.max(0, content.width() - 18),
                content.bottom() - contentY
        );
        UiRectangle search = new UiRectangle(
                viewport.x(), viewport.y(), content.width(),
                Math.min(18, viewport.height())
        );
        int catalogY = Math.min(content.bottom(), search.bottom() + 5);
        UiRectangle catalog = new UiRectangle(
                viewport.x(), catalogY, viewport.width(),
                content.bottom() - catalogY
        );
        return new RoadPresetEditorLayout(
                frame, header, navigation, workspace, properties,
                new UiRectangle(inner.x(), bodyY + bodyHeight + gap,
                        inner.width(), footerHeight),
                RoadPresetEditorUi.inset(workspace, 6),
                heading, viewport, search, catalog, wide
        );
    }
}
