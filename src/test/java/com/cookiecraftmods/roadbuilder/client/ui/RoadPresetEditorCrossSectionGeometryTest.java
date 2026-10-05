package com.cookiecraftmods.roadbuilder.client.ui;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadPresetEditorCrossSectionGeometryTest {
    @Test
    void fillsStripExactlyInComponentOrder() {
        RoadPresetEditorCrossSectionGeometry geometry =
                RoadPresetEditorCrossSectionGeometry.create(
                        new UiRectangle(10, 20, 12, 40),
                        List.of(2, 1, 3)
                );

        assertEquals(List.of(
                new UiRectangle(10, 20, 4, 40),
                new UiRectangle(14, 20, 2, 40),
                new UiRectangle(16, 20, 6, 40)
        ), geometry.componentRectangles());
        assertEquals(10, geometry.componentRectangle(0).x());
        assertEquals(22, geometry.componentRectangle(2).right());
    }

    @Test
    void assignsFractionalPixelsDeterministically() {
        RoadPresetEditorCrossSectionGeometry geometry =
                RoadPresetEditorCrossSectionGeometry.create(
                        new UiRectangle(0, 0, 10, 5),
                        List.of(1, 1, 1)
                );

        assertEquals(List.of(4, 3, 3), geometry.componentRectangles()
                .stream()
                .map(UiRectangle::width)
                .toList());
    }

    @Test
    void hitTestingUsesInclusiveExclusiveComponentEdges() {
        RoadPresetEditorCrossSectionGeometry geometry =
                RoadPresetEditorCrossSectionGeometry.create(
                        new UiRectangle(10, 20, 12, 40),
                        List.of(2, 1, 3)
                );

        assertEquals(OptionalInt.of(0), geometry.componentIndexAt(10, 20));
        assertEquals(OptionalInt.of(1), geometry.componentIndexAt(14, 30));
        assertEquals(OptionalInt.of(2), geometry.componentIndexAt(21.99, 59.99));
        assertEquals(OptionalInt.empty(), geometry.componentIndexAt(22, 30));
        assertEquals(OptionalInt.empty(), geometry.componentIndexAt(12, 60));
    }

    @Test
    void insertionHitTestingChoosesBeforeAndAfterHalves() {
        RoadPresetEditorCrossSectionGeometry geometry =
                RoadPresetEditorCrossSectionGeometry.create(
                        new UiRectangle(10, 20, 12, 40),
                        List.of(2, 1, 3)
                );

        assertEquals(OptionalInt.of(0), geometry.insertionIndexAt(10, 30));
        assertEquals(OptionalInt.of(0), geometry.insertionIndexAt(11, 30));
        assertEquals(OptionalInt.of(1), geometry.insertionIndexAt(12, 30));
        assertEquals(OptionalInt.of(1), geometry.insertionIndexAt(14, 30));
        assertEquals(OptionalInt.of(2), geometry.insertionIndexAt(15, 30));
        assertEquals(OptionalInt.of(3), geometry.insertionIndexAt(22, 30));
        assertEquals(OptionalInt.empty(), geometry.insertionIndexAt(22.01, 30));
        assertEquals(OptionalInt.empty(), geometry.insertionIndexAt(12, 60));
    }

    @Test
    void exposesExactCaretCoordinates() {
        RoadPresetEditorCrossSectionGeometry geometry =
                RoadPresetEditorCrossSectionGeometry.create(
                        new UiRectangle(10, 20, 12, 40),
                        List.of(2, 1, 3)
                );

        assertEquals(10, geometry.caretX(0));
        assertEquals(14, geometry.caretX(1));
        assertEquals(16, geometry.caretX(2));
        assertEquals(22, geometry.caretX(3));
        assertThrows(IndexOutOfBoundsException.class, () -> geometry.caretX(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> geometry.caretX(4));
    }

    @Test
    void remainsValidWhenStripIsNarrowerThanComponentCount() {
        RoadPresetEditorCrossSectionGeometry geometry =
                RoadPresetEditorCrossSectionGeometry.create(
                        new UiRectangle(5, 7, 2, 3),
                        List.of(1, 1, 1, 1)
                );

        assertEquals(List.of(1, 1, 0, 0), geometry.componentRectangles()
                .stream()
                .map(UiRectangle::width)
                .toList());
        assertEquals(OptionalInt.of(0), geometry.componentIndexAt(5.5, 8));
        assertEquals(OptionalInt.of(1), geometry.componentIndexAt(6.5, 8));
        assertEquals(7, geometry.componentRectangle(3).right());
        assertEquals(7, geometry.caretX(4));
    }

    @Test
    void acceptsAnEmptyCrossSectionAndDefensivelyCopiesInput() {
        List<Integer> widths = new ArrayList<>();
        RoadPresetEditorCrossSectionGeometry geometry =
                RoadPresetEditorCrossSectionGeometry.create(
                        new UiRectangle(4, 6, 8, 10),
                        widths
                );
        widths.add(2);

        assertTrue(geometry.componentRectangles().isEmpty());
        assertEquals(0, geometry.componentCount());
        assertEquals(OptionalInt.of(0), geometry.insertionIndexAt(8, 7));
        assertEquals(4, geometry.caretX(0));
    }

    @Test
    void rejectsInvalidComponentWidths() {
        UiRectangle strip = new UiRectangle(0, 0, 20, 10);

        assertThrows(
                IllegalArgumentException.class,
                () -> RoadPresetEditorCrossSectionGeometry.create(
                        strip,
                        List.of(2, 0, 1)
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> RoadPresetEditorCrossSectionGeometry.create(
                        strip,
                        List.of(-1)
                )
        );
        assertThrows(
                NullPointerException.class,
                () -> RoadPresetEditorCrossSectionGeometry.create(
                        strip,
                        java.util.Arrays.asList(1, null)
                )
        );
    }
}
