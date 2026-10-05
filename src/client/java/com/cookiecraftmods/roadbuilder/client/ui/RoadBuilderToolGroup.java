package com.cookiecraftmods.roadbuilder.client.ui;

import net.minecraft.network.chat.Component;

public enum RoadBuilderToolGroup {
    ROADS("roads"),
    DEMOLITION("demolition"),
    LANE_CONNECTORS("lane_connectors");

    private final String translationSuffix;

    RoadBuilderToolGroup(String translationSuffix) {
        this.translationSuffix = translationSuffix;
    }

    public Component label() {
        return Component.translatable(
                "toolbar.roadbuilder.group." + translationSuffix
        );
    }

    public Component tooltip() {
        return Component.translatable(
                "tooltip.roadbuilder.group." + translationSuffix
        );
    }
}
