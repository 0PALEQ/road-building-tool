package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.geometry.*;
import com.cookiecraftmods.roadbuilder.network.*;
import java.util.*;

/** Raises elevated crossings before graph nodes and materialization are prepared. */
public final class RoadOverpassPlanner {
    public static final int MINIMUM_FREE_HEIGHT = 7;
    private record Span(double start, double end, double lift, double approach) {}

    public ReferenceGeometry adjust(ReferenceGeometry geometry, int width, Collection<RoadSegment> existing) {
        if (geometry instanceof RoundaboutGeometry) return geometry;
        geometry = new LaneConnectionPlanner().adjust(geometry, width, existing);
        List<Span> spans = new ArrayList<>();
        for (Coverage cover : coverage(geometry,width,existing)) {
            double start=cover.start, end=cover.end;
            double lift=cover.lift+0.15;
            double approach=Math.max(16,lift*8);
            // A clipped approach still needs enough distance for both halves of
            // the easing curve. Never squeeze a descent into the last few blocks.
            double minimumApproach=lift*6;
            if(start<minimumApproach || geometry.length()-end<minimumApproach)
                throw new IllegalArgumentException("not enough room for a smooth overpass approach");
            spans.add(new Span(start,end,lift,approach));
        }
        if (spans.isEmpty()) return geometry;
        // Existing serialized multi-curves retain the profile across saves and network sync.
        int count = Math.max(2, Math.min(32, (int)Math.ceil(geometry.length()/2)));
        SortedSet<Double> distances = new TreeSet<>();
        for (int i=0; i<=count; i++) distances.add(geometry.length() * (i / (double) count));
        for (Span span : spans) {
            double from=Math.max(0,span.start-span.approach);
            double to=Math.min(geometry.length(),span.end+span.approach);
            distances.add(from);
            distances.add((from+span.start)/2);
            distances.add(span.start);
            distances.add(span.end);
            distances.add((span.end+to)/2);
            distances.add(to);
        }
        if (distances.size()>64) throw new IllegalArgumentException("too many crossings for a single road profile");
        List<RoadPoint> points = new ArrayList<>();
        for (double distance : distances) {
            RoadPoint point = geometry.sample(distance).position();
            double y = point.y();
            for (Span span : spans) {
                double from = Math.max(0, span.start-span.approach);
                double to = Math.min(geometry.length(), span.end+span.approach);
                if (distance < from || distance > to) continue;
                double target;
                if (distance < span.start) {
                    double t=(distance-from)/(span.start-from);
                    target=point.y()+span.lift*ease(t);
                } else if (distance > span.end) {
                    double t=(to-distance)/(to-span.end);
                    target=point.y()+span.lift*ease(t);
                } else target=point.y()+span.lift;
                y=Math.max(y,target);
            }
            if (distance==0 || distance==geometry.length()) y=point.y();
            points.add(new RoadPoint(point.x(),y,point.z()));
        }
        return new MultiCurveGeometry(points);
    }

    private record Coverage(double start,double end,double lift) {}

    private static List<Coverage> coverage(ReferenceGeometry geometry,int width,Collection<RoadSegment> roads) {
        List<Coverage> result=new ArrayList<>();
        int count=Math.max(1,(int)Math.ceil(geometry.length()/0.5));
        List<RoadPoint> proposedPoints=new ArrayList<>();
        for(int i=0;i<=count;i++) proposedPoints.add(geometry.sample(geometry.length() * (i / (double) count)).position());
        for(var road:roads) {
            var lower=road.referenceGeometry();
            int chords=Math.max(1,(int)Math.ceil(lower.length()/2));
            List<RoadPoint> points=new ArrayList<>();
            for(int i=0;i<=chords;i++) points.add(lower.sample(lower.length() * (i / (double) chords)).position());
            double runStart=-1, lift=0;
            double radius=(width+road.presetSnapshot().surfaceBlockWidth())/2.0+1;
            for(int i=0;i<=count;i++) {
                double distance=geometry.length() * (i / (double) count);
                var point=proposedPoints.get(i);
                double nearest=Double.POSITIVE_INFINITY, lowerY=0;
                for(int j=0;j<chords;j++) {
                    var first=points.get(j);var last=points.get(j+1);
                    if(point.x()<Math.min(first.x(),last.x())-radius || point.x()>Math.max(first.x(),last.x())+radius
                            || point.z()<Math.min(first.z(),last.z())-radius || point.z()>Math.max(first.z(),last.z())+radius) continue;
                    double dx=last.x()-first.x(),dz=last.z()-first.z();
                    double divisor=dx*dx+dz*dz;
                    if(divisor<1e-10) continue;
                    double t=Math.max(0,Math.min(1,((point.x()-first.x())*dx+(point.z()-first.z())*dz)/divisor));
                    double separation=Math.hypot(point.x()-first.x()-t*dx,point.z()-first.z()-t*dz);
                    if(separation<nearest){nearest=separation;lowerY=first.y()+(last.y()-first.y())*t;}
                }
                double required=Math.floor(lowerY+0.5)+1+MINIMUM_FREE_HEIGHT+RoadStructurePlanner.DECK_THICKNESS;
                double deficit=required-point.y();
                boolean overlaps=nearest<=radius && point.y()>lowerY+1 && deficit>1e-9;
                if(overlaps) {
                    if(runStart<0) runStart=Math.max(0,distance-0.5);
                    lift=Math.max(lift,deficit);
                }
                if(runStart>=0 && (!overlaps || i==count)) {
                    result.add(new Coverage(runStart,distance,lift));
                    runStart=-1;lift=0;
                }
            }
        }
        result.sort(Comparator.comparingDouble(Coverage::start));
        List<Coverage> merged=new ArrayList<>();
        for(var next:result) {
            if(!merged.isEmpty() && next.start<=merged.get(merged.size()-1).end+16) {
                var previous=merged.remove(merged.size()-1);
                merged.add(new Coverage(previous.start,Math.max(previous.end,next.end),Math.max(previous.lift,next.lift)));
            } else merged.add(next);
        }
        return merged;
    }

    /** Two quadratic halves join with equal slope and level ends. */
    static double ease(double t) {
        return t < 0.5 ? 2*t*t : 1-2*(1-t)*(1-t);
    }
}
