package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.test.MinecraftBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MaterializationRebuildWorldTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftBootstrap.bootstrap();
    }

    @Test
    void restoresTerrainWithoutChangingTheWorldOrLedger() {
        var pos = new BlockPos(1, 64, 2);
        var road = PlacementOperationId.create();
        var ground = Blocks.STONE.defaultBlockState();
        var surface = Blocks.GRAY_CONCRETE.defaultBlockState();
        var ledger = PlacementOwnershipLedger.restore(Level.OVERWORLD, Map.of(pos,
                PositionChangeStack.of(new BlockChangeLayer(road, ground, surface))), () -> {});
        var world = world(surface);
        var view = new MaterializationRebuildWorld(world, ledger, List.of(road));
        assertEquals(ground, view.blockState(pos));
        assertFalse(view.isMaterializedRoadPosition(pos));
        assertEquals(surface, world.blockState(pos));
        assertTrue(ledger.operation(road).isPresent());
        assertThrows(UnsupportedOperationException.class, () -> view.setBlockState(pos, ground));
    }

    @Test
    void retainsOtherRoadLayersAndPlayerEdits() {
        var pos = new BlockPos(1, 64, 2);
        var road = PlacementOperationId.create();
        var neighbor = PlacementOperationId.create();
        var ground = Blocks.STONE.defaultBlockState();
        var surface = Blocks.GRAY_CONCRETE.defaultBlockState();
        var neighborSurface = Blocks.WHITE_CONCRETE.defaultBlockState();
        var ledger = PlacementOwnershipLedger.restore(Level.OVERWORLD, Map.of(pos,
                PositionChangeStack.of(new BlockChangeLayer(road, ground, surface))
                        .push(new BlockChangeLayer(neighbor, surface, neighborSurface))), () -> {});
        var view = new MaterializationRebuildWorld(world(neighborSurface), ledger, List.of(road));
        assertEquals(neighborSurface, view.blockState(pos));
        assertTrue(view.isMaterializedRoadPosition(pos));
        var both = new MaterializationRebuildWorld(world(neighborSurface), ledger, List.of(road, neighbor));
        assertEquals(ground, both.blockState(pos));
        assertFalse(both.isMaterializedRoadPosition(pos));
        var edited = new MaterializationRebuildWorld(world(Blocks.GOLD_BLOCK.defaultBlockState()),
                ledger, List.of(road, neighbor));
        assertEquals(Blocks.GOLD_BLOCK.defaultBlockState(), edited.blockState(pos));
    }

    private static PlacementBlockWorld world(BlockState state) {
        return new PlacementBlockWorld() {
            public ResourceKey<Level> dimension() { return Level.OVERWORLD; }
            public BlockState blockState(BlockPos pos) { return state; }
            public boolean hasBlockEntity(BlockPos pos) { return false; }
            public boolean setBlockState(BlockPos pos, BlockState target) {
                throw new AssertionError("Planning must never write to the live world");
            }
        };
    }
}
