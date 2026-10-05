package com.cookiecraftmods.roadbuilder.placement;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public final class LayeredMaterialCellResolver {
    private static final Comparator<MaterialCell> STACK_ORDER =
            Comparator.comparingInt((MaterialCell cell) ->
                            cell.layer().priority())
                    .thenComparing(MaterialCell::ownerType)
                    .thenComparing(MaterialCell::ownerKey)
                    .thenComparing(cell -> cell.state().toString());

    public Map<BlockPos, ResolvedMaterialCell> resolve(
            Collection<MaterialCell> candidates
    ) {
        Objects.requireNonNull(candidates, "candidates");
        TreeMap<BlockPos, List<MaterialCell>> grouped = new TreeMap<>();
        for (MaterialCell candidate : candidates) {
            MaterialCell cell = Objects.requireNonNull(
                    candidate,
                    "candidates contains null"
            );
            grouped.computeIfAbsent(
                    cell.position(),
                    ignored -> new ArrayList<>()
            ).add(cell);
        }
        Map<BlockPos, ResolvedMaterialCell> result = new LinkedHashMap<>();
        grouped.forEach((position, values) -> {
            values.sort(STACK_ORDER);
            MaterialCell visible = values.get(values.size() - 1);
            result.put(position, new ResolvedMaterialCell(
                    position,
                    visible.state(),
                    values
            ));
        });
        return java.util.Collections.unmodifiableMap(result);
    }
}
