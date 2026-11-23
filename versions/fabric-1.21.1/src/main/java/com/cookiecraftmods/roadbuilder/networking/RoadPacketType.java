package com.cookiecraftmods.roadbuilder.networking;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

public record RoadPacketType<T extends RoadPacket>(
        ResourceLocation id,
        CustomPacketPayload.Type<T> fabricType,
        Function<FriendlyByteBuf, T> decoder
) {
    public static <T extends RoadPacket> RoadPacketType<T> create(
            ResourceLocation id, Function<FriendlyByteBuf, T> read
    ) {
        return new RoadPacketType<>(
                id, new CustomPacketPayload.Type<>(id), read
        );
    }

    public T read(FriendlyByteBuf buffer) {
        return decoder.apply(buffer);
    }

    private StreamCodec<RegistryFriendlyByteBuf, T> codec() {
        return StreamCodec.ofMember(RoadPacket::write, this::read);
    }

    void registerC2S() {
        PayloadTypeRegistry.playC2S().register(fabricType, codec());
    }

    void registerS2C() {
        PayloadTypeRegistry.playS2C().register(fabricType, codec());
    }
}
