package com.cookiecraftmods.roadbuilder.persistence;

import com.mojang.serialization.DataResult;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Objects;
import java.util.Optional;

public final class StrictBlockStateNbtCodec {
    private static final String NAME_KEY = "Name";
    private static final String PROPERTIES_KEY = "Properties";

    private StrictBlockStateNbtCodec() {
    }

        public static CompoundTag write(BlockState state) {
        return NbtUtils.writeBlockState(Objects.requireNonNull(state, "state"));
    }

        public static DataResult<BlockState> read(CompoundTag tag) {
        return read(BuiltInRegistries.BLOCK.asLookup(), tag);
    }

        public static DataResult<BlockState> read(
            HolderGetter<Block> blockLookup,
            CompoundTag tag
    ) {
        Objects.requireNonNull(blockLookup, "blockLookup");
        Objects.requireNonNull(tag, "tag");

        Tag nameTag = tag.get(NAME_KEY);
        if (!(nameTag instanceof StringTag)) {
            return error("block state Name must be a string");
        }

        String serializedName = nameTag.getAsString();
        ResourceLocation blockId = ResourceLocation.tryParse(serializedName);
        if (blockId == null) {
            return error("invalid block state Name: " + serializedName);
        }

        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, blockId);
        Optional<Holder.Reference<Block>> holder = blockLookup.get(blockKey);
        if (holder.isEmpty()) {
            return error("unknown block state Name: " + blockId);
        }

        Block block = holder.get().value();
        DataResult<BlockState> propertiesValidation = validateProperties(block, tag);
        if (propertiesValidation != null) {
            return propertiesValidation;
        }

        try {
            BlockState decoded = NbtUtils.readBlockState(blockLookup, tag);
            if (!decoded.is(block)) {
                return error("decoded block state did not match validated block " + blockId);
            }
            return DataResult.success(decoded);
        } catch (RuntimeException exception) {
            String message = exception.getMessage();
            return error("could not decode block state " + blockId
                    + (message == null || message.isBlank() ? "" : ": " + message));
        }
    }

        private static DataResult<BlockState> validateProperties(Block block, CompoundTag tag) {
        StateDefinition<Block, BlockState> definition = block.getStateDefinition();
        Tag rawProperties = tag.get(PROPERTIES_KEY);

        if (rawProperties == null) {
            if (definition.getProperties().isEmpty()) {
                return null;
            }
            return error("block state Properties is required for "
                    + definition.getProperties().size() + " declared properties");
        }
        if (!(rawProperties instanceof CompoundTag properties)) {
            return error("block state Properties must be a compound");
        }

        for (String propertyName : properties.getAllKeys()) {
            Property<?> property = definition.getProperty(propertyName);
            if (property == null) {
                return error("unknown block state property: " + propertyName);
            }

            Tag rawValue = properties.get(propertyName);
            if (!(rawValue instanceof StringTag)) {
                return error("block state property " + propertyName + " must be a string");
            }

            String serializedValue = rawValue.getAsString();
            if (property.getValue(serializedValue).isEmpty()) {
                return error("invalid value for block state property " + propertyName
                        + ": " + serializedValue);
            }
        }

        for (Property<?> property : definition.getProperties()) {
            if (!properties.contains(property.getName())) {
                return error("missing block state property: " + property.getName());
            }
        }

        return null;
    }

    private static <T> DataResult<T> error(String message) {
        return DataResult.error(() -> message);
    }
}
