package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.preset.RoadMarkingPattern;
import com.cookiecraftmods.roadbuilder.preset.RoadSurfaceColumn;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

final class RoadSurfaceResolver {
    private RoadSurfaceResolver() {
    }

    static Result resolve(List<RoadSurfaceColumn> columns) {
        columns = List.copyOf(Objects.requireNonNull(columns, "columns"));
        List<List<BlockState>> stateSets = new ArrayList<>(columns.size());
        List<List<BlockState>> underlaySets = new ArrayList<>(columns.size());
        List<RoadMarkingPattern> patterns = new ArrayList<>(columns.size());
        for (RoadSurfaceColumn column : columns) {
            Objects.requireNonNull(column, "columns contains null");
            Optional<List<BlockState>> states = resolveBlocks(column.blocks());
            Optional<List<BlockState>> underlays = resolveBlocks(
                    column.underlayBlocks()
            );
            if (states.isEmpty() || underlays.isEmpty()) {
                return new Result.Failure(
                        BlockPlanFailureCode.UNKNOWN_SURFACE_BLOCK
                );
            }
            if (states.orElseThrow().stream().anyMatch(BlockState::isAir)
                    || underlays.orElseThrow().stream()
                            .anyMatch(BlockState::isAir)) {
                return new Result.Failure(
                        BlockPlanFailureCode.AIR_SURFACE_BLOCK
                );
            }
            stateSets.add(states.orElseThrow());
            underlaySets.add(underlays.orElseThrow());
            patterns.add(column.pattern());
        }
        return new Result.Success(new ResolvedRoadSurface(
                stateSets.stream().map(states -> states.get(0)).toList(),
                underlaySets.stream().map(states -> states.get(0)).toList(),
                patterns,
                stateSets,
                underlaySets
        ));
    }

    private static Optional<List<BlockState>> resolveBlocks(
            List<net.minecraft.resources.ResourceLocation> ids
    ) {
        List<BlockState> states = new ArrayList<>(ids.size());
        for (var id : ids) {
            Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(id);
            if (block.isEmpty()) {
                return Optional.empty();
            }
            states.add(block.orElseThrow().defaultBlockState());
        }
        return Optional.of(List.copyOf(states));
    }

    sealed interface Result {
        record Success(ResolvedRoadSurface surface) implements Result {
            public Success {
                Objects.requireNonNull(surface, "surface");
            }
        }

        record Failure(BlockPlanFailureCode code) implements Result {
            public Failure {
                Objects.requireNonNull(code, "code");
            }
        }
    }
}
