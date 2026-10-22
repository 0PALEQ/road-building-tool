package com.cookiecraftmods.roadbuilder.placement;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BlockPositionSetsTest {
    @Test
    void denseRoadFootprintsCanBeCopiedAndQueriedWithoutLongProbingStalls() {
        var positions = new ArrayList<BlockPos>();
        for (int x = 0; x < 256; x++) {
            for (int z = 0; z < 64; z++) {
                positions.add(new BlockPos(4096 + x, 64, 4096 + z));
            }
        }
        assertTimeout(Duration.ofSeconds(5), () -> {
            var snapshot = BlockPositionSets.copyOf(positions);
            assertEquals(positions.size(), snapshot.size());
            assertTrue(snapshot.containsAll(positions));
        });
    }

    @Test
    void snapshotCannotBeChangedThroughItsSourceOrMutablePositions() {
        var position = new BlockPos.MutableBlockPos(1, 2, 3);
        var source = new ArrayList<BlockPos>(List.of(position));
        var snapshot = BlockPositionSets.copyOf(source);
        position.set(4, 5, 6);
        source.clear();
        assertEquals(java.util.Set.of(new BlockPos(1, 2, 3)), snapshot);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.clear());
    }
}
