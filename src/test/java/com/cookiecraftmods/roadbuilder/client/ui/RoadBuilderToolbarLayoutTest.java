package com.cookiecraftmods.roadbuilder.client.ui;

import com.cookiecraftmods.roadbuilder.client.tool.RoadBuilderTool;
import com.cookiecraftmods.roadbuilder.preset.RoadCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadBuilderToolbarLayoutTest {
    @Test
    void constructionRowsFollowReferenceOrder() {
        for (int width : new int[]{120, 400, 1920}) {
            var layout = RoadBuilderToolbarLayout.create(width, 240);
            assertTrue(layout.roadToolButton(RoadBuilderTool.REPLACE).bottom() <= layout.elevationDisplay().y());
            assertTrue(layout.elevationDisplay().bottom() <= layout.parallelDisplay().y());
            assertTrue(layout.parallelDisplay().bottom() <= layout.roadNodeSnappingButton().y());
            assertEquals(layout.presetPanel().x(), layout.panel().x());
            assertEquals(layout.presetPanel().width(), layout.panel().width());
            assertTrue(layout.toolModePanel().right() <= layout.panel().x());
            var buttons = java.util.List.of(layout.roadButton(), layout.removeButton(),
                    layout.groupButton(RoadBuilderToolGroup.LANE_CONNECTORS), layout.lanePathVisibilityButton(),
                    layout.crosswalkToolButton(), layout.roundaboutButton());
            for (int i = 0; i < buttons.size(); i++) {
                var button = buttons.get(i);
                assertEquals(layout.roadButton().y(), button.y());
                assertTrue(button.right() <= layout.panel().right());
                if (i > 0) assertTrue(buttons.get(i - 1).right() <= button.x());
            }
            assertTrue(layout.parallelLowerButton().right() <= layout.parallelDisplay().x());
            assertTrue(layout.parallelDisplay().right() <= layout.parallelRaiseButton().x());
            assertTrue(layout.parallelRaiseButton().right() <= layout.parallelToggleButton().x());
        }
    }

    @Test
    void rectangleUsesInclusiveExclusiveHitBounds() {
        UiRectangle rectangle = new UiRectangle(10, 20, 30, 40);

        assertTrue(rectangle.contains(10.0, 20.0));
        assertTrue(rectangle.contains(39.999, 59.999));
        assertFalse(rectangle.contains(40.0, 30.0));
        assertFalse(rectangle.contains(20.0, 60.0));
    }

}
