package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashSet;
import java.util.Optional;

final class RoadBlockStateFactory {
    private RoadBlockStateFactory() {
    }

    static BlockState solid(Block block) {
        BlockState state = block.defaultBlockState();
        return block instanceof SlabBlock
                ? state.setValue(SlabBlock.TYPE, SlabType.DOUBLE)
                : state;
    }

    static Optional<BlockState> slabCompanion(BlockState state) {
        if (state.getBlock() instanceof SlabBlock) {
            return Optional.of(state);
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(
                state.getBlock()
        );
        String path = id.getPath();
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        candidates.add(path + "_slab");
        replaceSuffix(candidates, path, "_block", "_slab");
        replaceSuffix(candidates, path, "_planks", "_slab");
        replaceSuffix(candidates, path, "_bricks", "_brick_slab");
        replaceSuffix(candidates, path, "_tiles", "_tile_slab");
        if (path.equals("bricks")) {
            candidates.add("brick_slab");
        }
        for (String candidatePath : candidates) {
            ResourceLocation candidateId = MinecraftIds.id(
                    id.getNamespace(),
                    candidatePath
            );
            Optional<Block> candidate = BuiltInRegistries.BLOCK.getOptional(
                    candidateId
            );
            if (candidate.isPresent()
                    && candidate.orElseThrow() instanceof SlabBlock) {
                return Optional.of(candidate.orElseThrow().defaultBlockState());
            }
        }
        return Optional.empty();
    }

    private static void replaceSuffix(
            LinkedHashSet<String> candidates,
            String path,
            String suffix,
            String replacement
    ) {
        if (path.endsWith(suffix)) {
            candidates.add(path.substring(0, path.length() - suffix.length())
                    + replacement);
        }
    }
}
