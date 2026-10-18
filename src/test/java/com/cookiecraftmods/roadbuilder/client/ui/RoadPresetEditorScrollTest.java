package com.cookiecraftmods.roadbuilder.client.ui;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RoadPresetEditorScrollTest {
    @Test
    void everyCatalogCardIsReachableAtTheSmallestGuiScale() {
        int viewport = RoadPresetEditorLayout.create(320, 240).catalogViewport().height();
        RoadPresetEditorScroll scroll = new RoadPresetEditorScroll();
        scroll.resize(38 * 32 - 4, viewport);
        Set<Integer> reachable = new HashSet<>();
        do {
            for (int card = 0; card < 32; card++) {
                int top = card * 38 - scroll.offset();
                if (top >= 0 && top + 34 <= viewport) {
                    reachable.add(card);
                }
            }
        } while (scroll.move(RoadPresetEditorScroll.STEP));
        assertEquals(32, reachable.size());
    }

    @Test
    void resizingOrFilteringClampsScrollAndCanRevealKeyboardSelection() {
        RoadPresetEditorScroll scroll = new RoadPresetEditorScroll();
        scroll.resize(700, 100);
        scroll.move(600);
        scroll.resize(200, 140);
        assertEquals(60, scroll.offset());
        scroll.reveal(0, 20, 140);
        assertEquals(0, scroll.offset());
        scroll.reveal(180, 20, 140);
        assertEquals(60, scroll.offset());
        scroll.resize(40, 140);
        assertEquals(0, scroll.offset());
        assertFalse(scroll.move(24));
    }
}
