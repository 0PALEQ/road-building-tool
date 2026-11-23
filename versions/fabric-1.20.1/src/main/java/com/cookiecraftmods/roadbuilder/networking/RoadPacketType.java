package com.cookiecraftmods.roadbuilder.networking;

import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

public record RoadPacketType<T extends RoadPacket>(ResourceLocation id, PacketType<T> fabricType) {
    public static <T extends RoadPacket> RoadPacketType<T> create(
            ResourceLocation id, Function<FriendlyByteBuf, T> read
    ) {
        return new RoadPacketType<>(id, PacketType.create(id, read));
    }

    public T read(FriendlyByteBuf buffer) {
        return fabricType.read(buffer);
    }

    void registerC2S() {
        // Fabric 1.20.1 registers the decoder when the receiver is installed.
    }

    void registerS2C() {
        // Fabric 1.20.1 registers the decoder when the receiver is installed.
    }
}
