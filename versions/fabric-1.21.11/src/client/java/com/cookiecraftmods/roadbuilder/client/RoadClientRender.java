package com.cookiecraftmods.roadbuilder.client;

import net.minecraft.client.Minecraft;

public final class RoadClientRender {
    private RoadClientRender() {
    }

    public static float partialTick(Minecraft client) {
        return client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
    }
}
