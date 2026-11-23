package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.RoundaboutGeometry;
import com.cookiecraftmods.roadbuilder.test.MinecraftBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoundaboutIslandPlannerTest {
    @BeforeAll
    static void bootstrap() { MinecraftBootstrap.bootstrap(); }

    @Test
    void replacesOldCenterMarkingsAndKeepsCarriagewayAndApproaches() {
        BlockPos center = new BlockPos(0, 64, 0);
        BlockPos lane = new BlockPos(12, 64, 0);
        BlockPos approach = new BlockPos(20, 64, 0);
        var asphalt = Blocks.GRAY_CONCRETE.defaultBlockState();
        var plan = BlockPlacementPlan.create(Level.OVERWORLD, List.of(
                new PlannedBlock(center, Blocks.WHITE_CONCRETE.defaultBlockState()),
                new PlannedBlock(lane, asphalt), new PlannedBlock(approach, asphalt)));
        var result = RoundaboutIslandPlanner.addIsland(plan,
                new RoundaboutGeometry(new RoadPoint(0.5, 64, 0.5), new RoadPoint(12.5, 64, 0.5)), 7);
        assertTrue(result.blocks().stream().anyMatch(block -> block.position().equals(center)
                && block.targetState().is(Blocks.GRASS_BLOCK)));
        for (BlockPos position : List.of(lane, approach)) {
            assertEquals(asphalt, result.blocks().stream().filter(block -> block.position().equals(position))
                    .findFirst().orElseThrow().targetState());
        }
        assertEquals(result.blocks().size(), result.positions().stream().distinct().count());
    }
}
