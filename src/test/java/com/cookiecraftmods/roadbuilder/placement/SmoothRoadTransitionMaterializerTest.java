package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.StraightGeometry;
import com.cookiecraftmods.roadbuilder.network.RoadId;
import com.cookiecraftmods.roadbuilder.network.RoadLaneId;
import com.cookiecraftmods.roadbuilder.network.RoadNode;
import com.cookiecraftmods.roadbuilder.network.RoadNodeId;
import com.cookiecraftmods.roadbuilder.network.RoadPresetSnapshot;
import com.cookiecraftmods.roadbuilder.network.RoadSegment;
import com.cookiecraftmods.roadbuilder.network.RoadSegmentId;
import com.cookiecraftmods.roadbuilder.preset.LaneComponent;
import com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection;
import com.cookiecraftmods.roadbuilder.preset.LaneType;
import com.cookiecraftmods.roadbuilder.preset.RoadPreset;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmoothRoadTransitionMaterializerTest {
    private static final ResourceLocation PRESET_ID =
            new ResourceLocation("roadbuilder", "transition_test");

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftBootstrap.bootstrap();
    }

    @Test
    void curvedTransitionMatchesTheRoadTangentAtBothEnds() {
        for (boolean reversed : new boolean[]{false, true}) {
            RoadNodeId nodeId = new RoadNodeId(UUID.randomUUID());
            RoadNodeId farId = new RoadNodeId(UUID.randomUUID());
            RoadNode node = new RoadNode(nodeId, new RoadPoint(0.5, 64.0, 0.5));
            RoadPoint far = new RoadPoint(40.5, 68.0, 20.5);
            var curve = new com.cookiecraftmods.roadbuilder.geometry.QuadraticBezierGeometry(
                    reversed ? far : node.position(),
                    new RoadPoint(20.5, 65.0, 0.5),
                    reversed ? node.position() : far);
            RoadSegment narrow = RoadSegment.path(
                    new RoadSegmentId(UUID.randomUUID()), new RoadId(UUID.randomUUID()),
                    reversed ? farId : nodeId, reversed ? nodeId : farId,
                    curve, PRESET_ID, 1, RoadPresetSnapshot.from(preset(7, 1)),
                    TrafficHandedness.RHT, List.of(new RoadLaneId(UUID.randomUUID())));
            RoadSegment wide = segment(new RoadPoint(-40.5, 64.0, 0.5), node.position(),
                    new RoadNodeId(UUID.randomUUID()), nodeId, preset(15, 2));
            var transition = SmoothRoadTransitionMaterializer.transitionGeometry(
                    node, narrow, wide, 20.0);
            var expected = curve.sample(reversed ? curve.length() - 20.0 : 20.0);
            var actual = transition.sample(transition.length());
            assertEquals(expected.position().x(), actual.position().x(), 1.0e-8);
            assertEquals(expected.position().y(), actual.position().y(), 1.0e-8);
            assertEquals(expected.position().z(), actual.position().z(), 1.0e-8);
            var tangent = expected.tangent().scale(reversed ? -1.0 : 1.0);
            assertEquals(tangent.x(), actual.tangent().x(), 1.0e-8);
            assertEquals(tangent.y(), actual.tangent().y(), 1.0e-8);
            assertEquals(tangent.z(), actual.tangent().z(), 1.0e-8);
            assertEquals(1.0, transition.sample(0.0).tangent().x(), 1.0e-8);
        }
    }

    @Test
    void tapersAConnectedNarrowRoadIntoTheWiderCrossSection() {
        RoadNodeId nodeId = new RoadNodeId(UUID.randomUUID());
        RoadNode node = new RoadNode(nodeId, new RoadPoint(0.5, 64.0, 0.5));
        RoadSegment narrow = segment(
                new RoadPoint(-30.5, 64.0, 0.5),
                node.position(),
                new RoadNodeId(UUID.randomUUID()),
                nodeId,
                preset(7, 1)
        );
        RoadSegment wide = segment(
                node.position(),
                new RoadPoint(30.5, 64.0, 0.5),
                nodeId,
                new RoadNodeId(UUID.randomUUID()),
                preset(15, 2)
        );

        BlockPlacementPlan plan = assertInstanceOf(
                BlockPlanResult.Success.class,
                new SmoothRoadTransitionMaterializer().plan(
                        Level.OVERWORLD,
                        node,
                        List.of(narrow, wide),
                        10_000,
                        100_000
                )
        ).plan();

        assertTrue(SmoothRoadTransitionMaterializer.supports(
                node, List.of(narrow, wide)
        ));
        assertTrue(plan.positions().contains(new BlockPos(0, 64, 7)));
        assertTrue(plan.blocks().stream().anyMatch(block ->
                block.position().equals(new BlockPos(0, 64, 7))
                        && block.targetState().is(Blocks.SMOOTH_STONE)));
        assertTrue(plan.blocks().stream().anyMatch(block ->
                block.position().equals(new BlockPos(0, 64, 3))
                        && block.targetState().is(Blocks.GRAY_CONCRETE)));
        assertTrue(plan.positions().contains(new BlockPos(-12, 64, 5)));
        assertFalse(plan.positions().contains(new BlockPos(-12, 64, 7)));
        assertFalse(plan.positions().contains(new BlockPos(-24, 64, 5)));
    }

    @Test
    void roundsAWidthChangeThroughANinetyDegreeBend() {
        RoadNodeId nodeId = new RoadNodeId(UUID.randomUUID());
        RoadNode node = new RoadNode(nodeId, new RoadPoint(0.5, 64.0, 0.5));
        RoadSegment west = segment(
                new RoadPoint(-20.5, 64.0, 0.5),
                node.position(),
                new RoadNodeId(UUID.randomUUID()),
                nodeId,
                preset(7, 1)
        );
        RoadSegment south = segment(
                node.position(),
                new RoadPoint(0.5, 64.0, 20.5),
                nodeId,
                new RoadNodeId(UUID.randomUUID()),
                preset(15, 2)
        );

        List<RoadSegment> incident = List.of(west, south);
        assertTrue(SmoothRoadTransitionMaterializer.supports(node, incident));
        BlockPlacementPlan plan = assertInstanceOf(
                BlockPlanResult.Success.class,
                new SmoothRoadTransitionMaterializer().plan(
                        Level.OVERWORLD,
                        node,
                        incident,
                        10_000,
                        100_000
                )
        ).plan();
        assertTrue(plan.positions().stream().anyMatch(position ->
                position.getX() < 0 && position.getZ() < 0));

        BlockPlacementPlan corner = assertInstanceOf(
                BlockPlanResult.Success.class,
                new StraightRoadIntersectionMaterializer().plan(
                        Level.OVERWORLD,
                        node,
                        incident,
                        new ResourceLocation("minecraft", "gray_concrete"),
                        new ResourceLocation("minecraft", "smooth_stone"),
                        10_000,
                        100_000
                )
        ).plan();
        Map<BlockPos, BlockState> transitionStates = plan.blocks().stream()
                .collect(java.util.stream.Collectors.toMap(
                        PlannedBlock::position,
                        PlannedBlock::targetState
                ));
        assertTrue(corner.blocks().stream().anyMatch(block ->
                transitionStates.containsKey(block.position())
                        && !transitionStates.get(block.position()).equals(
                                block.targetState()
                        )));

        BlockPlacementPlan prepared = assertInstanceOf(
                BlockPlanResult.Success.class,
                new RoadTransitionPlanPreparer().plan(
                        Level.OVERWORLD,
                        node,
                        incident,
                        new FlatWorld(63),
                        position -> false,
                        ConstructionLimits.defaults()
                )
        ).plan();
        var curvedSurfacePositions = plan.positions();
        var fanOnlyPositions = corner.positions().stream()
                .filter(position -> position.getY() == 64)
                .filter(position -> !curvedSurfacePositions.contains(position))
                .toList();
        assertFalse(fanOnlyPositions.isEmpty());
        assertTrue(fanOnlyPositions.stream().noneMatch(
                prepared.positions()::contains
        ));
    }

    private static RoadPreset preset(int width, int sidewalkWidth) {
        int pavementWidth = width - sidewalkWidth * 2;
        return new RoadPreset(
                RoadPreset.CURRENT_SCHEMA_VERSION,
                1,
                width,
                new ResourceLocation("minecraft", "gray_concrete"),
                List.of(new LaneComponent(
                        LaneType.VEHICLE,
                        Math.min(3.0, pavementWidth),
                        0.0,
                        LaneTravelDirection.FORWARD
                )),
                List.of(
                        new RoadSurfaceBand(
                                RoadSurfaceRole.SIDEWALK,
                                sidewalkWidth,
                                new ResourceLocation("minecraft", "smooth_stone")
                        ),
                        new RoadSurfaceBand(
                                RoadSurfaceRole.PAVEMENT,
                                pavementWidth,
                                new ResourceLocation("minecraft", "gray_concrete")
                        ),
                        new RoadSurfaceBand(
                                RoadSurfaceRole.SIDEWALK,
                                sidewalkWidth,
                                new ResourceLocation("minecraft", "smooth_stone")
                        )
                )
        );
    }

    private static RoadSegment segment(
            RoadPoint start,
            RoadPoint end,
            RoadNodeId startId,
            RoadNodeId endId,
            RoadPreset preset
    ) {
        return RoadSegment.straight(
                new RoadSegmentId(UUID.randomUUID()),
                new RoadId(UUID.randomUUID()),
                startId,
                endId,
                new StraightGeometry(start, end),
                PRESET_ID,
                preset.revision(),
                RoadPresetSnapshot.from(preset),
                TrafficHandedness.RHT,
                List.of(new RoadLaneId(UUID.randomUUID()))
        );
    }

    private static final class FlatWorld implements PlacementBlockWorld {
        private final int groundY;

        private FlatWorld(int groundY) {
            this.groundY = groundY;
        }

        @Override
        public ResourceKey<Level> dimension() {
            return Level.OVERWORLD;
        }

        @Override
        public BlockState blockState(BlockPos position) {
            return position.getY() <= groundY
                    ? Blocks.STONE.defaultBlockState()
                    : Blocks.AIR.defaultBlockState();
        }

        @Override
        public boolean hasBlockEntity(BlockPos position) {
            return false;
        }

        @Override
        public boolean setBlockState(BlockPos position, BlockState state) {
            return true;
        }
    }
}
