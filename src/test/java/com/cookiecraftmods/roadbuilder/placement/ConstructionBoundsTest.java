package com.cookiecraftmods.roadbuilder.placement;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConstructionBoundsTest {
    @Test
    void copiesMutablePositionsAndCoversCompleteBlocks() {
        BlockPos.MutableBlockPos mutableMinimum = new BlockPos.MutableBlockPos(1, 2, 3);
        ConstructionBounds bounds = new ConstructionBounds(mutableMinimum, new BlockPos(4, 5, 6));
        mutableMinimum.set(10, 20, 30);

        assertNotSame(mutableMinimum, bounds.minimum());
        assertEquals(new BlockPos(1, 2, 3), bounds.minimum());
        assertEquals(1.0, bounds.asBox().minX);
        assertEquals(7.0, bounds.asBox().maxZ);
    }

    @Test
    void calculatesDistanceToFarthestBlockCenter() {
        ConstructionBounds bounds = new ConstructionBounds(
                new BlockPos(-1, -2, -3),
                new BlockPos(3, 4, 5)
        );

        assertEquals(3.5 * 3.5 + 4.5 * 4.5 + 5.5 * 5.5, bounds.maximumSquaredDistanceFrom(0, 0, 0));
    }

    @Test
    void calculatesCoveredChunksAcrossNegativeCoordinates() {
        ConstructionBounds bounds = new ConstructionBounds(
                new BlockPos(-17, 0, -1),
                new BlockPos(16, 0, 31)
        );

        assertEquals(-2, bounds.minimumChunkX());
        assertEquals(1, bounds.maximumChunkX());
        assertEquals(-1, bounds.minimumChunkZ());
        assertEquals(1, bounds.maximumChunkZ());
        assertEquals(12, bounds.coveredChunkCount());
    }

    @Test
    void rejectsReversedOrInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> new ConstructionBounds(
                new BlockPos(2, 0, 0),
                new BlockPos(1, 0, 0)
        ));
        assertThrows(NullPointerException.class, () -> ConstructionBounds.at(null));

        ConstructionBounds bounds = ConstructionBounds.at(BlockPos.ZERO);
        assertThrows(IllegalArgumentException.class,
                () -> bounds.maximumSquaredDistanceFrom(Double.NaN, 0, 0));
    }
}
