package com.cookiecraftmods.roadbuilder.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlacementOperationTest {
    @Test
    void derivesAnImmutableExactAuthorizationFootprint() {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(32, 70, 32);
        List<BlockPos> positions = new ArrayList<>(List.of(
                mutable,
                new BlockPos(-1, 64, -1),
                new BlockPos(0, 65, 0)
        ));

        PlacementOperation operation = PlacementOperation.create(
                new PlacementOperationId(UUID.randomUUID()),
                Level.OVERWORLD,
                positions
        );
        mutable.set(100, 100, 100);
        positions.clear();

        assertEquals(Level.OVERWORLD, operation.dimension());
        assertEquals(3, operation.affectedBlockCount());
        assertEquals(new ConstructionBounds(new BlockPos(-1, 64, -1), new BlockPos(32, 70, 32)), operation.bounds());
        assertEquals(List.of(new ChunkPos(-1, -1), new ChunkPos(0, 0), new ChunkPos(2, 2)), operation.touchedChunks());
        assertEquals(List.of(
                new BlockPos(-1, 64, -1),
                new BlockPos(0, 65, 0),
                new BlockPos(32, 70, 32)
        ), operation.positions());
        assertThrows(UnsupportedOperationException.class, () -> operation.positions().add(BlockPos.ZERO));
        assertThrows(
                UnsupportedOperationException.class,
                () -> operation.touchedChunks().add(new ChunkPos(0, 0))
        );
    }
}
