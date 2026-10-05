package com.cookiecraftmods.roadbuilder.placement;

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
        world.states.put(
                new BlockPos(10, 71, 0),
                Blocks.STONE.defaultBlockState()
        );
        world.states.put(
                new BlockPos(10, 73, 4),
                Blocks.GRASS.defaultBlockState()
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
        world.states.put(
                new BlockPos(10, 72, 0),
                Blocks.STONE.defaultBlockState()
        );

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
        ResourceLocation gray = new ResourceLocation("minecraft", "gray_concrete");
        ResourceLocation yellow = new ResourceLocation("minecraft", "yellow_concrete");
        ResourceLocation sidewalk = new ResourceLocation("minecraft", "smooth_stone");
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
                new ResourceLocation("roadbuilder", "tunnel_test"),
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
