package com.cookiecraftmods.roadbuilder.client.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

public final class RoadPresetEditorCrossSectionGeometry {
    private final UiRectangle strip;
    private final List<UiRectangle> componentRectangles;

    private RoadPresetEditorCrossSectionGeometry(
            UiRectangle strip,
            List<UiRectangle> componentRectangles
    ) {
        this.strip = strip;
        this.componentRectangles = List.copyOf(componentRectangles);
    }

    public static RoadPresetEditorCrossSectionGeometry create(
            UiRectangle strip,
            List<Integer> componentWidths
    ) {
        Objects.requireNonNull(strip, "strip");
        Objects.requireNonNull(componentWidths, "componentWidths");

        long totalWidth = 0;
        for (int index = 0; index < componentWidths.size(); index++) {
            Integer width = Objects.requireNonNull(
                    componentWidths.get(index),
                    "componentWidths[" + index + "]"
            );
            if (width <= 0) {
                throw new IllegalArgumentException(
                        "component widths must be positive"
                );
            }
            totalWidth = Math.addExact(totalWidth, width.longValue());
        }

        if (componentWidths.isEmpty()) {
            return new RoadPresetEditorCrossSectionGeometry(strip, List.of());
        }

        int[] pixelWidths = distributePixels(
                strip.width(),
                componentWidths,
                totalWidth
        );
        List<UiRectangle> rectangles = new ArrayList<>(pixelWidths.length);
        int x = strip.x();
        for (int pixelWidth : pixelWidths) {
            rectangles.add(new UiRectangle(
                    x,
                    strip.y(),
                    pixelWidth,
                    strip.height()
            ));
            x += pixelWidth;
        }
        return new RoadPresetEditorCrossSectionGeometry(strip, rectangles);
    }

    public UiRectangle strip() {
        return strip;
    }

    public List<UiRectangle> componentRectangles() {
        return componentRectangles;
    }

    public int componentCount() {
        return componentRectangles.size();
    }

    public UiRectangle componentRectangle(int index) {
        return componentRectangles.get(index);
    }

        public OptionalInt componentIndexAt(double pointerX, double pointerY) {
        if (!strip.contains(pointerX, pointerY)) {
            return OptionalInt.empty();
        }
        for (int index = 0; index < componentRectangles.size(); index++) {
            if (componentRectangles.get(index).contains(pointerX, pointerY)) {
                return OptionalInt.of(index);
            }
        }
        return OptionalInt.empty();
    }

        public OptionalInt insertionIndexAt(double pointerX, double pointerY) {
        if (pointerY < strip.y()
                || pointerY >= strip.bottom()
                || pointerX < strip.x()
                || pointerX > strip.right()) {
            return OptionalInt.empty();
        }
        if (pointerX <= strip.x()) {
            return OptionalInt.of(0);
        }
        if (pointerX >= strip.right()) {
            return OptionalInt.of(componentRectangles.size());
        }

        for (int index = 0; index < componentRectangles.size(); index++) {
            UiRectangle component = componentRectangles.get(index);
            if (component.width() == 0) {
                continue;
            }
            double midpoint = component.x() + component.width() / 2.0;
            if (pointerX < midpoint) {
                return OptionalInt.of(index);
            }
            if (pointerX < component.right()) {
                return OptionalInt.of(index + 1);
            }
        }
        return OptionalInt.of(componentRectangles.size());
    }

        public int caretX(int insertionIndex) {
        if (insertionIndex < 0
                || insertionIndex > componentRectangles.size()) {
            throw new IndexOutOfBoundsException(
                    "insertion index: " + insertionIndex
            );
        }
        if (componentRectangles.isEmpty()) {
            return strip.x();
        }
        if (insertionIndex == componentRectangles.size()) {
            return strip.right();
        }
        return componentRectangles.get(insertionIndex).x();
    }

    private static int[] distributePixels(
            int availablePixels,
            List<Integer> componentWidths,
            long totalWidth
    ) {
        int[] result = new int[componentWidths.size()];
        List<PixelRemainder> remainders = new ArrayList<>(
                componentWidths.size()
        );
        int assignedPixels = 0;
        for (int index = 0; index < componentWidths.size(); index++) {
            long scaledWidth = (long) availablePixels
                    * componentWidths.get(index);
            int basePixels = (int) (scaledWidth / totalWidth);
            result[index] = basePixels;
            assignedPixels += basePixels;
            remainders.add(new PixelRemainder(
                    index,
                    scaledWidth % totalWidth
            ));
        }

        remainders.sort(
                Comparator.comparingLong(PixelRemainder::remainder)
                        .reversed()
                        .thenComparingInt(PixelRemainder::index)
        );
        int unassignedPixels = availablePixels - assignedPixels;
        for (int index = 0; index < unassignedPixels; index++) {
            result[remainders.get(index).index()]++;
        }
        return result;
    }

    private record PixelRemainder(int index, long remainder) {
    }
}
