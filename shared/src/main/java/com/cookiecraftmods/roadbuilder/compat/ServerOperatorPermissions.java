package com.cookiecraftmods.roadbuilder.compat;

import net.minecraft.server.level.ServerPlayer;

public final class ServerOperatorPermissions {
    private ServerOperatorPermissions() {
    }

    public static boolean hasLevel(ServerPlayer player, int level) {
        return player.hasPermissions(level);
    }
}
