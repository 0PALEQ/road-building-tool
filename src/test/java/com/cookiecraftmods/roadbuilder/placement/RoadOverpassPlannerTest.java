package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.geometry.*;
import com.cookiecraftmods.roadbuilder.network.*;
import com.cookiecraftmods.roadbuilder.preset.*;
import com.cookiecraftmods.roadbuilder.test.MinecraftBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RoadOverpassPlannerTest {
    @Test void curvedOverpassRemainsBuildableAfterItsElevationIsAdjusted() {
        var curve=new CubicBezierGeometry(new RoadPoint(0,69,0),new RoadPoint(30,69,0),
                new RoadPoint(60,69,20),new RoadPoint(100,69,20));
        var adjusted=assertDoesNotThrow(()->new RoadOverpassPlanner().adjust(curve,9,List.of(lower())));
        assertEquals(curve.start(),adjusted.start());
        assertEquals(curve.end(),adjusted.end());
        assertNull(RoadCurveValidation.validate(adjusted,RoadCurveValidation.minimumRadius(9,RoadClass.LOCAL)));
    }

    @Test void slopingRoadsWithEnoughLocalClearanceDoNotRequireEndpointRamps() {
        var graph=new RoadGraph(Level.OVERWORLD);
        var lower=graph.prepareRoadNetwork(new StraightGeometry(new RoadPoint(0,64,0),new RoadPoint(100,84,0)),
                ID,preset(),TrafficHandedness.RHT).placedSegments();
        var upper=new StraightGeometry(new RoadPoint(0,78,2),new RoadPoint(100,98,2));
        assertSame(upper,assertDoesNotThrow(()->new RoadOverpassPlanner().adjust(upper,11,lower)));
    }

    @BeforeAll static void bootstrap() { MinecraftBootstrap.bootstrap(); }
    private static final ResourceLocation ID = new ResourceLocation("roadbuilder","overpass_test");
    private static RoadPreset preset() {
        return new RoadPreset(RoadPreset.CURRENT_SCHEMA_VERSION, 1, 11,
                new ResourceLocation("minecraft","gray_concrete"),
                List.of(new LaneComponent(LaneType.VEHICLE,3,0,LaneTravelDirection.FORWARD)));
    }
    private static RoadSegment lower() {
        var graph = new RoadGraph(Level.OVERWORLD);
        return graph.prepareRoadNetwork(new StraightGeometry(new RoadPoint(50.5,64,-30.5),
                new RoadPoint(50.5,64,30.5)), ID,preset(),TrafficHandedness.RHT).placedSegments().get(0);
    }
    private static ReferenceGeometry upper(double y) {
        return new StraightGeometry(new RoadPoint(0.5,y,0.5),new RoadPoint(100.5,y,0.5));
    }
    @Test void raisesWholeCrossingFootprintAndKeepsEndpointElevations() {
        var original=upper(69);
        var result=new RoadOverpassPlanner().adjust(original,11,List.of(lower()));
        assertEquals(original.start(),result.start());
        assertEquals(original.end(),result.end());
        assertNotSame(original,result);
        for(double d=0;d<result.length();d+=0.1) {
            var point=result.sample(d).position();
            if(point.x()>=45 && point.x()<=56)
                assertTrue(Math.floor(point.y()+0.5)-RoadStructurePlanner.DECK_THICKNESS-65 >= 7);
        }
        assertEquals(0.125,RoadOverpassPlanner.ease(0.25),1e-10);
        assertEquals(0.875,RoadOverpassPlanner.ease(0.75),1e-10);
    }
    @Test void leavesLevelIntersectionsAndAlreadyHighBridgesAlone() {
        var level=upper(64);
        assertSame(level,new RoadOverpassPlanner().adjust(level,11,List.of(lower())));
        var high=upper(80);
        assertSame(high,new RoadOverpassPlanner().adjust(high,11,List.of(lower())));
    }
    @Test void splittingProfileKeepsTheRaisedSpan() {
        var adjusted=new RoadOverpassPlanner().adjust(upper(69),11,List.of(lower()));
        var parts=ReferenceGeometryEditor.split(adjusted,List.of(0.2,0.8),List.of(
                adjusted.sample(adjusted.length()*0.2).position(),adjusted.sample(adjusted.length()*0.8).position()));
        var middle=parts.get(1);
        assertTrue(middle.sample(middle.length()/2).position().y() >= 74);
    }
    @Test void raisedProfileSurvivesNetworkRoundTrip() {
        var adjusted=new RoadOverpassPlanner().adjust(upper(69),11,List.of(lower()));
        var graph=new RoadGraph(Level.OVERWORLD);
        graph.applyNetworkMutation(graph.prepareRoadNetwork(adjusted,ID,preset(),TrafficHandedness.RHT));
        var packet=com.cookiecraftmods.roadbuilder.networking.RoadGraphSnapshotS2CPacket.current(graph.snapshot());
        var decoded=com.cookiecraftmods.roadbuilder.networking.RoadGraphSnapshotS2CPacket.decode(packet.encode());
        var restored=decoded.snapshot().segments().values().iterator().next().referenceGeometry();
        assertEquals(adjusted.length(),restored.length(),1e-6);
        assertTrue(restored.sample(restored.length()/2).position().y()>=74);
    }
    @Test void descentHasNoEndpointCliffAndAddedGradeStaysBounded() {
        var adjusted=new RoadOverpassPlanner().adjust(upper(69),11,List.of(lower()));
        var previous=adjusted.sample(0).position();
        for(double distance=0.1;distance<adjusted.length();distance+=0.1) {
            var point=adjusted.sample(distance).position();
            double horizontal=Math.hypot(point.x()-previous.x(),point.z()-previous.z());
            assertTrue(Math.abs(point.y()-previous.y())/horizontal<0.4,"overpass approach is too steep");
            previous=point;
        }
        assertTrue(Math.abs(adjusted.sample(adjusted.length()-0.1).position().y()-69)<0.02);
    }
    @Test void crossingNearEndpointCannotCompressItsDescentIntoACliff() {
        var shortExit=new StraightGeometry(new RoadPoint(0.5,69,0.5),new RoadPoint(70.5,69,0.5));
        assertThrows(IllegalArgumentException.class,()->new RoadOverpassPlanner().adjust(shortExit,11,List.of(lower())));
    }
    @Test void edgeOverlapRequiresClearanceEvenWhenReferenceLinesNeverCross() {
        var graph=new RoadGraph(Level.OVERWORLD);
        var lower=graph.prepareRoadNetwork(new StraightGeometry(new RoadPoint(40,64,8),new RoadPoint(60,64,8)),
                ID,preset(),TrafficHandedness.RHT).placedSegments().get(0);
        var adjusted=new RoadOverpassPlanner().adjust(upper(69),3,List.of(lower));
        assertTrue(adjusted.sample(adjusted.length()/2).position().y()>=74);
    }
    @Test void ordinaryRoadPreviewsWithFractionalLengthsAreNeverRejected() {
        var random=new java.util.Random(513);
        for(int i=0;i<200;i++) {
            var start=new RoadPoint(random.nextDouble()*10,64,random.nextDouble()*10);
            var end=new RoadPoint(30+random.nextDouble()*60,64,30+random.nextDouble()*60);
            var geometry=new StraightGeometry(start,end);
            assertDoesNotThrow(()->new RoadOverpassPlanner().adjust(geometry,3,List.of(lower())),
                    "ordinary flat road preview failed at "+end);
        }
    }
    @Test void fractionalExistingCurvesCannotBlockUnrelatedRoadPreviews() {
        var random=new java.util.Random(719);
        var proposed=new StraightGeometry(new RoadPoint(200,64,200),new RoadPoint(230,64,200));
        for(int i=0;i<100;i++) {
            var graph=new RoadGraph(Level.OVERWORLD);
            var curve=new CubicBezierGeometry(new RoadPoint(0,64,0),
                    new RoadPoint(10+random.nextDouble(),64,random.nextDouble()*8),
                    new RoadPoint(20+random.nextDouble(),64,10+random.nextDouble()*8),
                    new RoadPoint(30+random.nextDouble(),64,20+random.nextDouble()));
            var existing=graph.prepareRoadNetwork(curve,ID,preset(),TrafficHandedness.RHT).placedSegments();
            assertSame(proposed,assertDoesNotThrow(()->new RoadOverpassPlanner().adjust(proposed,3,existing)));
        }
    }
    @Test void bridgeStructuresDoNotIntrudeIntoLowerRoadHeadroom() {
        var adjusted=new RoadOverpassPlanner().adjust(upper(69),11,List.of(lower()));
        var graph=new RoadGraph(Level.OVERWORLD);
        var mutation=graph.prepareRoadNetwork(adjusted,ID,preset(),TrafficHandedness.RHT);
        PlacementBlockWorld world=new PlacementBlockWorld() {
            public ResourceKey<Level> dimension(){return Level.OVERWORLD;}
            public boolean hasBlockEntity(BlockPos pos){return false;}
            public boolean setBlockState(BlockPos pos,BlockState state){return true;}
            public BlockState blockState(BlockPos pos){
                if (pos.getY()==64 && pos.getX()>=45 && pos.getX()<=55) return Blocks.GRAY_CONCRETE.defaultBlockState();
                return (pos.getY()<=60?Blocks.STONE:Blocks.AIR).defaultBlockState();
            }
        };
        var result=new RoadMaterializationPlanner().plan(Level.OVERWORLD,mutation.placedSegments(),world,
                pos -> pos.getY()==64 && pos.getX()>=45 && pos.getX()<=55, ConstructionLimits.defaults());
        var plan=assertInstanceOf(BlockPlanResult.Success.class,result).plan();
        assertTrue(plan.blocks().stream().noneMatch(block -> !block.targetState().isAir()
                && block.position().getX()>=45 && block.position().getX()<=55
                && block.position().getY()>=65 && block.position().getY()<=71));
    }
}
