package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.compat.ServerOperatorPermissions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

@FunctionalInterface
public interface ForceRestorePermissionPolicy {
    int DEFAULT_OPERATOR_PERMISSION_LEVEL = 2;

    boolean mayForceRestore(
            ServerPlayer player,
            ServerLevel level,
            PlacementOperation operation
    );

    static ForceRestorePermissionPolicy operators() {
        return operatorLevel(DEFAULT_OPERATOR_PERMISSION_LEVEL);
    }

    static ForceRestorePermissionPolicy operatorLevel(int permissionLevel) {
        if (permissionLevel < 0 || permissionLevel > 4) {
            throw new IllegalArgumentException("permissionLevel must be between 0 and 4");
        }
        return (player, level, operation) -> {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(operation, "operation");
            return ServerOperatorPermissions.hasLevel(player, permissionLevel);
        };
    }
}
