package com.cookiecraftmods.roadbuilder.client.networking;

import com.cookiecraftmods.roadbuilder.networking.RoadPacket;
import com.cookiecraftmods.roadbuilder.networking.RoadPacketType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.function.Consumer;

public final class RoadClientTransport {
    private RoadClientTransport() {
    }

    public static <T extends RoadPacket> boolean registerGlobalReceiver(
            RoadPacketType<T> type, Consumer<T> receiver
    ) {
        return ClientPlayNetworking.registerGlobalReceiver(
                type.fabricType(), (packet, context) -> receiver.accept(packet)
        );
    }

    public static boolean canSend(RoadPacketType<?> type) {
        return ClientPlayNetworking.canSend(type.fabricType());
    }

    public static void send(RoadPacket packet) {
        ClientPlayNetworking.send(packet);
    }
}
