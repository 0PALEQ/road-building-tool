package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import com.cookiecraftmods.roadbuilder.geometry.*;
import com.cookiecraftmods.roadbuilder.network.*;
import com.cookiecraftmods.roadbuilder.preset.*;
import com.cookiecraftmods.roadbuilder.persistence.RoadBuilderNetworkSavedData;
import com.cookiecraftmods.roadbuilder.networking.RoadGraphSnapshotS2CPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LaneRampAndJunctionRegressionTest {
    private static final ResourceLocation GRAY=MinecraftIds.id("minecraft","gray_concrete");
    private static final ResourceLocation WHITE=MinecraftIds.id("minecraft","white_concrete");
    @BeforeAll static void bootstrap() {com.cookiecraftmods.roadbuilder.test.MinecraftBootstrap.bootstrap();}

    private static RoadSegment road(ReferenceGeometry geometry,RoadNodeId start,RoadNodeId end,int width,boolean multi) {
        var lanes=multi?List.of(new LaneComponent(LaneType.VEHICLE,3,-2,LaneTravelDirection.FORWARD),
                new LaneComponent(LaneType.VEHICLE,3,2,LaneTravelDirection.FORWARD)):
                List.of(new LaneComponent(LaneType.VEHICLE,3,0,LaneTravelDirection.FORWARD));
        var bands=List.of(new RoadSurfaceBand(RoadSurfaceRole.PAVEMENT,1,GRAY),new RoadSurfaceBand(RoadSurfaceRole.MARKING,1,WHITE),
                new RoadSurfaceBand(RoadSurfaceRole.PAVEMENT,width-4,GRAY),new RoadSurfaceBand(RoadSurfaceRole.MARKING,1,WHITE),
                new RoadSurfaceBand(RoadSurfaceRole.PAVEMENT,1,GRAY));
        return RoadSegment.path(new RoadSegmentId(UUID.randomUUID()),new RoadId(UUID.randomUUID()),start,end,geometry,
                MinecraftIds.id("roadbuilder","regression"),1,new RoadPresetSnapshot(width,GRAY,lanes,bands),TrafficHandedness.RHT,
                lanes.stream().map(l->RoadLaneId.create()).toList());
    }
    private static RoadNodeId id() {return new RoadNodeId(UUID.randomUUID());}

    private static ReferenceGeometry risingRamp() {
        var source=road(new StraightGeometry(new RoadPoint(-40,64,0),new RoadPoint(0,64,0)),id(),id(),11,true);
        var port=AuxiliaryLanePorts.ports(List.of(source)).stream().filter(AuxiliaryLanePorts.Port::incoming)
                .max(Comparator.comparingDouble(p->p.position().z())).orElseThrow();
        var raw=new CubicBezierGeometry(port.position(),port.position().add(new RoadVector(45,9,0)),
                new RoadPoint(110,68,30),new RoadPoint(160,84,40));
        return new RoadOverpassPlanner().adjust(raw,7,List.of(source));
    }

    @Test void rampWaitsUntilClearThenClimbsMonotonicallyAndLevelsOff() {
        var ramp=assertDoesNotThrow(LaneRampAndJunctionRegressionTest::risingRamp);
        assertInstanceOf(MultiCurveGeometry.class,ramp);
        assertEquals(64,ramp.sample(5).position().y(),1e-8);
        assertEquals(84,ramp.sample(ramp.length()-2).position().y(),1e-8);
        double previous=64;
        for(double d=0;d<ramp.length();d+=0.1) {
            var sample=ramp.sample(d);
            assertTrue(sample.position().y()>=previous-1e-7,"ramp must not dip before climbing");
            assertTrue(Math.abs(sample.tangent().y())/sample.tangent().horizontalLength()<=0.265);
            assertTrue(sample.position().y()<=84+1e-7);
            if(Math.hypot(sample.position().x(),sample.position().z())<=9)
                assertEquals(64,sample.position().y(),1e-7,"ramp must remain level inside the host footprint");
            previous=sample.position().y();
        }
        assertEquals(0,ramp.sample(ramp.length()).tangent().y(),1e-5);
    }

    @Test void descendingRampAlsoLevelsBeforeJoiningItsDestinationLane() {
        var host=road(new StraightGeometry(new RoadPoint(0,64,0),new RoadPoint(-40,64,0)),id(),id(),11,true);
        var port=AuxiliaryLanePorts.ports(List.of(host)).stream().filter(p->!p.incoming())
                .max(Comparator.comparingDouble(p->p.position().z())).orElseThrow();
        var raw=new CubicBezierGeometry(new RoadPoint(160,84,40),new RoadPoint(110,68,30),
                port.position().add(new RoadVector(45,9,0)),port.position());
        var ramp=assertDoesNotThrow(()->new RoadOverpassPlanner().adjust(raw,7,List.of(host)));
        double previous=84;
        for(double d=0;d<ramp.length();d+=0.1) {
            var sample=ramp.sample(d);
            assertTrue(sample.position().y()<=previous+1e-7);
            assertTrue(Math.abs(sample.tangent().y())/sample.tangent().horizontalLength()<=0.265);
            previous=sample.position().y();
        }
        assertEquals(64,ramp.sample(ramp.length()-5).position().y(),1e-7);
        assertEquals(0,ramp.sample(ramp.length()).tangent().y(),1e-5);
    }

    @Test void rampProfileSurvivesSaveSyncReverseAndSplitting() {
        var ramp=risingRamp();
        var preset=new RoadPreset(RoadPreset.CURRENT_SCHEMA_VERSION,1,7,GRAY,
                List.of(new LaneComponent(LaneType.VEHICLE,3,0,LaneTravelDirection.FORWARD)));
        var saved=RoadBuilderNetworkSavedData.create(Level.OVERWORLD);
        saved.graph().applyNetworkMutation(saved.graph().prepareRoadNetwork(ramp,MinecraftIds.id("roadbuilder","profile"),preset,TrafficHandedness.RHT));
        var loaded=RoadBuilderNetworkSavedData.load(Level.OVERWORLD,saved.save(new CompoundTag()));
        assertTrue(loaded.isCompatible());
        var restored=loaded.graph().snapshot().segments().values().iterator().next().referenceGeometry();
        assertEquals(ramp,restored);
        var packet=RoadGraphSnapshotS2CPacket.current(saved.graph().snapshot());
        var synced=RoadGraphSnapshotS2CPacket.decode(packet.encode()).snapshot().segments().values().iterator().next().referenceGeometry();
        assertEquals(ramp,synced);
        assertEquals(ramp,ReferenceGeometryReverser.reverse(ReferenceGeometryReverser.reverse(ramp)));
        var midpoint=ramp.sample(ramp.length()/2).position();
        var parts=ReferenceGeometryEditor.split(ramp,List.of(0.5),List.of(midpoint));
        assertEquals(ramp.length(),parts.stream().mapToDouble(ReferenceGeometry::length).sum(),0.02);
        assertEquals(midpoint,parts.get(0).end());
        assertTrue(parts.get(0).sample(parts.get(0).length()).tangent().dot(parts.get(1).sample(0).tangent())>0.99999);
    }

    @Test void shortSlopingJunctionArmSamplesBothEndsWithinItsLength() {
        var start = new RoadNode(id(), new RoadPoint(0, 70, 0));
        var end = new RoadNode(id(), new RoadPoint(12, 72, 0));
        var geometry = new StraightGeometry(start.position(), end.position());
        double length = geometry.length();
        int count = (int) Math.ceil(length / 0.5);
        assertTrue(length * count / count > length);
        var segment = road(geometry, start.id(), end.id(), 7, false);

        for (var node : List.of(start, end)) {
            var field = new JunctionSurfaceElevation(node, List.of(segment));
            assertEquals(70, field.height(0, 0), 1e-8);
            assertEquals(72, field.height(12, 0), 1e-8);
        }
    }

    @Test void slopingJunctionHasLevelCoreAndQuadraticApproaches() {
        var center=new RoadNode(id(),new RoadPoint(0.5,70,0.5));
        List<RoadSegment> roads=List.of(
                road(new StraightGeometry(center.position(),new RoadPoint(-60,58,0.5)),center.id(),id(),11,false),
                road(new StraightGeometry(center.position(),new RoadPoint(60,82,0.5)),center.id(),id(),11,false),
                road(new StraightGeometry(center.position(),new RoadPoint(0.5,70,-60)),center.id(),id(),11,false),
                road(new StraightGeometry(center.position(),new RoadPoint(0.5,70,60)),center.id(),id(),11,false));
        roads=roads.stream().map(r -> road(JunctionElevationProfile.fit(r.referenceGeometry(),7.5,0),
                center.id(),r.endNodeId(),11,false)).toList();
        var plan=assertInstanceOf(BlockPlanResult.Success.class,new StraightRoadIntersectionMaterializer().plan(
                Level.OVERWORLD,center,roads,GRAY,MinecraftIds.id("minecraft","smooth_stone"),20000,200000)).plan();
        var slope=plan.blocks().stream().filter(b->!b.targetState().isAir() && b.position().getX()>=-8 && b.position().getX()<=-5 && Math.abs(b.position().getZ())<=1).toList();
        assertFalse(slope.isEmpty());
        assertTrue(slope.stream().allMatch(b->b.position().getY()==70),"the junction core must be level");
        var field=new JunctionSurfaceElevation(center,roads);
        assertEquals(70,field.height(5.5,0.5),1e-8);
        assertTrue(field.height(20.5,0.5)>70);
        assertTrue(field.height(20.5,0.5)<74.1);
        for(var b:slope) assertEquals(field.height(b.position().getX()+0.5,b.position().getZ()+0.5),b.position().getY(),0.51);
        var markings=assertInstanceOf(BlockPlanResult.Success.class,new JunctionMarkingMaterializer().plan(
                Level.OVERWORLD,center,roads,JunctionDefinition.automatic(center.id()),20000)).plan();
        for(var b:markings.blocks()) assertEquals(field.height(b.position().getX()+0.5,b.position().getZ()+0.5),b.position().getY(),0.51);
    }

    @Test void mergePavementDoesNotCutTheContinuousOutsideLine() {
        var node=new RoadNode(id(),new RoadPoint(0.5,64,0.5));
        var trunk=road(new StraightGeometry(new RoadPoint(-60,64,0.5),node.position()),id(),node.id(),11,true);
        var port=AuxiliaryLanePorts.ports(List.of(trunk)).stream().filter(AuxiliaryLanePorts.Port::incoming)
                .max(Comparator.comparingDouble(p->p.position().z())).orElseThrow();
        var branch=road(new CubicBezierGeometry(port.position(),port.position().add(new RoadVector(20,0,0)),
                new RoadPoint(40.5,64,20.5),new RoadPoint(60.5,64,30.5)),node.id(),id(),7,false);
        List<PlannedBlock> pavement=new ArrayList<>();
        for(int x=-15;x<=15;x++) for(int z=-8;z<=12;z++) pavement.add(new PlannedBlock(new BlockPos(x,64,z),Blocks.GRAY_CONCRETE.defaultBlockState()));
        var restored=LaneMergeMarkingPlanner.restore(Level.OVERWORLD,node,List.of(trunk,branch),pavement,10000,200000);
        Set<BlockPos> white=new HashSet<>();
        restored.stream().filter(b->b.targetState().is(Blocks.WHITE_CONCRETE)).forEach(b->white.add(b.position()));
        assertTrue(white.contains(new BlockPos(-5,64,4)),"trunk outside line must be retained");
        Set<BlockPos> visited=new HashSet<>(); var queue=new ArrayDeque<BlockPos>();queue.add(new BlockPos(-5,64,4));
        while(!queue.isEmpty()) {var p=queue.removeFirst();if(!visited.add(p))continue;
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){var next=p.offset(dx,0,dz);if(white.contains(next)&&!visited.contains(next))queue.add(next);}}
        assertTrue(visited.stream().anyMatch(p->p.getX()>=8),"outside line must remain connected through the merge");
    }
}
