package com.cookiecraftmods.roadbuilder.placement;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

@FunctionalInterface
public interface ConstructionPermissionPolicy {
    boolean mayModify(ServerPlayer player, ServerLevel level, ConstructionFootprint footprint);
}
