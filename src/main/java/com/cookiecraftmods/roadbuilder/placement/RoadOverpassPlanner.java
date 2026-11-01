package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.geometry.*;
import com.cookiecraftmods.roadbuilder.network.*;
import java.util.*;

/** Fits separated crossings above and below roads before graph materialization. */
public final class RoadOverpassPlanner {
    public static final int MINIMUM_FREE_HEIGHT = 5;
    private record Span(double start, double end, double lift, double approach) {}

    public ReferenceGeometry adjust(ReferenceGeometry geometry, int width, Collection<RoadSegment> existing) {
        return adjust(geometry,width,existing,width/2.0+0.5);
    }

    public ReferenceGeometry adjust(ReferenceGeometry geometry, int width, Collection<RoadSegment> existing,
                                    double minimumRadius) {
        if (geometry instanceof RoundaboutGeometry) return geometry;
        geometry = new LaneConnectionPlanner().adjust(geometry, width, existing,minimumRadius);
        List<Span> spans = new ArrayList<>();
        for (Coverage cover : coverage(geometry,width,existing)) {
            double start=cover.start, end=cover.end;
            double lift=cover.lift+Math.copySign(0.15,cover.lift);
            double approach=Math.max(24,Math.abs(lift)*12);
            // A clipped approach still needs enough distance for both halves of
            // the easing curve. Never squeeze a descent into the last few blocks.
            double minimumApproach=Math.abs(lift)*10;
            if(start<minimumApproach || geometry.length()-end<minimumApproach)
                throw new IllegalArgumentException("not enough room for a smooth separated crossing approach");
            spans.add(new Span(start,end,lift,approach));
        }
        if (spans.isEmpty()) return geometry;
        for(int i=0;i<spans.size();i++) for(int j=i+1;j<spans.size();j++) {
            var a=spans.get(i);var b=spans.get(j);
            if(Math.signum(a.lift)!=Math.signum(b.lift)
                    && a.start-a.approach<b.end+b.approach
                    && b.start-b.approach<a.end+a.approach)
                throw new IllegalArgumentException("not enough room between overpass and underpass approaches");
        }
        List<CubicBezierGeometry> bases=cubicSpans(geometry);
        if(!bases.isEmpty()) return raiseCubics(bases,geometry.length(),spans);
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
                if (Math.abs(target-point.y())>Math.abs(y-point.y())) y=target;
            }
            if (distance==0 || distance==geometry.length()) y=point.y();
            points.add(new RoadPoint(point.x(),y,point.z()));
        }
        return new MultiCurveGeometry(points);
    }

    private static List<CubicBezierGeometry> cubicSpans(ReferenceGeometry geometry) {
        if(geometry instanceof MultiCurveGeometry multi) return multi.spans();
        if(geometry instanceof CubicBezierGeometry cubic) return List.of(cubic);
        if(geometry instanceof StraightGeometry straight) {
            var chord=straight.start().vectorTo(straight.end());
            return List.of(new CubicBezierGeometry(straight.start(),straight.start().add(chord.scale(1.0/3)),
                    straight.start().add(chord.scale(2.0/3)),straight.end()));
        }
        if(geometry instanceof QuadraticBezierGeometry quadratic) return List.of(new CubicBezierGeometry(
                quadratic.start(),quadratic.start().add(quadratic.start().vectorTo(quadratic.control()).scale(2.0/3)),
                quadratic.end().add(quadratic.end().vectorTo(quadratic.control()).scale(2.0/3)),quadratic.end()));
        return List.of();
    }

    /** Split existing cubics exactly in XZ; an elevation change must not refit the turn. */
    private static ReferenceGeometry raiseCubics(List<CubicBezierGeometry> bases,double length,List<Span> spans) {
        List<RoadPoint> controls=new ArrayList<>();
        NavigableSet<Double> breaks=new TreeSet<>();
        breaks.add(0.0);
        double boundary=0;
        for(var base:bases) breaks.add(boundary+=base.length());
        // Crossing boundaries must be exact to keep the entire deck clear. Extra
        // ramp samples are optional within the serialized 64-control-point budget.
        for(var span:spans) {
            addProfileMark(breaks,span.start);
            addProfileMark(breaks,span.end);
        }
        for(var span:spans) {
            double from=Math.max(0,span.start-span.approach),to=Math.min(length,span.end+span.approach);
            for(double mark:new double[]{from,to,(from+span.start)/2,(span.end+to)/2})
                if(breaks.size()<22) addProfileMark(breaks,mark);
        }
        double offset=0;
        for(var base:bases) {
            SortedSet<Double> marks=new TreeSet<>();
            marks.add(0.0); marks.add(base.length());
            for(double mark:breaks) {
                double local=mark-offset;
                if(local>1e-6 && local<base.length()-1e-6) marks.add(local);
            }
            var distances=new ArrayList<>(marks);
            for(int i=0;i<distances.size()-1;i++) {
                double a=distances.get(i),b=distances.get(i+1);
                double u=a==0?0:base.parameterAtDistance(a),v=b==base.length()?1:base.parameterAtDistance(b);
                var slice=base.slice(u,v);
                var first=lift(offset+a,length,spans); var last=lift(offset+b,length,spans);
                if(controls.isEmpty()) controls.add(slice.start().add(new RoadVector(0,first[0],0)));
                controls.add(slice.startControl().add(new RoadVector(0,
                        first[0]+first[1]*base.derivativeAt(u).length()*(v-u)/3,0)));
                controls.add(slice.endControl().add(new RoadVector(0,
                        last[0]-last[1]*base.derivativeAt(v).length()*(v-u)/3,0)));
                controls.add(slice.end().add(new RoadVector(0,last[0],0)));
            }
            offset+=base.length();
        }
        if(controls.size()>64) throw new IllegalArgumentException("too many crossings for a single road profile");
        return MultiCurveGeometry.fromControlPoints(controls);
    }

    private static void addProfileMark(NavigableSet<Double> marks,double mark) {
        Double lower=marks.floor(mark),upper=marks.ceiling(mark);
        if(lower!=null && mark-lower<=1e-6 || upper!=null && upper-mark<=1e-6) return;
        marks.add(mark);
    }

    /** Added height and its derivative with respect to the original arc length. */
    private static double[] lift(double distance,double length,List<Span> spans) {
        double height=0,slope=0;
        for(var span:spans) {
            double from=Math.max(0,span.start-span.approach),to=Math.min(length,span.end+span.approach);
            if(distance<=from || distance>=to) continue;
            double value=span.lift,derivative=0;
            if(distance<span.start) {
                double t=(distance-from)/(span.start-from);
                value*=ease(t); derivative=span.lift*4*Math.min(t,1-t)/(span.start-from);
            } else if(distance>span.end) {
                double t=(to-distance)/(to-span.end);
                value*=ease(t); derivative=-span.lift*4*Math.min(t,1-t)/(to-span.end);
            }
            if(Math.abs(value)>Math.abs(height)) {height=value;slope=derivative;}
        }
        return new double[]{height,slope};
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
                // Keep at-grade intersections intact. The existing vertical order
                // determines whether this road passes above or below the host.
                double separationY=point.y()-lowerY;
                boolean above=separationY>1;
                double required=above
                        ? Math.floor(lowerY+0.5)+1+MINIMUM_FREE_HEIGHT+RoadStructurePlanner.DECK_THICKNESS
                        : Math.floor(lowerY+0.5)-RoadStructurePlanner.DECK_THICKNESS-MINIMUM_FREE_HEIGHT-1;
                double deficit=required-point.y();
                boolean overlaps=nearest<=radius && Math.abs(separationY)>1
                        && (above ? deficit>1e-9 : deficit< -1e-9);
                if(runStart>=0 && overlaps && Math.signum(lift)!=Math.signum(deficit)) {
                    result.add(new Coverage(runStart,distance,lift));
                    runStart=-1;lift=0;
                }
                if(overlaps) {
                    if(runStart<0) runStart=Math.max(0,distance-0.5);
                    if(Math.abs(deficit)>Math.abs(lift)) lift=deficit;
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
            if(!merged.isEmpty() && Math.signum(next.lift)==Math.signum(merged.get(merged.size()-1).lift)
                    && next.start<=merged.get(merged.size()-1).end+48) {
                var previous=merged.remove(merged.size()-1);
                merged.add(new Coverage(previous.start,Math.max(previous.end,next.end),Math.abs(previous.lift)>Math.abs(next.lift)?previous.lift:next.lift));
            } else merged.add(next);
        }
        return merged;
    }

    /** Two quadratic halves join with equal slope and level ends. */
    static double ease(double t) {
        return t < 0.5 ? 2*t*t : 1-2*(1-t)*(1-t);
    }
}
