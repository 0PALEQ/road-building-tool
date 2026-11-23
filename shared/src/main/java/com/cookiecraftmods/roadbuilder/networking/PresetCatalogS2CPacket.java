package com.cookiecraftmods.roadbuilder.networking;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import com.cookiecraftmods.roadbuilder.preset.RoadPresetCatalog;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;

public record PresetCatalogS2CPacket(
        int protocolVersion,
        RoadPresetCatalog catalog
) implements RoadPacket {
    public static final int MAX_PAYLOAD_BYTES = 262_144;
    public static final RoadPacketType<PresetCatalogS2CPacket> TYPE =
            RoadPacketType.create(
                    MinecraftIds.id("roadbuilder", "preset_catalog_v1"),
                    PresetCatalogS2CPacket::new
            );

    public PresetCatalogS2CPacket {
        PacketValidation.requireProtocolVersion(protocolVersion);
        Objects.requireNonNull(catalog, "catalog");
    }

    public PresetCatalogS2CPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), RoadPresetPacketCodecs.readCatalog(buffer));
        PacketValidation.requireFullyRead(buffer, "preset catalog");
    }

    public static PresetCatalogS2CPacket current(RoadPresetCatalog catalog) {
        return new PresetCatalogS2CPacket(
                PacketValidation.PROTOCOL_VERSION,
                catalog
        );
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(protocolVersion);
        RoadPresetPacketCodecs.writeCatalog(buffer, catalog);
    }

    @Override
    public RoadPacketType<?> packetType() {
        return TYPE;
    }
}
