package com.cookiecraftmods.roadbuilder.client.ui;

/** A pixel scroll range; resizing and switching content cannot strand the viewport. */
final class RoadPresetEditorScroll {
    static final int STEP = 12;
    private int offset;
    private int maximum;

    int offset() {
        return offset;
    }

    int maximum() {
        return maximum;
    }

    void resize(int contentHeight, int viewportHeight) {
        maximum = Math.max(0, contentHeight - Math.max(0, viewportHeight));
        offset = Math.min(offset, maximum);
    }

    boolean move(int amount) {
        int previous = offset;
        offset = Math.max(0, Math.min(maximum, offset + amount));
        return previous != offset;
    }

    void reset() {
        offset = 0;
    }

    void reveal(int top, int height, int viewportHeight) {
        if (top < offset) {
            offset = top;
        } else if (top + height > offset + viewportHeight) {
            offset = top + height - viewportHeight;
        }
        offset = Math.max(0, Math.min(maximum, offset));
    }
}
