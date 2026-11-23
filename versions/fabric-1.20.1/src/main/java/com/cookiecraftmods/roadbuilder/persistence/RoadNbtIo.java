package com.cookiecraftmods.roadbuilder.persistence;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.nio.file.Path;

public final class RoadNbtIo {
    private RoadNbtIo() {
    }

    public static CompoundTag readCompressed(Path path) throws IOException {
        return NbtIo.readCompressed(path.toFile());
    }

    public static void writeCompressed(CompoundTag tag, Path path) throws IOException {
        NbtIo.writeCompressed(tag, path.toFile());
    }
}
