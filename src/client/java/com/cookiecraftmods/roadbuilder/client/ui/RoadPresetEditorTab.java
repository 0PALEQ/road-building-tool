package com.cookiecraftmods.roadbuilder.client.ui;

enum RoadPresetEditorTab {
    LAYOUT("layout"),
    MATERIALS("materials"),
    JUNCTIONS("junctions"),
    DETAILS("details"),
    SAVED_ROADS("saved");

    private final String key;

    RoadPresetEditorTab(String key) {
        this.key = "screen.roadbuilder.preset_editor.tab." + key;
    }

    String key() {
        return key;
    }
}
