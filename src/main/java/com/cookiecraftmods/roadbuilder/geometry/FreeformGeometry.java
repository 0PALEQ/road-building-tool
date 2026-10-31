package com.cookiecraftmods.roadbuilder.geometry;

/** Deterministic automatic handles; the cursor specifies an endpoint, not a stroke. */
public final class FreeformGeometry {
    private FreeformGeometry() { }

    /**
     * Three-click tool: only the entry direction is supplied by the player.
     * Reflecting it about the chord gives symmetric endpoint tangents; equal
     * handles balance both halves. Entry magnitude deliberately has no effect.
     * This approximates a circular arc, rather than promising constant curvature.
     */
    public static CubicBezierGeometry createBalanced(RoadPoint start, RoadPoint end,
                                                     RoadVector entry) {
        return create(start, end, entry, null);
    }

    public static CubicBezierGeometry create(RoadPoint start, RoadPoint end,
                                             RoadVector entry, RoadVector exit) {
        RoadVector chord = start.vectorTo(end);
        double distance = chord.horizontalLength();
        if (distance < 0.05) {
            throw new IllegalArgumentException("freeform endpoints must differ horizontally");
        }
        RoadVector forward = new RoadVector(chord.x() / distance, 0, chord.z() / distance);
        RoadVector incoming = horizontal(entry == null ? chord : entry);
        // Reflect the entry direction about the chord to obtain an arc-like exit.
        RoadVector outgoing = exit == null
                ? forward.scale(2 * incoming.dot(forward)).subtract(incoming)
                : horizontal(exit);
        double handle = exit == null
                ? distance * 2 / (3 * Math.max(0.5, 1 + incoming.dot(forward)))
                : distance / 3;
        // Interpolate elevation independently; mouse movement controls the plan view.
        RoadPoint first = new RoadPoint(start.x() + incoming.x() * handle,
                start.y() + chord.y() / 3, start.z() + incoming.z() * handle);
        RoadPoint second = new RoadPoint(end.x() - outgoing.x() * handle,
                end.y() - chord.y() / 3, end.z() - outgoing.z() * handle);
        var original=new CubicBezierGeometry(start, first, second, end);
        if(exit==null) return original;
        // With two independent tangents there is no circular-arc handle formula.
        // Balance curvature across the entire available span instead of fixing
        // both handles to one third of the chord.
        CubicBezierGeometry best=original;
        double bestScore=bendScore(original,forward,distance);
        double firstLength=distance/3, secondLength=distance/3;
        for(int pass=0;pass<3;pass++) {
            double step=distance*(pass==0 ? 0.1 : pass==1 ? 0.025 : 0.00625);
            double centerFirst=firstLength, centerSecond=secondLength;
            int range=pass==0 ? 3 : 2;
            for(int i=-range;i<=range;i++) for(int j=-range;j<=range;j++) {
                double a=centerFirst+i*step, b=centerSecond+j*step;
                if(a<distance*0.1 || b<distance*0.1 || a>distance*0.75 || b>distance*0.75) continue;
                var candidate=new CubicBezierGeometry(start,
                        new RoadPoint(start.x()+incoming.x()*a,first.y(),start.z()+incoming.z()*a),
                        new RoadPoint(end.x()-outgoing.x()*b,second.y(),end.z()-outgoing.z()*b),end);
                double score=bendScore(candidate,forward,distance);
                if(score<bestScore) { best=candidate; bestScore=score; firstLength=a; secondLength=b; }
            }
        }
        return best;
    }

    private static double bendScore(CubicBezierGeometry curve,RoadVector forward,double distance) {
        double peak=0, energy=0;
        for(int i=0;i<=64;i++) {
            double t=i/64.0;
            // Do not trade an ordinary connection for a loop or a reversal.
            if(curve.derivativeAt(t).dot(forward)<-1e-8) return Double.POSITIVE_INFINITY;
            double curvature=curve.horizontalCurvatureAt(t)*distance;
            peak=Math.max(peak,curvature);
            energy+=curvature*curvature/65;
        }
        return peak+0.01*energy;
    }

    private static RoadVector horizontal(RoadVector direction) {
        if (direction.horizontalLength() < 1e-10) {
            throw new IllegalArgumentException("freeform needs a horizontal tangent");
        }
        return new RoadVector(direction.x(), 0, direction.z())
                .scale(1 / direction.horizontalLength());
    }
}
