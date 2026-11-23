package com.cookiecraftmods.roadbuilder.geometry;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

public final class JunctionElevationProfile {
    private JunctionElevationProfile() {}

    public static ReferenceGeometry fit(ReferenceGeometry source, double startHold, double endHold) {
        if (startHold <= 0 && endHold <= 0) return source;
        List<CubicBezierGeometry> bases = cubics(source).stream().map(c -> new CubicBezierGeometry(
                atY(c.start(), 0), atY(c.startControl(), 0), atY(c.endControl(), 0), atY(c.end(), 0))).toList();
        if (bases.isEmpty()) return source;
        double length = bases.stream().mapToDouble(CubicBezierGeometry::length).sum();
        if (levelEnd(source, Math.min(startHold,length*0.25), false)
                && levelEnd(source, Math.min(endHold,length*0.25), true)) return source;
        double startGrade = startHold > 0 ? 0 : grade(source.sample(0));
        double endGrade = endHold > 0 ? 0 : grade(source.sample(source.length()));
        double y0 = source.start().y(), y1 = source.end().y();
        if (Math.abs(y1-y0)<1e-8 && Math.abs(startGrade)+Math.abs(endGrade)<1e-8
                && cubics(source).stream().flatMap(c -> c.controlPoints().stream()).allMatch(p -> Math.abs(p.y()-y0)<1e-8))
            return source;
        // Do not consume most of a short road with a level platform. The rest
        // is a constant grade joined by quadratic vertical curves at both ends.
        double a = Math.min(startHold, length*0.25);
        double b = length-Math.min(endHold, length*0.25);
        double ramp = (b-a)*0.2;
        double cruise = (y1-y0-ramp*(startGrade+endGrade)/2)/(b-a-ramp);
        var profile = new Profile(a,b,ramp,y0,y1,startGrade,endGrade,cruise);
        List<RoadPoint> controls = new ArrayList<>();
        double offset = 0;
        for (var base : bases) {
            TreeSet<Double> cuts = new TreeSet<>();
            cuts.add(0.0); cuts.add(base.length());
            for (double boundary : new double[]{a,a+ramp,b-ramp,b})
                if (boundary>offset+1e-7 && boundary<offset+base.length()-1e-7) cuts.add(boundary-offset);
            int count = Math.max(1,Math.min(16/bases.size(),(int)Math.ceil(base.length()/8)));
            for (int i=1;i<count;i++) {
                double cut=base.length()*i/count;
                if (cuts.stream().noneMatch(d -> Math.abs(d-cut)<1e-7)) cuts.add(cut);
            }
            var distances = new ArrayList<>(cuts);
            for (int i=0;i<distances.size()-1;i++) {
                double from=distances.get(i), to=distances.get(i+1);
                double u=base.parameterAtDistance(from), v=base.parameterAtDistance(to);
                var slice=base.slice(u,v);
                RoadPoint start=atY(slice.start(),profile.height(offset+from));
                RoadPoint end=atY(slice.end(),profile.height(offset+to));
                if (controls.isEmpty()) controls.add(start);
                controls.add(atY(slice.startControl(), start.y()+profile.slope(offset+from)
                        *base.derivativeAt(u).horizontalLength()*(v-u)/3));
                controls.add(atY(slice.endControl(), end.y()-profile.slope(offset+to)
                        *base.derivativeAt(v).horizontalLength()*(v-u)/3));
                controls.add(end);
            }
            offset+=base.length();
        }
        controls.set(0,source.start()); controls.set(controls.size()-1,source.end());
        return controls.size()==4 ? new CubicBezierGeometry(controls.get(0),controls.get(1),controls.get(2),controls.get(3))
                : MultiCurveGeometry.fromControlPoints(controls);
    }

    private record Profile(double a,double b,double ramp,double y0,double y1,double g0,double g1,double cruise) {
        double height(double d) {
            if (d<=a) return y0;
            if (d>=b) return y1;
            if (d<a+ramp) { double x=d-a; return y0+g0*x+(cruise-g0)*x*x/(2*ramp); }
            if (d>b-ramp) { double x=b-d; return y1-g1*x-(cruise-g1)*x*x/(2*ramp); }
            return y0+(g0+cruise)*ramp/2+cruise*(d-a-ramp);
        }
        double slope(double d) {
            if (d<=a) return g0;
            if (d>=b) return g1;
            if (d<a+ramp) return g0+(cruise-g0)*(d-a)/ramp;
            if (d>b-ramp) return g1+(cruise-g1)*(b-d)/ramp;
            return cruise;
        }
    }
    private static double grade(PathSample sample) { return sample.tangent().y()/sample.tangent().horizontalLength(); }
    private static boolean levelEnd(ReferenceGeometry geometry,double hold,boolean reverse) {
        if (hold<=0) return true;
        double y=reverse?geometry.end().y():geometry.start().y();
        int count=Math.max(1,(int)Math.ceil(hold*4));
        for(int i=0;i<=count;i++) {
            double d=hold*i/count;
            var sample=geometry.sample(reverse?geometry.length()-d:d);
            if(Math.abs(sample.position().y()-y)>1e-7 || Math.abs(sample.tangent().y())>1e-7) return false;
        }
        return true;
    }
    private static RoadPoint atY(RoadPoint p,double y) { return new RoadPoint(p.x(),y,p.z()); }
    private static List<CubicBezierGeometry> cubics(ReferenceGeometry geometry) {
        if (geometry instanceof MultiCurveGeometry multi) return multi.spans();
        if (geometry instanceof CubicBezierGeometry cubic) return List.of(cubic);
        if (geometry instanceof StraightGeometry straight) {
            var delta=straight.start().vectorTo(straight.end());
            return List.of(new CubicBezierGeometry(straight.start(),straight.start().add(delta.scale(1.0/3)),
                    straight.start().add(delta.scale(2.0/3)),straight.end()));
        }
        if (geometry instanceof QuadraticBezierGeometry q) return List.of(new CubicBezierGeometry(q.start(),
                q.start().add(q.start().vectorTo(q.control()).scale(2.0/3)),
                q.end().add(q.end().vectorTo(q.control()).scale(2.0/3)),q.end()));
        if (geometry instanceof FreehandGeometry free) return free.anchors().size()==2
                ? cubics(new StraightGeometry(free.start(),free.end())) : new MultiCurveGeometry(free.anchors()).spans();
        return List.of();
    }
}
