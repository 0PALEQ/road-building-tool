package com.cookiecraftmods.roadbuilder.client.ui;

import net.minecraft.client.gui.components.EditBox;

final class RoadPresetEditorFocus {
    private Target target = Target.NONE;
    private int cursor;

    void capture(EditBox nameBox, EditBox searchBox) {
        if (nameBox != null && nameBox.isFocused()) {
            capture(Target.NAME, nameBox);
        } else if (searchBox != null && searchBox.isFocused()) {
            capture(Target.SEARCH, searchBox);
        } else {
            target = Target.NONE;
        }
    }

    void restore(EditBox nameBox, EditBox searchBox) {
        EditBox box = switch (target) {
            case NAME -> nameBox;
            case SEARCH -> searchBox;
            case NONE -> null;
        };
        if (box == null) {
            return;
        }
        int length = box.getValue().length();
        box.setFocused(true);
        box.setCursorPosition(clamp(cursor, 0, length));
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
        SEARCH
    }
}
