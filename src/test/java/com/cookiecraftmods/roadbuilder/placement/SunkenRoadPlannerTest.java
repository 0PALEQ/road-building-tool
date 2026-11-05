package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.StraightGeometry;
import com.cookiecraftmods.roadbuilder.network.RoadPresetSnapshot;
import com.cookiecraftmods.roadbuilder.networking.RoadGraphSnapshotS2CPacket;
import com.cookiecraftmods.roadbuilder.persistence.RoadBuilderNetworkSavedData;
import com.cookiecraftmods.roadbuilder.preset.*;
import com.cookiecraftmods.roadbuilder.test.MinecraftBootstrap;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class SunkenRoadPlannerTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.bootstrap(); }

    @Test void excavatesToSkyWithSteppedSlopesAndPreservesSurface() {
        var position = new BlockPos(0, 64, 0);
        var surface = BlockPlacementPlan.create(Level.OVERWORLD,
                List.of(new PlannedBlock(position, Blocks.GRAY_CONCRETE.defaultBlockState())));
        var result = new SunkenRoadPlanner().excavate(surface, Set.of(position), new Terrain(80), p -> false, 20000);
        var plan = assertInstanceOf(BlockPlanResult.Success.class, result).plan();
        Map<BlockPos, BlockState> states = plan.blocks().stream()
                .collect(Collectors.toMap(PlannedBlock::position, PlannedBlock::targetState));
        assertEquals(Blocks.GRAY_CONCRETE.defaultBlockState(), states.get(position));
        assertTrue(states.get(position.above(16)).isAir());
        assertTrue(states.get(new BlockPos(1, 66, 0)).isAir());
        assertFalse(states.containsKey(new BlockPos(1, 65, 0)));
        assertFalse(states.containsKey(new BlockPos(16, 80, 0)));
    }

    @Test void respectsBudgetAndRejectsProtectedRoadsAndFluids() {
        var position = new BlockPos(0, 64, 0);
        var surface = BlockPlacementPlan.create(Level.OVERWORLD,
                List.of(new PlannedBlock(position, Blocks.GRAY_CONCRETE.defaultBlockState())));
        var planner = new SunkenRoadPlanner();
        assertInstanceOf(BlockPlanResult.Failure.class,
                planner.excavate(surface, Set.of(position), new Terrain(80), p -> false, 10));
        assertInstanceOf(BlockPlanResult.Failure.class,
                planner.excavate(surface, Set.of(position), new Terrain(80), p -> p.equals(position.above()), 20000));
        var water = new Terrain(80) {
            @Override public BlockState blockState(BlockPos pos) {
                return pos.equals(position.above()) ? Blocks.WATER.defaultBlockState() : super.blockState(pos);
            }
        };
        assertInstanceOf(BlockPlanResult.Failure.class,
                planner.excavate(surface, Set.of(position), water, p -> false, 20000));
    }

    @Test void modePersistsSynchronizesAndBuildsOpenCutWithoutTunnel() {
        var data = RoadBuilderNetworkSavedData.create(Level.OVERWORLD);
        var material = new ResourceLocation("minecraft", "gray_concrete");
        var preset = new RoadPreset(RoadPreset.CURRENT_SCHEMA_VERSION, 1, 3, material,
                List.of(new LaneComponent(LaneType.VEHICLE, 3, 0, LaneTravelDirection.FORWARD)));
        assertFalse(RoadPresetSnapshot.from(preset).sunkenRoad());
        var mutation = data.graph().prepareRoadNetwork(
                new StraightGeometry(new RoadPoint(0.5, 64, 0.5), new RoadPoint(20.5, 64, 0.5)),
                new ResourceLocation("roadbuilder", "test"), preset, TrafficHandedness.RHT).withSunkenRoad(true);
        data.graph().applyNetworkMutation(mutation);
        var saved = data.save(new CompoundTag());
        var loaded = RoadBuilderNetworkSavedData.load(Level.OVERWORLD, saved);
        assertTrue(loaded.isCompatible());
        assertEquals(data.graph().snapshot(), loaded.graph().snapshot());
        var snapshot = loaded.graph().snapshot();
        assertTrue(snapshot.segments().values().stream().allMatch(s -> s.presetSnapshot().sunkenRoad()));
        var buffer = PacketByteBufs.create();
        try {
            var packet = RoadGraphSnapshotS2CPacket.current(snapshot);
            packet.write(buffer);
            assertEquals(packet, RoadGraphSnapshotS2CPacket.TYPE.read(buffer));
        } finally { buffer.release(); }
        var result = new RoadMaterializationPlanner().plan(Level.OVERWORLD,
                List.copyOf(snapshot.segments().values()), new Terrain(80), p -> false,
                new ConstructionLimits(512, 131072));
        var plan = assertInstanceOf(BlockPlanResult.Success.class, result).plan();
        assertTrue(plan.blocks().stream().anyMatch(b -> b.position().equals(new BlockPos(10, 80, 0))
                && b.targetState().isAir()));
        assertFalse(plan.blocks().stream().anyMatch(b -> b.targetState().is(Blocks.POLISHED_ANDESITE)));
    }

    private static class Terrain implements PlacementBlockWorld {
        private final int height;
        Terrain(int height) { this.height = height; }
        @Override public net.minecraft.resources.ResourceKey<Level> dimension() { return Level.OVERWORLD; }
        @Override public BlockState blockState(BlockPos position) {
            return (position.getY() <= height ? Blocks.STONE : Blocks.AIR).defaultBlockState();
        }
        @Override public boolean hasBlockEntity(BlockPos position) { return false; }
        @Override public boolean setBlockState(BlockPos position, BlockState state) { throw new UnsupportedOperationException(); }
    }
}
