package com.cookiecraftmods.roadbuilder.testmatrix;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelData;

import java.util.Set;

final class MatrixServerPlayer {
    private MatrixServerPlayer() {
    }

    static String name(ServerPlayer player) {
        return player.getGameProfile().name();
    }

    static void setSpawn(ServerLevel level, BlockPos position) {
        level.setRespawnData(LevelData.RespawnData.of(level.dimension(), position, 0.0F, 0.0F));
    }

    static void teleport(ServerPlayer player, ServerLevel level, double x, double y, double z) {
        player.teleportTo(level, x, y, z, Set.of(), 0.0F, 0.0F, false);
    }
}
