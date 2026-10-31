package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.geometry.*;
import com.cookiecraftmods.roadbuilder.network.*;
import java.util.*;

/** Fits travel-aligned lane connections against full road footprints. */
public final class LaneConnectionPlanner {
    private record Chord(RoadSegmentId segment, RoadPoint a, RoadPoint b, double halfWidth) {}

    public ReferenceGeometry adjust(ReferenceGeometry geometry, int width, Collection<RoadSegment> roads) {
        if (!(geometry instanceof CubicBezierGeometry original)) return geometry;
        var ports=AuxiliaryLanePorts.ports(roads);
        var start=AuxiliaryLanePorts.matching(geometry,true,width,ports);
        var end=AuxiliaryLanePorts.matching(geometry,false,width,ports);
        if (start.isEmpty() || end.isEmpty()) return geometry;
        List<Chord> chords=new ArrayList<>();
        for (var road:roads) {
            var reference=road.referenceGeometry();
            int count=Math.max(1,(int)Math.ceil(reference.length()/2));
            for(int i=0;i<count;i++) chords.add(new Chord(road.id(),
                    reference.sample(reference.length() * (i / (double) count)).position(),
                    reference.sample(reference.length() * ((i+1) / (double) count)).position(),
                    road.presetSnapshot().surfaceBlockWidth()/2.0));
        }
        CubicBezierGeometry best=null;
        double bestScore=Double.POSITIVE_INFINITY;
        double[] factors={1,0.7,0.45,0.25,1.3,1.6};
        for(double first:factors) for(double second:factors) {
            var curve=new CubicBezierGeometry(original.start(),
                    original.start().add(original.start().vectorTo(original.startControl()).scale(first)),
                    original.end().add(original.end().vectorTo(original.endControl()).scale(second)),original.end());
            if(RoadCurveValidation.validate(curve,width/2.0+0.5)!=null) continue;
            double collisions=collisionCost(curve,width,chords,start.get().segmentId(),end.get().segmentId());
            double score=collisions*10000+Math.abs(first-1)+Math.abs(second-1);
            if(score<bestScore){bestScore=score;best=curve;}
        }
        if(best==null || bestScore>=10000)
            throw new IllegalArgumentException("lane connection overlaps another road; choose endpoints with more room");
        return best;
    }

    private static double collisionCost(CubicBezierGeometry curve,int width,List<Chord> chords,
                                        RoadSegmentId source,RoadSegmentId destination) {
        double collisions=0;
        int count=Math.max(8,(int)Math.ceil(curve.length()));
        for(int i=1;i<count;i++) {
            double distance=curve.length() * (i / (double) count);
            var point=curve.sample(distance).position();
            boolean blocked=false;
            boolean needsClearance=false;
            for(var chord:chords) {
                double radius=width/2.0+chord.halfWidth;
                if(point.x()<Math.min(chord.a.x(),chord.b.x())-radius || point.x()>Math.max(chord.a.x(),chord.b.x())+radius
                        || point.z()<Math.min(chord.a.z(),chord.b.z())-radius || point.z()>Math.max(chord.a.z(),chord.b.z())+radius) continue;
                double dx=chord.b.x()-chord.a.x(), dz=chord.b.z()-chord.a.z();
                double denominator=dx*dx+dz*dz;
                if(denominator<1e-10) continue;
                double t=Math.max(0,Math.min(1,((point.x()-chord.a.x())*dx+(point.z()-chord.a.z())*dz)/denominator));
                double x=chord.a.x()+dx*t,z=chord.a.z()+dz*t;
                double gap=point.y()-(chord.a.y()+(chord.b.y()-chord.a.y())*t);
                if(Math.hypot(point.x()-x,point.z()-z)>=width/2.0+chord.halfWidth-0.25) continue;
                // The shared footprint at a lane port is a junction, not a second
                // road surface. It must stay at the host elevation until clear.
                boolean attachment=(chord.segment.equals(source) && distance<=width+chord.halfWidth)
                        || (chord.segment.equals(destination) && curve.length()-distance<=width+chord.halfWidth);
                if(attachment && Math.abs(gap)<=0.5) continue;
                // Interior elevated crossings are raised by the overpass planner.
                if(Math.abs(gap)>1 && !attachment) {
                    if(Math.abs(gap)<1+RoadOverpassPlanner.MINIMUM_FREE_HEIGHT+RoadStructurePlanner.DECK_THICKNESS)
                        needsClearance=true;
                    continue;
                }
                blocked=true;
                break;
            }
            if(blocked) collisions++;
            else if(needsClearance) collisions+=0.25/count;
        }
        return collisions;
    }
}
