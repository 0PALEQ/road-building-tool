package com.cookiecraftmods.roadbuilder.client.ui;

import java.util.Objects;

final class RoadPresetEditorDragState {
    private static final double ACTIVATION_DISTANCE_SQUARED = 9.0;

    private final RoadPresetEditorComponentCatalog.Kind kind;
    private final int sourceIndex;
    private final RoadPresetEditorComponent component;
    private final double startX;
    private final double startY;
    private double currentX;
    private double currentY;
    private boolean active;
    private int dropIndex = -1;

    private RoadPresetEditorDragState(
            RoadPresetEditorComponentCatalog.Kind kind,
            int sourceIndex,
            RoadPresetEditorComponent component,
            double startX,
            double startY
    ) {
        this.kind = kind;
        this.sourceIndex = sourceIndex;
        this.component = Objects.requireNonNull(component, "component");
        this.startX = startX;
        this.startY = startY;
        currentX = startX;
        currentY = startY;
    }

    static RoadPresetEditorDragState palette(
            RoadPresetEditorComponentCatalog.Kind kind,
            RoadPresetEditorComponent component,
            double startX,
            double startY
    ) {
        return new RoadPresetEditorDragState(
                Objects.requireNonNull(kind, "kind"),
                -1,
                component,
                startX,
                startY
        );
    }

    static RoadPresetEditorDragState existing(
            int sourceIndex,
            RoadPresetEditorComponent component,
            double startX,
            double startY
    ) {
        if (sourceIndex < 0) {
            throw new IllegalArgumentException("sourceIndex must not be negative");
        }
        return new RoadPresetEditorDragState(
                null,
                sourceIndex,
                component,
                startX,
                startY
        );
    }

    void update(double mouseX, double mouseY, int candidateDropIndex) {
        currentX = mouseX;
        currentY = mouseY;
        double deltaX = mouseX - startX;
        double deltaY = mouseY - startY;
        if (!active && deltaX * deltaX + deltaY * deltaY
                >= ACTIVATION_DISTANCE_SQUARED) {
            active = true;
        }
        dropIndex = active ? candidateDropIndex : -1;
    }

    RoadPresetEditorComponentCatalog.Kind kind() {
        return kind;
    }

    int sourceIndex() {
        return sourceIndex;
    }

    RoadPresetEditorComponent component() {
        return component;
    }

    double currentX() {
        return currentX;
    }

    double currentY() {
        return currentY;
    }

    boolean active() {
        return active;
    }

    int dropIndex() {
        return dropIndex;
    }
}
