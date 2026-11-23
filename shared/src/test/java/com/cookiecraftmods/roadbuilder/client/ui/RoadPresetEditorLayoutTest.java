package com.cookiecraftmods.roadbuilder.client.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RoadPresetEditorLayoutTest {
    @Test
    void previewAndInspectorStayUsableAcrossGuiScales() {
        for (int width : List.of(320, 427, 480, 560, 640, 854, 960, 1280, 1920)) {
            for (int height : List.of(240, 270, 360, 480, 540, 720, 1080)) {
                RoadPresetEditorLayout layout = RoadPresetEditorLayout.create(width, height);
                assertContained(new UiRectangle(0, 0, width, height), layout.frame());
                List<UiRectangle> regions = List.of(layout.header(), layout.navigation(),
                        layout.workspacePanel(), layout.propertiesPanel(), layout.statusBar());
                regions.forEach(region -> assertContained(layout.frame(), region));
                assertDisjoint(regions);
                assertTrue(layout.workspacePanel().width() >= 120);
                assertTrue(layout.propertiesViewport().width() >= 130);
                assertTrue(layout.propertiesViewport().height() >= 76);
                assertTrue(layout.catalogViewport().height() >= 53);
                assertContained(layout.workspacePanel(), layout.preview());
                assertContained(layout.preview(), RoadPresetEditorRenderer.roadBounds(layout.preview()));
                assertContained(layout.propertiesPanel(), layout.propertiesViewport());
                assertContained(layout.propertiesPanel(), layout.catalogViewport());
                assertContained(layout.propertiesPanel(), layout.catalogSearch());
                assertDisjoint(List.of(layout.panelHeading(), layout.catalogSearch(), layout.catalogViewport()));
            }
        }
    }

    @Test
    void navigationAndFooterActionsHaveRoomAtMinimumSize() {
        RoadPresetEditorLayout layout = RoadPresetEditorLayout.create(320, 240);
        List<UiRectangle> tabs = RoadPresetEditorUi.columns(layout.navigation().x(),
                layout.navigation().y(), layout.navigation().width(), layout.navigation().height(), 5, 3);
        assertTrue(tabs.stream().allMatch(tab -> tab.width() >= 56 && tab.height() >= 20));
        assertTrue(layout.statusBar().height() >= 40);
        assertFalse(layout.wide());
        assertTrue(RoadPresetEditorLayout.create(960, 540).wide());
    }

    @Test
    void rejectsInvalidDimensions() {
        assertThrows(IllegalArgumentException.class, () -> RoadPresetEditorLayout.create(0, 240));
        assertThrows(IllegalArgumentException.class, () -> RoadPresetEditorLayout.create(320, -1));
    }

    private static void assertContained(UiRectangle parent, UiRectangle child) {
        assertTrue(child.x() >= parent.x() && child.y() >= parent.y()
                && child.right() <= parent.right() && child.bottom() <= parent.bottom(),
                () -> child + " is outside " + parent);
    }

    private static void assertDisjoint(List<UiRectangle> regions) {
        for (int first = 0; first < regions.size(); first++) {
            for (int second = first + 1; second < regions.size(); second++) {
                UiRectangle a = regions.get(first);
                UiRectangle b = regions.get(second);
                assertTrue(a.right() <= b.x() || b.right() <= a.x()
                        || a.bottom() <= b.y() || b.bottom() <= a.y(),
                        () -> a + " overlaps " + b);
            }
        }
    }
}
