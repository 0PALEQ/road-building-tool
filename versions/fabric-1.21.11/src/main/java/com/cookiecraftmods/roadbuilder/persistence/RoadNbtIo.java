package com.cookiecraftmods.roadbuilder.persistence;

import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.util.UUID;
import java.nio.file.Path;

public final class RoadNbtIo {
    private RoadNbtIo() {
    }

    public static CompoundTag readCompressed(Path path) throws IOException {
        return NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
    }

    public static void writeCompressed(CompoundTag tag, Path path) throws IOException {
        NbtIo.writeCompressed(tag, path);
    }

    public static boolean contains(CompoundTag tag, String key, int type) {
        Tag value = tag.get(key);
        return value != null && value.getId() == type;
    }

    public static void putUuid(CompoundTag tag, String key, UUID value) {
        tag.putIntArray(key, UUIDUtil.uuidToIntArray(value));
    }

    public static UUID getUuid(CompoundTag tag, String key) {
        if (tag.get(key) instanceof IntArrayTag value) {
            return readUuid(value);
        }
        throw new IllegalArgumentException("Expected UUID tag: " + key);
    }

    public static UUID readUuid(IntArrayTag tag) {
        return UUIDUtil.uuidFromIntArray(tag.getAsIntArray());
    }
}
