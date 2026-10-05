package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.test.MinecraftBootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockTransitionStateMatcherTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftBootstrap.bootstrap();
    }

    @Test
    void acceptsReplaceableVegetationRemovedByAnEarlierPlannedWrite() {
        var lowerTallGrass = Blocks.TALL_GRASS.defaultBlockState().setValue(
                DoublePlantBlock.HALF,
                DoubleBlockHalf.LOWER
        );

        assertTrue(BlockTransitionStateMatcher.matchesRecordedSource(
                Blocks.AIR.defaultBlockState(),
                lowerTallGrass
        ));
    }

    @Test
    void doesNotTreatMissingSolidTerrainAsAnExpectedUpdate() {
        assertFalse(BlockTransitionStateMatcher.matchesRecordedSource(
                Blocks.AIR.defaultBlockState(),
                Blocks.STONE.defaultBlockState()
        ));
    }

    @Test
    void acceptsNaturalSoilChangesWhileRejectingArbitraryEdits() {
        assertTrue(BlockTransitionStateMatcher.matchesRecordedSource(
                Blocks.GRASS_BLOCK.defaultBlockState(),
                Blocks.DIRT.defaultBlockState()
        ));
        assertTrue(BlockTransitionStateMatcher.matchesRecordedSource(
                Blocks.DIRT.defaultBlockState(),
                Blocks.GRASS_BLOCK.defaultBlockState()
        ));
        assertTrue(BlockTransitionStateMatcher.matchesRecordedSource(
                Blocks.DIRT.defaultBlockState(),
                Blocks.FARMLAND.defaultBlockState()
        ));
        assertFalse(BlockTransitionStateMatcher.matchesRecordedSource(
                Blocks.GOLD_BLOCK.defaultBlockState(),
                Blocks.DIRT.defaultBlockState()
        ));

        assertTrue(BlockTransitionStateMatcher.matchesUpdateDrivenTarget(
                Blocks.GRASS_BLOCK.defaultBlockState(),
                Blocks.DIRT.defaultBlockState()
        ));
        assertTrue(BlockTransitionStateMatcher.matchesUpdateDrivenTarget(
                Blocks.DIRT.defaultBlockState(),
                Blocks.GRASS_BLOCK.defaultBlockState()
        ));
        assertFalse(BlockTransitionStateMatcher.matchesUpdateDrivenTarget(
                Blocks.GOLD_BLOCK.defaultBlockState(),
                Blocks.DIRT.defaultBlockState()
        ));
        assertFalse(BlockTransitionStateMatcher.matchesPlannedTarget(
                Blocks.GRASS_BLOCK.defaultBlockState(),
                Blocks.DIRT.defaultBlockState()
        ));
    }

    @Test
    void acceptsFluidFlowIntoOrOutOfRecordedAir() {
        assertTrue(BlockTransitionStateMatcher.matchesRecordedSource(
                Blocks.WATER.defaultBlockState(),
                Blocks.AIR.defaultBlockState()
        ));
        assertTrue(BlockTransitionStateMatcher.matchesRecordedSource(
                Blocks.AIR.defaultBlockState(),
                Blocks.WATER.defaultBlockState()
        ));
        assertFalse(BlockTransitionStateMatcher.matchesRecordedSource(
                Blocks.WATER.defaultBlockState(),
                Blocks.DIRT.defaultBlockState()
        ));
    }
}
