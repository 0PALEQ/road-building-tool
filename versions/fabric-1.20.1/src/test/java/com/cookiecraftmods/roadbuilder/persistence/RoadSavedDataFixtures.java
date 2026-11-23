package com.cookiecraftmods.roadbuilder.persistence;

import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.nio.file.Path;

public final class RoadSavedDataFixtures {
    private RoadSavedDataFixtures() {
    }

    public static DimensionDataStorage open(Path directory) {
        return new DimensionDataStorage(directory.toFile(), DataFixers.getDataFixer());
    }
}
