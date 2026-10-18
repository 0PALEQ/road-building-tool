package com.cookiecraftmods.roadbuilder.client.ui;

import net.minecraft.client.gui.components.EditBox;

final class RoadPresetEditorFocus {
    private Target target = Target.NONE;
    private int cursor;

    void capture(EditBox nameBox, EditBox searchBox, EditBox speedBox) {
        if (nameBox != null && nameBox.isFocused()) {
            capture(Target.NAME, nameBox);
        } else if (searchBox != null && searchBox.isFocused()) {
            capture(Target.SEARCH, searchBox);
        } else if (speedBox != null && speedBox.isFocused()) {
            capture(Target.SPEED, speedBox);
        } else {
            target = Target.NONE;
        }
    }

    void restore(EditBox nameBox, EditBox searchBox, EditBox speedBox) {
        EditBox box = targetBox(nameBox, searchBox, speedBox);
        if (box == null || !box.visible) {
            return;
        }
        int length = box.getValue().length();
        box.setFocused(true);
        box.setCursorPosition(clamp(cursor, 0, length));
    }

    EditBox targetBox(EditBox nameBox, EditBox searchBox, EditBox speedBox) {
        return switch (target) {
            case NAME -> nameBox;
            case SEARCH -> searchBox;
            case SPEED -> speedBox;
            case NONE -> null;
        };
    }

    private void capture(Target target, EditBox box) {
        this.target = target;
        cursor = box.getCursorPosition();
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private enum Target {
        NONE,
        NAME,
        SEARCH,
        SPEED
    }
}
