package com.cookiecraftmods.roadbuilder.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Objects;

public record ResolvedMaterialCell(
        BlockPos position,
        BlockState state,
        List<MaterialCell> ownershipStack
) {
    public ResolvedMaterialCell {
        position = Objects.requireNonNull(position, "position").immutable();
        Objects.requireNonNull(state, "state");
        ownershipStack = List.copyOf(Objects.requireNonNull(
                ownershipStack,
                "ownershipStack"
        ));
        if (ownershipStack.isEmpty()) {
            throw new IllegalArgumentException("ownershipStack must not be empty");
        }
    }
}
