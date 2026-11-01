package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.geometry.*;
import com.cookiecraftmods.roadbuilder.network.*;
import java.util.*;

/** A level departure, a bounded ramp and a level arrival, independent of plan-view handles. */
final class LaneConnectionElevationPlanner {
    static final double MAXIMUM_GRADE=0.25;
    private static final double EASING_FRACTION=0.15;
    private record Chord(RoadPoint a,RoadPoint b,double halfWidth) {}
    private record Projection(double distance,double y,double halfWidth) {}
    private final int width;
    private final List<Chord> source,destination;

    LaneConnectionElevationPlanner(int width,Collection<RoadSegment> roads,
            Optional<AuxiliaryLanePorts.Port> start,Optional<AuxiliaryLanePorts.Port> end) {
        this.width=width;
        source=chords(roads,start.map(p->LaneConnectionPlanner.connectedHost(p,width,roads)).orElse(Set.of()));
        destination=chords(roads,end.map(p->LaneConnectionPlanner.connectedHost(p,width,roads)).orElse(Set.of()));
    }

    ReferenceGeometry adjust(CubicBezierGeometry geometry) {
        double startY=geometry.start().y(),endY=geometry.end().y();
        if(Math.abs(startY-endY)<1e-8) return geometry;
        var flat=new CubicBezierGeometry(atY(geometry.start(),0),atY(geometry.startControl(),0),
                atY(geometry.endControl(),0),atY(geometry.end(),0));
        double length=flat.length();
        double startHold=hold(flat,source,false),endHold=hold(flat,destination,true);
        double from=startHold,to=length-endHold,run=to-from;
        double fromY=source.isEmpty()?startY:project(flat.sample(from).position(),source).y;
        double toY=destination.isEmpty()?endY:project(flat.sample(to).position(),destination).y;
        if(run<=0 || Math.abs(toY-fromY)/(run*(1-EASING_FRACTION))>MAXIMUM_GRADE)
            throw new IllegalArgumentException("not enough separated road length for a gentle lane ramp");
        SortedSet<Double> distances=new TreeSet<>();
        distances.add(0.0); distances.add(length);
        for(int i=1;i<12;i++) distances.add(length*(i/12.0));
        distances.add(from); distances.add(to);
        distances.add(from+run*EASING_FRACTION); distances.add(to-run*EASING_FRACTION);
        List<Double> marks=new ArrayList<>();
        for(double d:distances) if(marks.isEmpty() || d-marks.get(marks.size()-1)>1e-6) marks.add(d);
        List<RoadPoint> controls=new ArrayList<>();
        controls.add(geometry.start());
        for(int i=0;i<marks.size()-1;i++) {
            double a=marks.get(i),b=marks.get(i+1);
            double u=flat.parameterAtDistance(a),v=flat.parameterAtDistance(b);
            var span=flat.slice(u,v);
            double ya=height(flat,a,from,to,fromY,toY,startY,endY);
            double yb=height(flat,b,from,to,fromY,toY,startY,endY);
            double ga=slope(flat,a,from,to,fromY,toY,startY,endY);
            double gb=slope(flat,b,from,to,fromY,toY,startY,endY);
            controls.add(atY(span.startControl(),ya+ga*flat.derivativeAt(u).horizontalLength()*(v-u)/3));
            controls.add(atY(span.endControl(),yb-gb*flat.derivativeAt(v).horizontalLength()*(v-u)/3));
            controls.add(i==marks.size()-2 ? geometry.end() : atY(span.end(),yb));
        }
        var result=MultiCurveGeometry.fromControlPoints(controls);
        for(var span:result.spans()) for(int i=0;i<=16;i++) {
            var tangent=span.derivativeAt(i/16.0);
            if(Math.abs(tangent.y())/tangent.horizontalLength()>MAXIMUM_GRADE+0.015)
                throw new IllegalArgumentException("lane ramp exceeds the maximum grade");
        }
        return result;
    }

    private double hold(CubicBezierGeometry flat,List<Chord> host,boolean reverse) {
        if(host.isEmpty()) return Math.min(4,flat.length()*0.025);
        int count=Math.max(1,(int)Math.ceil(flat.length()/0.5));
        for(int i=0;i<=count;i++) {
            double d=flat.length()*(i/(double)count);
            var projection=project(flat.sample(reverse?flat.length()-d:d).position(),host);
            if(projection.distance>width/2.0+projection.halfWidth+0.5) return Math.min(flat.length(),d+0.5);
        }
        return flat.length();
    }

    private double height(CubicBezierGeometry flat,double d,double from,double to,
            double fromY,double toY,double startY,double endY) {
        if(d<=from) return source.isEmpty()?startY:project(flat.sample(d).position(),source).y;
        if(d>=to) return destination.isEmpty()?endY:project(flat.sample(d).position(),destination).y;
        double t=(d-from)/(to-from),r=EASING_FRACTION;
        double progress=t<r?t*t/(2*r*(1-r)):t>1-r?1-(1-t)*(1-t)/(2*r*(1-r)):(t-r/2)/(1-r);
        return fromY+(toY-fromY)*progress;
    }

    private double slope(CubicBezierGeometry flat,double d,double from,double to,
            double fromY,double toY,double startY,double endY) {
        if(d>=from && d<=to) {
            double t=(d-from)/(to-from),r=EASING_FRACTION;
            double derivative=t<r?t/(r*(1-r)):t>1-r?(1-t)/(r*(1-r)):1/(1-r);
            return (toY-fromY)/(to-from)*derivative;
        }
        double a=Math.max(0,d-0.01),b=Math.min(flat.length(),d+0.01);
        if(d<from) b=Math.min(b,from); else a=Math.max(a,to);
        if(b-a<1e-9) return 0;
        return (height(flat,b,from,to,fromY,toY,startY,endY)-height(flat,a,from,to,fromY,toY,startY,endY))/(b-a);
    }

    private static RoadPoint atY(RoadPoint p,double y) { return new RoadPoint(p.x(),y,p.z()); }

    private static List<Chord> chords(Collection<RoadSegment> roads,Set<RoadSegmentId> ids) {
        List<Chord> result=new ArrayList<>();
        for(var road:roads) if(ids.contains(road.id())) {
            var g=road.referenceGeometry();
            int count=Math.max(1,(int)Math.ceil(g.length()/2));
            // Divide first so the last sample is exactly length(), rather than
            // one floating-point step beyond the geometry's valid interval.
            for(int i=0;i<count;i++) result.add(new Chord(g.sample(g.length()*(i/(double)count)).position(),
                    g.sample(g.length()*((i+1)/(double)count)).position(),road.presetSnapshot().surfaceBlockWidth()/2.0));
        }
        return result;
    }

    private static Projection project(RoadPoint p,List<Chord> chords) {
        Projection best=new Projection(Double.POSITIVE_INFINITY,0,0);
        for(var c:chords) {
            double dx=c.b.x()-c.a.x(),dz=c.b.z()-c.a.z(),denominator=dx*dx+dz*dz;
            if(denominator<1e-10) continue;
            double t=Math.max(0,Math.min(1,((p.x()-c.a.x())*dx+(p.z()-c.a.z())*dz)/denominator));
            double distance=Math.hypot(p.x()-c.a.x()-t*dx,p.z()-c.a.z()-t*dz);
            if(distance<best.distance) best=new Projection(distance,c.a.y()+(c.b.y()-c.a.y())*t,c.halfWidth);
        }
        return best;
    }
}
