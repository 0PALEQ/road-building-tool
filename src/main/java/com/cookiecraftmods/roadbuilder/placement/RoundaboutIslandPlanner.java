package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.geometry.RoundaboutGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

import java.util.LinkedHashMap;
import java.util.Map;

/** Reuses the old junction surface while removing through markings from the island. */
public final class RoundaboutIslandPlanner {
    private RoundaboutIslandPlanner() { }

    public static BlockPlacementPlan addIsland(BlockPlacementPlan plan, RoundaboutGeometry ring, double width) {
        double radius = ring.radius() - width / 2.0 - 1.0;
        Map<BlockPos, PlannedBlock> blocks = new LinkedHashMap<>();
        plan.blocks().forEach(block -> blocks.put(block.position(), block));
        int y = (int) Math.floor(ring.center().y() + 0.5);
        for (int x = (int) Math.floor(ring.center().x() - radius); x <= Math.ceil(ring.center().x() + radius); x++) {
            for (int z = (int) Math.floor(ring.center().z() - radius); z <= Math.ceil(ring.center().z() + radius); z++) {
                if (Math.hypot(x + 0.5 - ring.center().x(), z + 0.5 - ring.center().z()) > radius) continue;
                BlockPos position = new BlockPos(x, y, z);
                blocks.put(position, new PlannedBlock(position, Blocks.GRASS_BLOCK.defaultBlockState()));
            }
        }
        return BlockPlacementPlan.create(plan.dimension(), blocks.values());
    }
}
