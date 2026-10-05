package com.cookiecraftmods.roadbuilder.networking;

import com.cookiecraftmods.roadbuilder.preset.RoadPresetCatalog;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record PresetCatalogS2CPacket(
        int protocolVersion,
        RoadPresetCatalog catalog
) implements FabricPacket {
    public static final int MAX_PAYLOAD_BYTES = 262_144;
    public static final PacketType<PresetCatalogS2CPacket> TYPE =
            PacketType.create(
                    new ResourceLocation("roadbuilder", "preset_catalog_v1"),
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
    public PacketType<?> getType() {
        return TYPE;
    }
}
