package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.geometry.*;
import com.cookiecraftmods.roadbuilder.preset.RoadClass;

/** Shared client/server construction policy. Distances are Minecraft blocks, not CS2 metres. */
public final class RoadCurveValidation {
    private RoadCurveValidation() { }

    public static double minimumRadius(double width, RoadClass roadClass) {
        if (!Double.isFinite(width) || width <= 0) throw new IllegalArgumentException("invalid width");
        double classRadius = switch (roadClass) {
            case ALLEY -> 2;
            case LOCAL -> 4;
            case COLLECTOR -> 6;
            case ARTERIAL -> 10;
            case HIGHWAY -> 16;
        };
        return Math.max(width / 2 + 0.5, classRadius);
    }

    /** Null means valid. Internal junction connectors are deliberately outside this policy. */
    public static BlockPlanFailureCode validate(ReferenceGeometry geometry, double minimumRadius) {
        if (!Double.isFinite(minimumRadius) || minimumRadius <= 0) {
            throw new IllegalArgumentException("invalid minimum radius");
        }
        if (geometry instanceof OffsetGeometry offset) {
            // Conservative for either side: also prevent offset cusps/folded road edges.
            return validate(offset.reference(), minimumRadius + Math.abs(offset.lateralOffset()));
        }
        if (geometry instanceof QuadraticBezierGeometry quadratic) {
            RoadPoint p = quadratic.control();
            geometry = new CubicBezierGeometry(quadratic.start(),
                    quadratic.start().add(quadratic.start().vectorTo(p).scale(2.0 / 3)),
                    quadratic.end().add(quadratic.end().vectorTo(p).scale(2.0 / 3)), quadratic.end());
        }
        if (geometry instanceof MultiCurveGeometry multi) {
            for (CubicBezierGeometry span : multi.spans()) {
                BlockPlanFailureCode failure = validate(span, minimumRadius);
                if (failure != null) return failure;
            }
        }
        if (geometry instanceof CubicBezierGeometry cubic) {
            if (selfIntersects(cubic)) return BlockPlanFailureCode.SELF_INTERSECTION;
            if (!certifyRadius(cubic, minimumRadius, 0, 1, 0, new int[]{8192})) {
                return BlockPlanFailureCode.TOO_SHARP_TURN;
            }
        }
        return null;
    }

    private static boolean certifyRadius(CubicBezierGeometry curve, double radius,
            double from, double to, int depth, int[] budget) {
        if (--budget[0] < 0) return false;
        double middle = (from + to) / 2;
        for (double t : new double[]{from, middle, to}) {
            if (curve.horizontalCurvatureAt(t) * radius > 1 + 1e-9) return false;
        }
        RoadVector v0 = curve.derivativeAt(from);
        RoadVector v2 = curve.derivativeAt(to);
        RoadVector a0 = curve.secondDerivativeAt(from);
        RoadVector a1 = curve.secondDerivativeAt(to);
        // Restricted quadratic Bernstein derivative controls enclose every velocity.
        RoadVector v1 = v0.add(a0.scale((to - from) / 2));
        double minX = Math.min(v0.x(), Math.min(v1.x(), v2.x()));
        double maxX = Math.max(v0.x(), Math.max(v1.x(), v2.x()));
        double minZ = Math.min(v0.z(), Math.min(v1.z(), v2.z()));
        double maxZ = Math.max(v0.z(), Math.max(v1.z(), v2.z()));
        double speedLower = Math.hypot(distanceToZero(minX, maxX), distanceToZero(minZ, maxZ));
        double crossUpper = 0;
        for (RoadVector v : new RoadVector[]{v0, v1, v2}) {
            for (RoadVector a : new RoadVector[]{a0, a1}) {
                crossUpper = Math.max(crossUpper, Math.abs(v.x() * a.z() - v.z() * a.x()));
            }
        }
        if (speedLower > 1e-10 && crossUpper * radius <= Math.pow(speedLower, 3) * (1 + 1e-9)) {
            return true;
        }
        // Fail closed when a cusp or threshold cannot be certified within the work budget.
        return depth < 18
                && certifyRadius(curve, radius, from, middle, depth + 1, budget)
                && certifyRadius(curve, radius, middle, to, depth + 1, budget);
    }

    private static double distanceToZero(double minimum, double maximum) {
        return minimum > 0 ? minimum : maximum < 0 ? -maximum : 0;
    }

    private static double cross(RoadVector a, RoadVector b) {
        return a.x() * b.z() - a.z() * b.x();
    }

    /** Solve B(t)=B(u), t!=u, in plan view; elevated crossings remain possible. */
    static boolean selfIntersects(CubicBezierGeometry curve) {
        RoadVector d0 = curve.start().vectorTo(curve.startControl());
        RoadVector d1 = curve.startControl().vectorTo(curve.endControl());
        RoadVector d2 = curve.endControl().vectorTo(curve.end());
        RoadVector a = d0.subtract(d1.scale(2)).add(d2);
        RoadVector b = d1.subtract(d0).scale(3);
        RoadVector c = d0.scale(3);
        double determinant = cross(a, b);
        if (Math.abs(determinant) < 1e-12) return false; // Collinear reversals fail radius certification.
        double sum = -cross(a, c) / determinant;
        double q = cross(b, c) / determinant;
        double discriminant = 4 * q - 3 * sum * sum;
        if (discriminant <= 1e-12) return false;
        double first = (sum - Math.sqrt(discriminant)) / 2;
        double second = (sum + Math.sqrt(discriminant)) / 2;
        return first >= 0 && second <= 1
                && Math.abs(curve.positionAt(first).y() - curve.positionAt(second).y()) < 0.5;
    }
}
