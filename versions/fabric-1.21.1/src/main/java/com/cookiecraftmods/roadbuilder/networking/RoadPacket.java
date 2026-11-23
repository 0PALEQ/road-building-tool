package com.cookiecraftmods.roadbuilder.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public interface RoadPacket extends CustomPacketPayload {
    RoadPacketType<?> packetType();

    void write(FriendlyByteBuf buffer);

    @Override
    default Type<? extends CustomPacketPayload> type() {
        return packetType().fabricType();
    }
}
