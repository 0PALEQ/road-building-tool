package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.test.MinecraftBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockPlacementPlanTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftBootstrap.bootstrap();
    }

    @Test
    void derivesExactImmutableFactsAndStablePlacementOrder() {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(16, 70, 0);
        List<PlannedBlock> input = new ArrayList<>(List.of(
                new PlannedBlock(mutable, Blocks.STONE.defaultBlockState()),
                new PlannedBlock(new BlockPos(-1, 64, 0), Blocks.DIRT.defaultBlockState()),
                new PlannedBlock(new BlockPos(0, 65, 0), Blocks.GRASS_BLOCK.defaultBlockState())
        ));

        BlockPlacementPlan plan = BlockPlacementPlan.create(Level.OVERWORLD, input);
        mutable.set(100, 100, 100);
        input.clear();

        assertEquals(Level.OVERWORLD, plan.dimension());
        assertEquals(3, plan.affectedBlockCount());
        assertEquals(new ConstructionBounds(new BlockPos(-1, 64, 0), new BlockPos(16, 70, 0)), plan.bounds());
        assertEquals(List.of(new ChunkPos(-1, 0), new ChunkPos(0, 0), new ChunkPos(1, 0)), plan.touchedChunks());
        assertEquals(List.of(
                new BlockPos(-1, 64, 0),
                new BlockPos(0, 65, 0),
                new BlockPos(16, 70, 0)
        ), plan.blocks().stream().map(PlannedBlock::position).toList());
        assertEquals(
                plan.blocks().stream().map(PlannedBlock::position).toList(),
                plan.positions()
        );
        assertNotSame(mutable, plan.blocks().get(2).position());
        assertThrows(UnsupportedOperationException.class,
                () -> plan.blocks().add(new PlannedBlock(BlockPos.ZERO, Blocks.AIR.defaultBlockState())));
    }

    @Test
    void usesExactTouchedChunksInsteadOfTheBoundingRectangle() {
        BlockPlacementPlan plan = BlockPlacementPlan.create(Level.OVERWORLD, List.of(
                new PlannedBlock(new BlockPos(-1, 64, -1), Blocks.STONE.defaultBlockState()),
                new PlannedBlock(new BlockPos(32, 64, 32), Blocks.STONE.defaultBlockState())
        ));

        assertEquals(2, plan.touchedChunks().size());
        assertTrue(plan.bounds().coveredChunkCount() > plan.touchedChunks().size());
    }

    @Test
    void rejectsEmptyNullAndDuplicateTargets() {
        PlannedBlock origin = new PlannedBlock(BlockPos.ZERO, Blocks.STONE.defaultBlockState());

        assertThrows(IllegalArgumentException.class,
                () -> BlockPlacementPlan.create(Level.OVERWORLD, List.of()));
        assertThrows(NullPointerException.class,
                () -> BlockPlacementPlan.create(Level.OVERWORLD, null));
        assertThrows(IllegalArgumentException.class, () -> BlockPlacementPlan.create(Level.OVERWORLD, List.of(
                origin,
                new PlannedBlock(BlockPos.ZERO, Blocks.DIRT.defaultBlockState())
        )));
    }
}
