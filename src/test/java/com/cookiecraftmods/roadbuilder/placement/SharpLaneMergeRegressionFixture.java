package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.geometry.*;
import com.cookiecraftmods.roadbuilder.network.*;
import com.cookiecraftmods.roadbuilder.preset.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Three connected host segments reduced from a rejected highway connection. */
final class SharpLaneMergeRegressionFixture {
    static List<RoadSegment> roads() {
        var nodes=new RoadNodeId[]{new RoadNodeId(UUID.randomUUID()),new RoadNodeId(UUID.randomUUID()),
                new RoadNodeId(UUID.randomUUID()),new RoadNodeId(UUID.randomUUID())};
        return List.of(
                road(nodes[0],nodes[1],new CubicBezierGeometry(
                        new RoadPoint(-1.2221210584762368,73.00352371417895,38.11018667927105),
                        new RoadPoint(-30.64367962002665,73.00234914278597,17.130822776573144),
                        new RoadPoint(-68.69179173425081,73.00117457139298,10.64881058406331),
                        new RoadPoint(-104.5,73,15.5)),17,-2.5,2.5),
                road(nodes[2],nodes[0],new QuadraticBezierGeometry(
                        new RoadPoint(52.61096309513426,73.00615360874714,98.26754279567933),
                        new RoadPoint(38.3922980752974,73.00497703966857,68.54530559511997),
                        new RoadPoint(0.13749487910090896,73.00352012627388,36.009260967233296)),22,-5,0,5),
                road(nodes[1],nodes[3],new CubicBezierGeometry(
                        new RoadPoint(-104.5,73,15.5),
                        new RoadPoint(-143.44695922517928,74.33333333333333,25.082476454284553),
                        new RoadPoint(-184.35677595337754,75.66666666666667,10.528022425983252),
                        new RoadPoint(-208.5,77,-21.5)),17,-2.5,2.5));
    }

    static CubicBezierGeometry connection() {
        return new CubicBezierGeometry(new RoadPoint(3.3768617584047966,73.00352012627388,32.20052072651472),
                new RoadPoint(-47.828951491089825,76.66901341751593,-11.350471198557699),
                new RoadPoint(-126.3079270740246,80.33450670875796,-3.8055346079207553),
                new RoadPoint(-167.90448515389312,84,53.06163398766139));
    }

    private static RoadSegment road(RoadNodeId start,RoadNodeId end,ReferenceGeometry geometry,
                                    int width,double... offsets) {
        var lanes=Arrays.stream(offsets).mapToObj(offset->new LaneComponent(
                LaneType.VEHICLE,4,offset,LaneTravelDirection.FORWARD)).toList();
        return RoadSegment.path(new RoadSegmentId(UUID.randomUUID()),new RoadId(UUID.randomUUID()),
                start,end,geometry,new ResourceLocation("roadbuilder","sharp_merge_regression"),1,
                new RoadPresetSnapshot(width,new ResourceLocation("minecraft","gray_concrete"),lanes),
                TrafficHandedness.RHT,lanes.stream().map(lane->RoadLaneId.create()).toList());
    }
}
