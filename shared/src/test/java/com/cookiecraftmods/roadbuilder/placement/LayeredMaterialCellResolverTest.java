package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.test.MinecraftBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LayeredMaterialCellResolverTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.bootstrap();
    }

    @Test
    void resolutionAndOwnershipStackAreCommutative() {
        BlockPos position = new BlockPos(2, 4, 6);
        List<MaterialCell> cells = List.of(
                new MaterialCell(
                        position,
                        Blocks.STONE.defaultBlockState(),
                        MaterialLayer.SEGMENT_SURFACE,
                        "segment",
                        "b"
                ),
                new MaterialCell(
                        position,
                        Blocks.GRAY_CONCRETE.defaultBlockState(),
                        MaterialLayer.JUNCTION_SURFACE,
                        "node",
                        "j"
                ),
                new MaterialCell(
                        position,
                        Blocks.WHITE_CONCRETE.defaultBlockState(),
                        MaterialLayer.JUNCTION_MARKING,
                        "node-marking",
                        "j"
                )
        );
        var resolver = new LayeredMaterialCellResolver();
        var forward = resolver.resolve(cells);
        List<MaterialCell> reversed = new ArrayList<>(cells);
        java.util.Collections.reverse(reversed);
        var backward = resolver.resolve(reversed);

        assertEquals(forward, backward);
        assertEquals(
                Blocks.WHITE_CONCRETE.defaultBlockState(),
                forward.get(position).state()
        );
        assertEquals(3, forward.get(position).ownershipStack().size());
    }
}
