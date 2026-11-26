package com.cookiecraftmods.roadbuilder.testmatrix;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

final class MatrixServerPlayer {
    private MatrixServerPlayer() {
    }

    static String name(ServerPlayer player) {
        return player.getGameProfile().getName();
    }

    static void setSpawn(ServerLevel level, BlockPos position) {
        level.setDefaultSpawnPos(position, 0.0F);
    }

    static void teleport(ServerPlayer player, ServerLevel level, double x, double y, double z) {
        player.teleportTo(level, x, y, z, 0.0F, 0.0F);
    }
}
