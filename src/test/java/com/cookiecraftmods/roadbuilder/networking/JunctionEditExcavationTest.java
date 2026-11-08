package com.cookiecraftmods.roadbuilder.networking;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.StraightGeometry;
import com.cookiecraftmods.roadbuilder.network.*;
import com.cookiecraftmods.roadbuilder.placement.*;
import com.cookiecraftmods.roadbuilder.preset.*;
import com.cookiecraftmods.roadbuilder.test.MinecraftBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JunctionEditExcavationTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.bootstrap(); }

    @Test void crosswalkEditRetainsSunkenJunctionExcavation() { verifyRebuild(true, false); }
    @Test void laneConnectorEditRetainsSunkenJunctionExcavation() { verifyRebuild(false, false); }
    @Test void crosswalkEditRepairsPreviouslyRestoredTerrain() { verifyRebuild(true, true); }
    @Test void laneConnectorEditRepairsPreviouslyRestoredTerrain() { verifyRebuild(false, true); }

    private void verifyRebuild(boolean crosswalk, boolean previouslyRestored) {
        var graph = new RoadGraph(Level.OVERWORLD);
        var material = new ResourceLocation("minecraft", "gray_concrete");
        var id = new ResourceLocation("roadbuilder", "sunken_edit_test");
        var preset = new RoadPreset(RoadPreset.CURRENT_SCHEMA_VERSION, 1, 7, material,
                List.of(new LaneComponent(LaneType.VEHICLE, 3, 0, LaneTravelDirection.FORWARD)));
        graph.applyNetworkMutation(graph.prepareRoadNetwork(new StraightGeometry(
                new RoadPoint(-40.5, 64, 0.5), new RoadPoint(40.5, 64, 0.5)),
                id, preset, TrafficHandedness.RHT).withSunkenRoad(true));
        graph.applyNetworkMutation(graph.prepareRoadNetwork(new StraightGeometry(
                new RoadPoint(0.5, 64, 0.5), new RoadPoint(0.5, 64, 40.5)),
                id, preset, TrafficHandedness.RHT).withSunkenRoad(true));
        var node = graph.snapshot().nodes().values().stream().filter(n ->
                n.position().distanceTo(new RoadPoint(0.5, 64, 0.5)) < 0.01).findFirst().orElseThrow();
        var incident = graph.snapshot().segments().values().stream().filter(s ->
                s.startNodeId().equals(node.id()) || s.endNodeId().equals(node.id())).toList();
        var definition = JunctionDefinition.automatic(node.id());
        var limits = ConstructionLimits.defaults();
        var world = new Terrain();
        var surface = assertInstanceOf(BlockPlanResult.Success.class,
                new StraightRoadIntersectionMaterializer().plan(Level.OVERWORLD, node, incident,
                        material, material, limits.maximumAffectedBlocks(),
                        limits.maximumPlanningCandidates(), definition)).plan();
        var initial = assertInstanceOf(BlockPlanResult.Success.class,
                new RoadTransitionPlanPreparer().complete(surface, node, incident, world,
                        p -> false, limits)).plan();
        if (previouslyRestored) {
            initial = BlockPlacementPlan.create(Level.OVERWORLD, initial.blocks().stream()
                    .filter(b -> !b.targetState().isAir()).toList());
        }
        var ledger = new PlacementOwnershipLedger(Level.OVERWORLD);
        assertInstanceOf(PlacementCommitResult.Success.class, new BlockPlanCommitter().commitOwned(
                RoadMaterializationOwner.node(node.id()), initial, world, ledger));
        var cleared = new BlockPos(0, 72, 0);
        assertEquals(!previouslyRestored, world.blockState(cleared).isAir());

        BlockPlacementPlan rebuilt;
        if (crosswalk) {
            var updated = new JunctionDefinition(node.id(), definition.strategy(), definition.priorityPolicy(),
                    definition.primaryApproachKeys(), definition.crossingPolicy(),
                    Map.of(ApproachKey.at(incident.get(0), node.id()),
                            new JunctionApproachOverride(IntersectionControl.FREE, false)), 2);
            rebuilt = new CrosswalkToggleEndpoint(ConstructionAuthorizer.creativeOnly(), limits,
                    new BlockPlanCommitter()).nodePlan(graph, node, incident, updated, world, ledger);
        } else {
            rebuilt = new LaneConnectorEditEndpoint(ConstructionAuthorizer.creativeOnly(), limits,
                    new BlockPlanCommitter()).nodePlan(graph, node.id(),
                    graph.snapshot().connectors().values(), world, ledger);
        }
        assertNotNull(rebuilt);
        assertTrue(rebuilt.blocks().stream().anyMatch(b ->
                b.position().equals(cleared) && b.targetState().isAir()),
                "a node edit must keep ownership of the cleared terrain above its road");
        assertEquals(!previouslyRestored, world.blockState(cleared).isAir(),
                "planning must leave the live world unchanged");
        var previous = ledger.operation(RoadMaterializationOwner.node(node.id())).orElseThrow();
        var removedIds = List.of(previous.id());
        var overlay = crosswalk
                ? CrosswalkToggleEndpoint.finalStateOverlay(world, ledger, removedIds, List.of(rebuilt))
                : LaneConnectorEditEndpoint.finalStateOverlay(world, ledger, removedIds, List.of(rebuilt));
        var overlayId = PlacementOperationId.create();
        assertInstanceOf(PlacementCommitResult.Success.class,
                new BlockPlanCommitter().commitRestorationOverlay(overlayId, overlay, world, ledger));
        ledger.publishMaterializationRebuild(removedIds, overlayId, List.of(
                new MaterializationPartition(overlayId, RoadMaterializationOwner.node(node.id()), rebuilt.positions())));
        assertTrue(world.blockState(cleared).isAir(), "publishing an edit must not restore terrain over the road");
        assertTrue(ledger.expectedVisibleState(cleared).orElseThrow().isAir());
        var terrainView = new MaterializationRebuildWorld(world, ledger, List.of(overlayId));
        assertTrue(terrainView.blockState(cleared).is(Blocks.STONE),
                "the original terrain must still be available when the road is actually removed");
    }

    private static class Terrain implements PlacementBlockWorld {
        private final Map<BlockPos, BlockState> changes = new HashMap<>();
        public net.minecraft.resources.ResourceKey<Level> dimension() { return Level.OVERWORLD; }
        public BlockState blockState(BlockPos p) {
            return changes.getOrDefault(p, (p.getY() <= 72 ? Blocks.STONE : Blocks.AIR).defaultBlockState());
        }
        public boolean hasBlockEntity(BlockPos p) { return false; }
        public boolean setBlockState(BlockPos p, BlockState state) { changes.put(p.immutable(), state); return true; }
    }
}
