package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import com.cookiecraftmods.roadbuilder.geometry.*;
import com.cookiecraftmods.roadbuilder.network.*;
import com.cookiecraftmods.roadbuilder.preset.*;
import net.minecraft.resources.ResourceLocation;
import com.cookiecraftmods.roadbuilder.test.MinecraftBootstrap;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LaneConnectionSamplingTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.bootstrap(); }
    @Test void hostSamplingKeepsTheLastChordInsideTheGeometry() {
        double length=90.93841715006862;
        var host=new MultiCurveGeometry(List.of(new RoadPoint(0,73,0),
                new RoadPoint(length/2,73,0),
                new RoadPoint(length,73,0)));
        int count=(int)Math.ceil(host.length()/2);
        assertTrue(host.length()*count/count>host.length());
        var road=RoadSegment.path(new RoadSegmentId(UUID.randomUUID()),new RoadId(UUID.randomUUID()),
                new RoadNodeId(UUID.randomUUID()),new RoadNodeId(UUID.randomUUID()),host,
                MinecraftIds.id("roadbuilder","sampling_regression"),1,
                new RoadPresetSnapshot(17,MinecraftIds.id("minecraft","gray_concrete"),List.of(
                        new LaneComponent(LaneType.VEHICLE,4,-2.5,LaneTravelDirection.FORWARD),
                        new LaneComponent(LaneType.VEHICLE,4,2.5,LaneTravelDirection.FORWARD))),
                TrafficHandedness.RHT,List.of(RoadLaneId.create(),RoadLaneId.create()));
        var port=AuxiliaryLanePorts.ports(List.of(road)).get(0);
        assertDoesNotThrow(()->new LaneConnectionElevationPlanner(14,List.of(road),
                Optional.of(port),Optional.empty()));
    }
}
