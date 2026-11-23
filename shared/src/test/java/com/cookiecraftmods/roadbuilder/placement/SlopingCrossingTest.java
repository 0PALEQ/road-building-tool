package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import com.cookiecraftmods.roadbuilder.geometry.*;
import com.cookiecraftmods.roadbuilder.network.*;
import com.cookiecraftmods.roadbuilder.preset.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SlopingCrossingTest {
    @Test void joinsUpToFourBlocksButKeepsSixBlockCrossingsSeparated() {
        var material = MinecraftIds.id("minecraft", "gray_concrete");
        var preset = new RoadPreset(RoadPreset.CURRENT_SCHEMA_VERSION, 1, 11, material,
                List.of(new LaneComponent(LaneType.VEHICLE, 3, 0, LaneTravelDirection.FORWARD)));
        for (double base : new double[]{32, 80}) {
            for (double gap : new double[]{-6, -4.001, -4, -3, -2, 0, 2, 3, 4, 4.001, 6}) {
                var graph = new RoadGraph(Level.OVERWORLD);
                graph.applyNetworkMutation(graph.prepareRoadNetwork(new StraightGeometry(
                        new RoadPoint(-100, base - 5, 0), new RoadPoint(100, base + 5, 0)),
                        material, preset, TrafficHandedness.RHT));
                var proposed = new StraightGeometry(new RoadPoint(0, base + gap, -100),
                        new RoadPoint(0, base + gap, 100));
                var adjusted = assertDoesNotThrow(() -> new RoadOverpassPlanner().adjust(proposed, 11,
                        graph.snapshot().segments().values()), "gap=" + gap);
                var mutation = graph.prepareRoadNetwork(adjusted, material, preset, TrafficHandedness.RHT);
                if (Math.abs(gap) <= 4) {
                    assertSame(proposed, adjusted);
                    assertEquals(1, mutation.segmentSplits().size(), "gap=" + gap);
                    assertEquals(2, mutation.placedSegments().size());
                    var node = mutation.addedNodes().stream().filter(n -> Math.abs(n.position().x()) < 1e-8
                            && Math.abs(n.position().z()) < 1e-8).findFirst().orElseThrow();
                    assertEquals(base, node.position().y(), 1e-8);
                    assertInstanceOf(BlockPlanResult.Success.class, new StraightRoadIntersectionMaterializer().plan(
                            Level.OVERWORLD, node, mutation.allUpsertedSegments(), material,
                            MinecraftIds.id("minecraft", "smooth_stone"), 131072, 1000000));
                } else {
                    assertTrue(mutation.segmentSplits().isEmpty(), "gap=" + gap);
                    assertEquals(1, mutation.placedSegments().size());
                    double y = adjusted.sample(adjusted.length() / 2).position().y();
                    assertTrue(gap > 0 ? y >= base + 6 : y <= base - 6);
                }
            }
        }
    }

    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        com.cookiecraftmods.roadbuilder.test.MinecraftBootstrap.bootstrap();
    }

    @Test void wideSlopingCrossingsAreNotMistakenForOverpasses() {
        var id=MinecraftIds.id("roadbuilder","sloping_crossing");
        var preset=new RoadPreset(RoadPreset.CURRENT_SCHEMA_VERSION,1,29,
                MinecraftIds.id("minecraft","gray_concrete"),
                List.of(new LaneComponent(LaneType.VEHICLE,3,0,LaneTravelDirection.FORWARD)));
        for (double angle : List.of(30.0,60.0,90.0)) {
            for (double slope : List.of(-0.2,0.2)) {
                var graph=new RoadGraph(Level.OVERWORLD);
                graph.applyNetworkMutation(graph.prepareRoadNetwork(new StraightGeometry(
                        new RoadPoint(-80,70-80*slope,0),new RoadPoint(80,70+80*slope,0)),
                        id,preset,TrafficHandedness.RHT));
                double dx=44*Math.cos(Math.toRadians(angle)), dz=44*Math.sin(Math.toRadians(angle));
                var crossing=new StraightGeometry(new RoadPoint(-dx,69.5,-dz),new RoadPoint(dx,70.5,dz));
                var adjusted=assertDoesNotThrow(() -> new RoadOverpassPlanner().adjust(crossing,29,
                        graph.snapshot().segments().values()),"angle="+angle+", slope="+slope);
                assertSame(crossing,adjusted);
                var mutation=graph.prepareRoadNetwork(adjusted,id,preset,TrafficHandedness.RHT);
                assertEquals(1,mutation.segmentSplits().size());
                assertEquals(2,mutation.placedSegments().size());
                var centre=mutation.addedNodes().stream().filter(n -> Math.abs(n.position().x())<0.01
                        && Math.abs(n.position().z())<0.01).findFirst().orElseThrow();
                if (angle==90.0) {
                    var field=new JunctionSurfaceElevation(centre,mutation.allUpsertedSegments());
                    assertEquals(centre.position().y(),field.height(10,10),1e-8,
                            "the full overlapping pavement must be level, including wide short approaches");
                }
                var materialized=new StraightRoadIntersectionMaterializer().plan(Level.OVERWORLD,centre,
                        mutation.allUpsertedSegments(),MinecraftIds.id("minecraft","gray_concrete"),
                        MinecraftIds.id("minecraft","smooth_stone"),131072,1000000);
                assertInstanceOf(BlockPlanResult.Success.class,materialized);
                graph.applyNetworkMutation(mutation);
                assertEquals(4,graph.snapshot().segments().size());
            }
        }
    }

    @Test void addingAnArmToAnExistingNodeRefitsBothExistingRoads() {
        var graph=new RoadGraph(Level.OVERWORLD);
        var material=MinecraftIds.id("minecraft","gray_concrete");
        var preset=new RoadPreset(RoadPreset.CURRENT_SCHEMA_VERSION,1,11,material,
                List.of(new LaneComponent(LaneType.VEHICLE,3,0,LaneTravelDirection.FORWARD)));
        var centre=new RoadPoint(0,70,0);
        graph.applyNetworkMutation(graph.prepareRoadNetwork(new StraightGeometry(new RoadPoint(-120,58,0),centre),
                material,preset,TrafficHandedness.RHT));
        graph.applyNetworkMutation(graph.prepareRoadNetwork(new StraightGeometry(centre,new RoadPoint(120,82,0)),
                material,preset,TrafficHandedness.RHT));
        var mutation=graph.prepareRoadNetwork(new StraightGeometry(centre,new RoadPoint(0,74,100)),
                material,preset,TrafficHandedness.RHT);
        assertEquals(2,mutation.segmentSplits().size());
        assertTrue(mutation.segmentSplits().stream().allMatch(split -> split.replacements().size()==1));
        assertTrue(mutation.rebuildsExistingGeometry());
        for(var segment:mutation.allUpsertedSegments()) {
            var geometry=segment.referenceGeometry();
            assertEquals(0,geometry.sample(geometry.start().equals(centre)?0:geometry.length()).tangent().y(),1e-8);
        }
        graph.applyNetworkMutation(mutation);
        assertEquals(3,graph.snapshot().segments().size());
    }

    @Test void longAndComplexProfilesSurviveSaveAndNetworkSync() {
        var material=MinecraftIds.id("minecraft","gray_concrete");
        var preset=new RoadPreset(RoadPreset.CURRENT_SCHEMA_VERSION,1,3,material,
                List.of(new LaneComponent(LaneType.VEHICLE,3,0,LaneTravelDirection.FORWARD)));
        var anchors=new java.util.ArrayList<RoadPoint>();
        for(int i=0;i<64;i++) anchors.add(new RoadPoint(i*8,64+i*0.5,3*Math.sin(i*0.2)));
        for(var source:List.<ReferenceGeometry>of(new StraightGeometry(new RoadPoint(0,64,0),new RoadPoint(512,96,0)),
                new MultiCurveGeometry(anchors))) {
            var fitted=JunctionElevationProfile.fit(source,12,12);
            var data=com.cookiecraftmods.roadbuilder.persistence.RoadBuilderNetworkSavedData.create(Level.OVERWORLD);
            data.graph().applyNetworkMutation(data.graph().prepareRoadNetwork(fitted,material,preset,TrafficHandedness.RHT));
            var loaded=com.cookiecraftmods.roadbuilder.persistence.RoadBuilderNetworkSavedData.load(Level.OVERWORLD,
                    data.save(new net.minecraft.nbt.CompoundTag()));
            assertTrue(loaded.isCompatible());
            assertEquals(data.graph().snapshot(),loaded.graph().snapshot());
            var packet=com.cookiecraftmods.roadbuilder.networking.RoadGraphSnapshotS2CPacket.current(data.graph().snapshot());
            assertEquals(packet,com.cookiecraftmods.roadbuilder.networking.RoadGraphSnapshotS2CPacket.decode(packet.encode()));
        }
    }

    @Test void sunkenCrossingCanReplaceTheOldUphillSurface() {
        for (boolean incremental : List.of(false,true)) {
        var material=MinecraftIds.id("minecraft","gray_concrete");
        var preset=new RoadPreset(RoadPreset.CURRENT_SCHEMA_VERSION,1,11,material,
                List.of(new LaneComponent(LaneType.VEHICLE,3,0,LaneTravelDirection.FORWARD)));
        var saved=com.cookiecraftmods.roadbuilder.persistence.RoadBuilderNetworkSavedData.create(Level.OVERWORLD);
        var graph=saved.graph();
        var host=graph.prepareRoadNetwork(new StraightGeometry(new RoadPoint(-60.5,58,0.5),
                new RoadPoint(60.5,82,0.5)),material,preset,TrafficHandedness.RHT);
        graph.applyNetworkMutation(host);
        var states=new java.util.HashMap<net.minecraft.core.BlockPos,net.minecraft.world.level.block.state.BlockState>();
        var restoredGrass = new net.minecraft.core.BlockPos(10, 90, 15);
        PlacementBlockWorld world=new PlacementBlockWorld() {
            public net.minecraft.resources.ResourceKey<Level> dimension() { return Level.OVERWORLD; }
            public int maximumBuildHeight() { return 100; }
            public int highestBlockY(int x, int z) {
                if (x == restoredGrass.getX() && z == restoredGrass.getZ() && blockState(restoredGrass).isAir()) {
                    return restoredGrass.getY() - 1;
                }
                return PlacementBlockWorld.super.highestBlockY(x, z);
            }
            public boolean hasBlockEntity(net.minecraft.core.BlockPos p) { return false; }
            public net.minecraft.world.level.block.state.BlockState blockState(net.minecraft.core.BlockPos p) {
                return states.getOrDefault(p,(p.getY()<55?net.minecraft.world.level.block.Blocks.STONE:
                        net.minecraft.world.level.block.Blocks.AIR).defaultBlockState());
            }
            public boolean setBlockState(net.minecraft.core.BlockPos p,net.minecraft.world.level.block.state.BlockState s) {
                states.put(p,s); return true;
            }
        };
        var limits=ConstructionLimits.defaults();
        var ledger=new PlacementOwnershipLedger(Level.OVERWORLD);
        var oldPlan=assertInstanceOf(BlockPlanResult.Success.class,new RoadMaterializationPlanner().plan(
                Level.OVERWORLD,host.placedSegments(),world,ledger,limits)).plan();
        var grass = net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState();
        states.put(restoredGrass, grass);
        var oldTargets = new java.util.ArrayList<>(oldPlan.blocks());
        oldTargets.add(new PlannedBlock(restoredGrass, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()));
        oldPlan = BlockPlacementPlan.create(Level.OVERWORLD, oldTargets);
        assertInstanceOf(PlacementCommitResult.Success.class,new BlockPlanCommitter().commit(
                PlacementOperationId.create(),RoadMaterializationOwner.segment(host.placedSegments().get(0).id()),
                oldPlan,world,ledger));
        var mutation=graph.prepareRoadNetwork(new StraightGeometry(new RoadPoint(0.5,70,-60.5),
                new RoadPoint(0.5,70,60.5)),material,preset,TrafficHandedness.RHT).withSunkenRoad(true);
        assertTrue(mutation.rebuildsExistingGeometry());
        var roads=assertInstanceOf(BlockPlanResult.Success.class,new RoadMaterializationPlanner().plan(
                Level.OVERWORLD,mutation,world,ledger,limits)).plan();
        var node=mutation.addedNodes().stream().filter(n -> mutation.allUpsertedSegments().stream()
                .filter(r -> r.startNodeId().equals(n.id())||r.endNodeId().equals(n.id())).count()==4).findFirst().orElseThrow();
        var surface=assertInstanceOf(BlockPlanResult.Success.class,new StraightRoadIntersectionMaterializer().plan(
                Level.OVERWORLD,node,mutation.allUpsertedSegments(),material,
                MinecraftIds.id("minecraft","smooth_stone"),131072,1000000)).plan();
        var rebuilding=new MaterializationRebuildWorld(world,ledger,
                RoadMaterializationPlanner.replacedOperations(mutation,Level.OVERWORLD,ledger,limits));
        var roadTargets = assertInstanceOf(BlockPlanResult.Success.class,
                new RoadMaterializationPlanner().planRoads(Level.OVERWORLD, mutation, world, ledger, limits)).plan();
        assertTrue(BlockPositionSets.nonAirTargets(roads).contains(restoredGrass));
        assertFalse(BlockPositionSets.nonAirTargets(roadTargets).contains(restoredGrass));
        var cutFloor = restoredGrass.below(2);
        var cutSurface = BlockPlacementPlan.create(Level.OVERWORLD, List.of(
                new PlannedBlock(cutFloor, net.minecraft.world.level.block.Blocks.GRAY_CONCRETE.defaultBlockState())));
        var cut = assertInstanceOf(BlockPlanResult.Success.class, new SunkenRoadPlanner().excavate(
                cutSurface, java.util.Set.of(cutFloor), rebuilding,
                BlockPositionSets.nonAirTargets(roadTargets)::contains, limits.maximumAffectedBlocks())).plan();
        assertInstanceOf(BlockPlanResult.Failure.class, new SunkenRoadPlanner().excavate(
                cutSurface, java.util.Set.of(cutFloor), rebuilding,
                BlockPositionSets.nonAirTargets(roads)::contains, limits.maximumAffectedBlocks()));
        var completedCut = assertInstanceOf(BlockPlanResult.Success.class,
                new RoadMaterializationPlanner().restoreReplacedFootprint(cut, mutation, world, ledger, limits)).plan();
        assertTrue(completedCut.blocks().stream().anyMatch(b -> b.position().equals(restoredGrass)
                && b.targetState().isAir()), "Junction excavation must override terrain restoration");
        var junction=assertInstanceOf(BlockPlanResult.Success.class,new RoadTransitionPlanPreparer().complete(
                surface,node,mutation.allUpsertedSegments(),rebuilding,rebuilding::isMaterializedRoadPosition,limits)).plan();
        var retained=BlockPlacementPlans.mergeLayered(Level.OVERWORLD,List.of(
                new LayeredBlockPlan(roadTargets,MaterialLayer.SEGMENT_SURFACE,"road","test"),
                new LayeredBlockPlan(junction,MaterialLayer.JUNCTION_SURFACE,"node","test")));
        var plan=assertInstanceOf(BlockPlanResult.Success.class,
                new RoadMaterializationPlanner().restoreReplacedFootprint(retained,mutation,world,ledger,limits)).plan();
        var operation=PlacementOperationId.create();
        var repartitioner=new RoadMaterializationRepartitioner(limits);
        var partitions=new java.util.ArrayList<>(repartitioner.prepareExisting(mutation,Level.OVERWORLD,ledger));
        partitions.add(repartitioner.prepare(PlacementOperation.create(operation,Level.OVERWORLD,retained.positions()),
                mutation.materializedSegments(),Level.OVERWORLD,java.util.Map.of(node.id(),junction)));
        if (incremental) {
            var jobs=com.cookiecraftmods.roadbuilder.persistence.RoadBuilderConstructionJobSavedData.create(Level.OVERWORLD);
            var service=new IncrementalPlacementJobService(1);
            var job=assertInstanceOf(IncrementalPlacementJobService.EnqueueResult.Queued.class,service.enqueueIfExpensive(
                    java.util.UUID.randomUUID(),operation,plan,java.util.Optional.empty(),partitions,mutation,
                    world,ledger,graph,jobs)).job();
            job=com.cookiecraftmods.roadbuilder.persistence.RoadBuilderConstructionJobSavedData.load(Level.OVERWORLD,
                    jobs.save(new net.minecraft.nbt.CompoundTag())).job(job.id()).orElseThrow();
            boolean completed=false;
            for(int i=0;i<100;i++) {
                var tick=service.tick(job,2048,world,ledger,graph); job=tick.job();
                if(tick.outcome()==IncrementalPlacementJobService.TickOutcome.COMPLETED) { completed=true; break; }
            }
            assertTrue(completed,"persisted rebuild must complete");
        } else {
            assertInstanceOf(PlacementCommitResult.Success.class,new BlockPlanCommitter().commit(operation,plan,world,ledger));
            ledger.publishNetworkRepartitions(partitions);
            graph.applyNetworkMutation(mutation);
        }
        assertTrue(RoadGraphMaterializationConsistency.validate(graph,ledger).isEmpty());
        assertEquals(grass, world.blockState(restoredGrass));
        assertTrue(ledger.stack(restoredGrass).isEmpty(), "Restored terrain must not be owned by the new road");
        var loaded=com.cookiecraftmods.roadbuilder.persistence.RoadBuilderNetworkSavedData.load(Level.OVERWORLD,
                saved.save(new net.minecraft.nbt.CompoundTag()));
        assertEquals(graph.snapshot(),loaded.graph().snapshot());
        var packet=com.cookiecraftmods.roadbuilder.networking.RoadGraphSnapshotS2CPacket.current(graph.snapshot());
        assertEquals(packet,com.cookiecraftmods.roadbuilder.networking.RoadGraphSnapshotS2CPacket.decode(packet.encode()));
        // All refitted segment surfaces outside the shared junction use their
        // actual stored elevation, including the old road's descending arm.
        for(var segment:mutation.allUpsertedSegments()) {
            var expected=assertInstanceOf(BlockPlanResult.Success.class,new StraightRoadSegmentMaterializer().plan(
                    Level.OVERWORLD,segment,limits)).plan();
            for(var block:expected.blocks()) if(Math.hypot(block.position().getX()-node.position().x(),
                    block.position().getZ()-node.position().z())>20)
                assertEquals(block.targetState(),world.blockState(block.position()));
        }
        for(var owned:List.copyOf(ledger.operations())) assertInstanceOf(PlacementRemovalResult.Success.class,
                new PlacementRemovalService().remove(owned.id(),RemovalMode.SAFE,world,ledger));
        assertTrue(ledger.operations().isEmpty());
        assertEquals(grass, states.get(restoredGrass));
        assertTrue(states.entrySet().stream().filter(e -> !e.getKey().equals(restoredGrass)).allMatch(e -> e.getValue().equals((e.getKey().getY()<55
                ? net.minecraft.world.level.block.Blocks.STONE:net.minecraft.world.level.block.Blocks.AIR).defaultBlockState())),
                "removal must restore terrain, never the obsolete steep approach");
        }

    }
}

