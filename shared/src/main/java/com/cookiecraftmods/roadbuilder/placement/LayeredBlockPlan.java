package com.cookiecraftmods.roadbuilder.placement;

import java.util.Objects;

public record LayeredBlockPlan(
        BlockPlacementPlan plan,
        MaterialLayer layer,
        String ownerType,
        String ownerKey
) {
    public LayeredBlockPlan {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(layer, "layer");
        Objects.requireNonNull(ownerType, "ownerType");
        Objects.requireNonNull(ownerKey, "ownerKey");
    }
}
