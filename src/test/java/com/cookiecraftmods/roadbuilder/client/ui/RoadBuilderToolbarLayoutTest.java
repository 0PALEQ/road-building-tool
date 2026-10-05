package com.cookiecraftmods.roadbuilder.client.ui;

import com.cookiecraftmods.roadbuilder.client.tool.RoadBuilderTool;
import com.cookiecraftmods.roadbuilder.preset.RoadCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadBuilderToolbarLayoutTest {
    @Test
    void centersPreferredLayoutAndKeepsButtonsDisjoint() {
        RoadBuilderToolbarLayout layout = RoadBuilderToolbarLayout.create(400, 240);

        assertEquals(new UiRectangle(132, 154, 264, 25), layout.panel());
        assertEquals(new UiRectangle(4, 182, 392, 52), layout.contextPanel());
        assertEquals(new UiRectangle(4, 182, 125, 52), layout.toolModePanel());
        assertEquals(new UiRectangle(132, 182, 264, 52), layout.presetPanel());
        assertEquals(new UiRectangle(8, 158, 37, 17),
                layout.lanePathVisibilityButton());
        assertEquals(new UiRectangle(48, 158, 37, 17),
                layout.crosswalkToolButton());
        assertEquals(new UiRectangle(88, 158, 25, 17),
                layout.elevationDisplay());
        assertEquals(new UiRectangle(113, 166, 12, 9),
                layout.elevationLowerButton());
        assertEquals(new UiRectangle(113, 158, 12, 8),
                layout.elevationRaiseButton());
        assertContainedAndOrdered(layout);
    }

    @Test
    void contractsToNarrowGuiWithoutOverflowing() {
        RoadBuilderToolbarLayout layout = RoadBuilderToolbarLayout.create(120, 80);

        assertEquals(new UiRectangle(53, 0, 63, 25), layout.panel());
        assertEquals(new UiRectangle(4, 28, 112, 46), layout.contextPanel());
        assertContainedAndOrdered(layout);
        assertEquals(layout.panel().right() - 2, layout.cancelButton().right());
    }

    @Test
    void rectangleUsesInclusiveExclusiveHitBounds() {
        UiRectangle rectangle = new UiRectangle(10, 20, 30, 40);

        assertTrue(rectangle.contains(10.0, 20.0));
        assertTrue(rectangle.contains(39.999, 59.999));
        assertFalse(rectangle.contains(40.0, 30.0));
        assertFalse(rectangle.contains(20.0, 60.0));
    }

    private static void assertContainedAndOrdered(RoadBuilderToolbarLayout layout) {
        UiRectangle previous = null;
        for (RoadBuilderToolGroup group : RoadBuilderToolGroup.values()) {
            UiRectangle button = layout.groupButton(group);
            assertTrue(button.x() >= layout.panel().x());
            if (previous != null) {
                assertTrue(previous.right() <= button.x());
            }
            previous = button;
        }
        assertTrue(previous.right() <= layout.undoButton().x());
        assertTrue(layout.undoButton().right() <= layout.redoButton().x());
        assertTrue(layout.redoButton().right() <= layout.cancelButton().x());
        assertTrue(layout.cancelButton().right() <= layout.panel().right());
        assertTrue(layout.roadButton().y() >= layout.panel().y());
        assertTrue(layout.roadButton().bottom() <= layout.panel().bottom());
        assertTrue(layout.panel().bottom() <= layout.contextPanel().y());
        assertTrue(layout.statusPanel().bottom() <= layout.panel().y());
        assertTrue(layout.lanePathVisibilityButton().x()
                >= layout.contextPanel().x());
        assertTrue(layout.lanePathVisibilityButton().right()
                <= layout.crosswalkToolButton().x());
        assertTrue(layout.crosswalkToolButton().right()
                <= layout.elevationDisplay().x());
        assertTrue(layout.elevationDisplay().right()
                <= layout.elevationRaiseButton().x());
        assertEquals(layout.elevationRaiseButton().x(),
                layout.elevationLowerButton().x());
        assertEquals(layout.elevationRaiseButton().bottom(),
                layout.elevationLowerButton().y());
        assertTrue(layout.elevationLowerButton().right()
                <= layout.panel().x());
        assertTrue(layout.toolModePanel().x() >= layout.contextPanel().x());
        assertTrue(layout.toolModePanel().right() <= layout.presetPanel().x());
        assertTrue(layout.presetPanel().right() <= layout.contextPanel().right());
        UiRectangle straight = layout.roadToolButton(
                RoadBuilderTool.STRAIGHT_ROAD
        );
        UiRectangle simple = layout.roadToolButton(
                RoadBuilderTool.SIMPLE_CURVE
        );
        UiRectangle complex = layout.roadToolButton(
                RoadBuilderTool.COMPLEX_CURVE
        );
        UiRectangle roundabout = layout.roadToolButton(
                RoadBuilderTool.ROUNDABOUT
        );
        UiRectangle replace = layout.roadToolButton(
                RoadBuilderTool.REPLACE
        );
        assertTrue(straight.x() >= layout.toolModePanel().x());
        assertTrue(straight.right() <= simple.x());
        assertTrue(simple.right() <= complex.x());
        assertTrue(complex.right() <= roundabout.x());
        assertTrue(roundabout.right() <= replace.x());
        assertTrue(replace.right() <= layout.toolModePanel().right());
        UiRectangle endpointSnap = layout.roadNodeSnappingButton();
        UiRectangle guidelineSnap = layout.roadGuidelineSnappingButton();
        UiRectangle angleSnap = layout.roadAngleSnappingButton();
        assertTrue(endpointSnap.x() >= layout.toolModePanel().x());
        assertTrue(endpointSnap.right() <= guidelineSnap.x());
        assertTrue(guidelineSnap.right() <= angleSnap.x());
        assertTrue(angleSnap.right() <= layout.toolModePanel().right());
        assertTrue(endpointSnap.bottom() <= straight.y());
        assertTrue(guidelineSnap.bottom() <= straight.y());
        assertTrue(angleSnap.bottom() <= straight.y());

        UiRectangle previousPreset = null;
        for (int index = 0;
             index < RoadBuilderToolbarLayout.PRESET_SLOT_COUNT;
             index++) {
            UiRectangle preset = layout.presetCardButton(index);
            assertTrue(preset.x() >= layout.presetPanel().x());
            if (previousPreset != null) {
                assertTrue(previousPreset.right() <= preset.x());
            }
            previousPreset = preset;
        }
        assertTrue(previousPreset.right() <= layout.presetEditorButton().x());
        assertTrue(layout.presetEditorButton().right()
                <= layout.presetPanel().right());
        assertTrue(layout.presetEditorButton().right()
                <= layout.presetPageBullet(0).x());
        UiRectangle previousCategory = null;
        for (RoadCategory category : RoadCategory.values()) {
            UiRectangle tab = layout.presetCategoryButton(category);
            if (previousCategory != null) {
                assertTrue(previousCategory.right() <= tab.x());
            }
            previousCategory = tab;
        }
    }
}
