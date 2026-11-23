package com.cookiecraftmods.roadbuilder.networking;

import com.cookiecraftmods.roadbuilder.preset.RoadClass;
import com.cookiecraftmods.roadbuilder.preset.RoadPreset;
import com.cookiecraftmods.roadbuilder.preset.RoadPresetCatalog;
import com.cookiecraftmods.roadbuilder.preset.RoadPresetCatalogEntry;
import com.cookiecraftmods.roadbuilder.preset.RoadPresetDisplay;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

final class RoadPresetPacketCodecs {
    static final int MAXIMUM_CATALOG_ENTRIES = 512;
    static final int MAXIMUM_PRESET_JSON_BYTES = 65_536;

    private RoadPresetPacketCodecs() {
    }

    static void writeCatalog(
            FriendlyByteBuf buffer,
            RoadPresetCatalog catalog
    ) {
        if (catalog.entries().size() > MAXIMUM_CATALOG_ENTRIES) {
            throw new IllegalArgumentException("preset catalog is too large");
        }
        buffer.writeVarLong(catalog.revision());
        buffer.writeVarInt(catalog.entries().size());
        for (RoadPresetCatalogEntry entry : catalog.entries()) {
            PacketValidation.writeBoundedId(buffer, entry.id());
            buffer.writeVarInt(entry.revision());
            buffer.writeVarInt(entry.surfaceWidth());
            buffer.writeVarInt(entry.laneCount());
            buffer.writeUtf(entry.fingerprint(), 64);
            buffer.writeBoolean(entry.editable());
            writeDisplay(buffer, entry.display());
            buffer.writeByte(entry.roadClass().ordinal());
            buffer.writeByte(entry.priorityRank());
        }
    }

    static RoadPresetCatalog readCatalog(FriendlyByteBuf buffer) {
        long revision = buffer.readVarLong();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAXIMUM_CATALOG_ENTRIES) {
            throw new IllegalArgumentException(
                    "preset catalog entry count is invalid"
            );
        }
        List<RoadPresetCatalogEntry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            ResourceLocation id = PacketValidation.readBoundedId(
                    buffer,
                    "presetId"
            );
            int presetRevision = buffer.readVarInt();
            int surfaceWidth = buffer.readVarInt();
            int laneCount = buffer.readVarInt();
            String fingerprint = buffer.readUtf(64);
            boolean editable = buffer.readBoolean();
            RoadPresetDisplay display = readDisplay(buffer);
            int roadClassId = buffer.readUnsignedByte();
            RoadClass[] roadClasses = RoadClass.values();
            if (roadClassId >= roadClasses.length) {
                throw new IllegalArgumentException("unknown road class");
            }
            entries.add(new RoadPresetCatalogEntry(
                    id,
                    presetRevision,
                    surfaceWidth,
                    laneCount,
                    fingerprint,
                    editable,
                    display,
                    roadClasses[roadClassId],
                    buffer.readUnsignedByte()
            ));
        }
        return new RoadPresetCatalog(revision, entries);
    }

    static void writePreset(FriendlyByteBuf buffer, RoadPreset preset) {
        String json = RoadPreset.CODEC.encodeStart(JsonOps.INSTANCE, preset)
                .result()
                .orElseThrow(() -> new IllegalArgumentException(
                        "road preset could not be encoded"
                ))
                .toString();
        int bytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (bytes > MAXIMUM_PRESET_JSON_BYTES) {
            throw new IllegalArgumentException("road preset payload is too large");
        }
        buffer.writeUtf(json, MAXIMUM_PRESET_JSON_BYTES);
    }

    static RoadPreset readPreset(FriendlyByteBuf buffer) {
        String json = buffer.readUtf(MAXIMUM_PRESET_JSON_BYTES);
        if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAXIMUM_PRESET_JSON_BYTES) {
            throw new IllegalArgumentException("road preset payload is too large");
        }
        return RoadPreset.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString(json)
        ).result().orElseThrow(() -> new IllegalArgumentException(
                "road preset payload is invalid"
        ));
    }

    private static void writeDisplay(
            FriendlyByteBuf buffer,
            RoadPresetDisplay display
    ) {
        buffer.writeUtf(display.translationKey(), 128);
        buffer.writeUtf(display.fallbackName(), 64);
        buffer.writeUtf(display.category(), 32);
        buffer.writeUtf(display.thumbnailKey(), 128);
    }

    private static RoadPresetDisplay readDisplay(FriendlyByteBuf buffer) {
        return new RoadPresetDisplay(
                buffer.readUtf(128),
                buffer.readUtf(64),
                buffer.readUtf(32),
                buffer.readUtf(128)
        );
    }
}
