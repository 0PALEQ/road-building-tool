package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.test.MinecraftBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadClearancePlannerTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftBootstrap.bootstrap();
    }

    @Test
    void addsOnlyRealObstructionsWithinTheBoundedClearanceColumn() {
        BlockPos surface = new BlockPos(4, 64, 7);
        BlockPlacementPlan surfacePlan = BlockPlacementPlan.create(
                Level.OVERWORLD,
                List.of(new PlannedBlock(
                        surface,
                        Blocks.GRAY_CONCRETE.defaultBlockState()
                ))
        );
        MapWorld world = new MapWorld();
        world.states.put(surface.above(), Blocks.OAK_LOG.defaultBlockState());
        world.states.put(surface.above(4), Blocks.OAK_LEAVES.defaultBlockState());
        world.states.put(surface.above(6), Blocks.STONE.defaultBlockState());

        BlockPlacementPlan result = assertInstanceOf(
                BlockPlanResult.Success.class,
                new RoadClearancePlanner().addClearance(
                        surfacePlan,
                        world,
                        5,
                        100
                )
        ).plan();

        assertEquals(3, result.affectedBlockCount());
        assertTrue(result.blocks().stream().anyMatch(block ->
                block.position().equals(surface)
                        && block.targetState().is(Blocks.GRAY_CONCRETE)));
        assertTrue(result.blocks().stream().anyMatch(block ->
                block.position().equals(surface.above())
                        && block.targetState().isAir()));
        assertTrue(result.blocks().stream().anyMatch(block ->
                block.position().equals(surface.above(4))
                        && block.targetState().isAir()));
    }

    @Test
    void preservesMaterializedRoadsAtGradeSeparatedCrossings() {
        BlockPos surface = new BlockPos(0, 58, 0);
        BlockPos obstruction = new BlockPos(0, 60, 0);
        BlockPos crossedRoad = new BlockPos(0, 64, 0);
        BlockPlacementPlan surfacePlan = BlockPlacementPlan.create(
                Level.OVERWORLD,
                List.of(new PlannedBlock(
                        surface,
                        Blocks.GRAY_CONCRETE.defaultBlockState()
                ))
        );
        MapWorld world = new MapWorld();
        world.states.put(obstruction, Blocks.OAK_LOG.defaultBlockState());
        world.states.put(crossedRoad,
                Blocks.GRAY_CONCRETE.defaultBlockState());

        BlockPlacementPlan result = assertInstanceOf(
                BlockPlanResult.Success.class,
                new RoadClearancePlanner().addClearance(
                        surfacePlan,
                        world,
                        crossedRoad::equals,
                        100
                )
        ).plan();

        assertTrue(result.blocks().stream().anyMatch(block ->
                block.position().equals(obstruction)
                        && block.targetState().isAir()));
        assertTrue(result.blocks().stream().noneMatch(block ->
                block.position().equals(crossedRoad)));
    }

    private static final class MapWorld implements PlacementBlockWorld {
        private final Map<BlockPos, BlockState> states = new HashMap<>();

        @Override
        public ResourceKey<Level> dimension() {
            return Level.OVERWORLD;
        }

        @Override
        public BlockState blockState(BlockPos position) {
            return states.getOrDefault(position, Blocks.AIR.defaultBlockState());
        }

        @Override
        public boolean hasBlockEntity(BlockPos position) {
            return false;
        }

        @Override
        public boolean setBlockState(BlockPos position, BlockState state) {
            states.put(position.immutable(), state);
            return true;
        }
    }
}
