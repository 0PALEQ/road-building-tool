package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.geometry.*;
import com.cookiecraftmods.roadbuilder.network.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import java.util.*;

/** Shared height field for pavement, sidewalks and markings at a sloping junction. */
final class JunctionSurfaceElevation {
    private record Chord(RoadPoint a,RoadPoint b) {}
    private final List<List<Chord>> arms=new ArrayList<>();
    private final double fallback;

    JunctionSurfaceElevation(RoadNode node,Collection<RoadSegment> incident) {
        fallback=node.position().y();
        for(var road:incident) {
            var geometry=road.referenceGeometry();
            boolean atStart=road.startNodeId().equals(node.id());
            double reach=Math.min(geometry.length(),48);
            int count=Math.max(1,(int)Math.ceil(reach/0.5));
            List<Chord> chords=new ArrayList<>();
            for(int i=0;i<count;i++) {
                double a=reach*i/count,b=reach*(i+1)/count;
                chords.add(new Chord(geometry.sample(atStart?a:geometry.length()-a).position(),
                        geometry.sample(atStart?b:geometry.length()-b).position()));
            }
            arms.add(chords);
        }
    }

    double height(double x,double z) {
        double weighted=0,total=0;
        for(var arm:arms) {
            double nearest=Double.POSITIVE_INFINITY,y=fallback;
            for(var c:arm) {
                double dx=c.b.x()-c.a.x(),dz=c.b.z()-c.a.z(),divisor=dx*dx+dz*dz;
                if(divisor<1e-10) continue;
                double t=Math.max(0,Math.min(1,((x-c.a.x())*dx+(z-c.a.z())*dz)/divisor));
                double distance=Math.pow(x-c.a.x()-t*dx,2)+Math.pow(z-c.a.z()-t*dz,2);
                if(distance<nearest) { nearest=distance; y=c.a.y()+(c.b.y()-c.a.y())*t; }
            }
            double weight=1/Math.pow(0.25+nearest,2);
            weighted+=weight*y; total+=weight;
        }
        return total==0?fallback:weighted/total;
    }

    /** Remove the old approach surface where the junction replaces its elevation. */
    static List<PlannedBlock> clearSupersededSurfaces(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
            List<PlannedBlock> surface,RoadNode node,
            Collection<RoadSegment> incident,int maximumBlocks,int maximumWork) {
        Map<BlockPos,PlannedBlock> blocks=new LinkedHashMap<>();
        Map<Long,Integer> columns=new HashMap<>();
        for(var b:surface) {
            blocks.put(b.position(),b);
            columns.merge(column(b.position()),b.position().getY(),Math::max);
        }
        for(var road:incident) {
            var resolved=RoadSurfaceResolver.resolve(road.presetSnapshot().surfaceColumns());
            if(!(resolved instanceof RoadSurfaceResolver.Result.Success success)) continue;
            var geometry=road.referenceGeometry();
            double fraction=Math.min(1,48/geometry.length());
            boolean atStart=road.startNodeId().equals(node.id());
            if(fraction<1) {
                double cut=atStart?fraction:1-fraction;
                var parts=ReferenceGeometryEditor.split(geometry,List.of(cut),List.of(geometry.sample(geometry.length()*cut).position()));
                geometry=parts.get(atStart?0:1);
            }
            var result=new RoadPathPlanner().plan(new RoadPathPlanRequest(
                    dimension,geometry,success.surface()),maximumBlocks,maximumWork,
                    ConstructionLimits.UNRESTRICTED_MAXIMUM_GRADE);
            if(!(result instanceof BlockPlanResult.Success original)) continue;
            for(var old:original.plan().blocks()) {
                Integer newY=columns.get(column(old.position()));
                if(newY==null || newY==old.position().getY()) continue;
                // Clear only the displaced road/deck and its rail, not an entire terrain column.
                for(int dy=-RoadStructurePlanner.DECK_THICKNESS;dy<=2;dy++) {
                    var pos=old.position().above(dy);
                    if(pos.getY()<=newY || blocks.containsKey(pos)) continue;
                    if(blocks.size()>=maximumBlocks) throw new IllegalArgumentException("junction block limit exceeded");
                    blocks.put(pos,new PlannedBlock(pos,Blocks.AIR.defaultBlockState()));
                }
            }
        }
        return List.copyOf(blocks.values());
    }

    private static long column(BlockPos p) { return ((long)p.getX()<<32)^(p.getZ()&0xffffffffL); }
}
