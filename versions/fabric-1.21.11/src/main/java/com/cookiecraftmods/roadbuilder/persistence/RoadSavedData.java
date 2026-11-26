package com.cookiecraftmods.roadbuilder.persistence;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class RoadSavedData extends SavedData {
    private static final Map<DimensionDataStorage, Map<String, SavedDataType<?>>> TYPES =
            new WeakHashMap<>();

    public abstract CompoundTag save(CompoundTag tag);

    public static <T extends RoadSavedData> T loadOrCreate(
            DimensionDataStorage storage, Function<CompoundTag, T> load, Supplier<T> create, String name
    ) {
        return storage.computeIfAbsent(type(storage, load, create, name));
    }

    public static void flush(DimensionDataStorage storage) {
        storage.saveAndJoin();
    }

    @SuppressWarnings("unchecked")
    private static synchronized <T extends RoadSavedData> SavedDataType<T> type(
            DimensionDataStorage storage, Function<CompoundTag, T> load, Supplier<T> create, String name
    ) {
        // DimensionDataStorage keys by SavedDataType, so reuse the type within each dimension.
        // Each Road Builder file name permanently identifies a single data class.
        return (SavedDataType<T>) TYPES.computeIfAbsent(storage, unused -> new HashMap<>())
                .computeIfAbsent(name, unused -> new SavedDataType<>(
                        name,
                        create,
                        CompoundTag.CODEC.xmap(load, data -> data.save(new CompoundTag())),
                        DataFixTypes.SAVED_DATA_COMMAND_STORAGE
                ));
    }
}
