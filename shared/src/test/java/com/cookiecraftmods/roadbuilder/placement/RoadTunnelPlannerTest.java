package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.compat.MinecraftBlocks;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.StraightGeometry;
import com.cookiecraftmods.roadbuilder.network.RoadId;
import com.cookiecraftmods.roadbuilder.network.RoadNodeId;
import com.cookiecraftmods.roadbuilder.network.RoadPresetSnapshot;
import com.cookiecraftmods.roadbuilder.network.RoadSegment;
import com.cookiecraftmods.roadbuilder.network.RoadSegmentId;
import com.cookiecraftmods.roadbuilder.preset.LaneComponent;
import com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection;
import com.cookiecraftmods.roadbuilder.preset.LaneType;
import com.cookiecraftmods.roadbuilder.preset.RoadMarkingPattern;
import com.cookiecraftmods.roadbuilder.preset.RoadSurfaceBand;
import com.cookiecraftmods.roadbuilder.preset.RoadSurfaceRole;
import com.cookiecraftmods.roadbuilder.preset.TrafficHandedness;
import com.cookiecraftmods.roadbuilder.test.MinecraftBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadTunnelPlannerTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftBootstrap.bootstrap();
    }

    @Test
    void suppressesOneBlockTunnelsAndNeverProducesShortPortalSegments() {
        MapWorld world = new MapWorld();
        var geometry = new StraightGeometry(new RoadPoint(0.5,64,0.5), new RoadPoint(100.5,64,0.5));
        for (int x : new int[]{1, 20, 21, 98})
            for (int z=-5; z<=5; z++) world.states.put(new BlockPos(x,71,z), Blocks.STONE.defaultBlockState());
        assertTrue(RoadTunnelPlanner.portalFractions(geometry,11,world).isEmpty());
        for (int x=30; x<70; x++)
            if (x!=50) for (int z=-5; z<=5; z++) world.states.put(new BlockPos(x,71,z), Blocks.STONE.defaultBlockState());
        var cuts = RoadTunnelPlanner.portalFractions(geometry,11,world);
        assertEquals(List.of(0.3,0.7), cuts);
        double previous=0;
        for (double cut : cuts) {
            assertTrue((cut-previous)*geometry.length() >= RoadTunnelPlanner.MINIMUM_SEGMENT_LENGTH);
            previous=cut;
        }
    }

    @Test
    void diagonalTunnelWallsStayOutsideTravelCorridor() {
        var original = segment();
        var geometry = new StraightGeometry(new RoadPoint(0.5, 64, 0.5), new RoadPoint(20.5, 64, 20.5));
        var diagonal = RoadSegment.path(original.id(), original.roadId(), original.startNodeId(), original.endNodeId(),
                geometry, original.presetId(), original.presetRevision(), original.presetSnapshot(),
                original.trafficHandedness(), original.lanes().stream().map(lane -> lane.id()).toList());
        MapWorld world = new MapWorld();
        Map<BlockPos, PlannedBlock> surface = new HashMap<>();
        for (double distance = 0; distance <= geometry.length(); distance += 0.25) {
            var sample = geometry.sample(distance);
            for (double lateral = -5; lateral <= 5; lateral += 0.25) {
                var point = sample.position().add(sample.tangent().horizontalLeftUnit().scale(lateral));
                var pos = new BlockPos((int)Math.floor(point.x()), 64, (int)Math.floor(point.z()));
                surface.put(pos, new PlannedBlock(pos, Blocks.GRAY_CONCRETE.defaultBlockState()));
                world.states.put(pos.above(7), Blocks.STONE.defaultBlockState());
            }
        }
        var plan = new RoadTunnelPlanner().addTunnels(BlockPlacementPlan.create(Level.OVERWORLD, surface.values()),
                List.of(diagonal), world, position -> false, 30_000).plan().orElseThrow();
        long walls = 0;
        for (var block : plan.blocks()) {
            if (block.position().getY() != 68 || !block.targetState().is(Blocks.POLISHED_ANDESITE)) continue;
            walls++;
            double lateral = Math.abs((block.position().getZ() - block.position().getX()) / Math.sqrt(2));
            assertTrue(lateral >= 5, "wall crosses the travel corridor at " + block.position());
        }
        assertTrue(walls > 20);
    }

    @Test
    void hillsideOverOneEdgeDoesNotCreateAnExposedTunnelBox() {
        MapWorld world = new MapWorld();
        List<PlannedBlock> surface = new ArrayList<>();
        for (int x = 0; x <= 20; x++) {
            for (int z = -5; z <= 5; z++) {
                surface.add(new PlannedBlock(new BlockPos(x, 64, z), Blocks.GRAY_CONCRETE.defaultBlockState()));
                if (z >= 3) world.states.put(new BlockPos(x, 71, z), Blocks.STONE.defaultBlockState());
            }
        }
        assertTrue(RoadTunnelPlanner.portalFractions(segment().referenceGeometry(), 11, world).isEmpty());
        var result = new RoadTunnelPlanner().addTunnels(BlockPlacementPlan.create(Level.OVERWORLD, surface),
                List.of(segment()), world, position -> false, 20_000);
        assertTrue(result.protectedRoofPositions().isEmpty());
        assertTrue(result.plan().orElseThrow().blocks().stream().noneMatch(block -> block.position().getY() > 64));
    }

    @Test
    void tunnelCoversWholeSegmentWithWallsAndOpenPortals() {
        MapWorld world = new MapWorld();
        List<PlannedBlock> surface = new ArrayList<>();
        for (int x = 0; x <= 20; x++) {
            for (int z = -5; z <= 5; z++)
                surface.add(new PlannedBlock(new BlockPos(x, 64, z), Blocks.GRAY_CONCRETE.defaultBlockState()));
        }
        for (int x=0; x<=20; x++) for (int z = -5; z <= 5; z++) world.states.put(new BlockPos(x, 71, z), Blocks.STONE.defaultBlockState());
        var result = new RoadTunnelPlanner().addTunnels(BlockPlacementPlan.create(Level.OVERWORLD, surface),
                List.of(segment()), world, position -> false, 20_000);
        var plan = result.plan().orElseThrow();
        assertState(plan, new BlockPos(1, 72, 0), Blocks.POLISHED_ANDESITE.defaultBlockState());
        assertState(plan, new BlockPos(19, 72, 0), Blocks.POLISHED_ANDESITE.defaultBlockState());
        assertState(plan, new BlockPos(10, 68, -6), Blocks.POLISHED_ANDESITE.defaultBlockState());
        assertState(plan, new BlockPos(10, 71, 6), Blocks.POLISHED_ANDESITE.defaultBlockState());
        assertTrue(plan.blocks().stream().noneMatch(block -> block.position().equals(new BlockPos(-1, 68, 0))));
        assertTrue(plan.blocks().stream().noneMatch(block -> block.position().equals(new BlockPos(21, 68, 0))));
    }

    @Test
    void forestIsNotTerrainAndDoesNotCreatePortals() {
        MapWorld world = new MapWorld();
        for (int x = 0; x <= 20; x++) {
            for (int z = -6; z <= 6; z++)
                world.states.put(new BlockPos(x, 71, z), Blocks.OAK_LEAVES.defaultBlockState());
        }
        world.states.put(new BlockPos(10, 71, 0), Blocks.OAK_LOG.defaultBlockState());
        assertTrue(RoadTunnelPlanner.portalFractions(segment().referenceGeometry(), 11, world).isEmpty());
        assertTrue(!RoadTunnelPlanner.isTerrain(Blocks.OAK_LOG.defaultBlockState()));
        assertTrue(!RoadTunnelPlanner.isTerrain(Blocks.OAK_LEAVES.defaultBlockState()));
    }

    @Test
    void terrainCreatesEntryAndExitCuts() {
        MapWorld world = new MapWorld();
        for (int x = 5; x < 15; x++)
            for (int z = -5; z <= 5; z++) world.states.put(new BlockPos(x, 71, z), Blocks.STONE.defaultBlockState());
        assertEquals(List.of(), RoadTunnelPlanner.portalFractions(
                segment().referenceGeometry(), 11, world));
    }

    @Test
    void carvesSevenBlocksAndAddsMedianPillarsAndPairedRoofLights() {
        MapWorld world = new MapWorld();
        List<PlannedBlock> surface = new ArrayList<>();
        for (int x = 0; x <= 20; x++) {
            for (int z = -5; z <= 5; z++) {
                BlockPos position = new BlockPos(x, 64, z);
                surface.add(new PlannedBlock(
                        position, Blocks.GRAY_CONCRETE.defaultBlockState()
                ));
                for (int y = 65; y <= 73; y++) {
                    world.states.put(
                            new BlockPos(x, y, z),
                            Blocks.STONE.defaultBlockState()
                    );
                }
            }
        }
        BlockPlacementPlan surfacePlan = BlockPlacementPlan.create(
                Level.OVERWORLD, surface
        );
        RoadTunnelPlanner.Result result = new RoadTunnelPlanner().addTunnels(
                surfacePlan,
                List.of(segment()),
                world,
                position -> false,
                20_000
        );
        BlockPlacementPlan plan = result.plan().orElseThrow();

        assertState(plan, new BlockPos(1, 65, 0), Blocks.AIR.defaultBlockState());
        assertState(plan, new BlockPos(1, 71, 0), Blocks.AIR.defaultBlockState());
        assertTrue(result.protectedRoofPositions().contains(
                new BlockPos(1, 72, 0)
        ));
        assertState(plan, new BlockPos(1, 72, 0),
                Blocks.POLISHED_ANDESITE.defaultBlockState());
        assertState(plan, new BlockPos(1, 64, 0),
                Blocks.SMOOTH_STONE.defaultBlockState());
        assertState(plan, new BlockPos(2, 65, 0),
                Blocks.STONE_BRICKS.defaultBlockState());
        assertState(plan, new BlockPos(2, 71, 0),
                Blocks.STONE_BRICKS.defaultBlockState());
        assertState(plan, new BlockPos(10, 72, -2),
                Blocks.SEA_LANTERN.defaultBlockState());
        assertState(plan, new BlockPos(10, 72, 2),
                Blocks.SEA_LANTERN.defaultBlockState());
    }

    @Test
    void coveredLaneBuildsAContinuousRoofAndClearsFloatingVegetation() {
        MapWorld world = new MapWorld();
        List<PlannedBlock> surface = new ArrayList<>();
        for (int x = 0; x <= 20; x++) {
            for (int z = -5; z <= 5; z++) {
                surface.add(new PlannedBlock(
                        new BlockPos(x, 64, z),
                        Blocks.GRAY_CONCRETE.defaultBlockState()
                ));
            }
        }
        for (int x=0; x<=20; x++) for (int z = -5; z <= 5; z++) world.states.put(new BlockPos(x, 71, z), Blocks.STONE.defaultBlockState());
        world.states.put(
                new BlockPos(10, 73, 4),
                MinecraftBlocks.shortGrass().defaultBlockState()
        );

        BlockPlacementPlan plan = new RoadTunnelPlanner().addTunnels(
                BlockPlacementPlan.create(Level.OVERWORLD, surface),
                List.of(segment()),
                world,
                position -> false,
                20_000
        ).plan().orElseThrow();

        assertState(plan, new BlockPos(10, 72, 4),
                Blocks.POLISHED_ANDESITE.defaultBlockState());
        assertState(plan, new BlockPos(10, 73, 4),
                Blocks.AIR.defaultBlockState());
    }

    @Test
    void halfStepSurfaceUsesRasterizedSlabPositionsForTunnelFeatures() {
        MapWorld world = new MapWorld();
        List<PlannedBlock> surface = new ArrayList<>();
        for (int x = 0; x <= 20; x++) {
            for (int z = -5; z <= 5; z++) {
                surface.add(new PlannedBlock(
                        new BlockPos(x, 65, z),
                        Blocks.SMOOTH_STONE_SLAB.defaultBlockState()
                ));
            }
        }
        for (int x=0; x<=20; x++) for (int z = -5; z <= 5; z++) world.states.put(new BlockPos(x, 72, z), Blocks.STONE.defaultBlockState());

        BlockPlacementPlan plan = new RoadTunnelPlanner().addTunnels(
                BlockPlacementPlan.create(Level.OVERWORLD, surface),
                List.of(segment(64.499999999)),
                world,
                position -> false,
                20_000
        ).plan().orElseThrow();

        assertState(plan, new BlockPos(10, 65, 0),
                Blocks.SMOOTH_STONE.defaultBlockState());
        assertState(plan, new BlockPos(10, 73, 4),
                Blocks.POLISHED_ANDESITE.defaultBlockState());
        assertState(plan, new BlockPos(10, 66, 0),
                Blocks.STONE_BRICKS.defaultBlockState());
        assertState(plan, new BlockPos(10, 72, 0),
                Blocks.STONE_BRICKS.defaultBlockState());
    }

    private static RoadSegment segment() {
        return segment(64.0);
    }

    private static RoadSegment segment(double y) {
        ResourceLocation gray = MinecraftIds.id("minecraft", "gray_concrete");
        ResourceLocation yellow = MinecraftIds.id("minecraft", "yellow_concrete");
        ResourceLocation sidewalk = MinecraftIds.id("minecraft", "smooth_stone");
        RoadPresetSnapshot preset = new RoadPresetSnapshot(
                11,
                gray,
                List.of(
                        new LaneComponent(LaneType.VEHICLE, 3.0, -1.75,
                                LaneTravelDirection.FORWARD),
                        new LaneComponent(LaneType.VEHICLE, 3.0, 1.75,
                                LaneTravelDirection.BACKWARD)
                ),
                List.of(
                        new RoadSurfaceBand(RoadSurfaceRole.SIDEWALK, 1, sidewalk),
                        new RoadSurfaceBand(RoadSurfaceRole.PAVEMENT, 3, gray),
                        new RoadSurfaceBand(RoadSurfaceRole.MARKING, 3, yellow,
                                RoadMarkingPattern.DOUBLE_CONTINUOUS),
                        new RoadSurfaceBand(RoadSurfaceRole.PAVEMENT, 3, gray),
                        new RoadSurfaceBand(RoadSurfaceRole.SIDEWALK, 1, sidewalk)
                )
        );
        return RoadSegment.path(
                new RoadSegmentId(new UUID(0, 1)),
                new RoadId(new UUID(0, 2)),
                new RoadNodeId(new UUID(0, 3)),
                new RoadNodeId(new UUID(0, 4)),
                new StraightGeometry(
                        new RoadPoint(0.5, y, 0.5),
                        new RoadPoint(20.5, y, 0.5)
                ),
                MinecraftIds.id("roadbuilder", "tunnel_test"),
                1,
                preset,
                TrafficHandedness.RHT,
                List.of(
                        new com.cookiecraftmods.roadbuilder.network.RoadLaneId(
                                new UUID(0, 5)),
                        new com.cookiecraftmods.roadbuilder.network.RoadLaneId(
                                new UUID(0, 6))
                )
        );
    }

    private static void assertState(
            BlockPlacementPlan plan, BlockPos position, BlockState expected
    ) {
        BlockState actual = plan.blocks().stream()
                .filter(block -> block.position().equals(position))
                .findFirst().orElseThrow().targetState();
        assertEquals(expected, actual);
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
