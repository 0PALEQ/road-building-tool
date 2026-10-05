package com.cookiecraftmods.roadbuilder.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;

public record MaterialCell(
        BlockPos position,
        BlockState state,
        MaterialLayer layer,
        String ownerType,
        String ownerKey
) {
    public MaterialCell {
        position = Objects.requireNonNull(position, "position").immutable();
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(layer, "layer");
        ownerType = bounded(ownerType, "ownerType");
        ownerKey = bounded(ownerKey, "ownerKey");
    }

    private static String bounded(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > 128) {
            throw new IllegalArgumentException(
                    name + " must contain 1..128 characters"
            );
        }
        return value;
    }
}
