package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

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
    void curvedBridgeMediansSurviveSurfaceLayerMergingAsStone() {
        var gray = MinecraftIds.id("minecraft", "gray_concrete");
        var grass = MinecraftIds.id("minecraft", "grass_block");
        var lane = new com.cookiecraftmods.roadbuilder.preset.LaneComponent(
                com.cookiecraftmods.roadbuilder.preset.LaneType.VEHICLE, 3, 0,
                com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection.FORWARD);
        var bands = List.of(
                new com.cookiecraftmods.roadbuilder.preset.RoadSurfaceBand(
                        com.cookiecraftmods.roadbuilder.preset.RoadSurfaceRole.PAVEMENT, 3, gray),
                new com.cookiecraftmods.roadbuilder.preset.RoadSurfaceBand(
                        com.cookiecraftmods.roadbuilder.preset.RoadSurfaceRole.GRASS_MEDIAN, 3, grass),
                new com.cookiecraftmods.roadbuilder.preset.RoadSurfaceBand(
                        com.cookiecraftmods.roadbuilder.preset.RoadSurfaceRole.PAVEMENT, 3, gray));
        var geometry = new com.cookiecraftmods.roadbuilder.geometry.QuadraticBezierGeometry(
                new RoadPoint(0.5, 12, 0.5), new RoadPoint(20.5, 12, -10.5),
                new RoadPoint(40.5, 12, 30.5));
        var segment = com.cookiecraftmods.roadbuilder.network.RoadSegment.path(
                new com.cookiecraftmods.roadbuilder.network.RoadSegmentId(java.util.UUID.randomUUID()),
                new com.cookiecraftmods.roadbuilder.network.RoadId(java.util.UUID.randomUUID()),
                new com.cookiecraftmods.roadbuilder.network.RoadNodeId(java.util.UUID.randomUUID()),
                new com.cookiecraftmods.roadbuilder.network.RoadNodeId(java.util.UUID.randomUUID()),
                geometry, gray, 1,
                new com.cookiecraftmods.roadbuilder.network.RoadPresetSnapshot(9, gray, List.of(lane), bands),
                com.cookiecraftmods.roadbuilder.preset.TrafficHandedness.RHT,
                List.of(new com.cookiecraftmods.roadbuilder.network.RoadLaneId(java.util.UUID.randomUUID())));
        var limits = new ConstructionLimits(512, 131072);
        var surface = success(new StraightRoadSegmentMaterializer().plan(Level.OVERWORLD, segment, limits));
        var grassPositions = surface.blocks().stream().filter(block -> block.targetState().is(Blocks.GRASS_BLOCK))
                .map(PlannedBlock::position).toList();
        assertFalse(grassPositions.isEmpty());
        var built = success(new RoadMaterializationPlanner().plan(Level.OVERWORLD, List.of(segment),
                new MapWorld(0), pos -> false, limits));
        for (BlockPos position : grassPositions) {
            assertBlock(built, position, Blocks.SMOOTH_STONE);
        }
    }

    @Test
    void grassMediansBecomeStoneOnlyOnBridgeSpans() {
        var grass = MinecraftIds.id("minecraft", "grass_block");
        var asphalt = MinecraftIds.id("minecraft", "gray_concrete");
        var bands = List.of(
                new com.cookiecraftmods.roadbuilder.preset.RoadSurfaceBand(
                        com.cookiecraftmods.roadbuilder.preset.RoadSurfaceRole.PAVEMENT, 2, asphalt),
                new com.cookiecraftmods.roadbuilder.preset.RoadSurfaceBand(
                        com.cookiecraftmods.roadbuilder.preset.RoadSurfaceRole.GRASS_MEDIAN, 1, grass),
                new com.cookiecraftmods.roadbuilder.preset.RoadSurfaceBand(
                        com.cookiecraftmods.roadbuilder.preset.RoadSurfaceRole.PAVEMENT, 2, asphalt));
        for (int height : new int[]{1, 12}) {
            var geometry = new StraightGeometry(new RoadPoint(0.5, height, 2.5),
                    new RoadPoint(30.5, height, 2.5));
            var blocks = new java.util.ArrayList<PlannedBlock>();
            for (int x = 0; x <= 30; x++) {
                for (int z = 0; z <= 4; z++) {
                    blocks.add(new PlannedBlock(new BlockPos(x, height, z),
                            (z == 2 ? Blocks.GRASS_BLOCK : Blocks.GRAY_CONCRETE).defaultBlockState()));
                }
            }
            var surface = BlockPlacementPlan.create(Level.OVERWORLD, blocks);
            var spans = List.of(new RoadStructurePlanner.StructureSpan(geometry, 5, bands));
            var planner = new RoadStructurePlanner();
            var prepared = planner.prepareStructureSurface(surface, spans, new MapWorld(0), pos -> false);
            var built = success(planner.addStructures(surface, new MapWorld(0), spans, pos -> false, 20000));
            for (int x = 0; x <= 30; x++) {
                assertBlock(prepared, new BlockPos(x, height, 2),
                        height == 1 ? Blocks.GRASS_BLOCK : Blocks.SMOOTH_STONE);
                assertBlock(built, new BlockPos(x, height, 2),
                        height == 1 ? Blocks.GRASS_BLOCK : Blocks.SMOOTH_STONE);
                assertBlock(built, new BlockPos(x, height, 1), Blocks.GRAY_CONCRETE);
            }
            org.junit.jupiter.api.Assertions.assertEquals(prepared.blocks(),
                    planner.prepareStructureSurface(prepared, spans, new MapWorld(0), pos -> false).blocks());
        }
    }

    @Test
    void presetRailingsWorkAtGroundLevelAndReplaceAutomaticBridgeEdges() {
        var stone = MinecraftIds.id("minecraft", "smooth_stone");
        var pavement = MinecraftIds.id("minecraft", "gray_concrete");
        var bands = List.of(
                new com.cookiecraftmods.roadbuilder.preset.RoadSurfaceBand(
                        com.cookiecraftmods.roadbuilder.preset.RoadSurfaceRole.RAILING, 1, stone),
                new com.cookiecraftmods.roadbuilder.preset.RoadSurfaceBand(
                        com.cookiecraftmods.roadbuilder.preset.RoadSurfaceRole.PAVEMENT, 3, pavement),
                new com.cookiecraftmods.roadbuilder.preset.RoadSurfaceBand(
                        com.cookiecraftmods.roadbuilder.preset.RoadSurfaceRole.RAILING, 1, stone));
        for (int height : new int[]{1, 9}) {
            var geometry = new StraightGeometry(new RoadPoint(0.5, height, 2.5),
                    new RoadPoint(8.5, height, 2.5));
            var spans = List.of(new RoadStructurePlanner.StructureSpan(geometry, 5, bands));
            BlockPlacementPlan result = success(new RoadStructurePlanner().addStructures(
                    railingSurface(height, false), new MapWorld(0), spans,
                    position -> false, 20_000));
            for (int x = 0; x < 9; x++) {
                for (int z : new int[]{0, 4}) {
                    assertBlock(result, new BlockPos(x, height + 1, z), Blocks.IRON_BARS);
                    assertBlock(result, new BlockPos(x, height + 2, z), Blocks.IRON_TRAPDOOR);
                }
                assertFalse(result.positions().contains(new BlockPos(x, height + 1, -1)));
                assertFalse(result.positions().contains(new BlockPos(x, height + 1, 5)));
                assertFalse(result.positions().contains(new BlockPos(x, height + 1, 2)));
            }
            assertState(result, new BlockPos(4, height + 1, 0),
                    Blocks.IRON_BARS.defaultBlockState()
                            .setValue(CrossCollisionBlock.EAST, true)
                            .setValue(CrossCollisionBlock.WEST, true));
            BlockPlacementPlan crossing = success(new RoadStructurePlanner().addStructures(
                    railingSurface(height, false), new MapWorld(0), spans,
                    position -> position.equals(new BlockPos(4, height, -1)), 20_000));
            assertFalse(crossing.blocks().stream().anyMatch(block ->
                    block.position().equals(new BlockPos(4, height + 1, 0))
                            && block.targetState().is(Blocks.IRON_BARS)));
            assertInstanceOf(BlockPlanResult.Failure.class,
                    new RoadStructurePlanner().addStructures(railingSurface(height, false),
                            new MapWorld(0), spans, position -> false,
                            result.affectedBlockCount() - 1));
        }
    }

    @Test
    void explicitRailingSlabsSupportBarsAndBuriedFoundationsBecomePavement() {
        var stone = MinecraftIds.id("minecraft", "smooth_stone");
        var asphalt = MinecraftIds.id("minecraft", "gray_concrete");
        var bands = List.of(
                new com.cookiecraftmods.roadbuilder.preset.RoadSurfaceBand(
                        com.cookiecraftmods.roadbuilder.preset.RoadSurfaceRole.RAILING, 1, stone),
                new com.cookiecraftmods.roadbuilder.preset.RoadSurfaceBand(
                        com.cookiecraftmods.roadbuilder.preset.RoadSurfaceRole.PAVEMENT, 3, asphalt),
                new com.cookiecraftmods.roadbuilder.preset.RoadSurfaceBand(
                        com.cookiecraftmods.roadbuilder.preset.RoadSurfaceRole.RAILING, 1, stone));
        var geometry = new StraightGeometry(new RoadPoint(0.5, 1.5, 2.5),
                new RoadPoint(8.5, 1.5, 2.5));
        var spans = List.of(new RoadStructurePlanner.StructureSpan(geometry, 5, bands));
        MapWorld world = new MapWorld(0);
        BlockPos mergingLane = new BlockPos(4, 2, 0);
        world.states.put(mergingLane, Blocks.GRAY_CONCRETE.defaultBlockState());
        world.states.put(mergingLane.above(), Blocks.IRON_BARS.defaultBlockState());
        world.states.put(mergingLane.above(2), Blocks.IRON_TRAPDOOR.defaultBlockState());
        var result = success(new RoadStructurePlanner().addStructures(
                railingSurface(2, true), world, spans, world.states::containsKey, 20_000));
        assertState(result, new BlockPos(3, 2, 0), Blocks.SMOOTH_STONE_SLAB.defaultBlockState()
                .setValue(net.minecraft.world.level.block.SlabBlock.TYPE,
                        net.minecraft.world.level.block.state.properties.SlabType.DOUBLE));
        assertBlock(result, new BlockPos(3, 3, 0), Blocks.IRON_BARS);
        assertBlock(result, mergingLane, Blocks.GRAY_CONCRETE);
        assertBlock(result, mergingLane.above(), Blocks.AIR);
        assertBlock(result, mergingLane.above(2), Blocks.AIR);

        var opened = success(new RoadStructurePlanner().addStructures(
                railingSurface(2, false), new MapWorld(0), spans,
                pos -> pos.equals(new BlockPos(4, 2, -1)), 20_000));
        assertBlock(opened, mergingLane, Blocks.GRAY_CONCRETE);
        assertFalse(opened.blocks().stream().anyMatch(block ->
                block.position().equals(mergingLane.above()) && block.targetState().is(Blocks.IRON_BARS)));
    }

    private static BlockPlacementPlan railingSurface(int y, boolean slabs) {
        return BlockPlacementPlan.create(Level.OVERWORLD, surface(y, 5, 9).blocks().stream()
                .map(block -> block.position().getZ() == 0 || block.position().getZ() == 4
                        ? new PlannedBlock(block.position(), slabs
                                ? Blocks.SMOOTH_STONE_SLAB.defaultBlockState()
                                : Blocks.SMOOTH_STONE.defaultBlockState()) : block).toList());
    }

    @Test
    void selectsEmbankmentSolidBaseAndDeckAtHeightBoundaries() {
        MapWorld world = new MapWorld(59);
        RoadStructurePlanner planner = new RoadStructurePlanner();

        BlockPlacementPlan fill = success(planner.addStructures(
                surface(61, 3, 3), world, 10_000));
        assertState(fill, new BlockPos(1, 60, 1), Blocks.DIRT.defaultBlockState());
        assertState(fill, new BlockPos(0, 60, 0), Blocks.DIRT.defaultBlockState());
        assertState(fill, new BlockPos(-1, 60, 1), Blocks.GRASS_BLOCK.defaultBlockState());
        assertFalse(fill.positions().contains(new BlockPos(-2, 60, 1)));

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
    void roadOnALedgeDoesNotSpillADeepEmbankmentOutsideItsFootprint() {
        MapWorld world = new MapWorld(59);
        world.states.put(
                new BlockPos(0, 63, 0),
                Blocks.GRASS_BLOCK.defaultBlockState()
        );

        BlockPlacementPlan result = success(new RoadStructurePlanner()
                .addStructures(surface(64, 1, 1), world, 2_000));

        assertFalse(result.blocks().stream().anyMatch(block ->
                !block.targetState().isAir() && (block.position().getX() != 0 || block.position().getZ() != 0)));
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
        assertState(result, new BlockPos(12, 10, -1),
                Blocks.IRON_BARS.defaultBlockState()
                        .setValue(CrossCollisionBlock.EAST, true)
                        .setValue(CrossCollisionBlock.WEST, true));
        assertState(result, new BlockPos(12, 11, -1),
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
    void retainingWallEdgesHaveSupportedRailingsButLowEmbankmentsDoNot() {
        for (int height : new int[]{2, 3, 7}) {
            StraightGeometry geometry = new StraightGeometry(
                    new RoadPoint(0.5, height, 1.5),
                    new RoadPoint(8.5, height, 1.5));
            BlockPlacementPlan slabs = BlockPlacementPlan.create(Level.OVERWORLD,
                    surface(height, 3, 9).blocks().stream()
                            .map(block -> new PlannedBlock(block.position(),
                                    Blocks.SMOOTH_STONE_SLAB.defaultBlockState()))
                            .toList());
            BlockPlacementPlan result = success(new RoadStructurePlanner().addStructures(
                    slabs, new MapWorld(0), geometry, 3, position -> false, 20_000));

            for (PlannedBlock slab : slabs.blocks()) {
                assertState(result, slab.position(), slab.targetState());
            }
            if (height == 2) {
                assertFalse(result.blocks().stream().anyMatch(block ->
                        block.targetState().is(Blocks.IRON_BARS)));
                continue;
            }
            for (int z : new int[]{-1, 3}) {
                assertState(result, new BlockPos(4, height - 1, z),
                        Blocks.SMOOTH_STONE.defaultBlockState());
                assertState(result, new BlockPos(4, height, z),
                        Blocks.SMOOTH_STONE.defaultBlockState());
                assertState(result, new BlockPos(4, height + 1, z),
                        Blocks.IRON_BARS.defaultBlockState()
                                .setValue(CrossCollisionBlock.EAST, true)
                                .setValue(CrossCollisionBlock.WEST, true));
                assertBlock(result, new BlockPos(4, height + 2, z), Blocks.IRON_TRAPDOOR);
            }
            assertState(result, new BlockPos(4, height - 1, 0),
                    Blocks.POLISHED_ANDESITE.defaultBlockState());
            assertFalse(result.positions().contains(new BlockPos(-1, height + 1, 1)));
            assertFalse(result.positions().contains(new BlockPos(9, height + 1, 1)));
        }
    }

    @Test
    void slabBridgeHasSolidOuterLedgeUnderEveryRailing() {
        BlockPlacementPlan slabs = BlockPlacementPlan.create(Level.OVERWORLD,
                surface(9, 3, 9).blocks().stream()
                        .map(block -> new PlannedBlock(block.position(),
                                Blocks.SMOOTH_STONE_SLAB.defaultBlockState()))
                        .toList());
        StraightGeometry geometry = new StraightGeometry(
                new RoadPoint(0.5, 9.0, 1.5),
                new RoadPoint(8.5, 9.0, 1.5));
        BlockPlacementPlan result = success(new RoadStructurePlanner().addStructures(
                slabs, new MapWorld(0), geometry, 3, position -> false, 20_000));

        for (PlannedBlock slab : slabs.blocks()) {
            assertState(result, slab.position(), slab.targetState());
            assertFalse(result.positions().contains(slab.position().above()));
        }
        for (int z : new int[]{-1, 3}) {
            for (int x = 0; x < 9; x++) {
                assertState(result, new BlockPos(x, 8, z),
                        Blocks.SMOOTH_STONE.defaultBlockState());
                assertState(result, new BlockPos(x, 9, z),
                        Blocks.SMOOTH_STONE.defaultBlockState());
                assertBlock(result, new BlockPos(x, 10, z), Blocks.IRON_BARS);
            }
        }
        MapWorld builtWorld = new MapWorld(0);
        result.blocks().forEach(block -> builtWorld.states.put(block.position(), block.targetState()));
        BlockPlacementPlan rebuilt = success(new RoadStructurePlanner().addStructures(
                slabs, builtWorld, geometry, 3, result.positions()::contains, 20_000));
        for (PlannedBlock block : result.blocks()) {
            if (block.targetState().is(Blocks.IRON_BARS)) {
                assertState(rebuilt, block.position(), block.targetState());
            }
        }
        assertInstanceOf(BlockPlanResult.Failure.class,
                new RoadStructurePlanner().addStructures(slabs, new MapWorld(0),
                        geometry, 3, position -> false, result.blocks().size() - 1));
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
        for (BlockPos barrier : barriers) {
            assertState(result, barrier.below(), Blocks.SMOOTH_STONE.defaultBlockState());
            assertState(result, barrier.below(2), Blocks.SMOOTH_STONE.defaultBlockState());
        }
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
        assertState(result, new BlockPos(3, 10, -1),
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
        assertBlock(result, new BlockPos(0, 10, -1), Blocks.IRON_BARS);
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

    @Test
    void lowCrossingKeepsDeckAndDoesNotBuildPiersThroughLowerRoad() {
        MapWorld world = new MapWorld(0);
        Set<BlockPos> lower = new HashSet<>();
        for (int x = 0; x < 25; x++) for (int z = 0; z < 7; z++) {
            BlockPos pos = new BlockPos(x, 2, z);
            lower.add(pos);
            world.states.put(pos, Blocks.GRAY_CONCRETE.defaultBlockState());
        }
        var geometry = new StraightGeometry(new RoadPoint(0.5, 7, 3.5), new RoadPoint(24.5, 7, 3.5));
        var built = success(new RoadStructurePlanner().addStructures(surface(7, 7, 25),
                world, geometry, 7, lower::contains, 20000));
        assertBlock(built, new BlockPos(10, 6, 3), Blocks.COBBLESTONE);
        assertTrue(built.blocks().stream().noneMatch(block -> block.position().getZ() >= 0
                && block.position().getZ() < 7 && block.position().getY() < 5
                && !block.targetState().isAir()));
    }

    @Test
    void bridgeClearsTrunkBelowDeck() {
        MapWorld world = new MapWorld(0);
        for (int y = 1; y < 12; y++) world.states.put(new BlockPos(5, y, 2),
                Blocks.OAK_LOG.defaultBlockState());
        var built = success(new RoadStructurePlanner().addStructures(surface(12, 5, 12), world, 20000));
        assertBlock(built, new BlockPos(5, 4, 2), Blocks.AIR);
    }

    @Test
    void railingUsesRaisedSlabColumnAndSolidBase() {
        var stone = MinecraftIds.id("minecraft", "smooth_stone");
        var bands = List.of(
                new com.cookiecraftmods.roadbuilder.preset.RoadSurfaceBand(
                        com.cookiecraftmods.roadbuilder.preset.RoadSurfaceRole.RAILING, 1, stone),
                new com.cookiecraftmods.roadbuilder.preset.RoadSurfaceBand(
                        com.cookiecraftmods.roadbuilder.preset.RoadSurfaceRole.PAVEMENT, 1, stone));
        var geometry = new StraightGeometry(new RoadPoint(0.5, 12.3, 1), new RoadPoint(10.5, 12.3, 1));
        var blocks = new ArrayList<PlannedBlock>();
        for (int x = 0; x <= 10; x++) for (int z = 0; z < 2; z++) blocks.add(new PlannedBlock(
                new BlockPos(x, 13, z), Blocks.SMOOTH_STONE_SLAB.defaultBlockState()));
        var built = success(new RoadStructurePlanner().addStructures(
                BlockPlacementPlan.create(Level.OVERWORLD, blocks), new MapWorld(0),
                List.of(new RoadStructurePlanner.StructureSpan(geometry, 2, bands)), pos -> false, 20000));
        assertState(built, new BlockPos(5, 13, 0), Blocks.SMOOTH_STONE_SLAB.defaultBlockState()
                .setValue(net.minecraft.world.level.block.SlabBlock.TYPE,
                        net.minecraft.world.level.block.state.properties.SlabType.DOUBLE));
        assertBlock(built, new BlockPos(5, 14, 0), Blocks.IRON_BARS);
    }

    @Test
    void gapBetweenLowerRoadsGetsWallsWithoutBlockingEitherRoad() {
        MapWorld world = new MapWorld(0);
        Set<BlockPos> lower = new HashSet<>();
        for (int x = 0; x < 12; x++) for (int z : new int[]{0, 2}) {
            BlockPos pos = new BlockPos(x, 2, z);
            lower.add(pos);
            world.states.put(pos, Blocks.GRAY_CONCRETE.defaultBlockState());
        }
        world.states.put(new BlockPos(5, 3, 1), Blocks.OAK_LOG.defaultBlockState());
        var built = success(new RoadStructurePlanner().addStructures(surface(12, 3, 12),
                world, List.of(), lower::contains, 20000));
        assertBlock(built, new BlockPos(5, 3, 1), Blocks.POLISHED_ANDESITE);
        assertTrue(built.blocks().stream().noneMatch(block -> !block.targetState().isAir()
                && (block.position().getZ() == 0 || block.position().getZ() == 2)
                && block.position().getY() < 10));
    }

    @Test
    void customBridgeWithinTwoBlocksDoesNotReceiveDeckOrPiers() {
        MapWorld world = new MapWorld(0);
        for (int x = 0; x < 12; x++) for (int z = 0; z < 3; z++)
            world.states.put(new BlockPos(x, 10, z), Blocks.STONE_BRICKS.defaultBlockState());
        var geometry = new StraightGeometry(new RoadPoint(0.5, 12, 1.5), new RoadPoint(11.5, 12, 1.5));
        var built = success(new RoadStructurePlanner().addStructures(surface(12, 3, 12),
                world, geometry, 3, pos -> false, 20000));
        assertTrue(built.blocks().stream().noneMatch(block -> !block.targetState().isAir()
                && block.position().getX() >= 0 && block.position().getX() < 12
                && block.position().getZ() >= 0 && block.position().getZ() < 3
                && block.position().getY() <= 10));
    }

    @Test
    void fiveBlockRiseUsesRetainingWallsWithoutLateralEarthSpill() {
        var geometry = new StraightGeometry(new RoadPoint(0.5, 80, 6.5), new RoadPoint(24.5, 80, 6.5));
        var built = success(new RoadStructurePlanner().addStructures(surface(80, 14, 25),
                new MapWorld(75), geometry, 14, pos -> false, 20000));
        assertBlock(built, new BlockPos(12, 79, 0), Blocks.POLISHED_ANDESITE);
        assertTrue(built.blocks().stream().noneMatch(block -> block.targetState().is(Blocks.DIRT)
                || block.targetState().is(Blocks.GRASS_BLOCK)));
        assertTrue(built.blocks().stream().allMatch(block -> block.position().getZ() >= -1
                && block.position().getZ() <= 14));
    }

    @Test
    void relocatedPierCapsStayWithinTheDeckFootprint() {
        MapWorld world = new MapWorld(0);
        Set<BlockPos> lower = new HashSet<>();
        for (int x=0; x<25; x++) {
            BlockPos position = new BlockPos(x, 2, 5);
            lower.add(position);
            world.states.put(position, Blocks.GRAY_CONCRETE.defaultBlockState());
        }
        var geometry = new StraightGeometry(new RoadPoint(0.5, 12, 5.5), new RoadPoint(24.5, 12, 5.5));
        var built = success(new RoadStructurePlanner().addStructures(surface(12, 11, 25),
                world, geometry, 11, lower::contains, 20000));
        var pillars = built.blocks().stream().filter(block -> block.targetState().is(Blocks.STONE_BRICKS)).toList();
        assertFalse(pillars.isEmpty());
        assertTrue(pillars.stream().allMatch(block -> block.position().getZ() >= 0
                && block.position().getZ() < 11 && block.position().getX() >= 0 && block.position().getX() < 25));
        assertTrue(pillars.stream().noneMatch(block -> block.position().getZ() == 5));
    }

    @Test
    void ownedClearanceAirDoesNotHideTheLowerRoadOrCreateWallsOnItsLanes() {
        MapWorld world = new MapWorld(70);
        Set<BlockPos> owned = new HashSet<>();
        for(int x=0;x<12;x++) for(int z=0;z<5;z++) {
            world.states.put(new BlockPos(x,75,z),Blocks.GRAY_CONCRETE.defaultBlockState());
            for(int y=75;y<=87;y++) owned.add(new BlockPos(x,y,z));
        }
        var geometry = new StraightGeometry(new RoadPoint(0.5,83,2.5),new RoadPoint(11.5,83,2.5));
        var built = success(new RoadStructurePlanner().addStructures(surface(83,5,12),world,
                geometry,5,owned::contains,20000));
        assertBlock(built,new BlockPos(5,81,2),Blocks.SMOOTH_STONE);
        assertTrue(built.blocks().stream().noneMatch(block -> !block.targetState().isAir()
                && block.position().getZ()>=0 && block.position().getZ()<5
                && block.position().getY()>=76 && block.position().getY()<=80));
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
