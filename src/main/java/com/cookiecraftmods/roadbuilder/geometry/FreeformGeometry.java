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
        return new CubicBezierGeometry(start, first, second, end);
    }

    private static RoadVector horizontal(RoadVector direction) {
        if (direction.horizontalLength() < 1e-10) {
            throw new IllegalArgumentException("freeform needs a horizontal tangent");
        }
        return new RoadVector(direction.x(), 0, direction.z())
                .scale(1 / direction.horizontalLength());
    }
}
