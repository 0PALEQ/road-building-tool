package com.cookiecraftmods.roadbuilder.placement;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;

public record RoadStructureMaterialSet(
        BlockState terrainFill,
        BlockState embankmentSurface,
        BlockState reinforcedBase,
        BlockState deckCore,
        BlockState deckSoffit,
        BlockState deckFascia,
        BlockState retainingWall,
        BlockState pillar,
        BlockState barrier,
        BlockState barrierCap
) {
    public RoadStructureMaterialSet {
        terrainFill = requireMaterial(terrainFill, "terrainFill");
        embankmentSurface = requireMaterial(
                embankmentSurface,
                "embankmentSurface"
        );
        reinforcedBase = requireMaterial(reinforcedBase, "reinforcedBase");
        deckCore = requireMaterial(deckCore, "deckCore");
        deckSoffit = requireMaterial(deckSoffit, "deckSoffit");
        deckFascia = requireMaterial(deckFascia, "deckFascia");
        retainingWall = requireMaterial(retainingWall, "retainingWall");
        pillar = requireMaterial(pillar, "pillar");
        barrier = requireMaterial(barrier, "barrier");
        barrierCap = requireMaterial(barrierCap, "barrierCap");
    }

    public static RoadStructureMaterialSet standard() {
        return new RoadStructureMaterialSet(
                Blocks.DIRT.defaultBlockState(),
                Blocks.GRASS_BLOCK.defaultBlockState(),
                Blocks.GRAY_CONCRETE.defaultBlockState(),
                Blocks.COBBLESTONE.defaultBlockState(),
                Blocks.SMOOTH_STONE.defaultBlockState(),
                Blocks.SMOOTH_STONE.defaultBlockState(),
                Blocks.POLISHED_ANDESITE.defaultBlockState(),
                Blocks.STONE_BRICKS.defaultBlockState(),
                Blocks.IRON_BARS.defaultBlockState(),
                Blocks.IRON_TRAPDOOR.defaultBlockState()
        );
    }

    private static BlockState requireMaterial(BlockState state, String name) {
        Objects.requireNonNull(state, name);
        if (state.isAir()) {
            throw new IllegalArgumentException(name + " must not be air");
        }
        return state;
    }
}
