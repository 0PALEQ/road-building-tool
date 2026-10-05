package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.StraightGeometry;
import com.cookiecraftmods.roadbuilder.test.MinecraftBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadStructurePlannerTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftBootstrap.bootstrap();
    }

    @Test
    void selectsEmbankmentSolidBaseAndDeckAtHeightBoundaries() {
        MapWorld world = new MapWorld(59);
        RoadStructurePlanner planner = new RoadStructurePlanner();

        BlockPlacementPlan fill = success(planner.addStructures(
                surface(64, 3, 3),
                world,
                10_000
        ));
        assertState(fill, new BlockPos(1, 63, 1), Blocks.DIRT.defaultBlockState());
        assertState(fill, new BlockPos(0, 63, 0),
                Blocks.DIRT.defaultBlockState());
        assertState(fill, new BlockPos(-1, 63, 1),
                Blocks.GRASS_BLOCK.defaultBlockState());
        assertState(fill, new BlockPos(-2, 62, 1),
                Blocks.GRASS_BLOCK.defaultBlockState());

        BlockPlacementPlan solid = success(planner.addStructures(
                surface(66, 3, 3),
                world,
                10_000
        ));
        assertState(solid, new BlockPos(1, 65, 1),
                Blocks.GRAY_CONCRETE.defaultBlockState());
        assertState(solid, new BlockPos(0, 65, 0),
                Blocks.POLISHED_ANDESITE.defaultBlockState());

        BlockPlacementPlan deck = success(planner.addStructures(
                surface(67, 3, 3),
                world,
                10_000
        ));
        assertState(deck, new BlockPos(1, 66, 1),
                Blocks.COBBLESTONE.defaultBlockState());
        assertState(deck, new BlockPos(1, 65, 1),
                Blocks.SMOOTH_STONE.defaultBlockState());
    }

    @Test
    void cutWallsReplaceOnlySolidEdgeTerrainAndClearVegetation() {
        MapWorld world = new MapWorld(63);
        BlockPos edgeTerrain = new BlockPos(1, 64, 0);
        BlockPos exposed = edgeTerrain.above();
        BlockPos exposedTwo = edgeTerrain.above(2);
        BlockPos tallGrass = edgeTerrain.above(3);
        BlockPos leaves = edgeTerrain.above(4);
        BlockPos water = new BlockPos(-1, 64, 0);
        BlockPos beyondEdge = new BlockPos(2, 64, 0);
        world.states.put(edgeTerrain, Blocks.STONE.defaultBlockState());
        world.states.put(exposed, Blocks.STONE.defaultBlockState());
        world.states.put(exposedTwo, Blocks.STONE.defaultBlockState());
        world.states.put(tallGrass, Blocks.TALL_GRASS.defaultBlockState());
        world.states.put(leaves, Blocks.OAK_LEAVES.defaultBlockState());
        world.states.put(water, Blocks.WATER.defaultBlockState());
        world.states.put(beyondEdge, Blocks.STONE.defaultBlockState());

        BlockPlacementPlan result = success(new RoadStructurePlanner()
                .addStructures(surface(64, 1, 1), world, 1_000));

        assertState(result, edgeTerrain,
                Blocks.POLISHED_ANDESITE.defaultBlockState());
        assertState(result, exposed,
                Blocks.POLISHED_ANDESITE.defaultBlockState());
        assertState(result, exposedTwo,
                Blocks.POLISHED_ANDESITE.defaultBlockState());
        assertState(result, tallGrass, Blocks.AIR.defaultBlockState());
        assertState(result, leaves, Blocks.AIR.defaultBlockState());
        assertFalse(result.positions().contains(water));
        assertFalse(result.positions().contains(beyondEdge));
    }

    @Test
    void flatGroundDoesNotProduceCutWalls() {
        MapWorld world = new MapWorld(64);
        BlockPlacementPlan result = success(new RoadStructurePlanner()
                .addStructures(surface(64, 1, 1), world, 1_000));

        assertFalse(result.positions().contains(new BlockPos(1, 64, 0)));
        assertFalse(result.positions().contains(new BlockPos(-1, 64, 0)));
        assertFalse(result.positions().contains(new BlockPos(0, 64, 1)));
        assertFalse(result.positions().contains(new BlockPos(0, 64, -1)));
    }

    @Test
    void singleExposedBlockDoesNotProduceCutWalls() {
        MapWorld world = new MapWorld(64);
        world.states.put(
                new BlockPos(1, 65, 0),
                Blocks.STONE.defaultBlockState()
        );
        BlockPlacementPlan result = success(new RoadStructurePlanner()
                .addStructures(surface(64, 1, 1), world, 1_000));

        assertFalse(result.positions().contains(new BlockPos(1, 64, 0)));
        assertFalse(result.positions().contains(new BlockPos(1, 65, 0)));
    }

    @Test
    void twoExposedBlocksProduceFullWallIncludingBase() {
        MapWorld world = new MapWorld(64);
        world.states.put(
                new BlockPos(1, 65, 0),
                Blocks.STONE.defaultBlockState()
        );
        world.states.put(
                new BlockPos(1, 66, 0),
                Blocks.STONE.defaultBlockState()
        );
        BlockPlacementPlan result = success(new RoadStructurePlanner()
                .addStructures(surface(64, 1, 1), world, 1_000));

        assertState(result, new BlockPos(1, 64, 0),
                Blocks.POLISHED_ANDESITE.defaultBlockState());
        assertState(result, new BlockPos(1, 65, 0),
                Blocks.POLISHED_ANDESITE.defaultBlockState());
        assertState(result, new BlockPos(1, 66, 0),
                Blocks.POLISHED_ANDESITE.defaultBlockState());
    }

    @Test
    void wallGateDoesNotCountExistingRoadBlockEntityOrFluidAsSolid() {
        MapWorld roadWorld = new MapWorld(64);
        roadWorld.states.put(
                new BlockPos(1, 65, 0),
                Blocks.STONE.defaultBlockState()
        );
        roadWorld.states.put(
                new BlockPos(1, 66, 0),
                Blocks.STONE.defaultBlockState()
        );
        BlockPlacementPlan roadResult = success(new RoadStructurePlanner()
                .addStructures(
                        surface(64, 1, 1),
                        roadWorld,
                        List.of(),
                        position -> position.equals(new BlockPos(1, 65, 0)),
                        1_000
                ));
        assertFalse(roadResult.positions().contains(new BlockPos(1, 64, 0)));

        MapWorld entityWorld = new MapWorld(64);
        entityWorld.states.put(
                new BlockPos(1, 65, 0),
                Blocks.STONE.defaultBlockState()
        );
        entityWorld.states.put(
                new BlockPos(1, 66, 0),
                Blocks.STONE.defaultBlockState()
        );
        entityWorld.blockEntities.add(new BlockPos(1, 66, 0));
        BlockPlacementPlan entityResult = success(new RoadStructurePlanner()
                .addStructures(surface(64, 1, 1), entityWorld, 1_000));
        assertFalse(entityResult.positions().contains(new BlockPos(1, 64, 0)));

        MapWorld fluidWorld = new MapWorld(64);
        fluidWorld.states.put(
                new BlockPos(1, 65, 0),
                Blocks.WATER.defaultBlockState()
        );
        fluidWorld.states.put(
                new BlockPos(1, 66, 0),
                Blocks.STONE.defaultBlockState()
        );
        BlockPlacementPlan fluidResult = success(new RoadStructurePlanner()
                .addStructures(surface(64, 1, 1), fluidWorld, 1_000));
        assertFalse(fluidResult.positions().contains(new BlockPos(1, 64, 0)));
    }

    @Test
    void wallDoesNotReplaceProtectedOrNonTerrainBase() {
        MapWorld protectedWorld = new MapWorld(64);
        protectedWorld.states.put(
                new BlockPos(1, 65, 0),
                Blocks.STONE.defaultBlockState()
        );
        protectedWorld.states.put(
                new BlockPos(1, 66, 0),
                Blocks.STONE.defaultBlockState()
        );
        BlockPlacementPlan protectedResult = success(new RoadStructurePlanner()
                .addStructures(
                        surface(64, 1, 1),
                        protectedWorld,
                        List.of(),
                        position -> position.equals(new BlockPos(1, 64, 0)),
                        1_000
                ));
        assertFalse(protectedResult.positions().contains(new BlockPos(1, 64, 0)));

        MapWorld nonTerrainWorld = new MapWorld(64);
        nonTerrainWorld.states.put(
                new BlockPos(1, 64, 0),
                Blocks.WHITE_CARPET.defaultBlockState()
        );
        nonTerrainWorld.states.put(
                new BlockPos(1, 65, 0),
                Blocks.STONE.defaultBlockState()
        );
        nonTerrainWorld.states.put(
                new BlockPos(1, 66, 0),
                Blocks.STONE.defaultBlockState()
        );
        BlockPlacementPlan nonTerrainResult = success(new RoadStructurePlanner()
                .addStructures(surface(64, 1, 1), nonTerrainWorld, 1_000));
        assertFalse(nonTerrainResult.positions().contains(new BlockPos(1, 64, 0)));
    }

    @Test
    void wallDoesNotReplaceFloatingTerrainAboveGap() {
        MapWorld airWorld = new MapWorld(64);
        airWorld.states.put(
                new BlockPos(1, 65, 0),
                Blocks.STONE.defaultBlockState()
        );
        airWorld.states.put(
                new BlockPos(1, 66, 0),
                Blocks.STONE.defaultBlockState()
        );
        airWorld.states.put(
                new BlockPos(1, 68, 0),
                Blocks.STONE.defaultBlockState()
        );
        BlockPlacementPlan airResult = success(new RoadStructurePlanner()
                .addStructures(surface(64, 1, 1), airWorld, 1_000));
        assertState(airResult, new BlockPos(1, 64, 0),
                Blocks.POLISHED_ANDESITE.defaultBlockState());
        assertState(airResult, new BlockPos(1, 65, 0),
                Blocks.POLISHED_ANDESITE.defaultBlockState());
        assertState(airResult, new BlockPos(1, 66, 0),
                Blocks.POLISHED_ANDESITE.defaultBlockState());
        assertFalse(airResult.positions().contains(new BlockPos(1, 68, 0)));

        MapWorld vegetationWorld = new MapWorld(64);
        vegetationWorld.states.put(
                new BlockPos(1, 65, 0),
                Blocks.STONE.defaultBlockState()
        );
        vegetationWorld.states.put(
                new BlockPos(1, 66, 0),
                Blocks.STONE.defaultBlockState()
        );
        vegetationWorld.states.put(
                new BlockPos(1, 67, 0),
                Blocks.TALL_GRASS.defaultBlockState()
        );
        vegetationWorld.states.put(
                new BlockPos(1, 68, 0),
                Blocks.STONE.defaultBlockState()
        );
        BlockPlacementPlan vegetationResult = success(new RoadStructurePlanner()
                .addStructures(surface(64, 1, 1), vegetationWorld, 1_000));
        assertState(vegetationResult, new BlockPos(1, 64, 0),
                Blocks.POLISHED_ANDESITE.defaultBlockState());
        assertState(vegetationResult, new BlockPos(1, 65, 0),
                Blocks.POLISHED_ANDESITE.defaultBlockState());
        assertState(vegetationResult, new BlockPos(1, 66, 0),
                Blocks.POLISHED_ANDESITE.defaultBlockState());
        assertState(vegetationResult, new BlockPos(1, 67, 0),
                Blocks.AIR.defaultBlockState());
        assertFalse(vegetationResult.positions().contains(new BlockPos(1, 68, 0)));
    }

    @Test
    void roadOnALedgeRaisesTheLowerTerrainIntoAnEmbankment() {
        MapWorld world = new MapWorld(59);
        world.states.put(
                new BlockPos(0, 63, 0),
                Blocks.GRASS_BLOCK.defaultBlockState()
        );

        BlockPlacementPlan result = success(new RoadStructurePlanner()
                .addStructures(surface(64, 1, 1), world, 2_000));

        assertState(result, new BlockPos(1, 63, 0),
                Blocks.GRASS_BLOCK.defaultBlockState());
        assertState(result, new BlockPos(2, 62, 0),
                Blocks.GRASS_BLOCK.defaultBlockState());
        assertState(result, new BlockPos(4, 60, 0),
                Blocks.GRASS_BLOCK.defaultBlockState());
    }

    @Test
    void waterForcesADeckAndPillarsReachIntoTheSeabed() {
        MapWorld world = new MapWorld(55);
        for (int x = -2; x <= 26; x++) {
            for (int z = -2; z <= 4; z++) {
                for (int y = 56; y <= 64; y++) {
                    world.states.put(
                            new BlockPos(x, y, z),
                            Blocks.WATER.defaultBlockState()
                    );
                }
            }
        }

        BlockPlacementPlan result = success(new RoadStructurePlanner()
                .addStructures(
                        surface(66, 3, 25),
                        world,
                        new StraightGeometry(
                                new RoadPoint(0.5, 66.0, 1.5),
                                new RoadPoint(24.5, 66.0, 1.5)
                        ),
                        3,
                        position -> false,
                        20_000
                ));

        assertState(result, new BlockPos(0, 65, 0),
                Blocks.SMOOTH_STONE.defaultBlockState());
        assertState(result, new BlockPos(0, 65, 1),
                Blocks.COBBLESTONE.defaultBlockState());
        assertState(result, new BlockPos(0, 64, 1),
                Blocks.SMOOTH_STONE.defaultBlockState());
        assertFalse(result.positions().contains(new BlockPos(0, 64, 0)));
        assertState(result, new BlockPos(2, 60, 1),
                Blocks.STONE_BRICKS.defaultBlockState());
        assertState(result, new BlockPos(2, 55, 1),
                Blocks.STONE_BRICKS.defaultBlockState());
        assertFalse(result.blocks().stream().anyMatch(block ->
                block.targetState().is(Blocks.POLISHED_ANDESITE)));
    }

    @Test
    void referenceStyleViaductUsesFlaredPierAndConnectedBarriers() {
        MapWorld world = new MapWorld(0);
        BlockPlacementPlan result = success(new RoadStructurePlanner()
                .addStructures(
                        surface(9, 19, 25),
                        world,
                        new StraightGeometry(
                                new RoadPoint(0.5, 9.0, 9.5),
                                new RoadPoint(24.5, 9.0, 9.5)
                        ),
                        19,
                        position -> false,
                        20_000
                ));

        assertState(result, new BlockPos(12, 8, 0),
                Blocks.SMOOTH_STONE.defaultBlockState());
        assertState(result, new BlockPos(12, 8, 1),
                Blocks.COBBLESTONE.defaultBlockState());
        assertFalse(result.positions().contains(new BlockPos(12, 7, 0)));
        assertState(result, new BlockPos(12, 7, 1),
                Blocks.SMOOTH_STONE.defaultBlockState());
        assertState(result, new BlockPos(12, 10, 0),
                Blocks.IRON_BARS.defaultBlockState()
                        .setValue(CrossCollisionBlock.EAST, true)
                        .setValue(CrossCollisionBlock.WEST, true));
        assertState(result, new BlockPos(12, 11, 0),
                Blocks.IRON_TRAPDOOR.defaultBlockState());

        assertState(result, new BlockPos(2, 6, 4),
                Blocks.STONE_BRICKS.defaultBlockState());
        assertState(result, new BlockPos(2, 6, 14),
                Blocks.STONE_BRICKS.defaultBlockState());
        assertFalse(result.positions().contains(new BlockPos(2, 6, 3)));
        assertState(result, new BlockPos(2, 5, 6),
                Blocks.STONE_BRICKS.defaultBlockState());
        assertState(result, new BlockPos(2, 4, 8),
                Blocks.STONE_BRICKS.defaultBlockState());
        assertState(result, new BlockPos(2, 0, 7),
                Blocks.STONE_BRICKS.defaultBlockState());
        assertState(result, new BlockPos(22, 4, 10),
                Blocks.STONE_BRICKS.defaultBlockState());
    }

    @Test
    void wideReferenceStyleViaductUsesPairedPiersAndCrossbeam() {
        MapWorld world = new MapWorld(0);
        BlockPlacementPlan result = success(new RoadStructurePlanner()
                .addStructures(
                        surface(9, 27, 25),
                        world,
                        new StraightGeometry(
                                new RoadPoint(0.5, 9.0, 13.5),
                                new RoadPoint(24.5, 9.0, 13.5)
                        ),
                        27,
                        position -> false,
                        20_000
                ));

        assertState(result, new BlockPos(2, 6, 3),
                Blocks.STONE_BRICKS.defaultBlockState());
        assertState(result, new BlockPos(2, 6, 7),
                Blocks.STONE_BRICKS.defaultBlockState());
        assertFalse(result.positions().contains(new BlockPos(2, 6, 8)));
        assertFalse(result.positions().contains(new BlockPos(2, 6, 18)));
        assertState(result, new BlockPos(2, 6, 19),
                Blocks.STONE_BRICKS.defaultBlockState());
        assertState(result, new BlockPos(2, 6, 23),
                Blocks.STONE_BRICKS.defaultBlockState());
        assertState(result, new BlockPos(2, 4, 8),
                Blocks.STONE_BRICKS.defaultBlockState());
        assertState(result, new BlockPos(2, 4, 18),
                Blocks.STONE_BRICKS.defaultBlockState());
        assertState(result, new BlockPos(2, 3, 13),
                Blocks.STONE_BRICKS.defaultBlockState());
        assertFalse(result.positions().contains(new BlockPos(2, 2, 13)));
        assertState(result, new BlockPos(2, 0, 6),
                Blocks.STONE_BRICKS.defaultBlockState());
        assertState(result, new BlockPos(22, 0, 20),
                Blocks.STONE_BRICKS.defaultBlockState());
    }

    @Test
    void diagonalViaductBarriersFormUnbranchedEdgePaths() {
        MapWorld world = new MapWorld(0);
        StraightGeometry geometry = new StraightGeometry(
                new RoadPoint(0.5, 9.0, 0.5),
                new RoadPoint(12.5, 9.0, 6.5)
        );
        BlockPlacementPlan surface = success(new StraightRoadPlanner().plan(
                new StraightRoadPlanRequest(
                        Level.OVERWORLD,
                        geometry,
                        new ResolvedRoadSurface(
                                5,
                                Blocks.GRAY_CONCRETE.defaultBlockState()
                        )
                ),
                20_000,
                20_000
        ));
        BlockPlacementPlan result = success(new RoadStructurePlanner()
                .addStructures(
                        surface,
                        world,
                        geometry,
                        5,
                        position -> false,
                        20_000
                ));

        Set<BlockPos> barriers = result.blocks().stream()
                .filter(block -> block.targetState().is(Blocks.IRON_BARS))
                .map(PlannedBlock::position)
                .collect(java.util.stream.Collectors.toSet());
        assertFalse(barriers.isEmpty());
        assertTrue(barriers.stream().allMatch(position ->
                cardinalNeighborCount(position, barriers) <= 2));
    }

    @Test
    void crossingOpensBothRailingsAndRemovesOwnedBarrierBlocks() {
        MapWorld world = new MapWorld(0);
        Set<BlockPos> existingRoad = new java.util.HashSet<>();
        for (int z = -1; z <= 3; z++) {
            existingRoad.add(new BlockPos(4, 9, z));
        }
        for (int z : new int[]{0, 2}) {
            BlockPos barrier = new BlockPos(4, 10, z);
            BlockPos cap = barrier.above();
            existingRoad.add(barrier);
            existingRoad.add(cap);
            world.states.put(
                    barrier,
                    Blocks.IRON_BARS.defaultBlockState()
            );
            world.states.put(
                    cap,
                    Blocks.IRON_TRAPDOOR.defaultBlockState()
            );
        }
        StraightGeometry geometry = new StraightGeometry(
                new RoadPoint(0.5, 9.0, 1.5),
                new RoadPoint(8.5, 9.0, 1.5)
        );
        BlockPlacementPlan result = success(new RoadStructurePlanner()
                .addStructures(
                        surface(9, 3, 9),
                        world,
                        geometry,
                        3,
                        existingRoad::contains,
                        20_000
                ));

        assertState(result, new BlockPos(4, 10, 0),
                Blocks.AIR.defaultBlockState());
        assertState(result, new BlockPos(4, 11, 0),
                Blocks.AIR.defaultBlockState());
        assertState(result, new BlockPos(4, 10, 2),
                Blocks.AIR.defaultBlockState());
        assertState(result, new BlockPos(4, 11, 2),
                Blocks.AIR.defaultBlockState());
        assertState(result, new BlockPos(3, 10, 0),
                Blocks.IRON_BARS.defaultBlockState()
                        .setValue(CrossCollisionBlock.EAST, false)
                        .setValue(CrossCollisionBlock.WEST, true));
    }

    @Test
    void geometrylessContinuationDoesNotFenceAcrossTheRoadEnd() {
        MapWorld world = new MapWorld(0);
        Set<BlockPos> existingRoad = Set.of(
                new BlockPos(-1, 9, 0),
                new BlockPos(-1, 9, 1),
                new BlockPos(-1, 9, 2)
        );
        BlockPlacementPlan result = success(new RoadStructurePlanner()
                .addStructures(
                        surface(9, 3, 3),
                        world,
                        List.of(),
                        existingRoad::contains,
                        20_000
                ));

        assertFalse(result.positions().contains(new BlockPos(0, 10, 1)));
        assertBlock(result, new BlockPos(0, 10, 0), Blocks.IRON_BARS);
    }

    @Test
    void ownedSupportBlocksDoNotTurnAnElevatedNodeIntoAnEmbankment() {
        MapWorld world = new MapWorld(0);
        Set<BlockPos> ownedSupports = new java.util.HashSet<>();
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                for (int y = 1; y <= 7; y++) {
                    BlockPos position = new BlockPos(x, y, z);
                    ownedSupports.add(position);
                    world.states.put(
                            position,
                            Blocks.DIRT.defaultBlockState()
                    );
                }
            }
        }

        BlockPlacementPlan result = success(new RoadStructurePlanner()
                .addStructures(
                        surface(9, 3, 3),
                        world,
                        List.of(),
                        ownedSupports::contains,
                        20_000
                ));

        assertState(result, new BlockPos(1, 8, 1),
                Blocks.COBBLESTONE.defaultBlockState());
        assertState(result, new BlockPos(1, 7, 1),
                Blocks.SMOOTH_STONE.defaultBlockState());
        assertFalse(result.blocks().stream().anyMatch(block ->
                block.targetState().is(Blocks.DIRT)
                        || block.targetState().is(Blocks.GRASS_BLOCK)));
    }

    @Test
    void elevatedApproachKeepsItsNodeEdgeOnADeck() {
        MapWorld world = new MapWorld(0);
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                for (int y = 1; y <= 6; y++) {
                    world.states.put(
                            new BlockPos(x, y, z),
                            Blocks.STONE.defaultBlockState()
                    );
                }
            }
        }
        StraightGeometry approach = new StraightGeometry(
                new RoadPoint(0.5, 9.0, 1.5),
                new RoadPoint(20.5, 9.0, 1.5)
        );

        BlockPlacementPlan result = success(new RoadStructurePlanner()
                .addStructures(
                        surface(9, 3, 3),
                        world,
                        List.of(new RoadStructurePlanner.StructureSpan(
                                approach,
                                3
                        )),
                        position -> false,
                        20_000
                ));

        assertState(result, new BlockPos(1, 8, 1),
                Blocks.COBBLESTONE.defaultBlockState());
        assertState(result, new BlockPos(1, 7, 1),
                Blocks.SMOOTH_STONE.defaultBlockState());
        assertFalse(result.blocks().stream().anyMatch(block ->
                block.targetState().is(Blocks.DIRT)
                        || block.targetState().is(Blocks.GRASS_BLOCK)));
    }

    @Test
    void gradeSeparatedRoadBelowForcesADeckWithoutAColumnThroughIt() {
        MapWorld world = new MapWorld(Integer.MIN_VALUE);
        BlockPos crossedRoad = new BlockPos(0, 64, 0);
        world.states.put(
                crossedRoad,
                Blocks.GRAY_CONCRETE.defaultBlockState()
        );
        BlockPlacementPlan result = success(new RoadStructurePlanner()
                .addStructures(
                        surface(71, 1, 1),
                        world,
                        null,
                        1,
                        crossedRoad::equals,
                        1_000
                ));

        assertState(result, new BlockPos(0, 70, 0),
                Blocks.SMOOTH_STONE.defaultBlockState());
        assertFalse(result.positions().contains(new BlockPos(0, 69, 0)));
        assertFalse(result.positions().contains(crossedRoad));
    }

    @Test
    void retainingWallsAreLimitedToLongitudinalRoadSides() {
        MapWorld world = new MapWorld(59);
        BlockPos sideCut = new BlockPos(2, 66, -1);
        BlockPos frontCut = new BlockPos(-1, 66, 1);
        for (int y = 66;
                y <= 66 + RoadTunnelPlanner.FREE_HEIGHT + 1;
                y++) {
            world.states.put(
                    new BlockPos(sideCut.getX(), y, sideCut.getZ()),
                    Blocks.STONE.defaultBlockState()
            );
        }
        world.states.put(frontCut, Blocks.STONE.defaultBlockState());

        BlockPlacementPlan result = success(new RoadStructurePlanner()
                .addStructures(
                        surface(66, 3, 5),
                        world,
                        new StraightGeometry(
                                new RoadPoint(0.5, 66.0, 1.5),
                                new RoadPoint(4.5, 66.0, 1.5)
                        ),
                        3,
                        position -> false,
                        10_000
                ));

        assertState(result, new BlockPos(2, 65, 0),
                Blocks.POLISHED_ANDESITE.defaultBlockState());
        assertState(result, new BlockPos(0, 65, 1),
                Blocks.GRAY_CONCRETE.defaultBlockState());
        assertState(result, new BlockPos(4, 65, 1),
                Blocks.GRAY_CONCRETE.defaultBlockState());
        assertState(result, sideCut,
                Blocks.POLISHED_ANDESITE.defaultBlockState());
        assertState(
                result,
                sideCut.above(RoadTunnelPlanner.FREE_HEIGHT + 1),
                Blocks.POLISHED_ANDESITE.defaultBlockState()
        );
        assertFalse(result.positions().contains(frontCut));
    }

    private static BlockPlacementPlan surface(int y, int width, int length) {
        List<PlannedBlock> blocks = new ArrayList<>();
        for (int x = 0; x < length; x++) {
            for (int z = 0; z < width; z++) {
                blocks.add(new PlannedBlock(
                        new BlockPos(x, y, z),
                        Blocks.GRAY_CONCRETE.defaultBlockState()
                ));
            }
        }
        return BlockPlacementPlan.create(Level.OVERWORLD, blocks);
    }

    private static BlockPlacementPlan success(BlockPlanResult result) {
        return assertInstanceOf(BlockPlanResult.Success.class, result).plan();
    }

    private static int cardinalNeighborCount(
            BlockPos position,
            Set<BlockPos> positions
    ) {
        int count = 0;
        if (positions.contains(position.north())) {
            count++;
        }
        if (positions.contains(position.east())) {
            count++;
        }
        if (positions.contains(position.south())) {
            count++;
        }
        if (positions.contains(position.west())) {
            count++;
        }
        return count;
    }

    private static void assertBlock(
            BlockPlacementPlan plan,
            BlockPos position,
            net.minecraft.world.level.block.Block block
    ) {
        assertTrue(plan.blocks().stream().anyMatch(planned ->
                planned.position().equals(position)
                        && planned.targetState().is(block)));
    }

    private static void assertState(
            BlockPlacementPlan plan,
            BlockPos position,
            BlockState state
    ) {
        assertTrue(plan.blocks().stream().anyMatch(block ->
                block.position().equals(position)
                        && block.targetState().equals(state)), () ->
                "expected " + state + " at " + position + "; planned "
                        + plan.blocks().stream()
                        .filter(block -> block.position().equals(position))
                        .toList());
    }

    private static final class MapWorld implements PlacementBlockWorld {
        private final int groundY;
        private final Map<BlockPos, BlockState> states = new HashMap<>();
        private final Set<BlockPos> blockEntities = new HashSet<>();

        private MapWorld(int groundY) {
            this.groundY = groundY;
        }

        @Override
        public ResourceKey<Level> dimension() {
            return Level.OVERWORLD;
        }

        @Override
        public BlockState blockState(BlockPos position) {
            return states.getOrDefault(
                    position,
                    position.getY() <= groundY
                            ? Blocks.STONE.defaultBlockState()
                            : Blocks.AIR.defaultBlockState()
            );
        }

        @Override
        public boolean hasBlockEntity(BlockPos position) {
            return blockEntities.contains(position);
        }

        @Override
        public boolean setBlockState(BlockPos position, BlockState state) {
            states.put(position.immutable(), state);
            return true;
        }
    }

}
