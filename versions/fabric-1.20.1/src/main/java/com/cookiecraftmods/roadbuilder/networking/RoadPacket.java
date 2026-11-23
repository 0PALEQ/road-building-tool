package com.cookiecraftmods.roadbuilder.networking;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;

public interface RoadPacket extends FabricPacket {
    RoadPacketType<?> packetType();

    @Override
    default PacketType<?> getType() {
        return packetType().fabricType();
    }
}
