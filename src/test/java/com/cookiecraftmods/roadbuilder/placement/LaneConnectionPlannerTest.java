package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.geometry.*;
import com.cookiecraftmods.roadbuilder.network.*;
import com.cookiecraftmods.roadbuilder.preset.*;
import com.cookiecraftmods.roadbuilder.test.MinecraftBootstrap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LaneConnectionPlannerTest {
    @Test void automaticConnectionHonorsTheRoadClassRadiusAfterRaisingCrossings() {
        var curve=SharpLaneMergeRegressionFixture.connection();
        var roads=SharpLaneMergeRegressionFixture.roads();
        double radius=RoadCurveValidation.minimumRadius(14,RoadClass.HIGHWAY);
        assertNull(RoadCurveValidation.validate(curve,radius));
        assertEquals(BlockPlanFailureCode.TOO_SHARP_TURN, RoadCurveValidation.validate(
                new LaneConnectionPlanner().adjust(curve,14,roads),radius));
        var result=assertDoesNotThrow(()->new RoadOverpassPlanner().adjust(curve,14,roads,radius));
        assertNull(RoadCurveValidation.validate(result,radius));
        assertEquals(curve.start(),result.start());
        assertEquals(curve.end(),result.end());
        assertTrue(assertInstanceOf(MultiCurveGeometry.class,result).controlPoints().size()<=64);
        for(int i=0;i<=1024;i++) {
            var tangent=result.sample(result.length()*(i/1024.0)).tangent();
            assertTrue(Math.abs(tangent.y())/tangent.horizontalLength()<=ConstructionLimits.DEFAULT_MAXIMUM_GRADE);
        }
    }

    @Test void bothLanesCanJoinTheSlopingContinuation() {
        var roads=LaneMergeRegressionFixture.roads();
        var ports=AuxiliaryLanePorts.ports(roads);
        for(var start:ports.stream().filter(p->p.segmentId().equals(roads.get(0).id()) && p.incoming()).toList())
            for(var end:ports.stream().filter(p->p.segmentId().equals(roads.get(3).id()) && !p.incoming()).toList()) {
                var original=FreeformGeometry.create(start.position(),end.position(),start.direction(),end.direction());
                assertDoesNotThrow(()->new RoadOverpassPlanner().adjust(original,9,roads),
                        "connection from "+start.position()+" to "+end.position());
            }
    }

    @Test void mergesAtASlopingContinuationBuiltAsSeparateRoads() {
        var roads=LaneMergeRegressionFixture.roads();
        var ports=AuxiliaryLanePorts.ports(roads);
        var start=ports.stream().filter(p->p.segmentId().equals(roads.get(0).id()) && p.incoming())
                .max(Comparator.comparingDouble(p->p.position().z())).orElseThrow();
        var end=ports.stream().filter(p->p.segmentId().equals(roads.get(3).id()) && !p.incoming())
                .min(Comparator.comparingDouble(p->p.position().x())).orElseThrow();
        var original=FreeformGeometry.create(start.position(),end.position(),start.direction(),end.direction());
        var result=assertDoesNotThrow(()->new RoadOverpassPlanner().adjust(original,9,roads));
        assertEquals(start.position(),result.start());
        assertEquals(end.position(),result.end());
        assertNull(RoadCurveValidation.validate(result,5));
        assertTrue(AuxiliaryLanePorts.matching(result,true,9,ports).isPresent());
        assertTrue(AuxiliaryLanePorts.matching(result,false,9,ports).isPresent());
        assertNotEquals(original,result);
        assertNull(RoadCurveValidation.validate(result,60),"the approach must keep a broad turning radius");
    }

    @Test void extendedMergeAreaDoesNotExemptAnUnrelatedRoadAtTheEndpoint() {
        var roads=new ArrayList<>(LaneMergeRegressionFixture.roads());
        var ports=AuxiliaryLanePorts.ports(roads);
        var start=ports.stream().filter(p->p.segmentId().equals(roads.get(0).id()) && p.incoming())
                .max(Comparator.comparingDouble(p->p.position().z())).orElseThrow();
        var end=ports.stream().filter(p->p.segmentId().equals(roads.get(3).id()) && !p.incoming())
                .min(Comparator.comparingDouble(p->p.position().x())).orElseThrow();
        var center=end.position().subtract(end.direction().scale(2));
        roads.add(road(center.add(new RoadVector(-100,0,0)),center.add(new RoadVector(100,0,0)),9,false));
        var original=FreeformGeometry.create(start.position(),end.position(),start.direction(),end.direction());
        assertThrows(IllegalArgumentException.class,()->new LaneConnectionPlanner().adjust(original,9,roads));
    }

    @BeforeAll static void bootstrap(){MinecraftBootstrap.bootstrap();}
    private static RoadSegment road(RoadPoint a,RoadPoint b,int width,boolean multi) {
        var preset=new RoadPreset(RoadPreset.CURRENT_SCHEMA_VERSION,1,width,
                new ResourceLocation("minecraft","gray_concrete"), multi?List.of(
                new LaneComponent(LaneType.VEHICLE,3,-2,LaneTravelDirection.FORWARD),
                new LaneComponent(LaneType.VEHICLE,3,2,LaneTravelDirection.FORWARD)):List.of(
                new LaneComponent(LaneType.VEHICLE,3,0,LaneTravelDirection.FORWARD)));
        return new RoadGraph(Level.OVERWORLD).prepareRoadNetwork(new StraightGeometry(a,b),
                new ResourceLocation("roadbuilder","connection_test"),preset,TrafficHandedness.RHT).placedSegments().get(0);
    }
    @Test void adjustsBothHandlesToAvoidAnObstacleBetweenLanePorts() {
        assertObstacleAvoided(true);
    }

    @Test void adjustsHandlesWhenOnlyTheDestinationHasLanePorts() {
        assertObstacleAvoided(false);
    }

    private void assertObstacleAvoided(boolean sourceHasPorts) {
        var source=road(new RoadPoint(-40,64,0),new RoadPoint(0,64,0),9,sourceHasPorts);
        var destination=road(new RoadPoint(60,64,24),new RoadPoint(100,64,24),9,true);
        var ports=AuxiliaryLanePorts.ports(List.of(source,destination));
        var start=sourceHasPorts?ports.stream().filter(port->port.segmentId().equals(source.id())&&port.incoming())
                .max(Comparator.comparingDouble(port->port.position().z())).orElseThrow():
                new AuxiliaryLanePorts.Port(source.endNodeId(),source.id(),source.lanes().get(0).id(),
                        source.referenceGeometry().end(),new RoadVector(1,0,0),true,9);
        var end=ports.stream().filter(port->port.segmentId().equals(destination.id())&&!port.incoming())
                .max(Comparator.comparingDouble(port->port.position().z())).orElseThrow();
        var original=(CubicBezierGeometry)LaneConnectionGeometry.align(
                FreeformGeometry.createBalanced(start.position(),end.position(),start.direction()),start.direction(),end.direction());
        var center=original.positionAt(0.5);
        var obstacle=road(new RoadPoint(center.x(),64,center.z()-0.1),new RoadPoint(center.x(),64,center.z()+0.1),3,false);
        var adjusted=(CubicBezierGeometry)new LaneConnectionPlanner().adjust(original,3,List.of(source,destination,obstacle));
        assertNotEquals(original,adjusted);
        assertEquals(original.start(),adjusted.start());
        assertEquals(original.end(),adjusted.end());
        assertEquals(1,adjusted.sample(0).tangent().dot(start.direction()),1e-6);
        assertEquals(1,adjusted.sample(adjusted.length()).tangent().dot(end.direction()),1e-6);
        for(double distance=0;distance<adjusted.length();distance+=0.1) {
            var point=adjusted.sample(distance).position();
            assertTrue(Math.hypot(point.x()-center.x(),point.z()-center.z())>2.5);
        }
    }
    @Test void risingBranchCanLeaveItsSourceAndJoinTheElevatedLane() {
        var source=road(new RoadPoint(-40,64,0),new RoadPoint(0,64,0),9,true);
        var destination=road(new RoadPoint(80,74,24),new RoadPoint(120,74,24),9,true);
        var roads=List.of(source,destination);
        var ports=AuxiliaryLanePorts.ports(roads);
        var start=ports.stream().filter(port->port.segmentId().equals(source.id())&&port.incoming())
                .max(Comparator.comparingDouble(port->port.position().z())).orElseThrow();
        var end=ports.stream().filter(port->port.segmentId().equals(destination.id())&&!port.incoming())
                .max(Comparator.comparingDouble(port->port.position().z())).orElseThrow();
        var curve=LaneConnectionGeometry.align(FreeformGeometry.createBalanced(start.position(),end.position(),start.direction()),
                start.direction(),end.direction());
        var result=new RoadOverpassPlanner().adjust(curve,3,roads);
        assertEquals(start.position(),result.start());
        assertEquals(end.position(),result.end());
        assertTrue(AuxiliaryLanePorts.matching(result,true,3,ports).isPresent());
        assertTrue(AuxiliaryLanePorts.matching(result,false,3,ports).isPresent());
        assertTrue(result.sample(result.length()/2).position().y()>64);
    }
    @Test void impossibleConnectionsAreRejectedRatherThanBuiltThroughTheHost() {
        var source=road(new RoadPoint(-40,64,0),new RoadPoint(0,64,0),9,true);
        var destination=road(new RoadPoint(12,80,2),new RoadPoint(52,80,2),9,true);
        var ports=AuxiliaryLanePorts.ports(List.of(source,destination));
        var start=ports.stream().filter(port->port.segmentId().equals(source.id())&&port.incoming()).findFirst().orElseThrow();
        var end=ports.stream().filter(port->port.segmentId().equals(destination.id())&&!port.incoming()).findFirst().orElseThrow();
        var curve=LaneConnectionGeometry.align(FreeformGeometry.createBalanced(start.position(),end.position(),start.direction()),
                start.direction(),end.direction());
        assertThrows(IllegalArgumentException.class,()->new RoadOverpassPlanner().adjust(curve,3,List.of(source,destination)));
    }
}
