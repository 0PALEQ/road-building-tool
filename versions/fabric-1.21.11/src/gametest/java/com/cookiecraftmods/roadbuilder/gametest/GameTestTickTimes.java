package com.cookiecraftmods.roadbuilder.gametest;

import net.minecraft.server.MinecraftServer;

final class GameTestTickTimes {
    private GameTestTickTimes() {
    }

    static long nanosAt(MinecraftServer server, long tick) {
        long[] times = server.getTickTimesNanos();
        return times[Math.floorMod(tick, times.length)];
    }
}
