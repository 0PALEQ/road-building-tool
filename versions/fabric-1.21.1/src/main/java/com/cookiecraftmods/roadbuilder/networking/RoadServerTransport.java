package com.cookiecraftmods.roadbuilder.networking;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.BiConsumer;

public final class RoadServerTransport {
    private RoadServerTransport() {
    }

    public static <T extends RoadPacket> boolean registerGlobalReceiver(
            RoadPacketType<T> type, BiConsumer<T, ServerPlayer> receiver
    ) {
        return ServerPlayNetworking.registerGlobalReceiver(
                type.fabricType(), (packet, context) -> receiver.accept(packet, context.player())
        );
    }

    public static boolean canSend(ServerPlayer player, RoadPacketType<?> type) {
        return ServerPlayNetworking.canSend(player, type.fabricType());
    }

    public static void send(ServerPlayer player, RoadPacket packet) {
        ServerPlayNetworking.send(player, packet);
    }
}
