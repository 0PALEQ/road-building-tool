package com.cookiecraftmods.roadbuilder.client.ui;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

final class RoadPresetEditorSelection {
    private int primary = -1;
    private final Set<Integer> selected = new LinkedHashSet<>();

    int primary() {
        return primary;
    }

    Set<Integer> selected() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(selected));
    }

    boolean isEmpty() {
        return selected.isEmpty();
    }

    boolean isSingle() {
        return selected.size() == 1;
    }

    void selectOnly(int component) {
        requireValidIndex(component);
        selected.clear();
        selected.add(component);
        primary = component;
    }

    void toggle(int component) {
        requireValidIndex(component);
        if (selected.remove(component)) {
            primary = selected.stream().findFirst().orElse(-1);
            return;
        }
        selected.add(component);
        primary = component;
    }

    void clear() {
        selected.clear();
        primary = -1;
    }

    private static void requireValidIndex(int component) {
        if (component < 0) {
            throw new IllegalArgumentException("component must not be negative");
        }
    }
}
