package com.cookiecraftmods.roadbuilder.persistence;

import com.cookiecraftmods.roadbuilder.preset.RoadPreset;
import com.cookiecraftmods.roadbuilder.preset.RoadPresetRepository;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class RoadPresetSavedData extends RoadSavedData {
    public static final String DATA_NAME = "roadbuilder_world_presets";
    private static final int SCHEMA_VERSION = 1;
    private static final int MAXIMUM_PRESETS = 512;
    private static final int MAXIMUM_JSON_CHARACTERS = 65_536;

    private final Map<ResourceLocation, RoadPreset> presets =
            new LinkedHashMap<>();

    public static RoadPresetSavedData get(ServerLevel overworld) {
        return loadOrCreate(
                overworld.getDataStorage(),
                RoadPresetSavedData::load,
                RoadPresetSavedData::new,
                DATA_NAME
        );
    }

    public void loadInto(RoadPresetRepository repository) {
        repository.replaceServerAuthored(presets);
    }

    public void replaceFrom(RoadPresetRepository repository) {
        presets.clear();
        presets.putAll(repository.authoredSnapshot());
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("schema_version", SCHEMA_VERSION);
        ListTag entries = new ListTag();
        presets.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    CompoundTag value = new CompoundTag();
                    value.putString("id", entry.getKey().toString());
                    String json = RoadPreset.CODEC.encodeStart(
                            JsonOps.INSTANCE,
                            entry.getValue()
                    ).result().orElseThrow().toString();
                    value.putString("preset", json);
                    entries.add(value);
                });
        tag.put("presets", entries);
        return tag;
    }

    private static RoadPresetSavedData load(CompoundTag root) {
        if (root.getInt("schema_version") != SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported world-preset schema"
            );
        }
        ListTag entries = root.getList("presets", Tag.TAG_COMPOUND);
        if (entries.size() > MAXIMUM_PRESETS) {
            throw new IllegalArgumentException(
                    "world preset count exceeds limit"
            );
        }
        RoadPresetSavedData result = new RoadPresetSavedData();
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag entry = entries.getCompound(index);
            ResourceLocation id = ResourceLocation.tryParse(
                    entry.getString("id")
            );
            String json = entry.getString("preset");
            if (id == null || json.length() > MAXIMUM_JSON_CHARACTERS) {
                throw new IllegalArgumentException(
                        "invalid world preset entry"
                );
            }
            RoadPreset preset = RoadPreset.CODEC.parse(
                    JsonOps.INSTANCE,
                    JsonParser.parseString(json)
            ).result().orElseThrow(() -> new IllegalArgumentException(
                    "invalid world preset content"
            ));
            if (result.presets.put(id, preset) != null) {
                throw new IllegalArgumentException(
                        "duplicate world preset " + id
                );
            }
        }
        return result;
    }
}
