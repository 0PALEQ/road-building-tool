package com.cookiecraftmods.roadbuilder.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;

public final class ServerOperatorPermissions {
    private ServerOperatorPermissions() {
    }

    public static boolean hasLevel(ServerPlayer player, int level) {
        return player.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(level)));
    }
}
