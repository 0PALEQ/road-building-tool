package com.cookiecraftmods.roadbuilder.persistence;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.function.Function;
import java.util.function.Supplier;

public abstract class RoadSavedData extends SavedData {
    public abstract CompoundTag save(CompoundTag tag);

    @Override
    public final CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        return save(tag);
    }

    public static <T extends RoadSavedData> T loadOrCreate(
            DimensionDataStorage storage, Function<CompoundTag, T> load, Supplier<T> create, String name
    ) {
        // 1.21.1 requires a data-fix type. Command storage leaves our fields alone;
        // each Road Builder SavedData class handles its own schema migrations.
        SavedData.Factory<T> factory = new SavedData.Factory<>(
                create, (tag, registries) -> load.apply(tag), DataFixTypes.SAVED_DATA_COMMAND_STORAGE
        );
        return storage.computeIfAbsent(factory, name);
    }
}
