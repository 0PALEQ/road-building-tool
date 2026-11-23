package com.cookiecraftmods.roadbuilder.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

abstract class GameTestPlayer extends ServerPlayer {
    GameTestPlayer(ServerLevel level, GameProfile profile) {
        super(level.getServer(), level, profile);
    }
}
