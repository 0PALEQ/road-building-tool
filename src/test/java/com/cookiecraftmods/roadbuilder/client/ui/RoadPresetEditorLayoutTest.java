package com.cookiecraftmods.roadbuilder.client.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadPresetEditorLayoutTest {
    @Test
    void laysOutThreeColumnsOnWideGui() {
        RoadPresetEditorLayout layout = RoadPresetEditorLayout.create(960, 540);

        assertTrue(layout.wide());
        assertEquals(new UiRectangle(20, 8, 920, 524), layout.frame());
        assertEquals(layout.propertiesPanel().y(), layout.workspacePanel().y());
        assertEquals(layout.workspacePanel().y(), layout.libraryPanel().y());
        assertEquals(
                layout.propertiesPanel().height(),
                layout.workspacePanel().height()
        );
        assertEquals(
                layout.workspacePanel().height(),
                layout.libraryPanel().height()
        );
        assertTrue(
                layout.propertiesPanel().right()
                        < layout.workspacePanel().x()
        );
        assertTrue(
                layout.workspacePanel().right()
                        < layout.libraryPanel().x()
        );
        assertEquals(
                layout.workspacePanel().height() - 12,
                layout.preview().height()
        );
        assertLayoutIsValid(layout, 960, 540);
    }

    @Test
    void stacksLibraryBelowSplitWorkspaceOnNarrowGui() {
        RoadPresetEditorLayout layout = RoadPresetEditorLayout.create(640, 360);

        assertFalse(layout.wide());
        assertEquals(new UiRectangle(8, 8, 624, 344), layout.frame());
        assertEquals(layout.propertiesPanel().y(), layout.workspacePanel().y());
        assertEquals(
                layout.propertiesPanel().height(),
                layout.workspacePanel().height()
        );
        assertTrue(
                layout.propertiesPanel().right()
                        < layout.workspacePanel().x()
        );
        assertTrue(
                layout.workspacePanel().bottom()
                        < layout.libraryPanel().y()
        );
        assertEquals(layout.propertiesPanel().x(), layout.libraryPanel().x());
        assertEquals(layout.libraryPanel().right(), layout.frame().right() - 6);
        assertEquals(
                layout.workspacePanel().height() - 12,
                layout.preview().height()
        );
        assertLayoutIsValid(layout, 640, 360);
    }

    @Test
    void keepsEveryRegionUsableAtMinimumSupportedGuiSize() {
        RoadPresetEditorLayout layout = RoadPresetEditorLayout.create(320, 240);

        assertFalse(layout.wide());
        assertEquals(new UiRectangle(8, 8, 304, 224), layout.frame());
        for (UiRectangle rectangle : allRectangles(layout)) {
            assertTrue(rectangle.width() >= 0);
            assertTrue(rectangle.height() >= 0);
        }
        assertTrue(layout.propertiesPanel().width() > 0);
        assertTrue(layout.workspacePanel().width() > 0);
        assertTrue(layout.libraryPanel().height() > 0);
        assertTrue(layout.preview().height() > 0);
        assertTrue(layout.catalogTabs().height() > 0);
        assertTrue(layout.catalogSearch().height() > 0);
        assertTrue(layout.catalogViewport().height() > 0);
        assertLayoutIsValid(layout, 320, 240);
    }

    private static void assertLayoutIsValid(
            RoadPresetEditorLayout layout,
            int guiWidth,
            int guiHeight
    ) {
        assertEquals((guiWidth - layout.frame().width()) / 2, layout.frame().x());
        assertEquals(
                (guiHeight - layout.frame().height()) / 2,
                layout.frame().y()
        );
        assertTrue(layout.frame().width() <= 920);
        assertContained(new UiRectangle(0, 0, guiWidth, guiHeight), layout.frame());

        List<UiRectangle> frameSections = List.of(
                layout.header(),
                layout.propertiesPanel(),
                layout.workspacePanel(),
                layout.libraryPanel(),
                layout.statusBar()
        );
        for (UiRectangle section : frameSections) {
            assertContained(layout.frame(), section);
        }
        assertPairwiseDisjoint(frameSections);

        assertEquals(inset(layout.workspacePanel(), 6), layout.preview());

        assertContained(layout.libraryPanel(), layout.catalogTabs());
        assertContained(layout.libraryPanel(), layout.catalogSearch());
        assertContained(layout.libraryPanel(), layout.catalogViewport());
        assertPairwiseDisjoint(List.of(
                layout.catalogTabs(),
                layout.catalogSearch(),
                layout.catalogViewport()
        ));
    }

    private static List<UiRectangle> allRectangles(
            RoadPresetEditorLayout layout
    ) {
        return List.of(
                layout.frame(),
                layout.header(),
                layout.propertiesPanel(),
                layout.workspacePanel(),
                layout.libraryPanel(),
                layout.statusBar(),
                layout.preview(),
                layout.catalogTabs(),
                layout.catalogSearch(),
                layout.catalogViewport()
        );
    }

    private static void assertContained(
            UiRectangle container,
            UiRectangle child
    ) {
        assertTrue(child.x() >= container.x());
        assertTrue(child.y() >= container.y());
        assertTrue(child.right() <= container.right());
        assertTrue(child.bottom() <= container.bottom());
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

    private static void assertPairwiseDisjoint(List<UiRectangle> rectangles) {
        for (int first = 0; first < rectangles.size(); first++) {
            for (int second = first + 1; second < rectangles.size(); second++) {
                assertTrue(disjoint(rectangles.get(first), rectangles.get(second)));
            }
        }
    }

    private static boolean disjoint(UiRectangle first, UiRectangle second) {
        return first.right() <= second.x()
                || second.right() <= first.x()
                || first.bottom() <= second.y()
                || second.bottom() <= first.y();
    }
}
