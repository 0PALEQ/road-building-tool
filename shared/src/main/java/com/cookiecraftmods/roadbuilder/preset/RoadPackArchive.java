package com.cookiecraftmods.roadbuilder.preset;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class RoadPackArchive {
    // Leave room for the packet header under Minecraft 1.20.1's payload limit.
    public static final int MAXIMUM_ARCHIVE_BYTES = 30 * 1024;
    public static final int MAXIMUM_ROADS = 64;
    private static final int MAXIMUM_ENTRIES = 256;
    private static final int MAXIMUM_EXPANDED_BYTES = 512 * 1024;
    private static final int MAXIMUM_ENTRY_BYTES = 65_536;

    private RoadPackArchive() {
    }

    public static Map<ResourceLocation, RoadPreset> read(byte[] archive) throws IOException {
        if (archive.length == 0 || archive.length > MAXIMUM_ARCHIVE_BYTES) {
            throw new IllegalArgumentException("road pack size is out of range");
        }
        Map<ResourceLocation, RoadPreset> presets = new LinkedHashMap<>();
        int remaining = MAXIMUM_EXPANDED_BYTES;
        int entries = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAXIMUM_ENTRIES) {
                    throw new IllegalArgumentException("road pack has too many ZIP entries");
                }
                int limit = Math.min(remaining, MAXIMUM_ENTRY_BYTES);
                byte[] contents = zip.readNBytes(limit + 1);
                if (contents.length > limit) {
                    throw new IllegalArgumentException("road pack expanded size exceeds the limit");
                }
                remaining -= contents.length;
                if (entry.isDirectory()) {
                    continue;
                }
                ResourceLocation id = presetId(entry.getName());
                if (id == null) {
                    continue;
                }
                if (presets.size() == MAXIMUM_ROADS || presets.containsKey(id)) {
                    throw new IllegalArgumentException("road pack has too many or duplicate presets");
                }
                String json = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(contents)).toString();
                RoadPreset preset = RoadPreset.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                        .result().orElseThrow(() -> new IllegalArgumentException("invalid road preset " + id));
                presets.put(id, preset);
            }
        }
        if (presets.isEmpty()) {
            throw new IllegalArgumentException("road pack contains no server road presets");
        }
        return Map.copyOf(presets);
    }

    private static ResourceLocation presetId(String entryName) {
        String[] parts = entryName.split("/", 4);
        if (parts.length != 4 || !parts[0].equals("data")
                || !parts[2].equals(RoadPresetReloadListener.DIRECTORY) || !parts[3].endsWith(".json")) {
            return null;
        }
        String path = parts[3].substring(0, parts[3].length() - ".json".length());
        String encoded = parts[1] + ":" + path;
        ResourceLocation id = ResourceLocation.tryParse(encoded);
        if (id == null || parts[1].isEmpty() || encoded.length() > 128) {
            throw new IllegalArgumentException("invalid road preset ID");
        }
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("invalid road preset path");
            }
        }
        return id;
    }
}
