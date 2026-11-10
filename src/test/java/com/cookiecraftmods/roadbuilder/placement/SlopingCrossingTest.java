package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.geometry.*;
import com.cookiecraftmods.roadbuilder.network.*;
import com.cookiecraftmods.roadbuilder.preset.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SlopingCrossingTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        com.cookiecraftmods.roadbuilder.test.MinecraftBootstrap.bootstrap();
    }

    @Test void wideSlopingCrossingsAreNotMistakenForOverpasses() {
        var id=new ResourceLocation("roadbuilder","sloping_crossing");
        var preset=new RoadPreset(RoadPreset.CURRENT_SCHEMA_VERSION,1,29,
                new ResourceLocation("minecraft","gray_concrete"),
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
                        mutation.allUpsertedSegments(),new ResourceLocation("minecraft","gray_concrete"),
                        new ResourceLocation("minecraft","smooth_stone"),131072,1000000);
                assertInstanceOf(BlockPlanResult.Success.class,materialized);
                graph.applyNetworkMutation(mutation);
                assertEquals(4,graph.snapshot().segments().size());
            }
        }
    }

    @Test void sunkenCrossingCanReplaceTheOldUphillSurface() {
        var material=new ResourceLocation("minecraft","gray_concrete");
        var preset=new RoadPreset(RoadPreset.CURRENT_SCHEMA_VERSION,1,11,material,
                List.of(new LaneComponent(LaneType.VEHICLE,3,0,LaneTravelDirection.FORWARD)));
        var graph=new RoadGraph(Level.OVERWORLD);
        var host=graph.prepareRoadNetwork(new StraightGeometry(new RoadPoint(-60.5,58,0.5),
                new RoadPoint(60.5,82,0.5)),material,preset,TrafficHandedness.RHT);
        graph.applyNetworkMutation(host);
        var states=new java.util.HashMap<net.minecraft.core.BlockPos,net.minecraft.world.level.block.state.BlockState>();
        PlacementBlockWorld world=new PlacementBlockWorld() {
            public net.minecraft.resources.ResourceKey<Level> dimension() { return Level.OVERWORLD; }
            public int maximumBuildHeight() { return 100; }
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
        assertInstanceOf(PlacementCommitResult.Success.class,new BlockPlanCommitter().commit(
                PlacementOperationId.create(),RoadMaterializationOwner.segment(host.placedSegments().get(0).id()),
                oldPlan,world,ledger));
        var mutation=graph.prepareRoadNetwork(new StraightGeometry(new RoadPoint(0.5,70,-60.5),
                new RoadPoint(0.5,70,60.5)),material,preset,TrafficHandedness.RHT).withSunkenRoad(true);
        assertInstanceOf(BlockPlanResult.Success.class,new RoadMaterializationPlanner().plan(
                Level.OVERWORLD,mutation,world,ledger,limits));
    }
}

