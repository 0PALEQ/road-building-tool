package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.geometry.*;
import com.cookiecraftmods.roadbuilder.network.*;
import java.util.*;

/** Fits travel-aligned lane connections against full road footprints. */
public final class LaneConnectionPlanner {
    private record Chord(RoadSegmentId segment, RoadPoint a, RoadPoint b, double halfWidth) {}

    public ReferenceGeometry adjust(ReferenceGeometry geometry, int width, Collection<RoadSegment> roads) {
        CubicBezierGeometry original;
        if(geometry instanceof CubicBezierGeometry cubic) original=cubic;
        else if(geometry instanceof StraightGeometry straight) original=new CubicBezierGeometry(straight.start(),
                straight.start().add(straight.start().vectorTo(straight.end()).scale(1.0/3)),
                straight.start().add(straight.start().vectorTo(straight.end()).scale(2.0/3)),straight.end());
        else if(geometry instanceof QuadraticBezierGeometry quadratic) original=new CubicBezierGeometry(quadratic.start(),
                quadratic.start().add(quadratic.start().vectorTo(quadratic.control()).scale(2.0/3)),
                quadratic.end().add(quadratic.end().vectorTo(quadratic.control()).scale(2.0/3)),quadratic.end());
        else return geometry;
        var ports=AuxiliaryLanePorts.ports(roads);
        var start=AuxiliaryLanePorts.matching(geometry,true,width,ports);
        var end=AuxiliaryLanePorts.matching(geometry,false,width,ports);
        if(start.isEmpty() && end.isEmpty()) return geometry;
        var elevation=new LaneConnectionElevationPlanner(width,roads,start,end);
        if(start.isEmpty() || end.isEmpty()) return elevation.adjust(original);
        // Every candidate stays inside the hull of these extreme handles.
        var extremeFirst=original.start().add(original.start().vectorTo(original.startControl()).scale(2.5));
        var extremeSecond=original.end().add(original.end().vectorTo(original.endControl()).scale(2.5));
        double minX=Math.min(Math.min(original.start().x(),original.end().x()),Math.min(extremeFirst.x(),extremeSecond.x()));
        double maxX=Math.max(Math.max(original.start().x(),original.end().x()),Math.max(extremeFirst.x(),extremeSecond.x()));
        double minZ=Math.min(Math.min(original.start().z(),original.end().z()),Math.min(extremeFirst.z(),extremeSecond.z()));
        double maxZ=Math.max(Math.max(original.start().z(),original.end().z()),Math.max(extremeFirst.z(),extremeSecond.z()));
        List<Chord> chords=new ArrayList<>();
        for (var road:roads) {
            var reference=road.referenceGeometry();
            int count=Math.max(1,(int)Math.ceil(reference.length()/2));
            double halfWidth=road.presetSnapshot().surfaceBlockWidth()/2.0;
            double clearance=width/2.0+halfWidth;
            for(int i=0;i<count;i++) {
                var a=reference.sample(reference.length() * (i / (double) count)).position();
                var b=reference.sample(reference.length() * ((i+1) / (double) count)).position();
                if(Math.max(a.x(),b.x())+clearance<minX || Math.min(a.x(),b.x())-clearance>maxX
                        || Math.max(a.z(),b.z())+clearance<minZ || Math.min(a.z(),b.z())-clearance>maxZ) continue;
                chords.add(new Chord(road.id(),a,b,halfWidth));
            }
        }
        ReferenceGeometry best=null;
        double bestScore=Double.POSITIVE_INFINITY;
        double[] factors={1,0.7,0.45,0.25,1.3,1.6,2,2.5};
        var sourceHost=connectedHost(start.get(), width, roads);
        var destinationHost=connectedHost(end.get(), width, roads);
        try {
            var profiled=elevation.adjust(original);
            if(RoadCurveValidation.validate(profiled,width/2.0+0.5)==null
                    && collisionCost(profiled,width,chords,sourceHost,destinationHost)<1) return profiled;
        } catch(IllegalArgumentException insufficientRoom) { /* Try longer plan-view variants below. */ }
        for(double first:factors) for(double second:factors) {
            var curve=new CubicBezierGeometry(original.start(),
                    original.start().add(original.start().vectorTo(original.startControl()).scale(first)),
                    original.end().add(original.end().vectorTo(original.endControl()).scale(second)),original.end());
            if(RoadCurveValidation.validate(curve,width/2.0+0.5)!=null) continue;
            ReferenceGeometry profiled;
            try { profiled=elevation.adjust(curve); }
            catch(IllegalArgumentException insufficientRoom) { continue; }
            double collisions=collisionCost(profiled,width,chords,sourceHost,destinationHost);
            // Prefer a broad turn over merely keeping the original handle lengths.
            // A short exit handle can clear the host but concentrate the entire
            // direction change into the last few blocks.
            double score=collisions*10000+peakCurvature(curve)*original.length()
                    +0.05*(Math.abs(first-1)+Math.abs(second-1));
            if(score<bestScore){bestScore=score;best=profiled;}
        }
        if(best==null || bestScore>=10000)
            throw new IllegalArgumentException("lane connection overlaps another road; choose endpoints with more room");
        return best;
    }

    private static double peakCurvature(CubicBezierGeometry curve) {
        double peak=0;
        for(int i=0;i<=64;i++) peak=Math.max(peak,curve.horizontalCurvatureAt(i/64.0));
        return peak;
    }

    private static double collisionCost(ReferenceGeometry curve,int width,List<Chord> chords,
                                        Set<RoadSegmentId> source,Set<RoadSegmentId> destination) {
        double collisions=0;
        var sourceChords=chords.stream().filter(chord->source.contains(chord.segment)).toList();
        var destinationChords=chords.stream().filter(chord->destination.contains(chord.segment)).toList();
        var entryTangent=curve.sample(0).tangent();
        var exitTangent=curve.sample(curve.length()).tangent();
        int count=Math.max(8,(int)Math.ceil(curve.length()));
        for(int i=1;i<count;i++) {
            double distance=curve.length() * (i / (double) count);
            var sample=curve.sample(distance);
            var point=sample.position();
            boolean blocked=false;
            boolean needsClearance=false;
            double sourceGap=nearestHostGap(point,sourceChords,source);
            double destinationGap=nearestHostGap(point,destinationChords,destination);
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
                // A shallow merge occupies the combined widths along the host.
                double contactRange=width+2*chord.halfWidth;
                // A broad, shallow merge needs a longer shared surface than a
                // perpendicular junction. Extend it only while travel is aligned.
                boolean sourceAttachment=source.contains(chord.segment) && inMergeApproach(
                        distance,contactRange,sample.tangent(),entryTangent);
                boolean destinationAttachment=destination.contains(chord.segment)
                        && inMergeApproach(curve.length()-distance,contactRange,
                                sample.tangent(),exitTangent);
                // Compare with the closest host surface, not every clamped chord
                // endpoint: a sloping host has several heights inside its footprint.
                if(sourceAttachment && Math.abs(sourceGap)<=0.5
                        || destinationAttachment && Math.abs(destinationGap)<=0.5) continue;
                // Interior elevated crossings are raised by the overpass planner.
                if(Math.abs(gap)>1) {
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

    private static boolean inMergeApproach(double distance,double contactRange,
                                            RoadVector tangent,RoadVector endpointTangent) {
        return distance<=contactRange || distance<=3*contactRange
                && tangent.horizontalLeftUnit().dot(endpointTangent.horizontalLeftUnit())>=Math.cos(Math.PI/4);
    }

    private static double nearestHostGap(RoadPoint point,List<Chord> chords,Set<RoadSegmentId> host) {
        double nearest=Double.POSITIVE_INFINITY;
        double gap=Double.POSITIVE_INFINITY;
        for(var chord:chords) {
            if(!host.contains(chord.segment)) continue;
            double dx=chord.b.x()-chord.a.x(), dz=chord.b.z()-chord.a.z();
            double denominator=dx*dx+dz*dz;
            if(denominator<1e-10) continue;
            double t=Math.max(0,Math.min(1,((point.x()-chord.a.x())*dx+(point.z()-chord.a.z())*dz)/denominator));
            double separation=Math.hypot(point.x()-chord.a.x()-t*dx,point.z()-chord.a.z()-t*dz);
            if(separation<nearest) {
                nearest=separation;
                gap=point.y()-(chord.a.y()+(chord.b.y()-chord.a.y())*t);
            }
        }
        return gap;
    }

    static Set<RoadSegmentId> connectedHost(AuxiliaryLanePorts.Port port, int width,
                                                   Collection<RoadSegment> roads) {
        // A lane port can lie at a continuation created by a separate placement.
        // Follow shared nodes only inside the local contact area, regardless of road ID.
        Set<RoadSegmentId> result=new HashSet<>();
        Set<RoadNodeId> nodes=new HashSet<>();
        nodes.add(port.nodeId());
        double contactRange=3*(width+port.roadWidth());
        boolean changed;
        do {
            changed=false;
            for(var road:roads) {
                if(result.contains(road.id())) continue;
                if(nodes.contains(road.startNodeId()) || nodes.contains(road.endNodeId())) {
                    result.add(road.id());
                    if(road.referenceGeometry().start().distanceTo(port.position())<=contactRange)
                        nodes.add(road.startNodeId());
                    if(road.referenceGeometry().end().distanceTo(port.position())<=contactRange)
                        nodes.add(road.endNodeId());
                    changed=true;
                }
            }
        } while(changed);
        return result;
    }
}
