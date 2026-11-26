package com.cookiecraftmods.roadbuilder.persistence;

import net.minecraft.core.HolderLookup;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.nio.file.Path;
import java.util.stream.Stream;

public final class RoadSavedDataFixtures {
    private RoadSavedDataFixtures() {
    }

    public static DimensionDataStorage open(Path directory) {
        return new DimensionDataStorage(directory, DataFixers.getDataFixer(), HolderLookup.Provider.create(Stream.empty()));
    }
}
