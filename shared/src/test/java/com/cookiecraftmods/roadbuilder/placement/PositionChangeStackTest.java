package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.test.MinecraftBootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PositionChangeStackTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftBootstrap.bootstrap();
    }

    @Test
    void removingHiddenLayerDoesNotRebaseAcrossExternalEditSeam() {
        PlacementOperationId operationA = new PlacementOperationId(new UUID(0, 100));
        PlacementOperationId operationB = new PlacementOperationId(new UUID(0, 101));
        BlockChangeLayer layerA = new BlockChangeLayer(
                operationA,
                Blocks.DIRT.defaultBlockState(),
                Blocks.STONE.defaultBlockState()
        );
        BlockChangeLayer layerB = new BlockChangeLayer(
                operationB,
                Blocks.GRASS_BLOCK.defaultBlockState(),
                Blocks.SAND.defaultBlockState()
        );

        StackRemoval removal = PositionChangeStack.of(layerA).push(layerB).remove(operationA);

        assertFalse(removal.wasTop());
        assertFalse(removal.successorRebased());
        PositionChangeStack updated = removal.updatedStack().orElseThrow();
        assertEquals(Blocks.GRASS_BLOCK.defaultBlockState(), updated.top().beforeState());
        assertEquals(Blocks.SAND.defaultBlockState(), updated.top().afterState());
        assertTrue(updated.isTop(operationB));
    }

    @Test
    void removingHiddenLayerRebasesAcrossUpdateDrivenPropertyChange() {
        PlacementOperationId operationA = new PlacementOperationId(
                new UUID(0, 102)
        );
        PlacementOperationId operationB = new PlacementOperationId(
                new UUID(0, 103)
        );
        var originalLeaves = Blocks.OAK_LEAVES.defaultBlockState()
                .setValue(BlockStateProperties.DISTANCE, 1);
        var updatedLeaves = originalLeaves.setValue(
                BlockStateProperties.DISTANCE,
                2
        );
        BlockChangeLayer layerA = new BlockChangeLayer(
                operationA,
                Blocks.DIRT.defaultBlockState(),
                originalLeaves
        );
        BlockChangeLayer layerB = new BlockChangeLayer(
                operationB,
                updatedLeaves,
                Blocks.STONE.defaultBlockState()
        );

        StackRemoval removal = PositionChangeStack.of(layerA)
                .push(layerB)
                .remove(operationA);

        assertTrue(removal.successorRebased());
        assertEquals(
                Blocks.DIRT.defaultBlockState(),
                removal.updatedStack().orElseThrow().top().beforeState()
        );
    }
}
