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
    @Test void slopingRoadAllowsSideBranchAtItsSharedEndpoint() {
        var graph = new RoadGraph(Level.OVERWORLD);
        var preset = new RoadPreset(RoadPreset.CURRENT_SCHEMA_VERSION, 1, 29,
                new ResourceLocation("minecraft", "gray_concrete"), List.of(
                new LaneComponent(LaneType.VEHICLE, 3, 0, LaneTravelDirection.FORWARD)));
        var node = new RoadPoint(9118.5, 85, 9220.5);
        var host = new CubicBezierGeometry(new RoadPoint(9019.5, 76, 9194.5),
                new RoadPoint(9053.345092264211, 79, 9199.551506308091),
                new RoadPoint(9086.540314843469, 82, 9208.269443551128), node);
        var roads = graph.prepareRoadNetwork(host, ID, preset, TrafficHandedness.RHT).placedSegments();
        for (double angle : new double[]{60, 90, 120}) {
            double radians = Math.toRadians(angle);
            var branch = new StraightGeometry(node,
                    node.add(new RoadVector(84 * Math.cos(radians), 3, 84 * Math.sin(radians))));
            assertDoesNotThrow(() -> new RoadOverpassPlanner().adjust(branch, 29, roads));
        }
    }

    @Test void slopingWideRoadCanBeContinuedAtEitherEnd() {
        var graph = new RoadGraph(Level.OVERWORLD);
        var preset = new RoadPreset(RoadPreset.CURRENT_SCHEMA_VERSION, 1, 29,
                new ResourceLocation("minecraft", "gray_concrete"), List.of(
                new LaneComponent(LaneType.VEHICLE, 3, -8, LaneTravelDirection.BACKWARD),
                new LaneComponent(LaneType.VEHICLE, 3, -4, LaneTravelDirection.BACKWARD),
                new LaneComponent(LaneType.VEHICLE, 3, 4, LaneTravelDirection.FORWARD),
                new LaneComponent(LaneType.VEHICLE, 3, 8, LaneTravelDirection.FORWARD)));
        var host = new StraightGeometry(new RoadPoint(8885.5, 67, 9174.5),
                new RoadPoint(9019.5, 76, 9194.5));
        var roads = graph.prepareRoadNetwork(host, ID, preset, TrafficHandedness.RHT).placedSegments();
        var delta = host.start().vectorTo(host.end());
        for (var continuation : List.of(
                new StraightGeometry(host.end(), host.end().add(delta)),
                new StraightGeometry(host.start().add(delta.scale(-1)), host.start()))) {
            var adjusted = assertDoesNotThrow(() -> new RoadOverpassPlanner().adjust(continuation, 29, roads));
            assertSame(continuation, adjusted);
        }
    }

    @Test void underpassLowersTheWholeFootprintWithGentleApproaches() {
        var original=upper(59);
        var adjusted=new RoadOverpassPlanner().adjust(original,11,List.of(lower()));
        assertEquals(original.start(),adjusted.start());
        assertEquals(original.end(),adjusted.end());
        assertTrue(adjusted.sample(adjusted.length()*0.2).position().y()<58.9,
                "the descent should begin well before the crossing");
        for(var span:assertInstanceOf(MultiCurveGeometry.class,adjusted).spans()) {
            for(int i=0;i<=32;i++) {
                var point=span.positionAt(i/32.0);
                var tangent=span.derivativeAt(i/32.0);
                assertTrue(Math.abs(tangent.y())/tangent.horizontalLength()<=0.25);
                if(point.x()>=39 && point.x()<=62)
                    assertTrue(Math.floor(point.y()+0.5)+1+RoadOverpassPlanner.MINIMUM_FREE_HEIGHT
                            <=64-RoadStructurePlanner.DECK_THICKNESS);
            }
        }
    }

    @Test void alreadyLowRoadKeepsItsOriginalProfile() {
        var original=upper(50);
        assertSame(original,new RoadOverpassPlanner().adjust(original,11,List.of(lower())));
    }

    @Test void underpassNearEndpointCannotCreateAnAbruptRamp() {
        var shortExit=new StraightGeometry(new RoadPoint(0.5,60,0.5),new RoadPoint(70.5,60,0.5));
        assertThrows(IllegalArgumentException.class,
                ()->new RoadOverpassPlanner().adjust(shortExit,11,List.of(lower())));
    }

    @Test void overpassStartsClimbingEarlyAndLimitsItsGrade() {
        var adjusted=new RoadOverpassPlanner().adjust(upper(69),11,List.of(lower()));
        assertTrue(adjusted.sample(adjusted.length()*0.2).position().y()>69.1);
        for(var span:assertInstanceOf(MultiCurveGeometry.class,adjusted).spans())
            for(int i=0;i<=32;i++) {
                var tangent=span.derivativeAt(i/32.0);
                assertTrue(Math.abs(tangent.y())/tangent.horizontalLength()<=0.25);
            }
    }

    @Test void curvedOverpassRemainsBuildableAfterItsElevationIsAdjusted() {
        var curve=new CubicBezierGeometry(new RoadPoint(0,69,0),new RoadPoint(30,69,0),
                new RoadPoint(60,69,20),new RoadPoint(100,69,20));
        var adjusted=assertDoesNotThrow(()->new RoadOverpassPlanner().adjust(curve,9,List.of(lower())));
        assertEquals(curve.start(),adjusted.start());
        assertEquals(curve.end(),adjusted.end());
        assertNull(RoadCurveValidation.validate(adjusted,RoadCurveValidation.minimumRadius(9,RoadClass.LOCAL)));
        for(var span:assertInstanceOf(MultiCurveGeometry.class,adjusted).spans()) {
            for(int i=0;i<=16;i++) {
                var point=span.positionAt(i/16.0);
                double from=0,to=1;
                for(int step=0;step<50;step++) {
                    double middle=(from+to)/2;
                    if(curve.positionAt(middle).x()<point.x()) from=middle; else to=middle;
                }
                assertEquals(curve.positionAt((from+to)/2).z(),point.z(),1e-8,
                        "raising the deck must preserve the original path in plan view");
            }
        }
    }

    @Test void slopingRoadsWithEnoughLocalClearanceDoNotRequireEndpointRamps() {
        var graph=new RoadGraph(Level.OVERWORLD);
        var lower=graph.prepareRoadNetwork(new StraightGeometry(new RoadPoint(0,64,0),new RoadPoint(100,84,0)),
                ID,preset(),TrafficHandedness.RHT).placedSegments();
        var upper=new StraightGeometry(new RoadPoint(0,78,2),new RoadPoint(100,98,2));
        assertSame(upper,assertDoesNotThrow(()->new RoadOverpassPlanner().adjust(upper,11,lower)));
    }

    @Test void minimumHeightFreeformCrossingSurvivesGraphPreparationWithoutJoiningLowerRoad() {
        var graph = new RoadGraph(Level.OVERWORLD);
        var lowerMutation = graph.prepareRoadNetwork(lower().referenceGeometry(), ID, preset(), TrafficHandedness.RHT);
        graph.applyNetworkMutation(lowerMutation);
        var lowerRoad = lowerMutation.placedSegments().get(0);
        var original = FreeformGeometry.create(new RoadPoint(0.5, 69, 0.5),
                new RoadPoint(100.5, 69, 0.5), new RoadVector(1, 0, 0), new RoadVector(1, 0, 0));
        var adjusted = new RoadOverpassPlanner().adjust(original, 11, List.of(lowerRoad));
        var mutation = graph.prepareRoadNetwork(adjusted, ID, preset(), TrafficHandedness.RHT);
        assertTrue(mutation.segmentSplits().isEmpty(), "the lower road must not become a junction");
        assertEquals(1, mutation.placedSegments().size());
        var built = mutation.placedSegments().get(0).referenceGeometry();
        for (double distance=0; distance<built.length(); distance+=0.25) {
            var p=built.sample(distance).position();
            if (p.x()>=45 && p.x()<=56) {
                assertTrue(Math.floor(p.y()+0.5)-RoadStructurePlanner.DECK_THICKNESS>=70,
                        "five empty blocks must separate the lower pavement and the upper deck");
                assertTrue(p.y()<72.5, "do not raise the crossing more than its clearance requires");
            }
        }
    }

    @Test void automaticLanePortsStayAttachedWhileCrossingRemainsSeparated() {
        var graph = new RoadGraph(Level.OVERWORLD);
        var hostPreset = new RoadPreset(RoadPreset.CURRENT_SCHEMA_VERSION, 1, 11,
                new ResourceLocation("minecraft", "gray_concrete"), List.of(
                new LaneComponent(LaneType.VEHICLE, 3, -2, LaneTravelDirection.FORWARD),
                new LaneComponent(LaneType.VEHICLE, 3, 2, LaneTravelDirection.FORWARD)));
        for (var geometry : List.of(
                new StraightGeometry(new RoadPoint(-50,69,0),new RoadPoint(0,69,0)),
                new StraightGeometry(new RoadPoint(100,69,0),new RoadPoint(150,69,0))))
            graph.applyNetworkMutation(graph.prepareRoadNetwork(geometry,ID,hostPreset,TrafficHandedness.RHT));
        graph.applyNetworkMutation(graph.prepareRoadNetwork(lower().referenceGeometry(),ID,preset(),TrafficHandedness.RHT));
        var roads=graph.snapshot().segments().values();
        var ports=AuxiliaryLanePorts.ports(roads);
        var start=ports.stream().filter(p -> p.incoming() && p.position().x()==0)
                .min(Comparator.comparingDouble(p -> p.position().z())).orElseThrow();
        var end=ports.stream().filter(p -> !p.incoming() && p.position().x()==100)
                .min(Comparator.comparingDouble(p -> p.position().z())).orElseThrow();
        var raw=FreeformGeometry.create(start.position(),end.position(),start.direction(),end.direction());
        var adjusted=new RoadOverpassPlanner().adjust(raw,3,roads);
        assertTrue(AuxiliaryLanePorts.matching(adjusted,true,3,ports).isPresent());
        assertTrue(AuxiliaryLanePorts.matching(adjusted,false,3,ports).isPresent());
        var branchPreset=new RoadPreset(RoadPreset.CURRENT_SCHEMA_VERSION,1,3,
                new ResourceLocation("minecraft","gray_concrete"),
                List.of(new LaneComponent(LaneType.VEHICLE,3,0,LaneTravelDirection.FORWARD)));
        var mutation=graph.prepareRoadNetwork(adjusted,ID,branchPreset,TrafficHandedness.RHT);
        assertTrue(mutation.segmentSplits().isEmpty());
        for(var segment:mutation.placedSegments()) for(int i=0;i<=100;i++) {
            var sample=segment.referenceGeometry().sample(segment.referenceGeometry().length()*(i/100.0));
            if(sample.position().x()>=45 && sample.position().x()<=56)
                assertTrue(sample.position().y()>=72);
        }
        assertEquals(69,adjusted.sample(5).position().y(),1e-8);
        assertEquals(69,adjusted.sample(adjusted.length()-5).position().y(),1e-8);
    }

    @Test void longApproachesSpreadTheClimbAcrossAvailableDistance() {
        var shortRoad=new RoadOverpassPlanner().adjust(upper(69),11,List.of(lower()));
        var longRoad=new RoadOverpassPlanner().adjust(new StraightGeometry(
                new RoadPoint(-100,69,0.5),new RoadPoint(200,69,0.5)),11,List.of(lower()));
        assertTrue(peakGrade(longRoad)<peakGrade(shortRoad)/2);
        assertTrue(longRoad.sample(20).position().y()>69);
    }

    private static double peakGrade(ReferenceGeometry geometry) {
        double maximum=0;
        for(int i=0;i<=1000;i++) {
            var tangent=geometry.sample(geometry.length()*(i/1000.0)).tangent();
            maximum=Math.max(maximum,Math.abs(tangent.y())/tangent.horizontalLength());
        }
        return maximum;
    }

    @Test void slopedCrossingNearReportedWorldCoordinatesHasFiveClearBlockLayers() {
        var graph = new RoadGraph(Level.OVERWORLD);
        var material = new ResourceLocation("minecraft", "smooth_stone");
        var lowerPreset = new RoadPreset(RoadPreset.CURRENT_SCHEMA_VERSION, 1, 17, material,
                List.of(new LaneComponent(LaneType.VEHICLE, 4, 0, LaneTravelDirection.FORWARD)));
        var lower = graph.prepareRoadNetwork(new StraightGeometry(new RoadPoint(-250,76,36),
                new RoadPoint(-90,73,36)), ID, lowerPreset, TrafficHandedness.RHT).placedSegments().get(0);
        var lowerPlan = assertInstanceOf(BlockPlanResult.Success.class,
                new StraightRoadSegmentMaterializer().plan(Level.OVERWORLD,lower,ConstructionLimits.defaults())).plan();
        Map<BlockPos,BlockState> lowerBlocks = new HashMap<>();
        lowerPlan.blocks().forEach(block -> lowerBlocks.put(block.position(),block.targetState()));
        var curve = FreeformGeometry.create(new RoadPoint(-180,80,-50), new RoadPoint(-165,85,130),
                new RoadVector(0.1,0,1),new RoadVector(-0.1,0,1));
        var adjusted = new RoadOverpassPlanner().adjust(curve,14,List.of(lower));
        var upperPreset = new RoadPreset(RoadPreset.CURRENT_SCHEMA_VERSION,1,14,material,
                List.of(new LaneComponent(LaneType.VEHICLE,4,0,LaneTravelDirection.FORWARD)));
        var segments = graph.prepareRoadNetwork(adjusted,ID,upperPreset,TrafficHandedness.RHT).placedSegments();
        PlacementBlockWorld world = new PlacementBlockWorld() {
            public ResourceKey<Level> dimension(){return Level.OVERWORLD;}
            public boolean hasBlockEntity(BlockPos position){return false;}
            public boolean setBlockState(BlockPos position,BlockState state){return true;}
            public BlockState blockState(BlockPos position){return lowerBlocks.getOrDefault(position,
                    (position.getY()<=70 ? Blocks.STONE : Blocks.AIR).defaultBlockState());}
        };
        var built = assertInstanceOf(BlockPlanResult.Success.class,new RoadMaterializationPlanner().plan(
                Level.OVERWORLD,segments,world,lowerBlocks::containsKey,ConstructionLimits.defaults())).plan();
        Set<BlockPos> solids = new HashSet<>();
        built.blocks().stream().filter(block -> !block.targetState().isAir()).forEach(block -> solids.add(block.position()));
        for(BlockPos surface:lowerBlocks.keySet()) for(int height=1;height<=5;height++)
            assertFalse(solids.contains(surface.above(height)), "obstructed clearance at " + surface.above(height));
        assertTrue(solids.stream().anyMatch(position -> position.getX()>=-185 && position.getX()<=-155
                && position.getZ()>=30 && position.getZ()<=40 && position.getY()>80));
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
                assertTrue(Math.floor(point.y()+0.5)-RoadStructurePlanner.DECK_THICKNESS-65 >= RoadOverpassPlanner.MINIMUM_FREE_HEIGHT);
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
        assertTrue(middle.sample(middle.length()/2).position().y() >= 72);
    }
    @Test void raisedProfileSurvivesNetworkRoundTrip() {
        var adjusted=new RoadOverpassPlanner().adjust(upper(69),11,List.of(lower()));
        var graph=new RoadGraph(Level.OVERWORLD);
        graph.applyNetworkMutation(graph.prepareRoadNetwork(adjusted,ID,preset(),TrafficHandedness.RHT));
        var packet=com.cookiecraftmods.roadbuilder.networking.RoadGraphSnapshotS2CPacket.current(graph.snapshot());
        var decoded=com.cookiecraftmods.roadbuilder.networking.RoadGraphSnapshotS2CPacket.decode(packet.encode());
        var restored=decoded.snapshot().segments().values().iterator().next().referenceGeometry();
        assertEquals(adjusted.length(),restored.length(),1e-6);
        assertTrue(restored.sample(restored.length()/2).position().y()>=72);
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
        assertTrue(adjusted.sample(adjusted.length()/2).position().y()>=72);
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
                && block.position().getY()>=65 && block.position().getY()<=69));
    }
    @Test void lowUnadjustedBridgeKeepsItsEntireDeck() {
        var adjusted=upper(67);
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
        assertTrue(plan.blocks().stream().anyMatch(block -> block.position().equals(new BlockPos(50, 65, 0))
                && block.targetState().is(Blocks.SMOOTH_STONE)));
        assertTrue(plan.blocks().stream().noneMatch(block -> !block.targetState().isAir()
                && block.position().getX()>=45 && block.position().getX()<=55
                && block.position().getY()==64));
    }
}
