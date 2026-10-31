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

    @Test
    void clearsEdgeTrunksTallRemnantsAndConnectedCanopyWithoutExcavatingTheMargin() {
        BlockPos road = new BlockPos(0, 64, 0);
        var surface = BlockPlacementPlan.create(Level.OVERWORLD,
                List.of(new PlannedBlock(road, Blocks.GRAY_CONCRETE.defaultBlockState())));
        MapWorld world = new MapWorld();
        BlockPos trunk = road.offset(3, 0, 0);
        for (int y = 0; y <= 19; y++) world.states.put(trunk.above(y), Blocks.ACACIA_LOG.defaultBlockState());
        for (int x = 3; x <= 8; x++) world.states.put(road.offset(x, 20, 0), Blocks.ACACIA_LEAVES.defaultBlockState());
        world.states.put(trunk.above(20), Blocks.ACACIA_LEAVES.defaultBlockState());
        BlockPos terrain = road.offset(-2, 1, 0);
        BlockPos building = road.offset(2, 1, 2);
        BlockPos water = road.offset(1, 1, 2);
        BlockPos neighboringTree = road.offset(8, 1, 2);
        world.states.put(terrain, Blocks.STONE.defaultBlockState());
        world.states.put(building, Blocks.BRICKS.defaultBlockState());
        world.states.put(water, Blocks.WATER.defaultBlockState());
        world.states.put(neighboringTree, Blocks.OAK_LOG.defaultBlockState());
        var result = assertInstanceOf(BlockPlanResult.Success.class,
                new RoadClearancePlanner().addClearance(surface, world, 1000)).plan();
        var removed = result.blocks().stream().filter(block -> block.targetState().isAir())
                .map(PlannedBlock::position).collect(java.util.stream.Collectors.toSet());
        assertTrue(removed.contains(trunk));
        assertTrue(removed.contains(trunk.above(18)));
        assertTrue(removed.contains(road.offset(8, 20, 0)));
        assertTrue(!removed.contains(terrain));
        assertTrue(!removed.contains(building));
        assertTrue(!removed.contains(water));
        assertTrue(!removed.contains(neighboringTree));
    }

    @Test
    void vegetationMarginRespectsPreservedBlocksAndThePlanBudget() {
        BlockPos road = new BlockPos(0, 64, 0);
        var surface = BlockPlacementPlan.create(Level.OVERWORLD,
                List.of(new PlannedBlock(road, Blocks.GRAY_CONCRETE.defaultBlockState())));
        MapWorld world = new MapWorld();
        BlockPos protectedTree = road.offset(3, 1, 0);
        world.states.put(protectedTree, Blocks.OAK_LOG.defaultBlockState());
        var protectedPlan = assertInstanceOf(BlockPlanResult.Success.class,
                new RoadClearancePlanner().addClearance(surface, world, protectedTree::equals, 100)).plan();
        assertTrue(protectedPlan.blocks().stream().noneMatch(block -> block.position().equals(protectedTree)));
        assertEquals(BlockPlanFailureCode.BLOCK_LIMIT_EXCEEDED, assertInstanceOf(BlockPlanResult.Failure.class,
                new RoadClearancePlanner().addClearance(surface, world, 1)).code());
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
