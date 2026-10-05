package com.cookiecraftmods.roadbuilder.client.ui;

import java.util.Objects;

public record RoadPresetEditorLayout(
        UiRectangle frame,
        UiRectangle header,
        UiRectangle propertiesPanel,
        UiRectangle workspacePanel,
        UiRectangle libraryPanel,
        UiRectangle statusBar,
        UiRectangle preview,
        UiRectangle catalogTabs,
        UiRectangle catalogSearch,
        UiRectangle catalogViewport,
        boolean wide
) {
    public static final int WIDE_BREAKPOINT = 680;
    private static final int MAX_FRAME_WIDTH = 920;
    private static final int SCREEN_MARGIN = 8;
    private static final int FRAME_INSET = 6;
    private static final int PANEL_INSET = 6;
    private static final int GAP = 6;
    private static final int CONTROL_GAP = 4;
    private static final int HEADER_HEIGHT = 34;
    private static final int STATUS_HEIGHT = 24;
    private static final int MINIMUM_PROPERTIES_WIDTH = 100;
    private static final int MINIMUM_WIDE_PROPERTIES_WIDTH = 140;
    private static final int MAXIMUM_PROPERTIES_WIDTH = 210;
    private static final int MINIMUM_LIBRARY_WIDTH = 170;
    private static final int MAXIMUM_LIBRARY_WIDTH = 260;
    private static final int MINIMUM_STACKED_LIBRARY_HEIGHT = 74;

    public RoadPresetEditorLayout {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(propertiesPanel, "propertiesPanel");
        Objects.requireNonNull(workspacePanel, "workspacePanel");
        Objects.requireNonNull(libraryPanel, "libraryPanel");
        Objects.requireNonNull(statusBar, "statusBar");
        Objects.requireNonNull(preview, "preview");
        Objects.requireNonNull(catalogTabs, "catalogTabs");
        Objects.requireNonNull(catalogSearch, "catalogSearch");
        Objects.requireNonNull(catalogViewport, "catalogViewport");
    }

    public static RoadPresetEditorLayout create(int guiWidth, int guiHeight) {
        if (guiWidth <= 0 || guiHeight <= 0) {
            throw new IllegalArgumentException("GUI dimensions must be positive");
        }

        int frameWidth = Math.min(
                MAX_FRAME_WIDTH,
                Math.max(1, guiWidth - SCREEN_MARGIN * 2)
        );
        int frameHeight = Math.max(1, guiHeight - SCREEN_MARGIN * 2);
        UiRectangle frame = new UiRectangle(
                (guiWidth - frameWidth) / 2,
                (guiHeight - frameHeight) / 2,
                frameWidth,
                frameHeight
        );
        UiRectangle innerFrame = inset(frame, FRAME_INSET);

        int sectionGap = Math.min(GAP, innerFrame.height() / 4);
        int sectionHeight = Math.max(
                0,
                innerFrame.height() - sectionGap * 2
        );
        int headerHeight = Math.min(HEADER_HEIGHT, sectionHeight / 2);
        int statusHeight = Math.min(
                STATUS_HEIGHT,
                sectionHeight - headerHeight
        );
        int bodyHeight = sectionHeight - headerHeight - statusHeight;

        UiRectangle header = new UiRectangle(
                innerFrame.x(),
                innerFrame.y(),
                innerFrame.width(),
                headerHeight
        );
        UiRectangle body = new UiRectangle(
                innerFrame.x(),
                header.bottom() + sectionGap,
                innerFrame.width(),
                bodyHeight
        );
        UiRectangle statusBar = new UiRectangle(
                innerFrame.x(),
                body.bottom() + sectionGap,
                innerFrame.width(),
                statusHeight
        );

        boolean wide = guiWidth >= WIDE_BREAKPOINT;
        PanelLayout panels = wide
                ? createWidePanels(body)
                : createNarrowPanels(body);
        UiRectangle preview = inset(panels.workspace(), PANEL_INSET);
        CatalogLayout catalog = createCatalogLayout(panels.library());

        return new RoadPresetEditorLayout(
                frame,
                header,
                panels.properties(),
                panels.workspace(),
                panels.library(),
                statusBar,
                preview,
                catalog.tabs(),
                catalog.search(),
                catalog.viewport(),
                wide
        );
    }

    private static PanelLayout createWidePanels(UiRectangle body) {
        int columnGap = Math.min(GAP, body.width() / 4);
        int usableWidth = Math.max(0, body.width() - columnGap * 2);
        int propertiesWidth = Math.min(
                usableWidth,
                clamp(
                        usableWidth * 22 / 100,
                        MINIMUM_WIDE_PROPERTIES_WIDTH,
                        MAXIMUM_PROPERTIES_WIDTH
                )
        );
        int remainingWidth = usableWidth - propertiesWidth;
        int libraryWidth = Math.min(
                remainingWidth,
                clamp(
                        usableWidth * 27 / 100,
                        MINIMUM_LIBRARY_WIDTH,
                        MAXIMUM_LIBRARY_WIDTH
                )
        );
        int workspaceWidth = remainingWidth - libraryWidth;

        UiRectangle properties = new UiRectangle(
                body.x(),
                body.y(),
                propertiesWidth,
                body.height()
        );
        UiRectangle workspace = new UiRectangle(
                properties.right() + columnGap,
                body.y(),
                workspaceWidth,
                body.height()
        );
        UiRectangle library = new UiRectangle(
                workspace.right() + columnGap,
                body.y(),
                libraryWidth,
                body.height()
        );
        return new PanelLayout(properties, workspace, library);
    }

    private static PanelLayout createNarrowPanels(UiRectangle body) {
        int rowGap = Math.min(GAP, body.height() / 2);
        int usableHeight = Math.max(0, body.height() - rowGap);
        int minimumLibraryHeight = Math.min(
                MINIMUM_STACKED_LIBRARY_HEIGHT,
                usableHeight
        );
        int libraryHeight = Math.max(
                minimumLibraryHeight,
                usableHeight * 45 / 100
        );
        int topHeight = usableHeight - libraryHeight;

        int columnGap = Math.min(GAP, body.width() / 3);
        int usableWidth = Math.max(0, body.width() - columnGap);
        int propertiesWidth = Math.min(
                usableWidth,
                clamp(
                        usableWidth / 3,
                        MINIMUM_PROPERTIES_WIDTH,
                        MAXIMUM_PROPERTIES_WIDTH
                )
        );
        int workspaceWidth = usableWidth - propertiesWidth;

        UiRectangle properties = new UiRectangle(
                body.x(),
                body.y(),
                propertiesWidth,
                topHeight
        );
        UiRectangle workspace = new UiRectangle(
                properties.right() + columnGap,
                body.y(),
                workspaceWidth,
                topHeight
        );
        UiRectangle library = new UiRectangle(
                body.x(),
                body.y() + topHeight + rowGap,
                body.width(),
                libraryHeight
        );
        return new PanelLayout(properties, workspace, library);
    }

    private static CatalogLayout createCatalogLayout(UiRectangle panel) {
        UiRectangle content = inset(panel, PANEL_INSET);
        int gap = Math.min(CONTROL_GAP, content.height() / 3);
        int usableHeight = Math.max(0, content.height() - gap * 2);
        int tabsHeight = Math.min(20, usableHeight / 3);
        int searchHeight = Math.min(
                20,
                (usableHeight - tabsHeight) / 2
        );
        int viewportHeight = usableHeight - tabsHeight - searchHeight;

        UiRectangle tabs = new UiRectangle(
                content.x(),
                content.y(),
                content.width(),
                tabsHeight
        );
        UiRectangle search = new UiRectangle(
                content.x(),
                tabs.bottom() + gap,
                content.width(),
                searchHeight
        );
        UiRectangle viewport = new UiRectangle(
                content.x(),
                search.bottom() + gap,
                content.width(),
                viewportHeight
        );
        return new CatalogLayout(tabs, search, viewport);
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

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private record PanelLayout(
            UiRectangle properties,
            UiRectangle workspace,
            UiRectangle library
    ) {
    }

    private record CatalogLayout(
            UiRectangle tabs,
            UiRectangle search,
            UiRectangle viewport
    ) {
    }
}
