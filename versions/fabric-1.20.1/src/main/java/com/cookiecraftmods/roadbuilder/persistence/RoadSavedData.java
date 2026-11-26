package com.cookiecraftmods.roadbuilder.persistence;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.function.Function;
import java.util.function.Supplier;

public abstract class RoadSavedData extends SavedData {
    public static void flush(DimensionDataStorage storage) {
        storage.save();
    }

    public static <T extends RoadSavedData> T loadOrCreate(
            DimensionDataStorage storage, Function<CompoundTag, T> load, Supplier<T> create, String name
    ) {
        return storage.computeIfAbsent(load, create, name);
    }
}
